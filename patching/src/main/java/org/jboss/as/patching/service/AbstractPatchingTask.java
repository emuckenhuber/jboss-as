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

import org.jboss.as.patching.Patch;
import org.jboss.as.patching.PatchContentLoader;
import org.jboss.as.patching.PatchingException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Emanuel Muckenhuber
 */
public abstract class AbstractPatchingTask implements PatchingTask {

    private static MessageDigest digest;
    static {
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public PatchInfo execute(final Patch patch, final PatchingContext context) throws PatchingException {
        //
        final String patchId = patch.getPatchId();
        final PatchInfo info = context.getPatchInfo();

        // Check if we can apply this patch
        final List<String> appliesTo = patch.getAppliesTo();
        if(! appliesTo.contains(info.getVersion())) {
            throw new PatchingException("Patch does not apply - expected (%s), but was (%s)", appliesTo, info.getVersion());
        }
        // Setup the repository
        try {
            PatchInfoImpl.setup(info);
        } catch(final IOException e) {
            throw new PatchingException("failed to setup repository", e);
        }
        // process the content stream
        final File patchContent;
        try {
            patchContent = processPatchContent(patch, context.getContentLoader());
        } catch (IOException e) {
            throw new PatchingException("Failed to create copy patch content", e);
        }
        // Create the local overlay directory
        try {
            createLocalOverlay(patch, patchContent, info);
        } catch (final IOException e) {
            throw new PatchingException("Failed to create local overlay directory", e);
        } finally {
            patchContent.delete();
        }
        // Create the new info
        final PatchInfo newInfo;
        if(Patch.PatchType.ONE_OFF == patch.getPatchType()) {
            final List<String> patches = new ArrayList<String>(info.getPatchIDs());
            patches.add(0, patchId);
            newInfo = new PatchInfoImpl("undefined", info.getCumulativeID(), patches, info.getEnvironment());
        } else {
            newInfo = new PatchInfoImpl("undefined", patchId, info.getPatchIDs(), info.getEnvironment());
        }
        return newInfo;
    }

    /**
     * Create the local overlay directory
     *
     * @param patch the patch metadata
     * @param patchContent the patch content
     * @param patchInfo the local patch info
     * @throws IOException
     */
    static void createLocalOverlay(final Patch patch, final File patchContent, final PatchInfo patchInfo) throws IOException {
        final PatchEnvironment environment = patchInfo.getEnvironment();
        final String patchId = patch.getPatchId();
        final File patchDir = environment.getPatchDirectory(patchId);
        final File history = environment.getHistoryDir(patchId);
        if(patchDir.exists()) {
            if(patchId.equals(patchInfo.getCumulativeID()) || patchInfo.getPatchIDs().contains(patchId)) {
                // nothing to do
                return;
            }
            if(history.exists()) {
                PatchUtils.recursiveDelete(history);
            }
            // TODO check module hashes
        }
        PatchUtils.createDirIfNotExists(patchDir);
        // PatchUtils.createDirIfNotExists(history);

        // Unpack the patch contents
        unpack(patchContent, patchDir);

    }

    /**
     * Check the patch content hash and create a local temp file.
     *
     * @param patch the patch
     * @param loader the content loader
     * @return a temp file
     * @throws PatchingException
     * @throws IOException
     */
    static File processPatchContent(final Patch patch, final PatchContentLoader loader) throws PatchingException, IOException {
        final File tempFile = File.createTempFile("content", patch.getPatchId());
        tempFile.deleteOnExit();
        final InputStream is = loader.openContentStream();
        try {
            final OutputStream os = new FileOutputStream(tempFile);
            try {
                synchronized (digest) {
                    digest.reset();
                    final DigestOutputStream dos = new DigestOutputStream(os, digest);
                    PatchUtils.copyStreamAndClose(is, dos);
                    final byte[] hash = dos.getMessageDigest().digest();
                    if(! Arrays.equals(hash, patch.getContentHash())) {
                        throw new PatchingException("Content hash (%s) does not match expected (%s).", PatchUtils.bytesToHexString(hash), PatchUtils.bytesToHexString(patch.getContentHash()));
                    }
                }
            } finally {
                PatchUtils.safeClose(os);
            }
        } finally {
            PatchUtils.safeClose(is);
        }
        return tempFile;
    }

    /**
     * unpack...
     *
     * @param zip the zip
     * @param patchDir the patch dir
     * @throws IOException
     */
    static void unpack(final File zip, final File patchDir) throws IOException {
        final ZipFile zipFile = new ZipFile(zip);
        try {
            unpack(zipFile, patchDir);
        } finally {
            if(zip != null) try {
                zipFile.close();
            } catch (IOException ignore) {
                //
            }
        }
    }

    /**
     * unpack...
     *
     * @param zip the zip
     * @param patchDir the patch dir
     * @throws IOException
     */
    static void unpack(final ZipFile zip, final File patchDir) throws IOException {
        final Enumeration<? extends ZipEntry> entries = zip.entries();
        while(entries.hasMoreElements()) {
            final ZipEntry entry = entries.nextElement();
            final String name = entry.getName();
            final File current = new File(patchDir, name);
            if(entry.isDirectory()) {
                continue;
            } else {
                if(! current.getParentFile().exists()) {
                    current.getParentFile().mkdirs();
                }
                final InputStream eis = zip.getInputStream(entry);
                try {
                    final FileOutputStream eos = new FileOutputStream(current);
                    try {
                        PatchUtils.copyStream(eis, eos);
                        eis.close();
                        eos.close();
                    } finally {
                        PatchUtils.safeClose(eos);
                    }
                } finally {
                    PatchUtils.safeClose(eis);
                }
            }
        }
    }

    protected static void write(final PatchInfo info) throws IOException {
        final PatchEnvironment environment = info.getEnvironment();
        final String cumulativeID = info.getCumulativeID();
        PatchUtils.writeRef(environment.getCumulativeLink(), info.getCumulativeID());
        PatchUtils.writeRefs(environment.getCumulativeRefs(cumulativeID), info.getPatchIDs());
    }

}