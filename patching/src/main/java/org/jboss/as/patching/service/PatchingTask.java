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

import org.jboss.as.patching.Patch;
import org.jboss.as.patching.PatchContentLoader;
import org.jboss.as.patching.PatchingException;

/**
 * Server side patching task...
 *
 * @author Emanuel Muckenhuber
 */
public interface PatchingTask {

    /**
     * Execute a patch.
     *
     * @param patch the patch metadata
     * @param context the patching context
     * @return the new patch info
     * @throws PatchingException
     */
    PatchInfo execute(Patch patch, PatchingContext context) throws PatchingException;

    public interface PatchingContext {

        /**
         * Get the local patch info.
         *
         * @return the local patch info
         */
        PatchInfo getPatchInfo();

        /**
         * Get the patch content loader.
         *
         * @return the content loader
         */
        PatchContentLoader getContentLoader();

    }

}
