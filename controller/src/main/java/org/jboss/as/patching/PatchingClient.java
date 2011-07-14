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

/**
 * The patching client is responsible of patching module, binary content only. Additional
 * configuration, validation has to be done by the tooling.
 *
 * @author Emanuel Muckenhuber
 */
public interface PatchingClient {

    /**
     * Apply a patch.
     *
     * @param patch the patch
     */
    void apply(Patch patch) throws PatchingException;

    /**
     * Rollback a patch.
     *
     * @param patch the patch
     */
    void rollback(Patch patch) throws PatchingException;

    public static class Factory {

        private Factory() { }

        public static PatchingClient create(final ModelControllerClient controllerClient) {
            return new PatchingClientImpl(controllerClient);
        }

    }

}
