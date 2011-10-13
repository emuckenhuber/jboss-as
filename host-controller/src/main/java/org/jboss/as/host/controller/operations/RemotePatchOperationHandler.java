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

package org.jboss.as.host.controller.operations;

import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProxyController;
import org.jboss.as.controller.client.MessageSeverity;
import org.jboss.as.controller.client.OperationAttachments;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.domain.controller.LocalHostControllerInfo;
import org.jboss.as.domain.controller.operations.coordination.PrepareStepHandler;
import org.jboss.as.host.controller.HostControllerRegistration;
import org.jboss.dmr.ModelNode;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Emanuel Muckenhuber
 */
public class RemotePatchOperationHandler implements OperationStepHandler {

    public static final String OPERATION_NAME = "patch";

    private final HostControllerRegistration registration;
    private final LocalHostControllerInfo localHostControllerInfo;
    private final ExecutorService executorService;

    public RemotePatchOperationHandler(final HostControllerRegistration registration, final LocalHostControllerInfo localHostControllerInfo,
                                       final ExecutorService executorService) {
        this.registration = registration;
        this.executorService = executorService;
        this.localHostControllerInfo = localHostControllerInfo;
    }

    @Override
    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {

        final String hostName = operation.require("name").asString();

        final PathAddress host = PathAddress.pathAddress(PathElement.pathElement("host", hostName));
        if(hostName.equals(localHostControllerInfo.getLocalHostName())) {
            throw new OperationFailedException(new ModelNode().set("cannot remote patch local host"));
        }
        final ProxyController proxy = context.getResourceRegistration().getProxyController(PathAddress.pathAddress(host));
        if(proxy == null) {
            throw new OperationFailedException(new ModelNode().set("no such host"));
        }

        // Execute remote HC
        final ModelNode patch = operation.clone();
        patch.get(ModelDescriptionConstants.OP).set(LocalPatchOperationHandler.OPERATION_NAME);
        patch.get(ModelDescriptionConstants.OP_ADDR).set(host.toModelNode());

        final ProxyTask task = new ProxyTask(hostName, patch, context, proxy);
        task.finalizeTransaction(true);
        executorService.submit(task);

        final RemoteHostRestartVerificationHandler step = new RemoteHostRestartVerificationHandler(hostName);
        registration.registerCallback(step);
        try {
            context.getResult().set(" 1234 ");
            context.addStep(step, OperationContext.Stage.VERIFY);
            context.completeStep();
        } finally {
            registration.unregisterCallback(step);
        }
        context.completeStep();
    }

    private static class RemoteHostRestartVerificationHandler implements OperationStepHandler, HostControllerRegistration.Callback {

        private final String hostName;
        private final CountDownLatch registered = new CountDownLatch(1);
        private final CountDownLatch unregistered = new CountDownLatch(1);
        private RemoteHostRestartVerificationHandler(String hostName) {
            this.hostName = hostName;
        }

        @Override
        public void registered(final String name, final ProxyController proxy) {
            if(hostName.equals(name)) {
                registered.countDown();
            }
        }

        @Override
        public void unregistered(String name) {
            if(hostName.equals(name)) {
                unregistered.countDown();
            }
        }

        @Override
        public void removed() {
            if(unregistered.getCount() == 1) {
                System.out.printf("%s not unregistered", hostName);
            }
            if(registered.getCount() == 1) {
                System.out.printf("%s not registered", hostName);
            }
        }

        @Override
        public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            try {
                if(! unregistered.await(30, TimeUnit.SECONDS)) {
                    System.out.println("not unregistered");
                }
                if(! registered.await(5, TimeUnit.MINUTES)) {
                    throw new OperationFailedException(new ModelNode().set(String.format("Host failed to start %s ....", hostName)));
                }
            } catch(InterruptedException e) {
                context.getFailureDescription().set("Interrupted "  + e.getMessage());
                e.printStackTrace();
            }
            context.completeStep();
        }
    }

    class ProxyTask implements Callable<ModelNode> {

        private final ProxyController proxyController;
        private final String host;
        private final ModelNode operation;
        private final OperationContext context;

        private final AtomicReference<Boolean> transactionAction = new AtomicReference<Boolean>();
        private final AtomicReference<ModelNode> uncommittedResultRef = new AtomicReference<ModelNode>();
        private boolean cancelRemoteTransaction;

        public ProxyTask(String host, ModelNode operation, OperationContext context, ProxyController proxyController) {
            this.host = host;
            this.operation = operation;
            this.context = context;
            this.proxyController = proxyController;
        }

        @Override
        public ModelNode call() throws Exception {

            boolean trace = PrepareStepHandler.isTraceEnabled();
            if (trace) {
                // PrepareStepHandler.log.trace("Sending " + operation + " to " + host);
            }
            OperationMessageHandler messageHandler = new DelegatingMessageHandler(context);

            final AtomicReference<ModelController.OperationTransaction> txRef = new AtomicReference<ModelController.OperationTransaction>();
            final AtomicReference<ModelNode> preparedResultRef = new AtomicReference<ModelNode>();
            final AtomicReference<ModelNode> finalResultRef = new AtomicReference<ModelNode>();
            final ProxyController.ProxyOperationControl proxyControl = new ProxyController.ProxyOperationControl() {

                @Override
                public void operationPrepared(ModelController.OperationTransaction transaction, ModelNode result) {
                    txRef.set(transaction);
                    preparedResultRef.set(result);
                }

                @Override
                public void operationFailed(ModelNode response) {
                    finalResultRef.set(response);
                }

                @Override
                public void operationCompleted(ModelNode response) {
                    finalResultRef.set(response);
                }
            };

            proxyController.execute(operation, messageHandler, proxyControl, new DelegatingOperationAttachments(context));

            ModelController.OperationTransaction remoteTransaction = null;
            ModelNode result = finalResultRef.get();
            if (result != null) {
                // operation failed before it could commit
                if (trace) {
                    // PrepareStepHandler.log.trace("Received final result " + result + " from " + host);
                }
            } else {
                result = preparedResultRef.get();
                if (trace) {
                    // PrepareStepHandler.log.trace("Received prepared result " + result + " from " + host);
                }
                remoteTransaction = txRef.get();
            }

            synchronized (uncommittedResultRef) {
                uncommittedResultRef.set(result);
                uncommittedResultRef.notifyAll();
            }

            if (remoteTransaction != null) {
                if (cancelRemoteTransaction) {
                    // Controlling thread was cancelled
                    remoteTransaction.rollback();
                } else {
                    synchronized (transactionAction) {
                        while (transactionAction.get() == null) {
                            try {
                                transactionAction.wait();
                            }
                            catch (InterruptedException ie) {
                                // Treat as cancellation
                                transactionAction.set(Boolean.FALSE);
                            }
                        }
                        if (transactionAction.get().booleanValue()) {
                            remoteTransaction.commit();
                        } else {
                            remoteTransaction.rollback();
                        }
                    }
                }
            }

            return finalResultRef.get();
        }

        ModelNode getUncommittedResult() throws InterruptedException {
            synchronized (uncommittedResultRef) {

                while (uncommittedResultRef.get() == null) {
                    try {
                        uncommittedResultRef.wait();
                    }
                    catch (InterruptedException ie) {
                        cancelRemoteTransaction = true;
                        throw ie;
                    }
                }
                return uncommittedResultRef.get();
            }
        }

        void finalizeTransaction(boolean commit) {
            synchronized (transactionAction) {
                transactionAction.set(Boolean.valueOf(commit));
                transactionAction.notifyAll();
            }
        }

        void cancel() {
            synchronized (uncommittedResultRef) {
                cancelRemoteTransaction = true;
            }
        }
    }

    private static class DelegatingMessageHandler implements OperationMessageHandler {

        private final OperationContext context;

        DelegatingMessageHandler(final OperationContext context) {
            this.context = context;
        }

        @Override
        public void handleReport(MessageSeverity severity, String message) {
            context.report(severity, message);
        }
    }

    private static class DelegatingOperationAttachments implements OperationAttachments {

        private final OperationContext context;
        private DelegatingOperationAttachments(final OperationContext context) {
            this.context = context;
        }

        @Override
        public List<InputStream> getInputStreams() {
            int count = context.getAttachmentStreamCount();
            List<InputStream> result = new ArrayList<InputStream>(count);
            for (int i = 0; i < count; i++) {
                result.add(context.getAttachmentStream(i));
            }
            return result;
        }
    }

}
