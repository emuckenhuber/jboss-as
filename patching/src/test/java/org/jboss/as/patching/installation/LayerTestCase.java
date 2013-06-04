/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

package org.jboss.as.patching.installation;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.jboss.as.patching.HashUtils.hashFile;
import static org.jboss.as.patching.IoUtils.NO_CONTENT;
import static org.jboss.as.patching.IoUtils.mkdir;
import static org.jboss.as.patching.PatchInfo.BASE;
import static org.jboss.as.patching.metadata.ModificationType.ADD;
import static org.jboss.as.patching.runner.PatchingAssert.assertDefinedModule;
import static org.jboss.as.patching.runner.PatchingAssert.assertDirExists;
import static org.jboss.as.patching.runner.PatchingAssert.assertPatchHasBeenApplied;
import static org.jboss.as.patching.runner.TestUtils.createModule;
import static org.jboss.as.patching.runner.TestUtils.createPatchXMLFile;
import static org.jboss.as.patching.runner.TestUtils.createZippedPatchFile;
import static org.jboss.as.patching.runner.TestUtils.newFile;
import static org.jboss.as.patching.runner.TestUtils.randomString;
import static org.jboss.as.patching.runner.TestUtils.tree;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Properties;

import org.jboss.as.patching.Constants;
import org.jboss.as.patching.DirectoryStructure;
import org.jboss.as.patching.IoUtils;
import org.jboss.as.patching.metadata.ContentModification;
import org.jboss.as.patching.metadata.ModuleItem;
import org.jboss.as.patching.metadata.Patch;
import org.jboss.as.patching.metadata.PatchBuilder;
import org.jboss.as.patching.metadata.impl.IdentityImpl;
import org.jboss.as.patching.metadata.impl.PatchElementImpl;
import org.jboss.as.patching.metadata.impl.PatchElementProviderImpl;
import org.jboss.as.patching.runner.AbstractTaskTestCase;
import org.jboss.as.patching.runner.PatchingResult;
import org.jboss.as.patching.runner.TestUtils;
import org.jboss.as.version.ProductConfig;
import org.junit.Test;

/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2013 Red Hat inc.
 */
public class LayerTestCase extends AbstractTaskTestCase {

    @Test
    public void testLayerNotInLayersConf() throws Exception {
        String layerName = randomString();
        installLayer(env.getModuleRoot(), null, layerName);

        ProductConfig productConfig = new ProductConfig("product", "version", "consoleSlot");
        InstalledIdentity installedIdentity = InstalledIdentity.load(env.getInstalledImage().getJbossHome(), productConfig, env.getInstalledImage().getModulesDir());

        // if the layer name is not in layers.conf, it's not part of the installed identity
        assertTrue(installedIdentity.getLayers().isEmpty());
    }

    @Test
    public void testInstalledLayer() throws Exception {
        String layerName = randomString();
        installLayer(env.getModuleRoot(), env.getInstalledImage().getLayersConf(), layerName);

        ProductConfig productConfig = new ProductConfig("product", "version", "consoleSlot");

        TestUtils.tree(env.getInstalledImage().getJbossHome());

        InstalledIdentity installedIdentity = InstalledIdentity.load(env.getInstalledImage().getJbossHome(), productConfig, env.getInstalledImage().getModulesDir());

        Identity identity = installedIdentity.getIdentity();
        assertEquals(productConfig.getProductName(), identity.getName());
        assertEquals(productConfig.resolveVersion(), identity.getVersion());

        List<Layer> layers = installedIdentity.getLayers();
        assertEquals(1, layers.size());
        Layer layer = layers.get(0);
        assertEquals(layerName, layer.getName());

        PatchableTarget.TargetInfo targetInfo = layer.loadTargetInfo();
        assertEquals(BASE, targetInfo.getCumulativeID());
        assertTrue(targetInfo.getPatchIDs().isEmpty());
        DirectoryStructure directoryStructure = targetInfo.getDirectoryStructure();
        assertEquals(newFile(env.getModuleRoot(), "system", "layers", layerName), directoryStructure.getModuleRoot());
        assertNull(directoryStructure.getBundleRepositoryRoot());
    }

    @Test
    public void testPatchedLayer() throws Exception {
        // start from a base installation
        ProductConfig productConfig = new ProductConfig("product", "version", "consoleSlot");

        // add a layer
        String layerName = "mylayer"; //randomString();
        installLayer(env.getModuleRoot(), env.getInstalledImage().getLayersConf(), layerName);

        InstalledIdentity installedIdentity = InstalledIdentity.load(env.getInstalledImage().getJbossHome(), productConfig, env.getInstalledImage().getModulesDir());

        System.out.println("installation =>>");
        tree(env.getInstalledImage().getJbossHome());

        // build a one-off patch for the layer with 1 added module
        String patchID = "patchId"; //randomString();
        File patchDir = mkdir(tempDir, patchID);
        String moduleName = "mymodule"; randomString();
        // FIXME if all the layers' module are installed at the root of the patch, it's up to the patch gen to avoid any overrides
        File patchedLayerModuleRoot = newFile(patchDir, "system", "layers", layerName);
        File moduleDir = createModule(patchDir /* patchedLayerModuleRoot */, moduleName);
        byte[] newHash = hashFile(moduleDir);
        ContentModification moduleAdded = new ContentModification(new ModuleItem(moduleName, ModuleItem.MAIN_SLOT, newHash), NO_CONTENT, ADD);

        String layerPatchId = randomString();
        PatchElementImpl layerPatch = new PatchElementImpl(layerPatchId);
        layerPatch.addContentModification(moduleAdded);
        layerPatch.setProvider(new PatchElementProviderImpl(layerName, "1.0.1", false));
        layerPatch.setNoUpgrade();
        Patch patch = PatchBuilder.create()
                .setPatchId(patchID)
                .setOneOffType(productConfig.getProductVersion())
                .setIdentity(new IdentityImpl(installedIdentity.getIdentity().getName(), installedIdentity.getIdentity().getVersion()))
                .addElement(layerPatch)
                .build();

        createPatchXMLFile(patchDir, patch);
        System.out.println("patch =>>");
        File zippedPatch = createZippedPatchFile(patchDir, patchID);

        // apply patch
        PatchingResult result = executePatch(zippedPatch, installedIdentity, env.getInstalledImage());
        assertPatchHasBeenApplied(result, patch);

        System.out.println("installation =>>");
        tree(env.getInstalledImage().getJbossHome());

        DirectoryStructure layerDirStructure = installedIdentity.getLayers().get(0).loadTargetInfo().getDirectoryStructure();
        File modulesPatchDir = layerDirStructure.getModulePatchDirectory(layerPatchId);
        assertDirExists(modulesPatchDir);
        assertDefinedModule(modulesPatchDir, moduleName, newHash);
    }

    private static void installLayer(File baseDir, File layerConf, String... layers) throws Exception {
        for (String layer : layers) {
            IoUtils.mkdir(baseDir, "system", "layers", layer);
        }
        if (layerConf != null) {
            Properties props = new Properties();
            StringBuilder str = new StringBuilder();
            for (int i = 0; i < layers.length; i++) {
                if (i > 0) {
                    str.append(',');
                }
                str.append(layers[i]);
            }
            props.put(Constants.LAYERS, str.toString());
            props.store(new FileOutputStream(layerConf), "");
        }
    }
}
