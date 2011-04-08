package org.jboss.as.host.controller;

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
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.persistence.ExtensibleConfigurationPersister;
import org.jboss.as.controller.persistence.NullConfigurationPersister;
import org.jboss.as.controller.registry.BasicNodeRegistration;
import org.jboss.as.server.ServerState;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CancellationException;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.*;

/**
 * {@code ModelNodeRegistration}
 *
 * @author Emanuel Muckenhuber
 */
class ServerNodeRegistration extends BasicNodeRegistration {
    private static final Logger log = Logger.getLogger("org.jboss.as.host.controller");
    /** The server inventory. */
    private final ServerInventory inventory;
    /** The address to this host. */
    private final PathAddress hostAddr;
    private final DelegateController controller = new DelegateController();

    public ServerNodeRegistration(final String name, final ServerInventory inventory) {
        super(ModelDescriptionConstants.SERVER, null);
        this.inventory = inventory;
        this.hostAddr = PathAddress.pathAddress(PathElement.pathElement(HOST, name));
    }

    protected Set<String> getChildNames() {
        return inventory.getChildNames();
    }

    protected ManagedServer getServer(final String processName) {
        return inventory.getServer(processName);
    }

    /** {@inheritDoc} */
    @Override
    protected Set<String> getChildNames(Iterator<PathElement> iterator) {
        return getChildNames();
    }

    /** {@inheritDoc} */
    @Override
    protected Set<PathElement> getChildAddresses(Iterator<PathElement> iterator) {
        final Set<String> names = getChildNames();
        final HashSet<PathElement> addresses = new HashSet<PathElement>();
        for(final String name : names) {
            addresses.add(PathElement.pathElement(SERVER, name));
        }
        return addresses;
    }

    /** {@inheritDoc} */
    @Override
    protected ProxyController getProxyController(Iterator<PathElement> iterator) {
        return controller;
    }

    /** {@inheritDoc} */
    @Override
    protected void getProxyControllers(Iterator<PathElement> iterator, Set<ProxyController> controllers) {
        if(iterator.hasNext()) {
            final PathElement path = iterator.next();
            final ManagedServer server = getServer(ManagedServer.getServerProcessName(path.getValue()));
            if(server == null) {
                return;
            }
            final PathAddress address = hostAddr.append(PathElement.pathElement(SERVER, server.getServerName()));
            controllers.add(new ProxyController() {
                @Override
                public ModelNode execute(Operation operation) throws CancellationException {
                    return server.execute(operation);
                }
                @Override
                public OperationResult execute(Operation operation, ResultHandler handler) {
                    return server.execute(operation, handler);
                }
                @Override
                public PathAddress getProxyNodeAddress() {
                    return address;
                }
            });
        } else {
            final Collection<ManagedServer> servers = inventory.getManagedServers();
            for(final ManagedServer server : servers) {
                if (server.getState() == ServerState.STARTED) {
                    final PathAddress address = hostAddr.append(PathElement.pathElement(SERVER, server.getServerName()));
                    controllers.add(new ProxyController() {
                        @Override
                        public ModelNode execute(Operation operation) throws CancellationException {
                            return server.execute(operation);
                        }
                        @Override
                        public OperationResult execute(Operation operation, ResultHandler handler) {
                            return server.execute(operation, handler);
                        }
                        @Override
                        public PathAddress getProxyNodeAddress() {
                            return address;
                        }
                    });
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void resolveAddress(PathAddress address, PathAddress base, Set<PathAddress> addresses) {
        final Set<String> names = getChildNames();
        for(final String name : names) {
            addresses.add(base.append(PathElement.pathElement(SERVER, name)));
        }
    }

    static final ExtensibleConfigurationPersister persister = new NullConfigurationPersister();
    class DelegateController extends BasicModelController implements ProxyController {

        protected DelegateController() {
            super(new ModelNode(), persister, ServerNodeRegistration.this);
        }

        /** {@inheritDoc} */
        @Override
        public PathAddress getProxyNodeAddress() {
            return hostAddr; // just keep server=*
        }

        /** {@inheritDoc} */
        @Override
        public OperationResult execute(Operation operation, ResultHandler handler) {

            final ModelNode operationNode = operation.getOperation();
            final PathAddress address = PathAddress.pathAddress(operationNode.require(OP_ADDR));
            if(address.size() == 0) {
                throw new IllegalStateException();
            }
            String operationName = operationNode.get(OP).asString();
            final OperationHandler localHandler = getOperationHandler(PathAddress.EMPTY_ADDRESS, operationName);
            if(localHandler != null) {
                final OperationContext context = getOperationContext(new ModelNode(), localHandler, operation, getModelProvider());
                try {
                    return localHandler.execute(context, operationNode, handler);
                } catch(OperationFailedException e) {
                    log.debugf(e, "operation (%s) failed - address: (%s)", operation.getOperation().get(OP), operation.getOperation().get(OP_ADDR));
                    handler.handleFailed(e.getFailureDescription());
                    return new BasicOperationResult();
                }
            }
            // In case there is no local handler run it on the actual server
            final PathElement path = address.getElement(0);
            ManagedServer managedServer = getServer(ManagedServer.getServerProcessName(path.getValue()));
            if(managedServer == null) {
                handler.handleFailed(new ModelNode().set("no such server" + path.getValue()));
                return new BasicOperationResult();
            }
            final ModelNode newOperation = operationNode.clone();
            newOperation.get(OP_ADDR).set(PathAddress.EMPTY_ADDRESS.toModelNode());
            return managedServer.execute(operation.clone(newOperation), handler);
        }

    }

}
