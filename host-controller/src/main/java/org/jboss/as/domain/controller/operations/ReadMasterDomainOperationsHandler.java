/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.domain.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.TestBootTimeTestThing;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.transform.Transformers;
import org.jboss.as.host.controller.mgmt.DomainControllerRuntimeIgnoreTransformationRegistry;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author Emanuel Muckenhuber
 */
public class ReadMasterDomainOperationsHandler implements OperationStepHandler {

    private static final PathAddressFilter FILTER = new PathAddressFilter(true);
    public static final OperationStepHandler INSTANCE = new ReadMasterDomainOperationsHandler();
    public static final SimpleOperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder("model-operations", ControllerResolver.getResolver(SUBSYSTEM))
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.READ_WHOLE_CONFIG)
            .setReplyType(ModelType.LIST)
            .setReplyValueType(ModelType.OBJECT)
            .setPrivateEntry()
            .build();

    static {
        FILTER.addReject(PathAddress.pathAddress(PathElement.pathElement(EXTENSION)));
        FILTER.addReject(PathAddress.pathAddress(PathElement.pathElement(HOST)));
    }

    ReadMasterDomainOperationsHandler() {
        //
    }

    @Override
    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
        context.acquireControllerLock();
        context.attach(PathAddressFilter.KEY, FILTER);
        context.addStep(operation, GenericModelDescribeOperationHandler.INSTANCE, OperationContext.Stage.MODEL);

        final ModelNode compareOp = new ModelNode();
        compareOp.get(OP).set("compare");
        compareOp.get(OP_ADDR).setEmptyList();

        context.addStep(compareOp, new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

                final ModelNode result = context.getResult();
                final List<ModelNode> operations = result.asList();
                final Resource original = context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS);
                final Resource updated = TestBootTimeTestThing.runOperations(operations, context.getResourceRegistrationForUpdate());
                final Report report = new Report();
                compare(PathAddress.EMPTY_ADDRESS, original, updated, context.getResourceRegistration(), report);
                System.out.println(report.toString());
                context.stepCompleted();

            }
        }, OperationContext.Stage.VERIFY);
        context.stepCompleted();
    }

    static List<ModelNode> transformOperations(final Transformers transformers, final DomainControllerRuntimeIgnoreTransformationRegistry ignoredDomainResourceRegistry) {
        return null;
    }


    static void compare(PathAddress address, Resource original, Resource updated, ImmutableManagementResourceRegistration registration, Report report) {

        if (registration == null) {
            report.missingRegistration.add(address);
            return;
        }
        if (registration.isRemote() || registration.isRemote() || registration.isAlias()) {
            // Skip
            return;
        }
        if (original == null && updated == null) {
            return;
        }
        if (original == null) {
            report.unexpected.put(address, updated);
            return;
        }
        if (updated == null) {
            report.missing.put(address, original);
            return;
        }
        compare(address, original.getModel(), updated.getModel(), report);

        final Set<PathElement> expected = new HashSet<>();
        for (final String type : original.getChildTypes()) {
            for (final Resource.ResourceEntry entry : original.getChildren(type)) {
                final PathElement element = entry.getPathElement();
                expected.add(element);
                final ImmutableManagementResourceRegistration childRegistration = registration.getSubModel(PathAddress.EMPTY_ADDRESS.append(element));
                final PathAddress childAddress = address.append(element);
                final Resource childResource = updated.getChild(element);
                compare(childAddress, entry, childResource, childRegistration, report);
            }
        }

        for (final String type : updated.getChildTypes()) {
            for (final Resource.ResourceEntry entry : updated.getChildren(type)) {
                final PathElement element = entry.getPathElement();
                final PathAddress childAddress = address.append(element);
                if (!expected.remove(element)) {
                    report.unexpected.put(childAddress, entry);
                }
            }
        }

    }

    static class Report {

        private final Map<PathAddress, Resource> missing = new LinkedHashMap<>();
        private final Map<PathAddress, Resource> unexpected = new LinkedHashMap<>();
        private final Map<PathAddress, ModelNode> inconsistent = new LinkedHashMap<>();
        private final Set<PathAddress> missingRegistration = new LinkedHashSet<>();

        @Override
        public String toString() {
            return errorReport(this);
        }
    }

    static void compare(final PathAddress address, final ModelNode original, final ModelNode updated, Report report) {
        if (!original.equals(updated)) {
            // TODO better formatting
            report.inconsistent.put(address, updated);
        }
    }

    static String errorReport(final Report report) {
        final StringBuilder builder = new StringBuilder("ERROR");

        for (Map.Entry<PathAddress, Resource> entry : report.missing.entrySet()) {
            report("MISSING", entry, builder);
        }

        for (Map.Entry<PathAddress, Resource> entry : report.unexpected.entrySet()) {
            report("UNEXPECTED", entry, builder);
        }

        for (Map.Entry<PathAddress, ModelNode> entry : report.inconsistent.entrySet()) {
            report("INCONSISTENT", entry.getKey(), entry.getValue(), builder);
        }

        for (PathAddress address : report.missingRegistration) {
            builder.append("REG: ").append(address).append('\n');
        }

        return builder.toString();
    }

    static void report(String prefix, Map.Entry<PathAddress, Resource> entry, StringBuilder builder) {
        report(prefix, entry.getKey(), entry.getValue().getModel(), builder);
    }

    static void report(String prefix, PathAddress address, ModelNode model, StringBuilder builder) {
        builder.append(prefix).append(": ").append(address).append(" ").append(model).append('\n');
    }

}
