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

package org.jboss.as.test.integration.domain;

import org.jboss.as.arquillian.container.domain.managed.DomainLifecycleUtil;
import org.jboss.as.controller.Extension;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.patching.Patch;
import org.jboss.as.patching.PatchContentLoader;
import org.jboss.as.patching.PatchImpl;
import org.jboss.as.patching.PatchingClient;
import org.jboss.as.patching.PatchingPlanBuilder;
import org.jboss.as.patching.domain.DomainPatchingClient;
import org.jboss.as.patching.domain.DomainPatchingPlanBuilder;
import org.jboss.as.test.example.ExampleExtension;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.GenericArchive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;

/**
 * @author Emanuel Muckenhuber
 */
public class HostPatchOperationTestCase {

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainMasterLifecycleUtil;
    private static DomainLifecycleUtil domainSlaveLifecycleUtil;

    private static final ModelNode PATCH_INFO_OP = new ModelNode();

    static {
        PATCH_INFO_OP.get(ModelDescriptionConstants.OP).set("patch-info");
        PATCH_INFO_OP.get(ModelDescriptionConstants.OP_ADDR).add("host", "slave");
        PATCH_INFO_OP.protect();
    }

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = new DomainTestSupport(HostPatchOperationTestCase.class.getSimpleName(), "domain-configs/domain-standard.xml", "host-configs/host-master.xml", "host-configs/host-slave.xml");
        testSupport.start();
        domainMasterLifecycleUtil = testSupport.getDomainMasterLifecycleUtil();
        domainSlaveLifecycleUtil = testSupport.getDomainSlaveLifecycleUtil();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport.stop();
    }

    @Test
    public void testRemote() throws Exception {

        final ModelControllerClient client = ModelControllerClient.Factory.create(DomainTestSupport.masterAddress, 9999);
        try {

            // Create the patch content, which is just a new extension
            final File temp = File.createTempFile("test", "remote");
            temp.deleteOnExit();
            final byte[] hash = createEmptyArchive(temp);

            // Get the current patch info
            final ModelNode patchInfo = TestUtils.executeForResult(client, PATCH_INFO_OP);

            // Build the patch metdata
            final ModelNode patch = new ModelNode();
            patch.get(PatchImpl.PATCH_ID).set("patch-01");
            patch.get(PatchImpl.DESCRIPTION).set("new extension");
            patch.get(PatchImpl.PATCH_TYPE).set(Patch.PatchType.CUMULATIVE.toString());
            patch.get(PatchImpl.CONTENT_HASH).set(hash);
            patch.get(PatchImpl.APPLIES_TO).add(patchInfo.get("version"));

            // Create the patching client
            final DomainPatchingClient patchingClient = PatchingClient.Factory.createDomainClient(client);
            final Patch p = patchingClient.create(patch);
            final DomainPatchingPlanBuilder builder = patchingClient.createBuilder(p, new PatchContentLoader() {
                @Override
                public InputStream openContentStream() throws IOException {
                    return new FileInputStream(temp);
                }
            });
            // Execute the patching plan
            builder.addHost("master")
                   .addHost("slave").build().execute();

            temp.delete();

            // And now enable our new extension on the host!
            final ModelNode operation = new ModelNode();
            operation.get(ModelDescriptionConstants.OP).set("add");
            operation.get(ModelDescriptionConstants.OP_ADDR).add("extension", "org.jboss.as.test.example");

            TestUtils.executeForResult(client, operation);

        } finally {
            try {
                client.close();
            } catch (IOException ioe) {
                //
            }
        }
    }

    static byte[] createEmptyArchive(final File output) throws Exception {
        final Archive archive = ShrinkWrap.create(GenericArchive.class);
        final Archive extension = createJar();
        archive.add(extension, "org/jboss/as/test/example/main", ZipExporter.class);
        archive.add(new StringAsset(MODULE_XML), "org/jboss/as/test/example/main/module.xml");
        return exportArchive(output, archive);
    }

    static Archive createJar() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "example.jar");
        archive.addPackage(ExampleExtension.class.getPackage());
        archive.addAsServiceProvider(Extension.class, ExampleExtension.class);
        return archive;
    }

    static byte[] exportArchive(final File output, final Archive archive) throws Exception {
        final FileOutputStream fos = new FileOutputStream(output);
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.reset();
            final DigestOutputStream os = new DigestOutputStream(fos, digest);
            archive.as(ZipExporter.class).exportTo(os);
            return os.getMessageDigest().digest();
        } finally {
            fos.close();
        }
    }

    static final String MODULE_XML = "<module xmlns=\"urn:jboss:module:1.0\" name=\"org.jboss.as.test.example\">\n" +
            "    <resources>\n" +
            "        <resource-root path=\"example.jar\"/>\n" +
            "    </resources>\n" +
            "\n" +
            "    <dependencies>\n" +
            "        <module name=\"javax.api\"/>\n" +
            "        <module name=\"org.jboss.staxmapper\"/>\n" +
            "        <module name=\"org.jboss.as.controller\"/>\n" +
            "        <module name=\"org.jboss.as.network\"/>\n" +
            "        <module name=\"org.jboss.as.server\" />\n" +
            "        <module name=\"org.jboss.msc\"/>\n" +
            "        <module name=\"org.jboss.logging\"/>\n" +
            "    </dependencies>\n" +
            "</module>";

}
