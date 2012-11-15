/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author Emanuel Muckenhuber
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 * @author <a href="http://jmesnil/net/">Jeff Mesnil</a> (c) 2012 Red Hat Inc
 */
public class HashUtils {

    private static final char[] TABLE = "0123456789abcdef".toCharArray();

    private static final MessageDigest DIGEST;
    static {
        try {
            DIGEST = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] hashFile(File file) throws IOException {
        synchronized (DIGEST) {
            DIGEST.reset();
            updateDigest(DIGEST, file);
            return DIGEST.digest();
        }
    }

    private static void updateDigest(MessageDigest digest, File file) throws IOException {
        if (file.isDirectory()) {
            File[] childList = file.listFiles();
            if (childList != null) {
                Map<String, File> sortedChildren = new TreeMap<String, File>();
                for (File child : childList) {
                    sortedChildren.put(child.getName(), child);
                }
                for (File child : sortedChildren.values()) {
                    updateDigest(digest, child);
                }
            }
        } else {
            FileInputStream fis = new FileInputStream(file);
            try {

                BufferedInputStream bis = new BufferedInputStream(fis);
                byte[] bytes = new byte[8192];
                int read;
                while ((read = bis.read(bytes)) > -1) {
                    digest.update(bytes, 0, read);
                }
            } finally {
                IoUtils.safeClose(fis);
            }

        }
    }

    public static byte[] copyAndGetHash(final InputStream is, final OutputStream os) throws IOException {
        byte[] sha1Bytes;
        synchronized (DIGEST) {
            DIGEST.reset();
            BufferedInputStream bis = new BufferedInputStream(is);
            DigestOutputStream dos = new DigestOutputStream(os, DIGEST);
            IoUtils.copyStream(bis, dos);
            sha1Bytes = DIGEST.digest();
        }
        return sha1Bytes;
    }

    /**
     * Convert a byte array into a hex string.
     *
     * @param bytes the bytes
     * @return the string
     */
    public static String bytesToHexString(final byte[] bytes) {
        final StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(TABLE[b >> 4 & 0x0f]).append(TABLE[b & 0x0f]);
        }
        return builder.toString();
    }

    /**
     * Convert a hex string into a byte[].
     *
     * @param s the string
     * @return the bytes
     */
    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len >> 1];
        for (int i = 0, j = 0; j < len; i++) {
            int x = Character.digit(s.charAt(j), 16) << 4;
            j++;
            x = x | Character.digit(s.charAt(j), 16);
            j++;
            data[i] = (byte) (x & 0xFF);
        }
        return data;
    }
}
