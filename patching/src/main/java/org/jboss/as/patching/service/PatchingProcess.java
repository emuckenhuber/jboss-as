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

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import org.jboss.as.patching.PatchingException;
import org.jboss.as.process.AsyncProcessControllerClient;
import org.jboss.as.process.Main;
import org.jboss.as.process.protocol.ProtocolClient;
import org.jboss.as.process.protocol.StreamUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.logmanager.Level;
import org.jboss.marshalling.ByteInput;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.SimpleClassResolver;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.stdio.LoggingOutputStream;
import org.jboss.stdio.NullInputStream;
import org.jboss.stdio.SimpleStdioContextSelector;
import org.jboss.stdio.StdioContext;

import javax.net.SocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * A process used to restart the host controller.
 *
 * @author Emanuel Muckenhuber
 */
public class PatchingProcess {

    private static final String HOST_CONTROLLER_PROCESS_NAME = Main.HOST_CONTROLLER_PROCESS_NAME;

    private static final MarshallerFactory MARSHALLER_FACTORY;
    private static final MarshallingConfiguration CONFIG;
    static {
        try {
            MARSHALLER_FACTORY = Marshalling.getMarshallerFactory("river", Module.getModuleFromCallerModuleLoader(ModuleIdentifier.fromString("org.jboss.marshalling.river")).getClassLoader());
        } catch (ModuleLoadException e) {
            throw new RuntimeException(e);
        }
        final ClassLoader cl = PatchingProcess.class.getClassLoader();
        final MarshallingConfiguration config = new MarshallingConfiguration();
        config.setVersion(2);
        config.setClassResolver(new SimpleClassResolver(cl));
        CONFIG = config;
    }

    public static class PatchInformation implements Serializable {

        private final InetSocketAddress pcAddress;
        private final InetSocketAddress mgmtAddress;

        private byte[] authKey;
        private String[] command;
        private Map<String, String> env;
        private String workDir;

        public PatchInformation(final InetSocketAddress pcAddress, final InetSocketAddress mgmtAddress, final byte[] authKey, final String[] command,
                                final Map<String, String> env, final String workingDir) {
            this.pcAddress = pcAddress;
            this.mgmtAddress = mgmtAddress;
            this.authKey = authKey;
            this.command = command;
            this.env = env;
            this.workDir = workingDir;
        }

        public InetSocketAddress getProcessControllerAddress() {
            return pcAddress;
        }

        public InetSocketAddress getMgmtAddress() {
            return mgmtAddress;
        }
    }

    public static void main(String[] args) {
        // TODO: privileged block
        System.setProperty("log4j.defaultInitOverride", "true");

        final InputStream initialInput = System.in;
        final PrintStream initialError = System.err;

        // Install JBoss Stdio to avoid any nasty crosstalk.
        StdioContext.install();
        final StdioContext context = StdioContext.create(
            new NullInputStream(),
            new LoggingOutputStream(org.jboss.logmanager.Logger.getLogger("stdout"), Level.INFO),
            new LoggingOutputStream(org.jboss.logmanager.Logger.getLogger("stderr"), Level.ERROR)
        );
        StdioContext.setStdioContextSelector(new SimpleStdioContextSelector(context));

        final byte[] authKey = new byte[16];
        try {
            StreamUtils.readFully(initialInput, authKey);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
            throw new IllegalStateException(); // not reached
        }
        final Unmarshaller unmarshaller;
        final ByteInput byteInput;

        try {
            unmarshaller = MARSHALLER_FACTORY.createUnmarshaller(CONFIG);
            byteInput = Marshalling.createByteInput(initialInput);

            unmarshaller.start(byteInput);
            PatchInformation information = unmarshaller.readObject(PatchInformation.class);
            unmarshaller.finish();

            final ProtocolClient.Configuration configuration = new ProtocolClient.Configuration();
            configuration.setSocketFactory(SocketFactory.getDefault());
            configuration.setReadExecutor(Executors.newCachedThreadPool());
            configuration.setServerAddress(information.getProcessControllerAddress());
            configuration.setThreadFactory(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r);
                }
            });

            final AsyncProcessControllerClient client = AsyncProcessControllerClient.connect(configuration, authKey);

            client.stopProcess(HOST_CONTROLLER_PROCESS_NAME).get();
            client.removeProcess(HOST_CONTROLLER_PROCESS_NAME).get();
            client.addPrivilegedProcess(HOST_CONTROLLER_PROCESS_NAME, information.authKey, information.command, information.workDir, information.env, true).get();
            client.startProcess(HOST_CONTROLLER_PROCESS_NAME).get();

            Thread.sleep(TimeUnit.SECONDS.toMillis(5));

            final ModelControllerClient controller = ModelControllerClient.Factory.create(information.getMgmtAddress().getHostName(), information.getMgmtAddress().getPort());

            final ModelNode operation = new ModelNode();
            operation.get(OP).set(READ_RESOURCE_OPERATION);
            operation.get(OP_ADDR).setEmptyList();
            executeForResult(controller, OperationBuilder.create(operation).build());

        } catch(Exception e) {
            e.printStackTrace(initialError);
            System.exit(-6666);
            throw new IllegalStateException(); // not reached
        } finally {
            //
        }
        System.exit(-6666);
    }

    static ModelNode executeForResult(final ModelControllerClient client, final Operation operation) throws PatchingException {
        try {
            final ModelNode result = client.execute(operation);
            if(SUCCESS.equals(result.get(OUTCOME).asString()) && result.hasDefined(RESULT)) {
                return result.get(RESULT);
            } else {
                throw new PatchingException("operation failed " + result);
            }
        } catch(IOException ioe) {
            throw new PatchingException(ioe);
        }
    }

}
