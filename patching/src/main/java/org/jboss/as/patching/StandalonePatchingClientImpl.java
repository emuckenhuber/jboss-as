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

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.IN_SERIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import org.jboss.as.patching.standalone.StandalonePatchingClient;
import org.jboss.as.patching.standalone.StandalonePatchingPlan;
import org.jboss.as.patching.standalone.StandalonePatchingPlanBuilder;
import org.jboss.dmr.ModelNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * @author Emanuel Muckenhuber
 */
class StandalonePatchingClientImpl implements StandalonePatchingClient {

    private final ModelControllerClient client;
    StandalonePatchingClientImpl(ModelControllerClient client) {
        this.client = client;
    }

    @Override
    public StandalonePatchingPlanBuilder createBuilder(final Patch patch, final PatchContentLoader contentLoader) {
        return new SPCB(patch, contentLoader);
    }

    @Override
    public Patch create(final ModelNode model) {
        return PatchImpl.fromModelNode(model);
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

    protected void execute(final StandalonePatchingPlan plan) throws PatchingException {

        final ModelNode operation = PatchImpl.toModelNode(plan.getPatch());
        operation.get(ModelDescriptionConstants.OP).set("apply-patch");
        operation.get(ModelDescriptionConstants.OP_ADDR).setEmptyList();

        try {
            final InputStream is = plan.getContentLoader().openContentStream();
            executeForResult(client, OperationBuilder.create(operation).addInputStream(is).build());
            is.close();
        } catch (IOException e) {
            throw new PatchingException(e);
        } catch (PatchingException e) {
            throw e;
        } finally {

        }
    }

    protected class SPCB implements StandalonePatchingPlanBuilder {

        private final Patch patch;
        private final PatchContentLoader loader;
        private long gracefulTimeout;

        SPCB(Patch patch, PatchContentLoader loader) {
            this.patch = patch;
            this.loader = loader;
        }

        @Override
        public StandalonePatchingPlanBuilder withGracefulTimeout(int timeout, TimeUnit timeUnit) {
            gracefulTimeout = timeUnit.toMillis(timeout);
            return this;
        }

        @Override
        public StandalonePatchingPlan build() {
            return new StandalonePatchingPlan() {
                @Override
                public long getGracefulShutdownPeriod() {
                    return gracefulTimeout;
                }

                @Override
                public Patch getPatch() {
                    return patch;
                }

                @Override
                public PatchContentLoader getContentLoader() {
                    return loader;
                }

                @Override
                public void execute() throws PatchingException {
                    StandalonePatchingClientImpl.this.execute(this);
                }
            };
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
