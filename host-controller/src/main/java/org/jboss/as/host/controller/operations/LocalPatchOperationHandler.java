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

package org.jboss.as.host.controller.operations;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.host.controller.HostControllerEnvironment;
import org.jboss.as.patching.Patch;
import org.jboss.as.patching.PatchContentLoader;
import org.jboss.as.patching.PatchImpl;
import org.jboss.as.patching.PatchingException;
import org.jboss.as.patching.service.AbstractPatchingTask;
import org.jboss.as.patching.service.PatchInfo;
import org.jboss.as.patching.service.PatchInfoService;
import org.jboss.as.patching.service.PatchingProcess;
import org.jboss.as.process.AsyncProcessControllerClient;
import org.jboss.as.process.CommandLineConstants;
import org.jboss.dmr.ModelNode;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.SimpleClassResolver;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.msc.service.ServiceController;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * {@code OperationStepHandler} to a apply a patch to local host-controller.
 *
 * @author Emanuel Muckenhuber
 */
public class LocalPatchOperationHandler extends AbstractPatchingTask implements OperationStepHandler {

    public static final String OPERATION_NAME = "apply-patch";

    private static final String PATCHING_PROCESS_NAME = "patching-process";
    private static final MarshallerFactory MARSHALLER_FACTORY;
    private static final MarshallingConfiguration CONFIG;
    static {
        try {
            MARSHALLER_FACTORY = Marshalling.getMarshallerFactory("river", Module.getModuleFromCallerModuleLoader(ModuleIdentifier.fromString("org.jboss.marshalling.river")).getClassLoader());
        } catch (ModuleLoadException e) {
            throw new RuntimeException(e);
        }
        final ClassLoader cl = LocalPatchOperationHandler.class.getClassLoader();
        final MarshallingConfiguration config = new MarshallingConfiguration();
        config.setVersion(2);
        config.setClassResolver(new SimpleClassResolver(cl));
        CONFIG = config;
    }

    private final HostControllerEnvironment hostEnvironment;
    private final AsyncProcessControllerClient client;

    public LocalPatchOperationHandler(final AsyncProcessControllerClient client, final HostControllerEnvironment hostEnvironment) {
        this.client = client;
        this.hostEnvironment = hostEnvironment;
    }

    @Override
    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
        // Acquire the controller lock
        context.acquireControllerLock();

        final Patch patch = PatchImpl.fromModelNode(operation);
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
                final ServiceController<?> controller = context.getServiceRegistry(false).getRequiredService(PatchInfoService.NAME);
                final PatchInfo info = (PatchInfo) controller.getValue();
                PatchInfo newInfo = null;
                try {
                    newInfo = LocalPatchOperationHandler.this.execute(patch, new PatchingContext() {
                        @Override
                        public PatchInfo getPatchInfo() {
                            return info;
                        }

                        @Override
                        public PatchContentLoader getContentLoader() {
                            return new PatchContentLoader() {
                                @Override
                                public InputStream openContentStream() {
                                    return context.getAttachmentStream(0);
                                }
                            };
                        }
                    });
                } catch(PatchingException e) {
                    e.printStackTrace();
                    context.getFailureDescription().set(e.getMessage());
                }
                try {
                    final String workDir = hostEnvironment.getHomeDir().getAbsolutePath();
                    final Map<String, String> env = new HashMap<String, String>();

                    final String modulePath = newInfo.getModulePath();

                    LocalPatchOperationHandler.write(newInfo);

                    final byte[] authKey = createAuthKey();
                    final String[] cmd = createPatchingProcessCmd(hostEnvironment, modulePath);
                    client.addPrivilegedProcess(PATCHING_PROCESS_NAME, authKey, cmd, workDir, env, false);
                    client.startProcess(PATCHING_PROCESS_NAME);

                    final InetSocketAddress address = new InetSocketAddress(hostEnvironment.getProcessControllerAddress(), hostEnvironment.getProcessControllerPort().intValue());

                    final PatchingProcess.PatchInformation information = new PatchingProcess.PatchInformation(address,
                            InetSocketAddress.createUnresolved("localhost", 9999),
                            createAuthKey(), createHCCommand(hostEnvironment, modulePath), env, workDir);

                    final Marshaller marshaller = MARSHALLER_FACTORY.createMarshaller(CONFIG);
                    final OutputStream os = client.sendStdin("patching-process");
                    marshaller.start(Marshalling.createByteOutput(os));
                    marshaller.writeObject(information);
                    marshaller.finish();
                    marshaller.close();
                    os.close();

                } catch (IOException e) {
                    e.printStackTrace();
                    throw new OperationFailedException(new ModelNode().set("failed to write new patch files"));
                }
                context.completeStep();
            }
        }, OperationContext.Stage.RUNTIME);

        context.completeStep();
    }

    static byte[] createAuthKey() {
        final Random rng = new Random(new SecureRandom().nextLong());
        byte[] authKey = new byte[16];
        rng.nextBytes(authKey);
        return authKey;
    }

    static String[] createPatchingProcessCmd(final HostControllerEnvironment environment, final String modulePath) {
        final String bootModule = "org.jboss.as.patching.process";
        final List<String> command = createCommand(environment, bootModule, modulePath);

        String loggingConfiguration = System.getProperty("logging.configuration");
        if (loggingConfiguration == null) {
            loggingConfiguration = "file:" + environment.getDomainConfigurationDir().getAbsolutePath() + "/logging.properties";
        }
        command.add("-Dlogging.configuration=" + loggingConfiguration);
        return command.toArray(new String[command.size()]);
    }

    static String[] createHCCommand(final HostControllerEnvironment environment, final String modulePath) {
        final String bootModule = "org.jboss.as.host-controller";
        final List<String> command = createCommand(environment, bootModule, modulePath);
        environment.getRawCommandLineArgs().append(command);
        final String loggingConfiguration = System.getProperty("logging.configuration");
        if (loggingConfiguration != null) {
            command.add("-Dlogging.configuration=" + loggingConfiguration);
        }
        return command.toArray(new String[command.size()]);
    }

    static List<String> createCommand(final HostControllerEnvironment environment, final String bootModule, final String modulePath) {

        final String jvm = environment.getDefaultJVM().getAbsolutePath();
        final List<String> javaOptions = new ArrayList<String>();
        final String bootJar = "jboss-modules.jar";
        final String logModule = "org.jboss.logmanager";
        final String jaxpModule = "javax.xml.jaxp-provider";

        return createCommand(jvm, javaOptions, bootJar, modulePath, logModule, jaxpModule, bootModule);
    }

    /**
     * Create the launch command...
     *
     * @param jvm the jvm path (java)
     * @param javaOptions some java options
     * @param bootJar the boot jar (java -jar)
     * @param modulePath the module path (-mp)
     * @param logModule the log module
     * @param jaxpModule the jaxp module
     * @param bootModule the boot module
     * @return the launch command
     */
    static List<String> createCommand(final String jvm, final List<String> javaOptions, String bootJar, String modulePath,
                                  String logModule, String jaxpModule, String bootModule) {
        final List<String> command = new ArrayList<String>();

        command.add(jvm);
        command.addAll(javaOptions);
        command.add("-jar");
        command.add(bootJar);
        command.add("-mp");
        command.add(modulePath);
        command.add("-logmodule");
        command.add(logModule);
        command.add("-jaxpmodule");
        command.add(jaxpModule);
        command.add(bootModule);

        return command;
    }

}
