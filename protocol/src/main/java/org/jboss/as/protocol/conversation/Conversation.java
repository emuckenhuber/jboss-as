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

import org.jboss.remoting3.Attachable;
import org.jboss.remoting3.HandleableCloseable;

import java.io.IOException;

/**
 * @author Emanuel Muckenhuber
 */
public interface Conversation extends HandleableCloseable<Conversation>, Attachable {

    /**
     * Write a one-way message, not expecting a response.
     *
     * @return the message output stream
     */
    MessageDataOutput writeMessage();

    /**
     * Write a message.
     *
     * @param responseHandler the response handler
     * @return the message output
     */
    MessageDataOutput writeMessage(MessageHandler responseHandler);

    /**
     * Handle a conversation failure, this will terminate this conversation and end all active messages.
     *
     * @param failure the failure
     */
    void handleFailure(IOException failure);

    public interface MessageHandler {

        /**
         * Handle a message.
         *
         * @param message the message
         * @param conversation the active conversation
         * @throws IOException
         */
        void handleMessage(MessageDataInput message, Conversation conversation) throws IOException;

        /**
         * Handle a failure.
         *
         * @param failure the failure message
         * @param conversation the active conversation
         * @throws IOException
         */
        void handleFailed(IOException failure, Conversation conversation) throws IOException;

    }

}
