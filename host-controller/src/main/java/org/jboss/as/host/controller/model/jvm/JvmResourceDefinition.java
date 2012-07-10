/*
* JBoss, Home of Professional Open Source.
* Copyright 2012, Red Hat Middleware LLC, and individual contributors
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

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.operations.global.WriteAttributeHandlers;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.host.controller.descriptions.HostEnvironmentResourceDescription;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author Emanuel Muckenhuber
 */
public class JvmResourceDefinition extends SimpleResourceDefinition {

    private static PathElement JVM_WILDCARD = PathElement.pathElement(ModelDescriptionConstants.JVM);
    private static PathElement JVM_SINGLE = PathElement.pathElement(ModelDescriptionConstants.CONFIGURATION, ModelDescriptionConstants.JVM);

    public static final JvmResourceDefinition GROUP = new JvmResourceDefinition(JVM_SINGLE, JvmAttributes.getServerGroupAttributes(), false);
    public static final JvmResourceDefinition HOST = new JvmResourceDefinition(JVM_WILDCARD, JvmAttributes.getHostAttributes(), false);
    public static final JvmResourceDefinition SERVER = new JvmResourceDefinition(JVM_SINGLE, JvmAttributes.getServerAttributes(), true);

    private final AttributeDefinition[] attributes;
    private final boolean server;

    protected JvmResourceDefinition(final PathElement path, final AttributeDefinition[] attributes, boolean server) {
        super(path,
                new StandardResourceDescriptionResolver("jvm", HostEnvironmentResourceDescription.class.getPackage().getName() + ".LocalDescriptions", HostEnvironmentResourceDescription.class.getClassLoader(), true, false),
                new JVMAddHandler(attributes),
                JVMRemoveHandler.INSTANCE);
        this.attributes = attributes;
        this.server = server;
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        for (AttributeDefinition attr : attributes) {
            resourceRegistration.registerReadWriteAttribute(attr, null, new WriteAttributeHandlers.AttributeDefinitionValidatingHandler(attr));
        }
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        resourceRegistration.registerOperationHandler(JVMOptionAddHandler.DEFINITION, JVMOptionAddHandler.INSTANCE);
        resourceRegistration.registerOperationHandler(JVMOptionRemoveHandler.DEFINITION, JVMOptionRemoveHandler.INSTANCE);

        //AS7-4437 is scheduled for 7.2.0 so uncomment these once we have decided on the format of the operation names
        //There are some tests in AbstractJvmModelTest for these which need uncommenting as well

        //resourceRegistration.registerOperationHandler(JVMEnvironmentVariableAddHandler.OPERATION_NAME, JVMEnvironmentVariableAddHandler.INSTANCE, JVMEnvironmentVariableAddHandler.INSTANCE, false);
        //resourceRegistration.registerOperationHandler(JVMEnvironmentVariableRemoveHandler.OPERATION_NAME, JVMEnvironmentVariableRemoveHandler.INSTANCE, JVMEnvironmentVariableRemoveHandler.INSTANCE, false);
    }

}
