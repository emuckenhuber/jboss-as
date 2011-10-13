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

package org.jboss.as.patching;


import org.jboss.as.controller.client.OperationBuilder;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILD_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_DEFAULTS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_RUNTIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MASTER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_RESOURCES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import org.jboss.dmr.ModelNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Emanuel Muckenhuber
 */
class PatchingClientImpl implements PatchingClient {

    private static final ModelNode READ_RESOURCE = new ModelNode();

    static {
        READ_RESOURCE.get(OP).set(READ_RESOURCE_OPERATION);
        READ_RESOURCE.get(OP_ADDR).setEmptyList();
        READ_RESOURCE.protect();
    }

    private final ModelControllerClient client;
    public PatchingClientImpl(ModelControllerClient client) {
        this.client = client;
    }

    @Override
    public Patch create(ModelNode model) {
        return PatchImpl.fromModelNode(model);
    }

    @Override
    public PatchingPlanBuilder createBuilder(final Patch patch, final PatchContentLoader contentLoader) {
        return new AbstractPatchingPlanBuilder.InitialPlanBuilder(patch, contentLoader, this);
    }

    void execute(final PatchingPlan plan) throws PatchingException {

//        TODO fix this operation
//        final ModelNode operation = new ModelNode();
//        operation.get(OP).set(READ_CHILDREN_RESOURCES_OPERATION);
//        operation.get(OP_ADDR).setEmptyList();
//        operation.get(CHILD_TYPE).set(HOST);
//        operation.get(INCLUDE_DEFAULTS).set(false);
//        operation.get(INCLUDE_RUNTIME).set(true);
//
//        final ModelNode result = executeForResult(client, operation);

        Task master = null; // actually master or local
        List<Task> others = new ArrayList<Task>();

        for(final String host : plan.getHosts()) {

            final ModelNode hostOp = new ModelNode();
            hostOp.get(OP).set(READ_RESOURCE_OPERATION);
            hostOp.get(OP_ADDR).add("host", host);
            hostOp.get(INCLUDE_RUNTIME).set(true);

            final ModelNode hostModel = executeForResult(client, hostOp);
            if(! hostModel.isDefined()) {
                throw new PatchingException("no such host " + host);
            }
            System.out.println(hostModel);

            boolean mdc = hostModel.get(MASTER).asBoolean(false);
            if(mdc) {
                master = new ReconnectTask(host, plan.getPatch(), plan.getContentLoader());
            } else {
                others.add(new BlockingTask(host, plan.getPatch(), plan.getContentLoader()));
            }
        }
        try {

            if(master != null) {
                master.execute();
            }
            // Wait for HCs to reconnect...
            TimeUnit.SECONDS.sleep(15);

            for(final Task task : others) {
                task.execute();
            }
        } catch (PatchingException e) {
            throw e;
        } catch (Exception e) {
            throw new PatchingException(e);
        } finally {

        }
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

    abstract class Task {
        abstract void execute() throws Exception;
    }

    class ReconnectTask extends Task {

        private final String host;
        private final Patch patch;
        private final PatchContentLoader loader;

        ReconnectTask(String host, Patch patch, PatchContentLoader loader) {
            this.host = host;
            this.patch = patch;
            this.loader = loader;
        }

        public void execute() throws Exception {

            final ModelNode operation = PatchImpl.toModelNode(patch);
            operation.get(OP).set("apply-patch");
            operation.get(OP_ADDR).add("host", host);

            final InputStream is = loader.openContentStream();
            try {
                executeForResult(client, OperationBuilder.create(operation).addInputStream(is).build());
            } finally {
                is.close();
            }

            // Graceful shutdown timeout...
            TimeUnit.SECONDS.sleep(15);

            for(int i = 0; i < 10; i++) {
                try {
                    executeForResult(client, READ_RESOURCE);
                } catch (final Exception e) {
                    System.out.println("failed to contact server. waiting...");
                    TimeUnit.SECONDS.sleep(1);
                }
            }

            checkPatchInfo(client, host, patch);
        }
    }

    class BlockingTask extends Task {

        private final String host;
        private final Patch patch;
        private final PatchContentLoader loader;

        BlockingTask(String host, Patch patch, PatchContentLoader loader) {
            this.host = host;
            this.patch = patch;
            this.loader = loader;
        }

        @Override
        void execute() throws Exception {

            final ModelNode operation = PatchImpl.toModelNode(patch);
            operation.get(OP).set("patch");
            operation.get(OP_ADDR).setEmptyList();
            operation.get(NAME).set(host);

            final InputStream is = loader.openContentStream();
            try {
                executeForResult(client, OperationBuilder.create(operation).addInputStream(is).build());
            } finally {
                is.close();
            }

            checkPatchInfo(client, host, patch);
        }
    }

    static void checkPatchInfo(final ModelControllerClient client, final String host, final Patch patch) throws PatchingException {
        final String patchId = patch.getPatchId();
        final ModelNode patchInfo = new ModelNode();
        patchInfo.get(OP).set("patch-info");
        patchInfo.get(OP_ADDR).add("host", host);

        final ModelNode result = executeForResult(client, patchInfo);
        System.out.println(result);

        if(patch.getPatchType() == Patch.PatchType.CUMULATIVE) {
            final String cp = result.get("cumulative").asString();
            if(! patchId.equals(cp)) {
                throw new PatchingException("expected patch '%s', but was '%s'", patchId, cp);
            }
        }  else {
            final List<ModelNode> patches = result.get("patches").asList();
            if(! patches.contains(new ModelNode().set(patchId))) {
                throw new PatchingException("expected patches to contain '%s', but was '%s'", patchId, patches);
            }
        }
    }

    static ModelNode executeForResult(final ModelControllerClient client, final ModelNode operation) throws PatchingException {
        return executeForResult(client, OperationBuilder.build(operation));
    }

    static ModelNode executeForResult(final ModelControllerClient client, final Operation operation) throws PatchingException {
        try {
            final ModelNode result = client.execute(operation);
            if(SUCCESS.equals(result.get(OUTCOME).asString())) {
                return result.get(RESULT);
            } else {
                throw new PatchingException("operation failed " + result);
            }
        } catch(IOException ioe) {
            throw new PatchingException(ioe);
        }
    }

}
