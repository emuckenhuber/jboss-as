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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Emanuel Muckenhuber
 */
class PatchInfoImpl implements PatchInfo {

    private final String version;
    private final String cumulativeId;
    private final List<String> patches;
    private final PatchEnvironment environment;

    PatchInfoImpl(final String version, final String cumulativeId, final List<String> patches, final PatchEnvironment environment) {
        this.version = version;
        this.cumulativeId = cumulativeId;
        this.patches = patches;
        this.environment = environment;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public String getCumulativeID() {
        return cumulativeId;
    }

    @Override
    public List<String> getPatchIDs() {
        return patches;
    }

    @Override
    public PatchEnvironment getEnvironment() {
        return environment;
    }

    @Override
    public String getModulePath() {
        final StringBuilder builder = new StringBuilder();
        for(final String patchId : getPatchIDs()) {
            final File path = environment.getPatchDirectory(patchId);
            builder.append(path.getAbsolutePath()).append(File.pathSeparatorChar);
        }
        if(cumulativeId != null && ! PatchInfo.BASE.equals(cumulativeId)) {
            final File path = environment.getPatchDirectory(cumulativeId);
            builder.append(path.getAbsolutePath()).append(File.pathSeparatorChar);
        }
        builder.append(environment.getInstalledImage().getModulesDir().getAbsolutePath());
        return builder.toString();
    }

    /**
     * Load the information from the disk.
     *
     * @param version the current version
     * @param environment the patch environment
     * @return the patches
     * @throws IOException
     */
    static PatchInfoImpl load(final String version, final PatchEnvironment environment) throws IOException {
        if(environment.getCumulativeLink().exists()) {
            final String ref = PatchUtils.readRef(environment.getCumulativeLink());
            final List<String> patches = PatchUtils.readRefs(environment.getCumulativeRefs(ref));
            return new PatchInfoImpl(version, ref, patches, environment);
        } else {
            return new PatchInfoImpl(version, PatchInfo.BASE, Collections.<String>emptyList(), environment);
        }
    }

    /**
     * Setup
     *
     * @param info the patch info
     * @throws IOException
     */
    static void setup(final PatchInfo info) throws IOException {
        final PatchEnvironment environment = info.getEnvironment();
        PatchUtils.createDirIfNotExists(environment.getInstalledImage().getPatchesDir());
        PatchUtils.createDirIfNotExists(environment.getPatchesMetadata());
        PatchUtils.createDirIfNotExists(new File(environment.getPatchesMetadata(), PatchEnvironment.REFERENCES));
    }

}
