/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

package org.jboss.as.patching.runner;

import org.jboss.as.patching.PatchInfo;
import org.jboss.as.patching.metadata.ContentItem;
import org.jboss.as.patching.metadata.Patch;

import java.util.Collection;

/**
 * The result when applying a patch.
 *
 * @author Emanuel Muckenhuber
 */
public interface PatchingResult {

    /**
     * Get the patch id.
     *
     * @return the patch id
     */
    String getPatchId();

    /**
     * Get the patch info.
     *
     * @return the patch info
     */
    PatchInfo getPatchInfo();

    /**
     * Complete.
     */
    void commit();

    /**
     * Rollback...
     */
    void rollback();

}
