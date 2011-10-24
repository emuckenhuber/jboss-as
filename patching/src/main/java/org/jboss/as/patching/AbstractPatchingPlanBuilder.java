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

import org.jboss.as.patching.domain.DomainPatchingPlan;
import org.jboss.as.patching.domain.DomainPatchingPlanBuilder;
import org.jboss.as.patching.domain.HostPatchingPlanBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Emanuel Muckenhuber
 */
class AbstractPatchingPlanBuilder implements DomainPatchingPlanBuilder {

    private final BasicPatchingPlan plan;

    protected AbstractPatchingPlanBuilder(BasicPatchingPlan plan) {
        this.plan = plan;
    }

    @Override
    public HostPatchingPlanBuilder addHost(String hostName) {
        final HostEntry entry = new HostEntry(hostName);
        plan.hosts.add(entry);
        return new HostPatchingPlanBuilderImpl(entry,  plan);
    }

    @Override
    public DomainPatchingPlan build() {
        if(plan.hosts.isEmpty()) {
            throw new IllegalStateException("empty plan");
        }
        return plan;
    }

    static class BasicPatchingPlan implements DomainPatchingPlan {

        private final Patch patch;
        private final DomainPatchingClientImpl client;
        private final PatchContentLoader loader;
        private final List<HostEntry> hosts = new ArrayList<HostEntry>();

        protected BasicPatchingPlan(final Patch patch, final PatchContentLoader loader, DomainPatchingClientImpl client) {
            this.patch = patch;
            this.client = client;
            this.loader = loader;
        }

        @Override
        public List<String> getHosts() {
            final List<String> hostNames = new ArrayList<String>();
            for(final HostEntry entry : hosts) {
                hostNames.add(entry.name);
            }
            return hostNames;
        }

        @Override
        public Patch getPatch() {
            return patch;
        }

        @Override
        public PatchContentLoader getContentLoader() {
            return loader;
        }

        @Override
        public void execute() throws PatchingException {
            client.execute(this);
        }
    }

    static class HostEntry {

        final String name;
        long gracefulTimeout;

        HostEntry(String name) {
            this.name = name;
        }

        void setGracefulTimeout(final long timeout) {
            gracefulTimeout = timeout;
        }

    }

    static class HostPatchingPlanBuilderImpl extends AbstractPatchingPlanBuilder implements HostPatchingPlanBuilder {
        private final HostEntry entry;
        HostPatchingPlanBuilderImpl(HostEntry entry, BasicPatchingPlan plan) {
            super(plan);
            this.entry = entry;
        }

        @Override
        public HostPatchingPlanBuilder withGracefulTimeout(int timeout, TimeUnit timeUnit) {
            entry.setGracefulTimeout(timeUnit.toMillis(timeout));
            return this;
        }
    }

    static class InitialPlanBuilder extends AbstractPatchingPlanBuilder {

        InitialPlanBuilder(final Patch patch, final PatchContentLoader loader, DomainPatchingClientImpl client) {
            super(new BasicPatchingPlan(patch, loader, client));
        }

    }

}
