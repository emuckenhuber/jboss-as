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

package org.jboss.as.patching;

import static org.jboss.as.patching.Constants.APP_CLIENT;
import static org.jboss.as.patching.Constants.BUNDLES;
import static org.jboss.as.patching.Constants.DOMAIN;
import static org.jboss.as.patching.Constants.INSTALLATION_METADATA;
import static org.jboss.as.patching.Constants.METADATA;
import static org.jboss.as.patching.Constants.MODULES;
import static org.jboss.as.patching.Constants.PATCHES;
import static org.jboss.as.patching.Constants.STANDALONE;
import static org.jboss.as.patching.IoUtils.newFile;

import java.io.File;

import org.jboss.as.patching.installation.InstalledImage;

/**
 * The patching directory structure.
 *
 * <pre>
 * <code>
 *
 * ${JBOSS_HOME}
 * |-- bin
 * |-- bundles
 * |   |-- system (system bundles contains only bundles, no patches metadata)
 * |   |   |-- layers
 * |   |   |   |-- xyz
 * |   |   |   |   `-- patches (overlay directory)
 * |   |   |   |       |-- patch-xyz-1
 * |   |   |   |       `-- patch-xyz-2
 * |   |   |   |-- vuw
 * |   |   |   |   `-- patches (overlay directory)
 * |   |   |   |       `-- patch-vuw-1
 * |   |   |   `-- base
 * |   |   |       |-- patches (overlay directory)
 * |   |   |       |   |-- patch-base-1
 * |   |   |       |   `-- patch-base-2
 * |   |   |       `-- org/jboss/as/osgi
 * |   |   `-- add-ons
 * |   |       `-- def
 * |   |           `-- patches (overlay directory)
 * |   |               |-- patch-def-1
 * |   |               `-- patch-def-2
 * |   |
 * |   `-- my/own/bundle/path/thing
 * |
 * |-- docs
 * |-- modules
 * |   |-- layers.conf (xyz,vuw)
 * |   |-- system (system modules contains only modules, no patches metadata)
 * |   |   |-- layers
 * |   |   |   |-- xyz
 * |   |   |   |    `-- patches (overlay directory)
 * |   |   |   |       |-- patch-xyz-1
 * |   |   |   |       `-- patch-xyz-2
 * |   |   |   |-- vuw
 * |   |   |   |    `-- patches (overlay directory)
 * |   |   |   |        `-- patch-vuw-1
 * |   |   |   ` -- base
 * |   |   |        |-- patches (overlay directory)
 * |   |   |        |   |-- patch-base-1
 * |   |   |        |   `-- patch-base-2
 * |   |   |        |-- org/jboss/as/...
 * |   |   |        `-- org/jboss/as/server/main/module.xml
 * |   |   `-- add-ons
 * |   |       `-- def
 * |   |           `-- patches (overlay directory)
 * |   |               |-- patch-def-1
 * |   |               `-- patch-def-2
 * |   |
 * |   `-- my/own/module/root/repo
 * |
 * |-- .installation (metadata directory for the installation)
 * |   |-- identity.conf (patched state for the installed identity)
 * |   `-- patches  (history of the patches applied to the identity)
 * |       `-- patch-identity-1
 * |           |-- patch.xml
 * |           |-- rollback.xml
 * |           |-- timestamp
 * |           |-- configuration   (configuration backup)
 * |           `-- misc            (misc backup)
 * |       |-- layers (metadata for patched layers)
 * |       |   |-- base
 * |       |   |   `-- layer.conf (patched state for the layer)
 * |       |   |-- xyz
 * |       |   |   `-- layer.conf
 * |       |   |-- vuw
 * |       |   |   `-- layer.conf
 * |       `-- add-ons (metadata for patched add-ons)
 * |           `-- def
 * |               `-- layer.conf
 * `-- jboss-modules.jar
 * </code>
 * </pre>
 *
 * Algorithm to build the module path when the server boots:
 *
 * <ol>
 *     <li>let paths be a list of File</li>
 *     <li>for each layer in {@link org.jboss.as.patching.installation.InstalledImage#getLayersConf()} file and "base":</li>
 *     <ol>
 *        <li>read the cumulative-patch-id in {@link org.jboss.as.patching.installation.Layer#loadTargetInfo()#getInstallationInfo()}</li>
 *        <li>append {@link org.jboss.as.patching.installation.Layer#loadTargetInfo()#getModulePatchDirectory(String)} for the cumulative-patch-id (if it exists) to the paths</li>
 *        <li>for each one-off patchIDs in {@link org.jboss.as.patching.installation.Layer#loadTargetInfo()#getInstallationInfo()}</li>
 *        <ol>
 *            <li>append {@link org.jboss.as.patching.installation.Layer#loadTargetInfo()#getModulePatchDirectory(String)} (if it exists) to the paths</li>
 *        </ol>
 *     </ol>
 *     <li>for each addOn in {@link InstalledImage#getModulesDir()}}/system/add-ons</li>
 *     <ol>
 *        <li>read the cumulative-patch-id in {@link org.jboss.as.patching.installation.AddOn#loadTargetInfo()#getInstallationInfo()}</li>
 *        <li>append {@link org.jboss.as.patching.installation.AddOn#loadTargetInfo()#getModulePatchDirectory(String)} for the cumulative-patch-id (if it exists) to the paths</li>
 *        <li>for each one-off patchIDs in {@link org.jboss.as.patching.installation.AddOn#loadTargetInfo()#getInstallationInfo()}</li>
 *        <ol>
 *            <li>append {@link org.jboss.as.patching.installation.AddOn#loadTargetInfo()#getModulePatchDirectory(String)} (if it exists) to the paths</li>
 *        </ol>
 *     </ol>
 *     <li>return paths</li>
 * </ol>
 *
 * Same algorithm applies to build the bundle path.
 *
 * @author Emanuel Muckenhuber
 */
public abstract class DirectoryStructure {

    /**
     * Get the installed image layout.
     *
     * @return the installed image
     */
    public abstract InstalledImage getInstalledImage();

    /**
     * Get the installation metadata.
     *
     * @return the installation metadata file
     */
    public abstract File getInstallationInfo();

    /**
     * Get the bundles repository root.
     *
     * @return the bundle base directory
     */
    public abstract File getBundleRepositoryRoot();

    /**
     * Get the bundles patch directory for a given patch-id.
     *
     * @param patchId the patch-id
     * @return the bundles patch directory
     */
    public abstract File getBundlesPatchDirectory(final String patchId);

    /**
     * Get the module root.
     *
     * @return the module root
     */
    public abstract File getModuleRoot();

    /**
     * Get the modules patch directory for a given patch-id.
     *
     * @param patchId the patch-id
     * @return the modules patch directory
     */
    public abstract File getModulePatchDirectory(final String patchId);

    /**
     * Create the legacy patch environment based on the default layout.
     *
     * @param jbossHome the $JBOSS_HOME
     * @return the patch environment
     * @deprecated see {@linkplain org.jboss.as.patching.installation.InstallationManager}
     */
    @Deprecated
    public static DirectoryStructure createLegacy(final File jbossHome) {
        final File appClient = new File(jbossHome, APP_CLIENT);
        final File bundles = new File(jbossHome, BUNDLES);
        final File domain = new File(jbossHome, DOMAIN);
        final File modules = new File(jbossHome, MODULES);
        final File installation = new File(jbossHome, Constants.INSTALLATION);
        final File patches = new File(modules, PATCHES);
        final File standalone = new File(jbossHome, STANDALONE);
        return new LegacyDirectoryStructure(new InstalledImage() {

            @Override
            public File getJbossHome() {
                return jbossHome;
            }

            @Override
            public File getBundlesDir() {
                return bundles;
            }

            @Override
            public File getModulesDir() {
                return modules;
            }

            @Override
            public File getInstallationMetadata() {
                return installation;
            }

            @Override
            public File getLayersConf() {
                return new File(getModulesDir(), Constants.LAYERS_CONF);
            }

            @Override
            public File getPatchesDir() {
                return patches;
            }

            @Override
            public File getPatchHistoryDir(String patchId) {
                return newFile(getInstallationMetadata(), PATCHES, patchId);
            }

            @Override
            public File getAppClientDir() {
                return appClient;
            }

            @Override
            public File getDomainDir() {
                return domain;
            }

            @Override
            public File getStandaloneDir() {
                return standalone;
            }
        });
    }

    static class LegacyDirectoryStructure extends DirectoryStructure {
        private final InstalledImage image;
        LegacyDirectoryStructure(final InstalledImage image) {
            this.image = image;
        }

        @Override
        public InstalledImage getInstalledImage() {
            return image;
        }

        public File getPatchesMetadata() {
            return new File(getInstalledImage().getPatchesDir(), METADATA);
        }

        @Override
        public File getInstallationInfo() {
            return new File(getPatchesMetadata(), INSTALLATION_METADATA);
        }

        public File getPatchDirectory(final String patchId) {
            return new File(getInstalledImage().getPatchesDir(), patchId);
        }

        @Override
        public File getBundlesPatchDirectory(final String patchId) {
            return new File(getPatchDirectory(patchId), BUNDLES);
        }

        @Override
        public File getModulePatchDirectory(final String patchId) {
            return new File(getPatchDirectory(patchId), MODULES);
        }

        @Override
        public File getBundleRepositoryRoot() {
            return getInstalledImage().getBundlesDir();
        }

        @Override
        public File getModuleRoot() {
            return getInstalledImage().getModulesDir();
        }
    }

}