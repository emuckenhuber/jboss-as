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

package org.jboss.as.controller;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.audit.AuditLogger;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.client.OperationAttachments;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.persistence.ConfigurationPersistenceException;
import org.jboss.as.controller.persistence.ConfigurationPersister;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceContainer;

/**
 * @author Emanuel Muckenhuber
 */
public class TestBootTimeTestThing {

    static ProcessType processType = ProcessType.STANDALONE_SERVER;
    static RunningMode runningMode = RunningMode.NORMAL;
    static EnumSet<AbstractOperationContext.ContextFlag> contextFlags = EnumSet.of(AbstractOperationContext.ContextFlag.MODEL_ONLY);
    static OperationMessageHandler messageHandler = OperationMessageHandler.DISCARD;
    static OperationAttachments attachments = OperationAttachments.EMPTY;
    static ModelController.OperationTransactionControl transactionControl = ModelController.OperationTransactionControl.COMMIT;
    static ControlledProcessState controlledProcessState = new ControlledProcessState(true);
    static ManagedAuditLogger auditLogger = AuditLogger.NO_OP_LOGGER;
    static boolean booting = true;
    static int operationID = -1;
    static HostServerGroupTracker hostServerGroupTracker = null;

    private ServiceContainer container = ServiceContainer.Factory.create();


    final ModelControllerImpl controller;

    public TestBootTimeTestThing(ManagementResourceRegistration registration) {
        this.controller = new TestController(registration);
    }

    public static Resource runOperations(final List<ModelNode> operations, ManagementResourceRegistration registration) {
        final TestBootTimeTestThing thing = new TestBootTimeTestThing(registration);
        return thing.run(operations);
    }

    Resource run(final List<ModelNode> bootOperations) {

        final Resource resource = Resource.Factory.create();

        OperationContextImpl context = new OperationContextImpl(controller, processType, runningMode, contextFlags, messageHandler, attachments, resource,
                transactionControl, controlledProcessState, auditLogger, booting, operationID, hostServerGroupTracker);

        final ModelNode operation = new ModelNode();
        operation.get(ModelDescriptionConstants.OP).set("test");
        operation.get(ModelDescriptionConstants.OP_ADDR).setEmptyList();

        context.addStep(operation, new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode op) throws OperationFailedException {
                ImmutableManagementResourceRegistration registration = context.getResourceRegistration();
                for (final ModelNode operation : bootOperations) {
                    final String operationName = operation.require(ModelDescriptionConstants.OP).asString();
                    final PathAddress address = PathAddress.pathAddress(operation.require(ModelDescriptionConstants.OP_ADDR));
                    final OperationStepHandler handler = resolveOperationHandler(address, operationName, registration);
                    if (handler != null) {
                        context.addStep(operation, handler, OperationContext.Stage.MODEL);
                    } else {

                        System.out.println(" no step handler for " + address);
                    }

                }
                context.stepCompleted();
            }
        }, OperationContext.Stage.MODEL);

        context.executeOperation();

        return resource;
    }

    static ExpressionResolver resolver = ExpressionResolver.TEST_RESOLVER;

    class TestController extends ModelControllerImpl {

        TestController(ManagementResourceRegistration registration) {
            super(null, container, registration, null, null, processType, null, null, null, null, resolver, null, auditLogger);
        }


        @Override
        ConfigurationPersister.PersistenceResource writeModel(Resource resource, Set<PathAddress> affectedAddresses) throws ConfigurationPersistenceException {
            return new ConfigurationPersister.PersistenceResource() {
                @Override
                public void commit() {

                }

                @Override
                public void rollback() {

                }
            };
        }

        @Override
        void acquireLock(Integer permit, boolean interruptibly, OperationContext context) throws InterruptedException {

        }

        @Override
        void releaseLock(Integer permit) {

        }
    }

    private OperationStepHandler resolveOperationHandler(final PathAddress address, final String operationName, final ImmutableManagementResourceRegistration resourceRegistration) {
        OperationStepHandler result = resourceRegistration.getOperationHandler(address, operationName);
        if (result == null && address.size() > 0) {
            // For wildcard elements, check specific registrations where the same OSH is used
            // for all such registrations
            PathElement pe = address.getLastElement();
            if (pe.isWildcard()) {
                String type = pe.getKey();
                PathAddress parent = address.subAddress(0, address.size() - 1);
                Set<PathElement> children = resourceRegistration.getChildAddresses(parent);
                if (children != null) {
                    OperationStepHandler found = null;
                    for (PathElement child : children) {
                        if (type.equals(child.getKey())) {
                            OperationEntry oe = resourceRegistration.getOperationEntry(parent.append(child), operationName);
                            OperationStepHandler osh = oe == null ? null : oe.getOperationHandler();
                            if (osh == null || (found != null && !found.equals(osh))) {
                                // Not all children have the same handler; give up
                                found = null;
                                break;
                            }
                            // We have a candidate OSH
                            found = osh;
                        }
                    }
                    if (found != null) {
                        result = found;
                    }
                }
            }
        }
        return result;
    }

}
