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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.jboss.as.host.controller.mgmt.ManagementCommunicationService;
import org.jboss.as.process.ProcessControllerClient;
import org.jboss.as.process.ProcessInfo;
import org.jboss.as.process.ProcessMessageHandler;
import org.jboss.as.server.services.net.NetworkInterfaceBinding;
import org.jboss.logging.Logger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

/**
 * @author Emanuel Muckenhuber
 */
class ServerInventoryService implements Service<ServerInventory> {
    private static final Logger log = Logger.getLogger("org.jboss.as.host.controller");

    static final ServiceName SERVICE_NAME = ServiceName.JBOSS.append("host", "controller", "server-inventory");

    private final InjectedValue<ProcessControllerConnectionService> client = new InjectedValue<ProcessControllerConnectionService>();
    private final InjectedValue<ExecutorService> executor = new InjectedValue<ExecutorService>();
    private final HostControllerEnvironment environment;

    private ServerInventory serverInventory;

    ServerInventoryService(final HostControllerEnvironment environment) {
        this.environment = environment;
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void start(StartContext context) throws StartException {
        log.debug("Starting Host Controller Server Inventory");
        final ServerInventory serverInventory;
        try {
            final ProcessControllerClient client = this.client.getValue().getClient();
            final ExecutorService executor = this.executor.getValue();
            serverInventory = new ServerInventory(environment, client, executor);
        } catch (Exception e) {
            throw new StartException(e);
        }
        this.serverInventory = serverInventory;
        client.getValue().setServerInventory(serverInventory);
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void stop(StopContext context) {
        this.serverInventory = null;
        client.getValue().setServerInventory(null);
    }

    /** {@inheritDoc} */
    @Override
    public synchronized ServerInventory getValue() throws IllegalStateException, IllegalArgumentException {
        final ServerInventory serverInventory = this.serverInventory;
        if(serverInventory == null) {
            throw new IllegalStateException();
        }
        return serverInventory;
    }

    InjectedValue<ProcessControllerConnectionService> getClient() {
        return client;
    }

    InjectedValue<ExecutorService> getExecutor() {
        return executor;
    }

}
