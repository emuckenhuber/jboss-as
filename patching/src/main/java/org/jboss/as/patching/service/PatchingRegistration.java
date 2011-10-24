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

package org.jboss.as.patching.service;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.jboss.marshalling.ContextClassResolver;
import org.jboss.msc.service.ServiceController;

import java.util.EnumSet;
import java.util.Locale;

/**
 * @author Emanuel Muckenhuber
 */
public class PatchingRegistration {

    static final DescriptionProvider NULL = new DescriptionProvider() {
        @Override
        public ModelNode getModelDescription(Locale locale) {
            return new ModelNode();
        }
    };

    static final PathElement path = PathElement.pathElement(ModelDescriptionConstants.CORE_SERVICE, "patches");

    public static void registerSubModel(final ManagementResourceRegistration registration) {

        registration.registerOperationHandler("patch-info", new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                context.addStep(new OperationStepHandler() {
                    @Override
                    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                        final ServiceController<?> controller = context.getServiceRegistry(false).getRequiredService(PatchInfoService.NAME);
                        PatchInfo info = (PatchInfo) controller.getValue();
                        context.getResult().get("version").set(info.getVersion());
                        context.getResult().get("cumulative").set(info.getCumulativeID());
                        for(final String patch : info.getPatchIDs()) {
                            context.getResult().get("patches").add(patch);
                        }
                        context.completeStep();
                    }
                }, OperationContext.Stage.RUNTIME);
                context.completeStep();
            }
        }, NULL, false, OperationEntry.EntryType.PRIVATE,  EnumSet.of(OperationEntry.Flag.READ_ONLY));


    }

}
