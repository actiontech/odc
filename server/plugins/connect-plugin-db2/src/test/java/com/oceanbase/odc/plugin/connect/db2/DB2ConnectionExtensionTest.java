/*
 * Copyright (c) 2023 OceanBase.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.oceanbase.odc.plugin.connect.db2;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oceanbase.odc.core.shared.constant.OdcConstants;
import com.oceanbase.odc.plugin.connect.model.JdbcUrlProperty;

@RunWith(Parameterized.class)
public class DB2ConnectionExtensionTest {

    private final String testName;
    private final String host;
    private final Integer port;
    private final String catalogName;
    private final String expectedUrl;
    private final Class<? extends Exception> expectedException;

    public DB2ConnectionExtensionTest(String testName, String host, Integer port,
            String catalogName, String expectedUrl, Class<? extends Exception> expectedException) {
        this.testName = testName;
        this.host = host;
        this.port = port;
        this.catalogName = catalogName;
        this.expectedUrl = expectedUrl;
        this.expectedException = expectedException;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                {
                        "standard parameters generate correct URL",
                        "10.186.16.126", 50000, "testdb",
                        "jdbc:db2://10.186.16.126:50000/testdb:sslConnection=false", null
                },
                {
                        "missing catalogName throws validation exception",
                        "10.186.16.126", 50000, null,
                        null, NullPointerException.class
                },
                {
                        "empty catalogName throws validation exception",
                        "10.186.16.126", 50000, "",
                        null, IllegalArgumentException.class
                },
                {
                        "missing host throws validation exception",
                        null, 50000, "testdb",
                        null, NullPointerException.class
                }
        });
    }

    @Test
    public void testGenerateJdbcUrl() {
        DB2ConnectionExtension extension = new DB2ConnectionExtension();

        String effectiveHost = host != null ? host : "placeholder";
        Integer effectivePort = port != null ? port : 0;
        JdbcUrlProperty property = new JdbcUrlProperty(effectiveHost, effectivePort, null, null,
                null, null, catalogName);
        if (host == null) {
            property.setHost(null);
        }
        if (port == null) {
            property.setPort(null);
        }

        if (expectedException != null) {
            try {
                extension.generateJdbcUrl(property);
                Assert.fail("Expected exception: " + expectedException.getSimpleName());
            } catch (Exception e) {
                Assert.assertTrue("Expected " + expectedException.getSimpleName()
                        + " but got " + e.getClass().getSimpleName(),
                        expectedException.isInstance(e));
            }
            return;
        }

        String url = extension.generateJdbcUrl(property);
        Assert.assertEquals(expectedUrl, url);
    }

    @Test
    public void testGetDriverClassName() {
        DB2ConnectionExtension extension = new DB2ConnectionExtension();
        Assert.assertEquals(OdcConstants.DB2_DRIVER_CLASS_NAME, extension.getDriverClassName());
    }
}
