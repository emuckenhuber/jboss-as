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

import static org.jboss.as.test.integration.domain.TestUtils.executeForResult;

import junit.framework.Assert;
import org.jboss.as.arquillian.container.domain.managed.DomainLifecycleUtil;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

/**
 * Core domain infrastructure and topology operations.
 *
 * @author Emanuel Muckenhuber
 */
public class DomainInfrastructureTestCase {

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainMasterLifecycleUtil;
    private static DomainLifecycleUtil domainSlaveLifecycleUtil;

    private static final ModelNode LAUNCH_TYPE_OP = new ModelNode();
    private static final ModelNode PROCESS_TYPE_OP = new ModelNode();

    static {
        // The launch type operation
        LAUNCH_TYPE_OP.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION);
        LAUNCH_TYPE_OP.get(ModelDescriptionConstants.OP_ADDR).setEmptyList();
        LAUNCH_TYPE_OP.get(ModelDescriptionConstants.NAME).set("launch-type");
        LAUNCH_TYPE_OP.protect();


        PROCESS_TYPE_OP.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION);
        PROCESS_TYPE_OP.get(ModelDescriptionConstants.OP_ADDR).setEmptyList();
        PROCESS_TYPE_OP.get(ModelDescriptionConstants.NAME).set(ModelDescriptionConstants.PROCESS_TYPE);
        PROCESS_TYPE_OP.protect();
    }

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = new DomainTestSupport(DomainInfrastructureTestCase.class.getSimpleName(), "domain-configs/domain-standard.xml", "host-configs/host-master.xml", "host-configs/host-slave.xml");
        testSupport.start();
        domainMasterLifecycleUtil = testSupport.getDomainMasterLifecycleUtil();
        domainSlaveLifecycleUtil = testSupport.getDomainSlaveLifecycleUtil();
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport.stop();
    }

    @Test
    public void testLaunchType() throws IOException {
        final ModelNode master = executeForResult(domainMasterLifecycleUtil.getDomainClient(), LAUNCH_TYPE_OP);
        Assert.assertEquals("DOMAIN", master.asString());

        final ModelNode slave = executeForResult(domainSlaveLifecycleUtil.getDomainClient(), LAUNCH_TYPE_OP);
        Assert.assertEquals("DOMAIN", slave.asString());
    }

    @Test
    public void testProcessType() throws Exception {
        // Hmm, some enum would have been more interesting...
        final ModelNode master = executeForResult(domainMasterLifecycleUtil.getDomainClient(), PROCESS_TYPE_OP);
        Assert.assertEquals("Domain Controller", master.asString());

        final ModelNode slave = executeForResult(domainSlaveLifecycleUtil.getDomainClient(), PROCESS_TYPE_OP);
        Assert.assertEquals("Host Controller", slave.asString());
    }

}
