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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipFile;

/**
 * @author Emanuel Muckenhuber
 */
public interface PatchContentLoader extends Closeable {

    /**
     * Get the content for a resource.
     *
     * @param resourceName the relative name of the resource
     * @return the resource input stream
     */
    InputStream getResource(final String resourceName) throws IOException;

    /**
     * Get the content for a module resource.
     *
     * @param identifier the referenced module
     * @param resourceName the resource
     * @return the input stream
     * @throws IOException
     */
    InputStream getModuleResource(ModuleIdentifier identifier, String resourceName) throws IOException;

    static class FilePatchContentLoader implements PatchContentLoader {

        /** The path where contents should be loaded from. */
        private final File root;
        public FilePatchContentLoader(final File root) {
            this.root = root;
        }

        @Override
        public InputStream getModuleResource(final ModuleIdentifier identifier, final String resourceName) throws IOException {
            final File modules = new File(root, PatchLayout.MODULES);
            final File module = new File(modules, toPathString(identifier));
            final File resource = new File(module, resourceName);
            return new FileInputStream(resource);
        }

        @Override
        public InputStream getResource(String resourceName) throws IOException {
            final File modules = new File(root, PatchLayout.RESOURCES);
            final File resource = new File(modules,  resourceName);
            return new FileInputStream(resource);
        }

        @Override
        public void close() throws IOException {
            //
        }
        static String toPathString(final ModuleIdentifier moduleIdentifier) {
            final StringBuilder builder = new StringBuilder();
            builder.append(moduleIdentifier.getName().replace('.', File.separatorChar));
            builder.append(File.separatorChar).append(moduleIdentifier.getSlot());
            builder.append(File.separatorChar);
            return builder.toString();
        }
    }

}
