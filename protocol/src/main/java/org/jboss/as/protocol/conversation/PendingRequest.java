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

package org.jboss.as.protocol.conversation;

import java.io.IOException;

/**
 * @author Emanuel Muckenhuber
 */
class PendingRequest {

    private final int requestID;
    private final ConversationImpl conversation;
    private final Conversation.MessageHandler responseHandler;

    public PendingRequest(final int requestID, final ConversationImpl conversation, final Conversation.MessageHandler responseHandler) {
        this.requestID = requestID;
        this.conversation = conversation;
        this.responseHandler = responseHandler;
    }

    protected void handleResponse(final MessageDataInput message) {
        try {
            responseHandler.handleMessage(message, conversation);
        } catch (IOException e) {
            handleFailed(e);
        }
    }

    protected void handleFailed(final IOException failure) {
        try {
            responseHandler.handleFailed(failure, conversation);
        } catch (IOException e) {
            conversation.handleFailure(e);
        } catch (Exception e) {
            conversation.handleFailure(new IOException(e));
        }
    }

}
