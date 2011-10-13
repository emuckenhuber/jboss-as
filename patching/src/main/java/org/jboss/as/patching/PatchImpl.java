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

import org.jboss.dmr.ModelNode;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Emanuel Muckenhuber
 */
public class PatchImpl implements Patch {

    private final String patchId;
    private final String description;
    private final PatchType type;
    private final byte[] hash;
    private final List<String> appliesTo;

    PatchImpl(final String patchId, final String description, final PatchType type,
              final byte[] hash, final List<String> appliesTo) {
        this.patchId = patchId;
        this.description = description;
        this.type = type;
        this.hash = hash;
        this.appliesTo = appliesTo;
    }

    @Override
    public String getPatchId() {
        return patchId;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public PatchType getPatchType() {
        return type;
    }

    @Override
    public byte[] getContentHash() {
        return hash;
    }

    @Override
    public List<String> getAppliesTo() {
        return appliesTo;
    }

    public static final String PATCH_ID = "patch-id";
    public static final String DESCRIPTION = "description";
    public static final String PATCH_TYPE = "patch-type";
    public static final String CONTENT_HASH = "content-hash";
    public static final String APPLIES_TO = "applies-to";

    public static ModelNode toModelNode(final Patch patch) {
        final ModelNode model = new ModelNode();
        model.get(PATCH_ID).set(patch.getPatchId());
        model.get(DESCRIPTION).set(patch.getDescription());
        model.get(PATCH_TYPE).set(patch.getPatchType().toString());
        model.get(CONTENT_HASH).set(patch.getContentHash());
        for(final String version : patch.getAppliesTo()) {
            model.get(APPLIES_TO).add(version);
        }
        return model;
    }

    public static Patch fromModelNode(final ModelNode model) {
        final String patchId = model.require(PATCH_ID).asString();
        final String description = model.require(DESCRIPTION).asString();
        final PatchType type = PatchType.valueOf(model.require(PATCH_TYPE).asString().toUpperCase());
        final byte[] hash = model.require(CONTENT_HASH).asBytes();
        final List<String> appliesTo = new ArrayList<String>();
        for(final ModelNode version : model.require(APPLIES_TO).asList()) {
            appliesTo.add(version.asString());
        }
        return new PatchImpl(patchId, description, type, hash, appliesTo);
    }

}
