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

/**
 * @author <a href="http://jmesnil/net/">Jeff Mesnil</a> (c) 2012 Red Hat Inc
 */
public class HashUtils {

    private static final char[] TABLE = "0123456789abcdef".toCharArray();

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
