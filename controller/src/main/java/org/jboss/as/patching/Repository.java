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

import java.io.File;

/**
 * modules
 * |-- org/jboss/as/server/main/jboss-as-server.jar
 * |-- .patches/
 * |   |-- org/jboss/as/server/main/jboss-as-server.jar
 * |   `-- .history/
 * `-- .repository/
 *
 * @author Emanuel Muckenhuber
 */
class Repository {

    static final String PATCH_FOLDER = ".patch";
    static final String PATCH_BACKUP_FOLDER = ".history";
    static final String REPOSITORY_METADATA = ".repository";

    private final String name;
    private final File repositoryRoot;

    Repository(final String name, final File root) {
        this.name = name;
        this.repositoryRoot = root;
    }

    static Repository create(final String name, final File file) {
        return new Repository(name, file);
    }

    File getRepositoryRoot() {
        return repositoryRoot;
    }

    File getPatchFolder() {
        return new File(repositoryRoot, PATCH_FOLDER);
    }

    File getPatchHistoryFolder() {
        return new File(getPatchFolder(), PATCH_BACKUP_FOLDER);
    }

    File getRepositoryMetadataFolder() {
        return new File(repositoryRoot, REPOSITORY_METADATA);
    }

}
