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

import org.jboss.modules.ModuleIdentifier;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

/**
 * @author Emanuel Muckenhuber
 */
class PatchUtils {

    /**
     * Safe close...
     *
     * @param closeable
     */
    public static void safeClose(final Closeable closeable) {
        if(closeable != null) {
            try {
                closeable.close();
            } catch(IOException ignore) {
                //
            }
        }
    }

    /**
     * Attempt to recursively delete a file or directory.
     *
     * @param root the real file to delete
     * @return {@code true} if the file was deleted
     */
    static boolean recursiveDelete(File root) {
        boolean ok = true;
        if (root.isDirectory()) {
            final File[] files = root.listFiles();
            for (File file : files) {
                ok &= recursiveDelete(file);
            }
            return ok && (root.delete() || !root.exists());
        } else {
            ok &= root.delete() || !root.exists();
        }
        return ok;
    }

}
