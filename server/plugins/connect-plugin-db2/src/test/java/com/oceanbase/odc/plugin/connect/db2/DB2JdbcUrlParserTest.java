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

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oceanbase.odc.plugin.connect.api.HostAddress;

@RunWith(Parameterized.class)
public class DB2JdbcUrlParserTest {

    private final String testName;
    private final String jdbcUrl;
    private final String userName;
    private final String expectedHost;
    private final Integer expectedPort;
    private final String expectedSchema;
    private final Map<String, Object> expectedParams;
    private final Class<? extends Exception> expectedException;

    public DB2JdbcUrlParserTest(String testName, String jdbcUrl, String userName,
            String expectedHost, Integer expectedPort, String expectedSchema,
            Map<String, Object> expectedParams, Class<? extends Exception> expectedException) {
        this.testName = testName;
        this.jdbcUrl = jdbcUrl;
        this.userName = userName;
        this.expectedHost = expectedHost;
        this.expectedPort = expectedPort;
        this.expectedSchema = expectedSchema;
        this.expectedParams = expectedParams;
        this.expectedException = expectedException;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                {
                        "standard URL parses correctly",
                        "jdbc:db2://10.186.16.126:50000/testdb",
                        "db2admin",
                        "10.186.16.126", 50000, "DB2ADMIN",
                        java.util.Collections.emptyMap(), null
                },
                {
                        "URL with parameters parses correctly",
                        "jdbc:db2://localhost:50000/mydb:currentSchema=MYSCHEMA;sslConnection=false;",
                        "user1",
                        "localhost", 50000, "USER1",
                        new java.util.HashMap<String, Object>() {
                            {
                                put("currentSchema", "MYSCHEMA");
                                put("sslConnection", "false");
                            }
                        }, null
                },
                {
                        "null userName results in null schema",
                        "jdbc:db2://host1:50000/db1",
                        null,
                        "host1", 50000, null,
                        java.util.Collections.emptyMap(), null
                },
                {
                        "invalid URL prefix throws IllegalArgumentException",
                        "jdbc:mysql://localhost:3306/test",
                        "user",
                        null, null, null, null,
                        IllegalArgumentException.class
                },
                {
                        "missing port throws SQLException",
                        "jdbc:db2://localhost/testdb",
                        "user",
                        null, null, null, null,
                        SQLException.class
                }
        });
    }

    @Test
    public void testParse() throws Exception {
        if (expectedException != null) {
            try {
                new DB2JdbcUrlParser(jdbcUrl, userName);
                Assert.fail("Expected exception: " + expectedException.getSimpleName());
            } catch (Exception e) {
                Assert.assertTrue("Expected " + expectedException.getSimpleName() + " but got "
                        + e.getClass().getSimpleName(),
                        expectedException.isInstance(e));
            }
            return;
        }

        DB2JdbcUrlParser parser = new DB2JdbcUrlParser(jdbcUrl, userName);

        List<HostAddress> addresses = parser.getHostAddresses();
        Assert.assertEquals(1, addresses.size());
        Assert.assertEquals(expectedHost, addresses.get(0).getHost());
        Assert.assertEquals(expectedPort.intValue(), (int) addresses.get(0).getPort());
        Assert.assertEquals(expectedSchema, parser.getSchema());

        if (expectedParams != null) {
            Map<String, Object> actualParams = parser.getParameters();
            for (Map.Entry<String, Object> entry : expectedParams.entrySet()) {
                Assert.assertEquals("Parameter " + entry.getKey(),
                        entry.getValue(), actualParams.get(entry.getKey()));
            }
        }
    }
}
