/*
* JBoss, Home of Professional Open Source.
* Copyright 2011, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.as.controller.registry;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import org.jboss.as.controller.ControllerMessages;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.Stage;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.dmr.ModelNode;

/**
 * A handler that simply maps an alias onto a target part of the model.
 */
class AliasStepHandler implements OperationStepHandler {

    private final OperationConverter converter;

    AliasStepHandler(OperationConverter converter) {
        this.converter = converter;
    }

    AliasStepHandler(AliasEntry entry) {
        this(new OperationConverter.AliasEntryOperationConverter(entry));
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final String op = operation.require(OP).asString();
        final PathAddress addr = PathAddress.pathAddress(operation.require(OP_ADDR));

        final ModelNode converted = converter.convert(addr, operation);
        final PathAddress cAddr = PathAddress.pathAddress(converted.require(OP_ADDR));

        OperationStepHandler targetHandler = context.getRootResourceRegistration().getOperationHandler(cAddr, op);
        if (op == null) {
            throw ControllerMessages.MESSAGES.aliasStepHandlerOperationNotFound(op, addr, cAddr);
        }

        final ModelNode copy = converted;
        context.addStep(copy, targetHandler, Stage.IMMEDIATE);
        context.stepCompleted();
    }

}

