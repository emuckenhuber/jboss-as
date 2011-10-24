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

package org.jboss.as.server.operations;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.patching.Patch;
import org.jboss.as.patching.PatchContentLoader;
import org.jboss.as.patching.PatchImpl;
import org.jboss.as.patching.PatchingException;
import org.jboss.as.patching.service.AbstractPatchingTask;
import org.jboss.as.patching.service.PatchInfo;
import org.jboss.as.patching.service.PatchInfoService;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;

import java.io.InputStream;
import java.util.Locale;

/**
 * @author Emanuel Muckenhuber
 */
public class StandalonePatchOperationHandler extends AbstractPatchingTask implements OperationStepHandler, DescriptionProvider {

    public static final String OPERATION_NAME = "apply-patch";
    public static final StandalonePatchOperationHandler INSTANCE = new StandalonePatchOperationHandler();

    @Override
    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
        // Acquire the controller lock
        context.acquireControllerLock();
        context.restartRequired();

        final Patch patch = PatchImpl.fromModelNode(operation);
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
                final ServiceController<?> controller = context.getServiceRegistry(false).getRequiredService(PatchInfoService.NAME);
                final PatchInfo info = (PatchInfo) controller.getValue();
                PatchInfo newInfo = null;
                try {
                    newInfo = StandalonePatchOperationHandler.this.execute(patch, new PatchingContext() {
                        @Override
                        public PatchInfo getPatchInfo() {
                            return info;
                        }

                        @Override
                        public PatchContentLoader getContentLoader() {
                            return new PatchContentLoader() {
                                @Override
                                public InputStream openContentStream() {
                                    return context.getAttachmentStream(0);
                                }
                            };
                        }
                    });
                } catch(PatchingException e) {
                    context.getFailureDescription().set(e.getMessage());
                }
                context.completeStep();
            }
        }, OperationContext.Stage.RUNTIME);
        if(context.completeStep() != OperationContext.ResultAction.KEEP) {
            context.revertRestartRequired();
        }
    }

    @Override
    public ModelNode getModelDescription(Locale locale) {
        return new ModelNode();
    }
}
