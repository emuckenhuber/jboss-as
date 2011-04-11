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

package org.jboss.as.host.controller;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;

import org.jboss.as.controller.BasicModelController;
import org.jboss.as.controller.BasicOperationResult;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationHandler;
import org.jboss.as.controller.OperationResult;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProxyController;
import org.jboss.as.controller.ResultHandler;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.helpers.domain.ServerStatus;
import org.jboss.as.controller.persistence.ExtensibleConfigurationPersister;
import org.jboss.as.controller.persistence.NullConfigurationPersister;
import org.jboss.as.controller.registry.BasicNodeRegistration;
import org.jboss.as.domain.controller.DomainController;
import org.jboss.as.process.ProcessControllerClient;
import org.jboss.as.process.ProcessInfo;
import org.jboss.as.protocol.Connection;
import org.jboss.as.server.ServerState;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;

/**
 * Inventory of the managed servers.
 *
 * @author Emanuel Muckenhuber
 * @author Kabir Khan
 */
class ServerInventory implements ManagedServerLifecycleCallback {

    private static final Logger log = Logger.getLogger("org.jboss.as.host.controller");
    private final Map<String, ManagedServer> servers = Collections.synchronizedMap(new HashMap<String, ManagedServer>());

    private final HostControllerEnvironment environment;
    private final ProcessControllerClient processControllerClient;
    private final InetSocketAddress managementAddress;
    private volatile CountDownLatch processInventoryLatch;
    private volatile Map<String, ProcessInfo> processInfos;
    private final ExecutorService executorService;

    ServerInventory(final HostControllerEnvironment environment, final InetSocketAddress managementAddress,
                    final ProcessControllerClient processControllerClient, ExecutorService executorService) {
        this.environment = environment;
        this.managementAddress = managementAddress;
        this.processControllerClient = processControllerClient;
        this.executorService = executorService;
    }

    synchronized Map<String, ProcessInfo> determineRunningProcesses(){
        processInventoryLatch = new CountDownLatch(1);
        try {
            processControllerClient.requestProcessInventory();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            if (!processInventoryLatch.await(30, TimeUnit.SECONDS)){
                throw new RuntimeException("Could not get the server inventory in 30 seconds");
            }
        } catch (InterruptedException e) {
        }
        return processInfos;

    }

    @Override
    public void processInventory(Map<String, ProcessInfo> processInfos) {
        this.processInfos = processInfos;
        if (processInventoryLatch != null){
            processInventoryLatch.countDown();
        }
    }

    /**
     * Determine the status of a managed server.
     *
     * @param serverName the server name
     * @return the state of the server. <code>ServerStatus.DOES_NOT_EXIST</code> if there is not such server registered
     */
    ServerStatus determineServerStatus(final String serverName) {
        final String processName = ManagedServer.getServerProcessName(serverName);
        final ManagedServer client = servers.get(processName);
        if (client == null) {
            return ServerStatus.DOES_NOT_EXIST;
        } else {
            return ManagedServer.determineStatus(client.getState());
        }
    }

    void addServer(final String serverName) {
        addServer(serverName, ManagedServer.createAuthToken());
    }

    void addServer(final String serverName, byte[] authKey) {
        final String processName = ManagedServer.getServerProcessName(serverName);
        synchronized (servers) {
            final ManagedServer existing = servers.get(processName);
            if(existing != null) {
                throw new IllegalStateException("duplicate server " + serverName);
            }
            final ManagedServer server = createManagedServer(serverName);
            servers.put(processName, server);
        }
    }

    void removeServer(final String serverName) {
        final String processName = ManagedServer.getServerProcessName(serverName);
        synchronized (servers) {
            final ManagedServer server = servers.remove(processName);
            if(server == null) {
                //
            }
            servers.remove(processName);
        }
    }

    ServerStatus startServer(final String serverName, final ModelNode hostModel, final DomainController domainController) {
        final String processName = ManagedServer.getServerProcessName(serverName);
        synchronized (servers) {
            final ManagedServer server = getServer(processName);
            log.infof("Starting server %s", serverName);
            final ManagedServer.ManagedServerBootConfiguration bootConfiguration = createBootConfiguration(serverName, hostModel, domainController);
            server.setBootConfiguration(bootConfiguration);
            try {
                server.createServerProcess();
            } catch(IOException e) {
                log.errorf(e, "Failed to create server process %s", serverName);
            }
            try {
                server.startServerProcess();
            } catch(IOException e) {
                log.errorf(e, "Failed to start server %s", serverName);
            }
            return ManagedServer.determineStatus(server.getState());
        }

    }

    void reconnectServer(final String serverName, final ModelNode hostModel, final DomainController domainController, final boolean running){

        final String processName = ManagedServer.getServerProcessName(serverName);
        log.info("Reconnecting server " + serverName);
        final ManagedServer server = getServer(processName);
        if (running){
            try {
                server.reconnectServerProcess(environment.getHostControllerPort());
            } catch (IOException e) {
                log.errorf(e, "Failed to send reconnect message to server %s", serverName);
            }
        }
    }

    ServerStatus restartServer(String serverName, final int gracefulTimeout, final ModelNode hostModel, final DomainController domainController) {
        stopServer(serverName, gracefulTimeout);
        return startServer(serverName, hostModel, domainController);
    }

    ServerStatus stopServer(final String serverName, final int gracefulTimeout) {
        log.info("stopping server " + serverName);
        final String processName = ManagedServer.getServerProcessName(serverName);
        try {
            final ManagedServer server = getServer(processName);
            server.setState(ServerState.STOPPING);
            if (gracefulTimeout > -1) {
                // FIXME implement gracefulShutdown
                //server.gracefulShutdown(gracefulTimeout);
                // FIXME figure out how/when server.removeServerProcess() && servers.remove(processName) happens

                // Workaround until the above is fixed
                log.warnf("Graceful shutdown of server %s was requested but is not presently supported. " +
                        "Falling back to rapid shutdown.", serverName);
                server.stopServerProcess();
                server.removeServerProcess();
            }
            else {
                server.stopServerProcess();
                server.removeServerProcess();
            }
        }
        catch (final Exception e) {
            log.errorf(e, "Failed to stop server %s", serverName);
        }
        return determineServerStatus(serverName);
    }

    /** {@inheritDoc} */
    @Override
    public void serverRegistered(String serverName, Connection connection) {
        try {
            final ManagedServer server = getServer(serverName);
            server.setServerManagementConnection(connection);
            if (!environment.isRestart()){
                checkState(server, ServerState.STARTING);
            }
            server.setState(ServerState.STARTED);
            server.resetRespawnCount();
        } catch (final Exception e) {
            log.errorf(e, "Could not start server %s", serverName);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void serverStartFailed(String serverName) {
        final ManagedServer server = getServer(serverName);
        checkState(server, ServerState.STARTING);
        server.setState(ServerState.FAILED);
    }

    /** {@inheritDoc} */
    @Override
    public void serverStopped(String serverName) {
        final ManagedServer server = getServer(serverName);
        if (server.getState() != ServerState.STOPPING){
            //The server crashed, try to restart it
            // TODO: throttle policy
            try {
                //TODO make configurable
                if (server.incrementAndGetRespawnCount() < 10 ){
                    server.startServerProcess();
                    return;
                }
                server.setState(ServerState.MAX_FAILED);
            } catch(IOException e) {
                log.error("Failed to start server " + serverName, e);
            }
        }
        servers.remove(serverName);
    }

    private void checkState(final ManagedServer server, final ServerState expected) {
        final ServerState state = server.getState();
        if (state != expected) {
            log.warnf("Server %s is not in the expected %s state: %s" , server.getServerProcessName(), expected, state);
        }
    }

    private ManagedServer createManagedServer(final String serverName) {
        return new ManagedServer(serverName, processControllerClient, managementAddress, executorService);
    }

    protected ManagedServer.ManagedServerBootConfiguration createBootConfiguration(final String name, final ModelNode hostModel, final DomainController domainController) {
        return new ModelCombiner(name, hostModel, domainController, environment);
    }

    HostControllerEnvironment getEnvironment(){
        return environment;
    }

    protected ManagedServer getServer(final String processName) {
        synchronized (servers) {
            final ManagedServer server = servers.get(processName);
            if(server == null) {
                throw new IllegalStateException(String.format("Server not configured: %s", processName));
            }
            return server;
        }
    }

    protected Set<String> getChildNames() {
        final Set<String> names = new HashSet<String>();
        synchronized(servers) {
            for(final ManagedServer server : servers.values()) {
                names.add(server.getServerName());
            }
        }
        return names;
    }

    List<ManagedServer> getManagedServers(){
        synchronized (servers) {
            return new ArrayList<ManagedServer>(servers.values());
        }
    }
}
