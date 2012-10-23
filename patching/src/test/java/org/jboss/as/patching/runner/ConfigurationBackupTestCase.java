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

import static junit.framework.Assert.assertEquals;
import static org.jboss.as.patching.metadata.ModificationType.ADD;
import static org.jboss.as.patching.runner.PatchUtils.bytesToHexString;
import static org.jboss.as.patching.runner.PatchUtils.calculateHash;
import static org.jboss.as.patching.runner.PatchingAssert.assertDefinedModule;
import static org.jboss.as.patching.runner.PatchingAssert.assertFileContent;
import static org.jboss.as.patching.runner.PatchingAssert.assertFileExists;
import static org.jboss.as.patching.runner.PatchingAssert.assertPatchHasBeenApplied;
import static org.jboss.as.patching.runner.PatchingAssert.assertPatchHasBeenRolledBack;
import static org.jboss.as.patching.runner.PatchingTask.NO_CONTENT;
import static org.jboss.as.patching.runner.TestUtils.createModule;
import static org.jboss.as.patching.runner.TestUtils.createPatchXMLFile;
import static org.jboss.as.patching.runner.TestUtils.createZippedPatchFile;
import static org.jboss.as.patching.runner.TestUtils.dump;
import static org.jboss.as.patching.runner.TestUtils.mkdir;
import static org.jboss.as.patching.runner.TestUtils.randomString;
import static org.jboss.as.patching.runner.TestUtils.touch;
import static org.jboss.as.patching.runner.TestUtils.tree;
import static org.junit.Assert.assertArrayEquals;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;

import junit.framework.Assert;

import org.jboss.as.patching.LocalPatchInfo;
import org.jboss.as.patching.PatchInfo;
import org.jboss.as.patching.metadata.ContentModification;
import org.jboss.as.patching.metadata.ModuleItem;
import org.jboss.as.patching.metadata.Patch;
import org.jboss.as.patching.metadata.Patch.PatchType;
import org.jboss.as.patching.metadata.PatchBuilder;
import org.junit.Test;

/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2012, Red Hat Inc
 */
public class ConfigurationBackupTestCase extends AbstractTaskTestCase {

    @Test
    public void testApplyOneOffPatchBackupConfiguration() throws Exception {

        // start from a base installation
        PatchInfo info = new LocalPatchInfo(randomString(), PatchInfo.BASE, Collections.<String>emptyList(), env);
        // with some files in the configuration directories
        File appClientXmlFile = touch(env.getInstalledImage().getAppClientDir(), "configuration", "appclient.xml");
        dump(appClientXmlFile, "<original content of appclient configuration>");
        byte[] originalAppClientHash = calculateHash(appClientXmlFile);
        File standaloneXmlFile = touch(env.getInstalledImage().getStandaloneDir(), "configuration", "standalone.xml");
        dump(standaloneXmlFile, "<original content of standalone configuration>");
        byte[] originalStandaloneHash = calculateHash(standaloneXmlFile);
        File domainXmlFile = touch(env.getInstalledImage().getDomainDir(), "configuration", "domain.xml");
        dump(domainXmlFile, "<original content of domain configuration>");
        byte[] originalDomainHash = calculateHash(domainXmlFile);

        // build a cumulative patch for the base installation
        // with 1 added module
        String patchID = randomString();
        File patchDir = mkdir(tempDir, patchID);
        String moduleName = randomString();
        File moduleDir = createModule(patchDir, moduleName);
        byte[] newHash = calculateHash(moduleDir);
        ContentModification moduleAdded = new ContentModification(new ModuleItem(moduleName, newHash), NO_CONTENT , ADD);

        Patch patch = PatchBuilder.create()
                .setPatchId(patchID)
                .setDescription(randomString())
                .setPatchType(PatchType.CUMULATIVE)
                .setResultingVersion(info.getVersion() + "-CP")
                .addAppliesTo(info.getVersion())
                .addContentModification(moduleAdded)
                .build();
        createPatchXMLFile(patchDir, patch);
        File zippedPatch = createZippedPatchFile(patchDir, patchID);

        PatchingTaskRunner runner = new PatchingTaskRunner(info, env);
        PatchingResult result = runner.executeDirect(new FileInputStream(zippedPatch), ContentVerificationPolicy.STRICT);

        assertPatchHasBeenApplied(result, patch);
        tree(result.getPatchInfo().getEnvironment().getInstalledImage().getJbossHome());
        assertDefinedModule(result.getPatchInfo().getModulePath(), moduleName, newHash);
        // check the AS7 config files have been backed up
        File backupAppclientXmlFile = assertFileExists(result.getPatchInfo().getEnvironment().getHistoryDir(patchID), "configuration", "appclient", "appclient.xml");
        assertFileContent(originalAppClientHash, backupAppclientXmlFile);
        File backupStandaloneXmlFile = assertFileExists(result.getPatchInfo().getEnvironment().getHistoryDir(patchID), "configuration", "standalone", "standalone.xml");
        assertFileContent(originalStandaloneHash, backupStandaloneXmlFile);
        File backupDomainXmlFile = assertFileExists(result.getPatchInfo().getEnvironment().getHistoryDir(patchID), "configuration", "domain", "domain.xml");
        assertFileContent(originalDomainHash, backupDomainXmlFile);
        
        // let's change the standalone.xml file
        dump(standaloneXmlFile, "<updated standalone configuration with changes from the added module>");
        byte[] updatedStandaloneXmlFile = calculateHash(standaloneXmlFile);

        runner = new PatchingTaskRunner(result.getPatchInfo(), result.getPatchInfo().getEnvironment());
        PatchingResult rollbackResult = runner.rollback(patchID, true);
        
        assertPatchHasBeenRolledBack(rollbackResult, patch, info);
        File rolledBackStandaloneXmlFile = assertFileExists(result.getPatchInfo().getEnvironment().getInstalledImage().getStandaloneDir(), "configuration", "standalone.xml");
        //FIXME assertEquals("updated content was " + bytesToHexString(updatedStandaloneXmlFile), bytesToHexString(originalStandaloneHash), bytesToHexString(calculateHash(rolledBackStandaloneXmlFile)));
        
    }

}
