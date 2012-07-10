/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

package org.jboss.as.host.controller.model.jvm;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.JVM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.transform.OperationResultTransformer;
import org.jboss.as.controller.transform.OperationTransformer;
import org.jboss.as.controller.transform.ResourceTransformationContext;
import org.jboss.as.controller.transform.ResourceTransformer;
import org.jboss.as.controller.transform.TransformationContext;
import org.jboss.as.controller.transform.TransformersSubRegistration;
import org.jboss.dmr.ModelNode;

/**
 * @author Emanuel Muckenhuber
 */
public class JVMTransformer implements OperationTransformer, ResourceTransformer {

    public static final JVMTransformer INSTANCE = new JVMTransformer();
    public static final OperationTransformer OPERATION_TRANSFORMER = INSTANCE;
    public static final ResourceTransformer RESOURCE_TRANSFORMER = INSTANCE;

    /**
     * Register the transformers transforming a (configuration=jvm) to the legacy resource.
     *
     * @param registration the sub registration
     */
    public static void registerSingleTransformers(final TransformersSubRegistration registration) {
        registration.registerSubResource(JVMHandlers.JVM_SINGLE, RESOURCE_TRANSFORMER, OPERATION_TRANSFORMER);
    }

    @Override
    public void transformResource(final ResourceTransformationContext context, final PathAddress current, final Resource resource) throws OperationFailedException {

        // Get and remove the jvm name
        final String jvmName = resource.getModel().remove(JvmAttributes.JVM_REF).asString();
        final PathAddress transformed = current.subAddress(0, current.size() -1).append(PathElement.pathElement(JVM, jvmName));

        final ResourceTransformationContext childContext = context.addTransformedResourceFromRoot(transformed, resource);
        childContext.processChildren(resource);
    }

    @Override
    public TransformedOperation transformOperation(TransformationContext context, PathAddress address, ModelNode operation) throws OperationFailedException {

        final Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS);
        final ModelNode original = resource.getModel();
        final String jvmRef = original.require(JvmAttributes.JVM_REF).asString();
        final PathAddress fixed = fixAddress(address, jvmRef);
        // Transform the operation
        final ModelNode transformed = operation.clone();
        transformed.get(ModelDescriptionConstants.OP_ADDR).set(fixed.toModelNode());
        return new TransformedOperation(transformed, OperationResultTransformer.ORIGINAL_RESULT);
    }

    protected PathAddress fixAddress(final PathAddress address, final String jvmRef) {
        final PathAddress converted = PathAddress.EMPTY_ADDRESS;
        for(final PathElement element : address) {
            if(ModelDescriptionConstants.CONFIGURATION.equals(element.getKey())
                    && ModelDescriptionConstants.JVM.equals(element.getValue())) {
                converted.append(PathElement.pathElement(ModelDescriptionConstants.JVM, jvmRef));
            } else {
                converted.append(element);
            }
        }
        return converted;
    }


}
