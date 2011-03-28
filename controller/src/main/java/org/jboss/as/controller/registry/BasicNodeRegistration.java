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

package org.jboss.as.controller.registry;

import java.util.Collections;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.jboss.as.controller.OperationHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProxyController;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.registry.AttributeAccess.Storage;
import org.jboss.as.controller.registry.OperationEntry.EntryType;
import org.jboss.dmr.ModelNode;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTRIBUTES;

/**
 * @author Emanuel Muckenhuber
 */
public abstract class BasicNodeRegistration extends AbstractNodeRegistration {


    private volatile Map<String, OperationEntry> operations;
    private volatile Map<String, AttributeAccess> attributes;

    private static final AtomicMapFieldUpdater<BasicNodeRegistration, String, OperationEntry> operationsUpdater = AtomicMapFieldUpdater.newMapUpdater(AtomicReferenceFieldUpdater.newUpdater(BasicNodeRegistration.class, Map.class, "operations"));
    private static final AtomicMapFieldUpdater<BasicNodeRegistration, String, AttributeAccess> attributesUpdater = AtomicMapFieldUpdater.newMapUpdater(AtomicReferenceFieldUpdater.newUpdater(BasicNodeRegistration.class, Map.class, "attributes"));

    protected BasicNodeRegistration(String valueString, NodeSubregistry parent) {
        super(valueString, parent);
        operationsUpdater.clear(this);
        attributesUpdater.clear(this);
    }

    @Override
    protected OperationHandler getHandler(final ListIterator<PathElement> iterator, final String operationName) {
        final OperationEntry entry = operationsUpdater.get(this, operationName);
        if (entry != null && entry.isInherited()) {
            return entry.getOperationHandler();
        }
        return entry == null ? null : entry.getOperationHandler();
    }

    /** {@inheritDoc} */
    @Override
    public void registerOperationHandler(String operationName, OperationHandler handler, DescriptionProvider descriptionProvider, boolean inherited, EntryType entryType) {
        if (operationsUpdater.putIfAbsent(this, operationName, new OperationEntry(handler, descriptionProvider, inherited, entryType)) != null) {
            throw new IllegalArgumentException("A handler named '" + operationName + "' is already registered at location '" + getLocationString() + "'");
        }
    }

    /** {@inheritDoc} */
    @Override
    public void registerSubModel(PathElement address, ModelNodeRegistration subModel) {
        throw new IllegalStateException();
    }

    /** {@inheritDoc} */
    @Override
    public void registerReadWriteAttribute(final String attributeName, final OperationHandler readHandler, final OperationHandler writeHandler, AttributeAccess.Storage storage) {
        if (attributesUpdater.putIfAbsent(this, attributeName, new AttributeAccess(AttributeAccess.AccessType.READ_WRITE, storage, readHandler, writeHandler)) != null) {
            throw new IllegalArgumentException("An attribute named '" + attributeName + "' is already registered at location '" + getLocationString() + "'");
        }
    }

    /** {@inheritDoc} */
    @Override
    public void registerReadOnlyAttribute(final String attributeName, final OperationHandler readHandler, AttributeAccess.Storage storage) {
        if (attributesUpdater.putIfAbsent(this, attributeName, new AttributeAccess(AttributeAccess.AccessType.READ_ONLY, storage, readHandler, null)) != null) {
            throw new IllegalArgumentException("An attribute named '" + attributeName + "' is already registered at location '" + getLocationString() + "'");
        }
    }

    /** {@inheritDoc} */
    @Override
    public void registerMetric(String attributeName, OperationHandler metricHandler) {
        if (attributesUpdater.putIfAbsent(this, attributeName, new AttributeAccess(AttributeAccess.AccessType.METRIC, AttributeAccess.Storage.RUNTIME, metricHandler, null)) != null) {
            throw new IllegalArgumentException("An attribute named '" + attributeName + "' is already registered at location '" + getLocationString() + "'");
        }
    }

    /** {@inheritDoc} */
    @Override
    public ModelNodeRegistration registerSubModel(PathElement address, DescriptionProvider descriptionProvider) {
        throw new IllegalStateException();
    }

    /** {@inheritDoc} */
    @Override
    public void registerProxyController(PathElement address, ProxyController controller) throws IllegalArgumentException {
        throw new IllegalStateException();
    }

    /** {@inheritDoc} */
    @Override
    public void unregisterProxyController(PathElement address) {
        throw new IllegalStateException();
    }

    /** {@inheritDoc} */
    @Override
    protected AttributeAccess getAttributeAccess(ListIterator<PathElement> address, String attributeName) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    protected void getOperationDescriptions(ListIterator<PathElement> iterator, Map<String, OperationEntry> providers, boolean inherited) {
        //
    }

    /** {@inheritDoc} */
    @Override
    protected DescriptionProvider getOperationDescription(Iterator<PathElement> iterator, String operationName) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    protected DescriptionProvider getModelDescription(Iterator<PathElement> iterator) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    protected Set<String> getAttributeNames(Iterator<PathElement> iterator) {
        if (iterator.hasNext()) {
                return Collections.emptySet();
        } else {
            final Map<String, AttributeAccess> snapshot = attributesUpdater.get(this);
            return snapshot.keySet();
        }
    }

    /** {@inheritDoc} */
    @Override
    protected Set<String> getChildNames(Iterator<PathElement> iterator) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    protected Set<PathElement> getChildAddresses(Iterator<PathElement> iterator) {

        return null;
    }

    @Override
    protected abstract ProxyController getProxyController(Iterator<PathElement> iterator);

    @Override
    protected abstract void getProxyControllers(Iterator<PathElement> iterator, Set<ProxyController> controllers);

    @Override
    protected abstract void resolveAddress(PathAddress address, PathAddress base, Set<PathAddress> addresses);

    /** {@inheritDoc} */
    @Override
    protected ModelNodeRegistration getNodeRegistration(Iterator<PathElement> iterator) {
        if(iterator.hasNext()) {
            throw new IllegalStateException();
        }
        return this;
    }

}

