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

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TO_REPLACE;
import org.jboss.as.patching.domain.DomainPatchingClient;
import org.jboss.as.patching.domain.DomainPatchingPlanBuilder;
import org.jboss.as.patching.standalone.StandalonePatchingClient;
import org.jboss.as.patching.standalone.StandalonePatchingPlan;
import org.jboss.as.patching.standalone.StandalonePatchingPlanBuilder;
import org.jboss.dmr.ModelNode;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.metal.MetalLookAndFeel;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * The interactive patch tool.
 *
 * @author Emanuel Muckenhuber
 */
public final class PatchTool {

    private static final String HOST = System.getProperty("host", "localhost");
    private static final int PORT = Integer.getInteger("port", 9999);

    private static final ModelNode PATCH_INFO_OP = new ModelNode();
    private static final ModelNode LAUNCH_TYPE_OP = new ModelNode();
    private static final ModelNode READ_HOST_NAMES = new ModelNode();

    private static String swingLAF;

    static {
        // the patch info
        PATCH_INFO_OP.get(ModelDescriptionConstants.OP).set("patch-info");
        PATCH_INFO_OP.protect();

        //
        LAUNCH_TYPE_OP.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION);
        LAUNCH_TYPE_OP.get(ModelDescriptionConstants.OP_ADDR).setEmptyList();
        LAUNCH_TYPE_OP.get(ModelDescriptionConstants.NAME).set("launch-type");
        LAUNCH_TYPE_OP.protect();

        // Host names
        READ_HOST_NAMES.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.READ_CHILDREN_NAMES_OPERATION);
        READ_HOST_NAMES.get(ModelDescriptionConstants.OP_ADDR).setEmptyList();
        READ_HOST_NAMES.get(ModelDescriptionConstants.CHILD_TYPE).set(ModelDescriptionConstants.HOST);
        READ_HOST_NAMES.protect();
    }

    public static void main(final String[] args) throws Exception {

        final InputStreamReader converter = new InputStreamReader(System.in);
        final BufferedReader in = new BufferedReader(converter);

        final ModelControllerClient client = ModelControllerClient.Factory.create(HOST, PORT);
        try {
            boolean standalone = PatchClientUtils.isStandalone(client);
            final ModelNode patchInfo;
            if(standalone) {
                final ModelNode operation = PATCH_INFO_OP.clone();
                operation.get(ModelDescriptionConstants.OP_ADDR).setEmptyList();
                patchInfo = executeForResult(client, operation);
            } else {
                final ModelNode operation = PATCH_INFO_OP.clone();
                operation.get(ModelDescriptionConstants.OP_ADDR).add("host", "master");
                patchInfo = executeForResult(client, operation);
            }

            System.out.println("patch metadata builder...");
            System.out.print("> patch-id: ");
            final String id = in.readLine();
            final String type = chooseType(in).toString();
            final File content = chooseFile(in);
            if(content == null) {
                throw new IllegalStateException("null file");
            }
            final byte[] hash = calculateHash(content);

            final ModelNode patchModel = new ModelNode();
            patchModel.get(PatchImpl.PATCH_ID).set(id);
            patchModel.get(PatchImpl.DESCRIPTION).set(id);
            patchModel.get(PatchImpl.PATCH_TYPE).set(type);
            patchModel.get(PatchImpl.CONTENT_HASH).set(hash);
            patchModel.get(PatchImpl.APPLIES_TO).add(patchInfo.get("version"));

            final PatchContentLoader loader = PatchContentLoader.FilePatchContentLoader.create(content);
            if (standalone) {
                // Patch standalone
                final StandalonePatchingClient patchingClient = PatchingClient.Factory.createStandaloneClient(client);
                final Patch patch = patchingClient.create(patchModel);
                // Create the plan
                final StandalonePatchingPlanBuilder builder = patchingClient.createBuilder(patch, loader);
                final StandalonePatchingPlan plan = builder.build();

                System.out.println("Patch summary:");
                System.out.println(patchModel);

                if(executeQuestionMark(in)) {
                    // Execute the plan
                    plan.execute();

                    System.out.print("Validate the patch (Note the server has to be restarted manually before...) [y/n]? ");
                    boolean validate = yesQuestionMark(in);
                    if(validate) {
                        ModelNode newPatchInfo = null;
                        TimeUnit.SECONDS.sleep(3);
                        for(int i = 0; i < 5; i++) {
                            try {
                                final ModelNode operation = PATCH_INFO_OP.clone();
                                operation.get(ModelDescriptionConstants.OP_ADDR).setEmptyList();
                                newPatchInfo = executeForResult(client, operation);
                            } catch (Exception e) {
                                TimeUnit.SECONDS.sleep(1);
                            }
                        }
                        if(newPatchInfo == null) {
                            System.out.println("Failed to validate patch.");
                        } else {
                            System.out.println(newPatchInfo);
                            PatchClientUtils.validatePatchInfo(newPatchInfo, patch);
                        }
                    }
                    System.out.println("Done.");
                }
            } else {
                // Patch the domain
                final DomainPatchingClient patchingClient = PatchingClient.Factory.createDomainClient(client);
                final Patch patch = patchingClient.create(patchModel);

                final List<String> hostNames = chooseHosts(readHostNames(client), in);
                if(hostNames == null || hostNames.isEmpty()) {
                    System.out.println("no host selected.");
                    System.exit(1);
                }
                // Create the domain patching plan builder
                final DomainPatchingPlanBuilder builder = patchingClient.createBuilder(patch, loader);
                // Add all hosts
                for(final String host : hostNames) {
                    builder.addHost(host);
                }
                // Create the plan
                final PatchingPlan plan = builder.build();

                System.out.println("Patch summary:");
                System.out.println(PatchImpl.toModelNode(patch));
                System.out.println("Hosts to patch: " + hostNames);

                if(executeQuestionMark(in)) {
                    // execute the plan
                    System.out.println("Running patch...");
                    plan.execute();
                    System.out.println("Done.");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            client.close();
        }
    }

    /**
     * Choose the hosts to patch.
     *
     * @param hostNames the host names
     * @return
     */
    static List<String> chooseHosts(final Set<String> hostNames, final BufferedReader in) throws IOException {
        final List<String> selectedHosts = new ArrayList<String>();
        for(;;) {
            System.out.print("> available hosts: ");
            System.out.println(hostNames);
            System.out.print("> select host ('Q' to quit): ");
            final String hostName= in.readLine();
            if (hostName.equals("Q")) {
                return selectedHosts;
            }
            if(hostNames.remove(hostName)) {
                selectedHosts.add(hostName);
            }
            if (hostNames.isEmpty()) {
                return selectedHosts;
            }
        }
    }

    static boolean executeQuestionMark(final BufferedReader in) throws IOException {
        System.out.print("Execute patch [y/n]? ");
        return yesQuestionMark(in);
    }

    static boolean yesQuestionMark(final BufferedReader in) throws IOException {
        final String execute = in.readLine().toLowerCase();
        if("y".equals(execute) || "yes".equals(execute) || "yay".equals(execute)) {
            return true;
        }
        return false;
    }

    private static Patch.PatchType chooseType(final BufferedReader in) throws IOException {
        for(;;) {
            System.out.print("> patch-type [cumulative, one-off]: ");
            final String line = in.readLine();
            try {
                final Patch.PatchType type = Patch.PatchType.valueOf(line.replace('-', '_').toUpperCase());
                return type;
            } catch (final Exception e) {
                System.out.println("invalid type " + line);
            }
        }
    }

    private static File chooseFile(final BufferedReader in) throws IOException {
        final ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(PatchTool.class.getClassLoader());
            initializeSwing();
            JFileChooser chooser = new JFileChooser();
            FileNameExtensionFilter filter = new FileNameExtensionFilter("Archives", "zip");
            chooser.setFileFilter(filter);
            System.out.println("> patch-content: ");
            int returnVal = chooser.showOpenDialog(null);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                return chooser.getSelectedFile();
            }
        } catch (Exception e) {
            System.err.println("failed to setup swing...");
            for(;;) {
                System.out.print("> please enter file system path: ");
                final String file = in.readLine();
                final File f = new File(file);
                if(f.isFile()) {
                    return f;
                }
                System.out.println(f.getAbsolutePath() + " does not exist.");
            }
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
        return null;
    }

    private static synchronized void initializeSwing() {
        if (swingLAF != null)
            return;

        if (System.getProperty("swing.defaultlaf") != null)
            return;

        String sep = File.separator;
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            Properties props = new Properties();
            File file = new File(javaHome + sep + "lib" + sep + "swing.properties");
            if (file.exists()) {
                // InputStream has been buffered in Properties class
                FileInputStream ins = null;
                try {
                    ins = new FileInputStream(file);
                    props.load(ins);
                } catch (IOException ignored) {
                } finally {
                    if (ins != null) {
                        try {
                            ins.close();
                        } catch (IOException e) {
                            // ignore
                        }
                    }
                }
            }
            String clazz = props.getProperty("swing.defaultlaf");
            if (clazz != null) {
                try {
                    PatchTool.class.getClassLoader().loadClass(clazz);
                    swingLAF = clazz;
                } catch (ClassNotFoundException e) {
                    // ignore; we'll use Metal below
                }
            }
        }

        if (swingLAF == null) {
            // Configure swing to use a L&F that's in classes.jar javax.swing package
            swingLAF = MetalLookAndFeel.class.getName();
            System.setProperty("swing.defaultlaf", swingLAF);
        }


    }

    private static char[] table = {
            '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };

    /**
     * Convert a byte array into a hex string.
     *
     * @param bytes the bytes
     * @return the string
     */
    public static String bytesToHexString(final byte[] bytes) {
        final StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(table[b >> 4 & 0x0f]).append(table[b & 0x0f]);
        }
        return builder.toString();
    }

    /**
     * Convert a hex string into a byte[].
     *
     * @param s the string
     * @return the bytes
     */
    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len >> 1];
        for (int i = 0, j = 0; j < len; i++) {
            int x = Character.digit(s.charAt(j), 16) << 4;
            j++;
            x = x | Character.digit(s.charAt(j), 16);
            j++;
            data[i] = (byte) (x & 0xFF);
        }
        return data;
    }

    static final int DEFAULT_BUFFER_SIZE = 65536;
    static final OutputStream NULL_OUTPUT_STREAM = new OutputStream() {
        public void write(int b) throws IOException {
            //
        }
    };

    /**
     * Calculate the SHA-1 hash for a given file.
     *
     * @param file the file
     * @return the hash
     * @throws Exception for any error
     */
    static byte[] calculateHash(final File file) throws Exception {
        final MessageDigest digest = MessageDigest.getInstance("SHA-1");
        digest.reset();
        final InputStream is = new FileInputStream(file);
        try {
            final DigestOutputStream os = new DigestOutputStream(NULL_OUTPUT_STREAM, digest);
            try {
                byte[] buff = new byte[DEFAULT_BUFFER_SIZE];
                int rc;
                while ((rc = is.read(buff)) != -1) os.write(buff, 0, rc);

                return os.getMessageDigest().digest();
            } finally {
                os.close();
            }
        } finally {
            is.close();
        }
    }

    /**
     * Read the available host names.
     *
     * @param client the model controller client
     * @return the host names
     * @throws PatchingException for any error
     */
    static Set<String> readHostNames(final ModelControllerClient client) throws PatchingException {
        final ModelNode result = executeForResult(client, READ_HOST_NAMES);
        final Set<String> hostNames = new LinkedHashSet<String> ();
        for(final ModelNode host : result.asList()) {
            hostNames.add(host.asString());
        }
        return hostNames;
    }

    static ModelNode executeForResult(final ModelControllerClient client, final ModelNode operation) throws PatchingException {
        return executeForResult(client, OperationBuilder.create(operation).build());
    }

    static ModelNode executeForResult(final ModelControllerClient client, final Operation operation) throws PatchingException {
        try {
            final ModelNode result = client.execute(operation);
            if(SUCCESS.equals(result.get(OUTCOME).asString())) {
                return result.get(RESULT);
            } else {
                throw new PatchingException("operation failed " + result);
            }
        } catch(IOException ioe) {
            throw new PatchingException(ioe);
        }
    }

}
