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
import org.jboss.dmr.ModelNode;

import java.io.Closeable;
import java.io.InputStream;
import java.net.UnknownHostException;

/**
 * The patching client.
 *
 * @author Emanuel Muckenhuber
 */
public interface PatchingClient extends Closeable {

    /**
     * Create a patch metadata object from a detyped model.
     *
     * @param model the model
     * @return the patch meta data
     */
    Patch create(ModelNode model);

    /**
     * Create a new patching plan builder.
     *
     * @param patch the patch meta data
     * @param contentLoader the content loader
     * @return the plan builder
     */
    PatchingPlanBuilder createBuilder(Patch patch, PatchContentLoader contentLoader);

    public class Factory {

        private Factory() {
            //
        }

        /**
         * Create a patching client.
         *
         * @param client the model controller client
         * @return the patching client
         */
        public static PatchingClient create(final ModelControllerClient client) {
            return new PatchingClientImpl(client);
        }

        /**
         * Create a patching client
         *
         * @param host the host
         * @param port the port
         * @return the patching client
         * @throws UnknownHostException
         */
        public static PatchingClient create(final String host, final int port) throws UnknownHostException {
            return create(ModelControllerClient.Factory.create(host, port));
        }

    }

}
