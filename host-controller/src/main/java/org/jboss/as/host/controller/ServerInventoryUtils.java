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

package org.jboss.as.host.controller;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_NAMES_OPERATION;

import java.util.Locale;
import java.util.Set;

import org.jboss.as.controller.BasicOperationResult;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationHandler;
import org.jboss.as.controller.OperationResult;
import org.jboss.as.controller.ResultHandler;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.descriptions.common.GlobalDescriptions;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.host.controller.operations.ServerRestartHandler;
import org.jboss.as.host.controller.operations.ServerStartHandler;
import org.jboss.as.host.controller.operations.ServerStatusHandler;
import org.jboss.as.host.controller.operations.ServerStopHandler;
import org.jboss.dmr.ModelNode;

/**
 * @author Emanuel Muckenhuber
 */
class ServerInventoryUtils {

    private ServerInventoryUtils() {

    }

    static void registerServerOperations(final ServerNodeRegistration inventory, final HostController hostController) {

        final ServerStartHandler startHandler = new ServerStartHandler(hostController);
        final ServerRestartHandler restartHandler = new ServerRestartHandler(hostController);
        final ServerStopHandler stopHandler = new ServerStopHandler(hostController);

        inventory.registerOperationHandler(READ_CHILDREN_NAMES_OPERATION, new OperationHandler() {
            @Override
            public OperationResult execute(OperationContext context, ModelNode operation, ResultHandler resultHandler) throws OperationFailedException {
                final Set<String> servers = inventory.getChildNames();
                final ModelNode result = new ModelNode();
                for(final String name : servers) {
                    result.add(name);
                }
                resultHandler.handleResultFragment(Util.NO_LOCATION, result);
                resultHandler.handleResultComplete();
                return new BasicOperationResult();
            }
        }, new DescriptionProvider() {
            @Override
            public ModelNode getModelDescription(Locale locale) {
                return GlobalDescriptions.getReadChildrenNamesOperationDescription(locale);
            }
        }, true);

        inventory.registerMetric(ServerStatusHandler.ATTRIBUTE_NAME, new ServerStatusHandler(hostController));
        inventory.registerOperationHandler(ServerStartHandler.OPERATION_NAME, startHandler, startHandler, false);
        inventory.registerOperationHandler(ServerRestartHandler.OPERATION_NAME, restartHandler, restartHandler, false);
        inventory.registerOperationHandler(ServerStopHandler.OPERATION_NAME, stopHandler, stopHandler, false);
    }

}
