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

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.jboss.as.patching.metadata.ModificationType.ADD;
import static org.jboss.as.patching.metadata.ModificationType.MODIFY;
import static org.jboss.as.patching.metadata.ModificationType.REMOVE;
import static org.jboss.as.patching.runner.PatchUtils.calculateHash;
import static org.jboss.as.patching.runner.PatchUtils.recursiveDelete;
import static org.jboss.as.patching.runner.PatchingTask.NO_CONTENT;
import static org.jboss.as.patching.runner.TestUtils.assertContains;
import static org.jboss.as.patching.runner.TestUtils.assertDefinedAbsentModule;
import static org.jboss.as.patching.runner.TestUtils.assertDefinedModule;
import static org.jboss.as.patching.runner.TestUtils.assertFileExists;
import static org.jboss.as.patching.runner.TestUtils.createDir;
import static org.jboss.as.patching.runner.TestUtils.createModule;
import static org.jboss.as.patching.runner.TestUtils.randomString;
import static org.jboss.as.patching.runner.TestUtils.tree;

import java.io.File;
import java.util.Collections;

import org.jboss.as.boot.DirectoryStructure;
import org.jboss.as.patching.LocalPatchInfo;
import org.jboss.as.patching.PatchInfo;
import org.jboss.as.patching.metadata.ContentModification;
import org.jboss.as.patching.metadata.ModuleItem;
import org.jboss.as.patching.metadata.PatchBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2012, Red Hat Inc
 */
public class ModuleTaskTestCase {

    private File tempDir;
    private DirectoryStructure env;
    private String storedModulesPath = System.getProperty("module.path");

    @Before
    public void setup() throws Exception {
        tempDir = createDir(new File(System.getProperty("java.io.tmpdir")), randomString());
        File jbossHome = createDir(tempDir, "jboss-installation");
        env = DirectoryStructure.createDefault(jbossHome); 
        // make sur we put the installation modules dir in the module.path
        // FIXME is there a way set the module path without changing this sys prop?
        System.setProperty("module.path", env.getInstalledImage().getModulesDir().getAbsolutePath());
    }
    
    @After
    public void tearDown() {
        recursiveDelete(tempDir);
        // reset the module.path sys prop
        if (storedModulesPath != null) {
            System.setProperty("module.path", storedModulesPath);
        }
    }

    @Test
    public void testAddModule() throws Exception {
        
        PatchBuilder patch = new PatchBuilder();
        patch.setPatchId(randomString());
        patch.setDescription(randomString());
        String moduleName = randomString();
        ModuleItem item = new ModuleItem(moduleName, NO_CONTENT);
        ContentModification modification = new ContentModification(item, NO_CONTENT, ADD);
        patch.addContentModification(modification);

        // start from a base installation
        PatchInfo info = new LocalPatchInfo(randomString(), PatchInfo.BASE, Collections.<String>emptyList(), env);
        ContentVerificationPolicy policy = ContentVerificationPolicy.STRICT;
        
        // create an empty module for staging
        File workDir = PatchingTaskRunner.createTempDir();
        File workModulesDir = createDir(workDir, "modules");
        File moduleDir = createModule(workModulesDir, moduleName);
        byte[] newHash = calculateHash(moduleDir);
        tree(workDir);

        PatchingContext context = PatchingContext.create(patch, info, env, policy, workDir);        
        PatchingTask task = PatchingTask.Factory.createModuleTask(modification, item, context);
        assertTrue(task instanceof ModuleUpdateTask);        

        assertTrue("update module task can be applied", task.prepare(context));
        task.execute(context);
        PatchingResult result = context.finish(patch);
        
        assertFalse(result.hasFailures());        
        tree(env.getInstalledImage().getJbossHome());

        File modulesPatchDir = env.getModulePatchDirectory(patch.getPatchId());
        assertFileExists(modulesPatchDir, true);
        assertContains(modulesPatchDir, result.getPatchInfo().getModulePath());
        assertDefinedModule(result.getPatchInfo().getModulePath(), moduleName, newHash);
    }
    
    @Test
    public void testRemoveModule() throws Exception {

        String moduleName = randomString();
        
        // create an empty module in the AS7 installation
        createModule(env.getInstalledImage().getModulesDir(), moduleName);

        tree(env.getInstalledImage().getJbossHome());
        byte[] existingHash = PatchUtils.calculateHash(new File(env.getInstalledImage().getModulesDir(), moduleName));
        ModuleItem item = new ModuleItem(moduleName, existingHash);

        PatchBuilder patch = new PatchBuilder();
        patch.setPatchId(randomString());
        patch.setDescription(randomString());
        ContentModification modification = new ContentModification(item, existingHash, REMOVE);
        patch.addContentModification(modification);

        // start from a base installation
        PatchInfo info = new LocalPatchInfo(randomString(), PatchInfo.BASE, Collections.<String>emptyList(), env);
        ContentVerificationPolicy policy = ContentVerificationPolicy.STRICT;
        
        File workDir = PatchingTaskRunner.createTempDir();
        PatchingContext context = PatchingContext.create(patch, info, env, policy, workDir);        
        PatchingTask task = PatchingTask.Factory.createModuleTask(modification, item, context);
        assertTrue(task instanceof ModuleRemoveTask);        

        assertTrue("remove module task can be applied", task.prepare(context));
        task.execute(context);
        PatchingResult result = context.finish(patch);
        
        assertFalse(result.hasFailures());        
        tree(env.getInstalledImage().getJbossHome());

        File modulesPatchDir = env.getModulePatchDirectory(patch.getPatchId());
        assertFileExists(modulesPatchDir, true);
        assertContains(modulesPatchDir, result.getPatchInfo().getModulePath()); 
        assertDefinedAbsentModule(result.getPatchInfo().getModulePath(), moduleName);
    }

    @Test
    public void testUpdateModule() throws Exception {

        String moduleName = randomString();

        // create an empty module in the AS7 installation
        createModule(env.getInstalledImage().getModulesDir(), moduleName);

        tree(env.getInstalledImage().getJbossHome());
        byte[] existingHash = calculateHash(new File(env.getInstalledImage().getModulesDir(), moduleName));
        ModuleItem item = new ModuleItem(moduleName, existingHash);

        // create an empty module for staging that will update the installed one
        File workDir = PatchingTaskRunner.createTempDir();
        File workModulesDir = createDir(workDir, "modules");
        File moduleDir = createModule(workModulesDir, moduleName, "new resource in the module");
        byte[] newHash = calculateHash(moduleDir);
        tree(workDir);

        PatchBuilder patch = new PatchBuilder();
        patch.setPatchId(randomString());
        patch.setDescription(randomString());
        ContentModification modification = new ContentModification(item, existingHash, MODIFY);
        patch.addContentModification(modification);

        // start from a base installation
        PatchInfo info = new LocalPatchInfo(randomString(), PatchInfo.BASE, Collections.<String>emptyList(), env);
        ContentVerificationPolicy policy = ContentVerificationPolicy.STRICT;

        PatchingContext context = PatchingContext.create(patch, info, env, policy, workDir);
        PatchingTask task = PatchingTask.Factory.createModuleTask(modification, item, context);
        assertTrue(task instanceof ModuleUpdateTask);

        assertTrue("update module task can be applied", task.prepare(context));
        task.execute(context);
        PatchingResult result = context.finish(patch);

        assertFalse(result.hasFailures());
        tree(env.getInstalledImage().getJbossHome());

        File modulesPatchDir = env.getModulePatchDirectory(patch.getPatchId());
        assertFileExists(modulesPatchDir, true);
        assertContains(modulesPatchDir, result.getPatchInfo().getModulePath());
        // check that the defined module is the updated one
        assertDefinedModule(result.getPatchInfo().getModulePath(), moduleName, newHash);
    }

}
