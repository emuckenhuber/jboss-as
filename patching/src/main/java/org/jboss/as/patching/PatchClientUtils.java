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

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import org.jboss.as.patching.service.PatchEnvironment;
import org.jboss.as.patching.service.PatchInfo;
import org.jboss.dmr.ModelNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Some client utils.
 *
 * @author Emanuel Muckenhuber
 */
class PatchClientUtils {

    private static final ModelNode LAUNCH_TYPE_OP = new ModelNode();
    private static final ModelNode PROCESS_TYPE_OP = new ModelNode();

    private static final String MDC = "Domain Controller";

    static {

        // The launch-type operation
        LAUNCH_TYPE_OP.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION);
        LAUNCH_TYPE_OP.get(ModelDescriptionConstants.OP_ADDR).setEmptyList();
        LAUNCH_TYPE_OP.get(ModelDescriptionConstants.NAME).set("launch-type");
        LAUNCH_TYPE_OP.protect();

        // The process-type operation
        PROCESS_TYPE_OP.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION);
        PROCESS_TYPE_OP.get(ModelDescriptionConstants.OP_ADDR).setEmptyList();
        PROCESS_TYPE_OP.get(ModelDescriptionConstants.NAME).set(ModelDescriptionConstants.PROCESS_TYPE);
        PROCESS_TYPE_OP.protect();

    }

    /**
     * Determine whether the controller client is connected to a standalone instance or managed domain.
     *
     * @param client the model controller client
     * @return true or false
     * @throws IOException
     */
    public static boolean isStandalone(final ModelControllerClient client) throws PatchingException {
        return "STANDALONE".equals(executeForResult(client, LAUNCH_TYPE_OP).asString());
    }

    /**
     * Determine whether the controller client is connected to a managed domain.
     *
     * @param client the model controller client
     * @return true or false
     * @throws PatchingException
     */
    public static boolean isDomain(final ModelControllerClient client) throws PatchingException {
        return "DOMAIN".equals(executeForResult(client, LAUNCH_TYPE_OP).asString());
    }

    /**
     * Determine whether the controller is connected to the Master Domain Controller.
     *
     * @param client the model controller client
     * @return true or false
     * @throws PatchingException
     */
    public static boolean isMDC(final ModelControllerClient client) throws PatchingException {
        return MDC.equals(executeForResult(client, PROCESS_TYPE_OP));
    }

    /**
     * Determine whether a patch applies to a given info.
     *
     * @param info the patch info
     * @param patch the patch
     * @return true if the patch can be applied, false otherwise
     */
    public static boolean patchAppliesToInfo(final PatchInfo info, final Patch patch) {
        if(patch.getAppliesTo().contains(info.getVersion())) {
            final String patchId = patch.getPatchId();
            // TODO version checks?
            if(patch.getPatchType() == Patch.PatchType.CUMULATIVE) {
                if(! info.getCumulativeID().equals(patchId)) {
                    return true;
                }
            } else {
                if(! info.getPatchIDs().contains(patchId)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Validate the patch info.
     *
     * @param patchInfo the patch info
     * @param patch the patch
     * @throws PatchingException
     */
    static void validatePatchInfo(final ModelNode patchInfo, final Patch patch) throws PatchingException {
        final String patchId = patch.getPatchId();
        if(patch.getPatchType() == Patch.PatchType.CUMULATIVE) {
            final String cp = patchInfo.get("cumulative").asString();
            if(! patchId.equals(cp)) {
                throw new PatchingException("expected patch '%s', but was '%s'", patchId, cp);
            }
        }  else {
            final List<ModelNode> patches = patchInfo.get("patches").asList();
            if(! patches.contains(new ModelNode().set(patchId))) {
                throw new PatchingException("expected patches to contain '%s', but was '%s'", patchId, patches);
            }
        }
    }

    static ModelNode executeForResult(final ModelControllerClient client, final ModelNode operation) throws PatchingException {
        return executeForResult(client, OperationBuilder.create(operation).build());
    }

    static ModelNode executeForResult(final ModelControllerClient client, final Operation operation) throws PatchingException {
        try {
            final ModelNode result = client.execute(operation);
            if(SUCCESS.equals(result.get(OUTCOME).asString())) {
                return result.get(RESULT);
            } else {
                throw new PatchingException("operation failed " + result);
            }
        } catch(IOException ioe) {
            throw new PatchingException(ioe);
        }
    }

    static List<String> asSimpleList(final ModelNode model) {
        final List<String> list = new ArrayList<String>();
        for(final ModelNode node : model.asList()) {
            list.add(node.asString());
        }
        return list;
    }

    static class ModelPatchInfoImpl implements PatchInfo {
        // The underlying model
        private final ModelNode model;
        ModelPatchInfoImpl(ModelNode model) {
            this.model = model.clone();
            this.model.protect();
        }

        @Override
        public String getVersion() {
            return model.get("version").asString();
        }

        @Override
        public String getCumulativeID() {
            return model.get("cumulative").asString();
        }

        @Override
        public List<String> getPatchIDs() {
            return asSimpleList(model.get("patches"));
        }

        @Override
        public PatchEnvironment getEnvironment() {
            throw new IllegalStateException();
        }

        @Override
        public String getModulePath() {
            throw new IllegalStateException();
        }
    }

}
