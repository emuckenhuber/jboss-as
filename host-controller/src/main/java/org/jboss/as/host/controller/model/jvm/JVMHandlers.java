/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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


import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.AliasEntry;
import org.jboss.as.controller.registry.ManagementResourceRegistration;

/**
 * JVM specific registrations.
 *
 * TODO what should happen if you do jvm=customVM:add()? since the alias can just change the address, not "transform" the local op
 *
 * @author Emanuel Muckenhuber
 */
public final class JVMHandlers {

    static PathElement JVM_WILDCARD = PathElement.pathElement(ModelDescriptionConstants.JVM);
    static PathElement JVM_SINGLE = PathElement.pathElement(ModelDescriptionConstants.CONFIGURATION, ModelDescriptionConstants.JVM);

    /**
     * Register the host level JVM definition (jvm=*).
     *
     * @param registration the host registration
     */
    public static void registerHostVM(final ManagementResourceRegistration registration) {
        registration.registerSubModel(JvmResourceDefinition.HOST);
    }

    /**
     * Register the server JVM definition (configuration=jvm).
     *
     * @param registration the server registration
     */
    public static void registerServerVM(final ManagementResourceRegistration registration) {
        final ManagementResourceRegistration jvm = registration.registerSubModel(JvmResourceDefinition.SERVER);
        registration.registerAlias(JVM_WILDCARD, new JVMAliasEntry(jvm));
    }

    /**
     * Register the server-group JVM definition (configuration=jvm).
     *
     * @param registration the server-group registration
     */
    public static void registerServerGroupVM(final ManagementResourceRegistration registration) {
        final ManagementResourceRegistration jvm = registration.registerSubModel(JvmResourceDefinition.GROUP);
        registration.registerAlias(JVM_WILDCARD, new JVMAliasEntry(jvm));
    }

    /**
     *  Alias entry converting:
     *
     *  (jvm=*) to
     *  (configuration=jvm)
     */
    public static class JVMAliasEntry extends AliasEntry {

        public JVMAliasEntry(ManagementResourceRegistration target) {
            super(target);
        }

        @Override
        public PathAddress convertToTargetAddress(PathAddress address) {
            PathAddress converted = PathAddress.EMPTY_ADDRESS;
            for(final PathElement element : address) {
                if(element.getKey().equals(ModelDescriptionConstants.JVM)) {
                    converted = converted.append(JVM_SINGLE);
                } else {
                    converted = converted.append(element);
                }
            }
            return converted;
        }
    }

}
