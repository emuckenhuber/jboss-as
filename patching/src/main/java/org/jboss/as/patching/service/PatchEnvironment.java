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

import java.io.File;

/**
 * @author Emanuel Muckenhuber
 */
public final class PatchEnvironment {

    static String CUMULATIVE = "cumulative";
    static String HISTORY = "history";
    static String METADATA = ".metadata";
    static String MODULES = "modules";
    static String PATCHES = "patches";
    static String REFERENCES = "references";

    static interface InstalledImage {

        /**
         * Get the modules directory.
         *
         * @return the modules dir
         */
        File getModulesDir();

        /**
         * Get the patches directory.
         *
         * @return the patches directory
         */
        File getPatchesDir();

    }

    private final InstalledImage image;

    PatchEnvironment(final InstalledImage image) {
        this.image = image;
    }

    /**
     * Get the installed image layout.
     *
     * @return the installed image
     */
    public InstalledImage getInstalledImage() {
        return image;
    }

    /**
     * Get the patches metadata directory.
     *
     * @return the patches metadata directory
     */
    File getPatchesMetadata() {
        return new File(getInstalledImage().getPatchesDir(), METADATA);
    }

    /**
     * Get the cumulative patch symlink file.
     *
     * @return the cumulative patch id
     */
    File getCumulativeLink() {
        return new File(getPatchesMetadata(), CUMULATIVE);
    }

    /**
     * Get the references file, containing all active patches for a given
     * cumulative patch release.
     *
     *
     * @param cumulativeId the cumulative patch id
     * @return the cumulative references file
     */
    File getCumulativeRefs(final String cumulativeId) {
        final File references = new File(getPatchesMetadata(), REFERENCES);
        return new File(references, cumulativeId);
    }

    /**
     * Get the history dir for a given patch id.
     *
     * @param patchId the patch id
     * @return the history dir
     */
    File getHistoryDir(final String patchId) {
        final File history = new File(getPatchesMetadata(), HISTORY);
        return new File(history, patchId);
    }

    /**
     * Get the patch directory for a given patch-id.
     *
     * @param patchId the patch-id
     * @return the patch directory
     */
    File getPatchDirectory(final String patchId) {
        return new File(getInstalledImage().getPatchesDir(), patchId);
    }

    /**
     * Create the patch environment based on the default layout.
     *
     * @param jbossHome the $JBOSS_HOME
     * @return the patch environment
     */
    static PatchEnvironment createDefault(final File jbossHome) {
        final File modules = new File(jbossHome, MODULES);
        final File patches = new File(jbossHome,  PATCHES);
        return new PatchEnvironment(new InstalledImage() {
            @Override
            public File getModulesDir() {
                return modules;
            }

            @Override
            public File getPatchesDir() {
                return patches;
            }
        });
    }

}
