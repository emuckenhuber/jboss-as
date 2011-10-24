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

import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

import java.io.File;
import java.io.IOException;

/**
 * @author Emanuel Muckenhuber
 */
public final class PatchInfoService implements Service<PatchInfo> {

    public static ServiceName NAME = ServiceName.JBOSS.append("patch").append("info");

    private final String version;
    private final File jbossHome;
    private PatchInfo patchInfo;

    public PatchInfoService(final String version, final File jbossHome) {
        this.version = version;
        this.jbossHome = jbossHome;
    }

    @Override
    public synchronized void start(StartContext context) throws StartException {
        final PatchEnvironment environment = PatchEnvironment.createDefault(jbossHome);
        try {
            this.patchInfo = PatchInfoImpl.load(version, environment);
        } catch (IOException e) {
            throw new StartException(e);
        }
    }

    @Override
    public synchronized void stop(StopContext context) {
        this.patchInfo = null;
    }

    @Override
    public synchronized PatchInfo getValue() throws IllegalStateException, IllegalArgumentException {
        final PatchInfo patchInfo = this.patchInfo;
        if(patchInfo == null) {
            throw new IllegalStateException();
        }
        return patchInfo;
    }
}
