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

import org.jboss.as.protocol.StreamUtils;
import org.jboss.remoting3.Attachments;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * @author Emanuel Muckenhuber
 */
abstract class ConversationImpl extends AbstractHandleableCloseable<Conversation> implements Conversation {

    private final Attachments attachments = new Attachments();
    private volatile MessageHandler next;

    // Conversation state
    private volatile int state;
    private static final AtomicIntegerFieldUpdater<ConversationImpl> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(ConversationImpl.class, "state");

    protected ConversationImpl(final Executor executor) {
        super(executor, true);
    }

    /**
     * Get the associated channel with this conversation.
     *
     * @return the channel.
     */
    abstract Channel getChannel();

    @Override
    public Attachments getAttachments() {
        return attachments;
    }

    @Override
    public MessageDataOutput writeMessage() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public MessageDataOutput writeMessage(MessageHandler responseHandler) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void handleFailure(IOException e) {
        e.printStackTrace();
        StreamUtils.safeClose(this);
    }

    @Override
    protected void closeAction() throws IOException {
        // TODO something useful
        closeComplete();
    }

}
