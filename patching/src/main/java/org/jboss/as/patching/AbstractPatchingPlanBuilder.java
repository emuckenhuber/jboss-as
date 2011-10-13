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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Emanuel Muckenhuber
 */
class AbstractPatchingPlanBuilder implements PatchingPlanBuilder {

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
    public PatchingPlan build() {
        if(plan.hosts.isEmpty()) {
            throw new IllegalStateException("empty plan");
        }
        return plan;
    }

    static class BasicPatchingPlan implements PatchingPlan {

        private final Patch patch;
        private final PatchingClientImpl client;
        private final PatchContentLoader loader;
        private final List<HostEntry> hosts = new ArrayList<HostEntry>();

        protected BasicPatchingPlan(final Patch patch, final PatchContentLoader loader, PatchingClientImpl client) {
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

        InitialPlanBuilder(final Patch patch, final PatchContentLoader loader, PatchingClientImpl client) {
            super(new BasicPatchingPlan(patch, loader, client));
        }

    }

}
