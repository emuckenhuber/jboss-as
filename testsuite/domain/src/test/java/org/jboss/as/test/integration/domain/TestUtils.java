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

package org.jboss.as.test.integration.domain;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import org.jboss.as.patching.Patch;
import org.jboss.as.patching.PatchImpl;
import org.jboss.as.patching.PatchingException;
import org.jboss.as.version.Version;
import org.jboss.dmr.ModelNode;

import java.io.IOException;

/**
 * @author Emanuel Muckenhuber
 */
class TestUtils {

    private TestUtils() {}

    static ModelNode executeForResult(final ModelControllerClient client, final ModelNode operation) throws IOException {
        return executeForResult(client, OperationBuilder.create(operation).build());
    }

    static ModelNode executeForResult(final ModelControllerClient client, final Operation operation) throws IOException {
        try {
            final ModelNode result = client.execute(operation);
            if(SUCCESS.equals(result.get(OUTCOME).asString())) {
                return result.get(RESULT);
            } else {
                throw new RuntimeException("operation failed " + result);
            }
        } catch(IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    static Patch createPatch(final String name, final Patch.PatchType type, final String hash) {
        return createPatch(name, type, hexStringToByteArray(hash));
    }

    static Patch createPatch(final String name, final Patch.PatchType type, final byte[] hash) {

        final ModelNode model = new ModelNode();
        model.get(PatchImpl.PATCH_ID).set(name);
        model.get(PatchImpl.DESCRIPTION).set(name);
        model.get(PatchImpl.PATCH_TYPE).set(type.toString());
        model.get(PatchImpl.CONTENT_HASH).set(hash);
        model.get(PatchImpl.APPLIES_TO).add("7.1.0.Alpha2-SNAPSHOT");

        return PatchImpl.fromModelNode(model);
    }

    private static char[] table = {
            '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };

    /**
     * Convert a byte array into a hex string.
     *
     * @param bytes the bytes
     * @return the string
     */
    static String bytesToHexString(final byte[] bytes) {
        final StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(table[b >> 4 & 0x0f]).append(table[b & 0x0f]);
        }
        return builder.toString();
    }

    /**
     * Convert a hex string into a byte[].
     *
     * @param s the string
     * @return the bytes
     */
    static byte[] hexStringToByteArray(String s) {
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
