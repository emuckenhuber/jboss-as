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

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResultHandler;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.persistence.ConfigurationFile;
import org.jboss.as.controller.persistence.ConfigurationPersistenceException;
import org.jboss.as.controller.persistence.ExtensibleConfigurationPersister;
import org.jboss.as.controller.registry.ModelNodeRegistration;
import org.jboss.as.domain.controller.DomainController;
import org.jboss.as.domain.controller.DomainModelImpl;
import org.jboss.as.domain.controller.LocalHostModel;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.msc.service.AbstractServiceListener;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.*;

/**
 * @author Emanuel Muckenhuber
 */
public class NewHostControllerService implements Service<LocalHostModel> {

    private final Logger log = Logger.getLogger("org.jboss.as.host.controller");

    private final InjectedValue<ServerInventory> inventoryValue = new InjectedValue<ServerInventory>();
    private final HostControllerEnvironment environment;

    private LocalHostModel proxyController;

    public NewHostControllerService(HostControllerEnvironment environment) {
        this.environment = environment;
    }

    @Override
    public synchronized void start(StartContext startContext) throws StartException {

        final ServiceTarget serviceContainer = startContext.getChildTarget();

        final File configDir = environment.getDomainConfigurationDir();
        final ConfigurationFile configurationFile = environment.getHostConfigurationFile();
        final ExtensibleConfigurationPersister configurationPersister = createHostConfigurationPersister(configDir, configurationFile);

        // The first step is to load the host model, this also ensures there are no parsing errors before we
        // spend time initialising everything else.
        try {

            final List<ModelNode> operations = configurationPersister.load();

            // The first default services are registered before the bootstrap operations are executed.
            final ServiceTarget serviceTarget = serviceContainer;
            serviceTarget.addListener(new AbstractServiceListener<Object>() {
                @Override
                public void serviceFailed(final ServiceController<?> serviceController, final StartException reason) {
                    log.errorf(reason, "Service [%s] failed.", serviceController.getName());
                }
            });

            // The Bootstrap domain model is initialised ready for the operations to bootstrap the remainder of the
            // host controller.
            DomainModelProxyImpl domainModelProxy = new DomainModelProxyImpl();
            final ModelNodeRegistration hostRegistry = HostModelUtil.createHostRegistry(configurationPersister, environment, domainModelProxy);
            final ModelNodeRegistration rootRegistration = HostModelUtil.createBootstrapHostRegistry(hostRegistry, domainModelProxy);
            DomainModelImpl domainModel = new DomainModelImpl(rootRegistration, (ServiceContainer) serviceContainer, configurationPersister);
            domainModelProxy.setDomainModel(domainModel);

            final AtomicInteger count = new AtomicInteger(1);
            final ResultHandler resultHandler = new ResultHandler() {
                @Override
                public void handleResultFragment(final String[] location, final ModelNode result) {
                }

                @Override
                public void handleResultComplete() {
                    if (count.decrementAndGet() == 0) {
                        // some action
                    }
                }

                @Override
                public void handleFailed(final ModelNode failureDescription) {
                    if (count.decrementAndGet() == 0) {
                        // some action
                    }
                }

                @Override
                public void handleCancellation() {
                    if (count.decrementAndGet() == 0) {
                        // some action
                    }
                }
            };

            for (final ModelNode operation : operations) {
                count.incrementAndGet();
                operation.get(OPERATION_HEADERS, ROLLBACK_ON_RUNTIME_FAILURE).set(false);
                domainModel.execute(OperationBuilder.Factory.create(operation).build(), resultHandler);
            }
            if (count.decrementAndGet() == 0) {
                // some action?
            }

            final ServerInventory serverInventory = this.inventoryValue.getValue();
            final ModelNode hostModelNode = domainModel.getHostModel();
            final String name = hostModelNode.get(ModelDescriptionConstants.NAME).asString();

            final HostControllerImpl controller = new HostControllerImpl(name, configurationPersister, hostRegistry, serverInventory);
            controller.registerInternalOperations();

            final ServerNodeRegistration nodeRegistration = new ServerNodeRegistration(name, serverInventory);
            ServerInventoryUtils.registerServerOperations(nodeRegistration, controller);
            hostRegistry.registerSubModel(PathElement.pathElement(SERVER), nodeRegistration);

            this.proxyController = new LocalHostModel() {
                @Override
                public void startServers(DomainController domainController) {
                    controller.startServers(domainController);
                }

                @Override
                public void stopServers() {
                    controller.stopServers();
                }

                @Override
                public String getName() {
                    return name;
                }

                @Override
                public ModelNode getHostModel() {
                    return hostModelNode;
                }

                @Override
                public ModelNodeRegistration getRegistry() {
                    return hostRegistry;
                }

                @Override
                public ExtensibleConfigurationPersister getConfigurationPersister() {
                    return configurationPersister;
                }
            };

        } catch (Exception e) {
            throw new StartException(e);
        }
        try {
            configurationPersister.successfulBoot();
        } catch (ConfigurationPersistenceException e) {
            throw new StartException(e);
        }
    }

    @Override
    public synchronized void stop(StopContext stopContext) {
        this.proxyController = null;
    }

    @Override
    public synchronized LocalHostModel getValue() throws IllegalStateException, IllegalArgumentException {
        final LocalHostModel hostModel = this.proxyController;
        if(hostModel == null) {
            throw new IllegalStateException();
        }
        return hostModel;
    }

        /**
     * Create the host.xml configuration persister.
     *
     * @param configDir the domain configuration directory
     * @param configurationFile the configuration file
     * @return the configuration persister
     */
    static ExtensibleConfigurationPersister createHostConfigurationPersister(final File configDir, final ConfigurationFile configurationFile) {
        return ConfigurationPersisterFactory.createHostXmlConfigurationPersister(configDir, configurationFile);
    }

    /**
     * A Proxy to allow access to a DomainModel which will be created later.
     */
    static final class DomainModelProxyImpl implements DomainModelProxy {

        private DomainModelImpl domainModel;

        public void setDomainModel(final DomainModelImpl domainModel) {
            this.domainModel = domainModel;
        }

        @Override
        public DomainModelImpl getDomainModel() {
            if (domainModel == null) {
                throw new IllegalStateException("DomainModel has not been set.");
            }

            return domainModel;
        }
    }

}
