/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

package org.jboss.as.patching.management;

import static java.util.Arrays.asList;
import static org.jboss.as.patching.management.PatchManagementLogger.ROOT_LOGGER;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.as.patching.DirectoryStructure;
import org.jboss.as.patching.LocalPatchInfo;
import org.jboss.as.patching.PatchInfo;
import org.jboss.as.version.ProductConfig;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

/**
 * @author Emanuel Muckenhuber
 */
public class PatchInfoService implements Service<PatchInfoService> {

    public static ServiceName NAME = ServiceName.JBOSS.append("patch").append("info");

    private final InjectedValue<ProductConfig> productConfig = new InjectedValue<ProductConfig>();

    private volatile DirectoryStructure directoryStructure;
    private volatile PatchInfo patchInfo;

    /**
     * This field is set to true when a patch is applied/rolled back at runtime.
     * It prevents another patch to be applied and overrides the modifications brought by the previous one
     * unless the process is restarted first
     *
     * This field has to be {@code static} in order to survive server reloads.
     */
    private static final AtomicBoolean restartRequired = new AtomicBoolean(false);

    protected PatchInfoService() {
        //
    }

    @Override
    public synchronized void start(StartContext context) throws StartException {
        final ProductConfig config = productConfig.getValue();
        final File file = new File(System.getProperty("jboss.home.dir"));
        final DirectoryStructure structure = DirectoryStructure.createDefault(file);
        try {
            this.patchInfo = LocalPatchInfo.load(config, structure);
            ROOT_LOGGER.usingModulePath(asList(patchInfo.getModulePath()));
        } catch (IOException e) {
            throw new StartException(e);
        }
    }

    @Override
    public synchronized void stop(StopContext context) {
        this.patchInfo = null;
    }

    @Override
    public synchronized PatchInfoService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    InjectedValue<ProductConfig> getProductConfig() {
        return productConfig;
    }

    public DirectoryStructure getStructure() {
        return directoryStructure;
    }

    public PatchInfo getPatchInfo() {
        return patchInfo;
    }

    public boolean requiresRestart() {
        return restartRequired.get();
    }

    /**
     * Require a restart. This will set the patching service to read-only
     * and the server has to be restarted in order to execute the next
     * patch operation.
     *
     * In case the patch operation does not succeed it needs to clear the
     * reload required state using {@link #clearRestartRequired()}.
     *
     * @return this will return {@code true}
     */
    protected boolean restartRequired() {
        return restartRequired.compareAndSet(false, true);
    }

    protected void clearRestartRequired() {
        restartRequired.set(false);
    }

}
