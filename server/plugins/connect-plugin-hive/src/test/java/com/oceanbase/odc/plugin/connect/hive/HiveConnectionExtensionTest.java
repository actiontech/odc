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
package com.oceanbase.odc.plugin.connect.hive;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oceanbase.odc.plugin.connect.model.JdbcUrlProperty;

/**
 * Parameterized tests for {@link HiveConnectionExtension#generateJdbcUrl(JdbcUrlProperty)}. Covers
 * design.md section 5.2.8: basic connection, no schema, custom database.
 *
 * @since ODC_release_4.3.4
 */
@RunWith(Parameterized.class)
public class HiveConnectionExtensionTest {

    private final String testName;
    private final String host;
    private final int port;
    private final String schema;
    private final String expectedUrl;

    public HiveConnectionExtensionTest(String testName, String host, int port,
            String schema, String expectedUrl) {
        this.testName = testName;
        this.host = host;
        this.port = port;
        this.schema = schema;
        this.expectedUrl = expectedUrl;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> testCases() {
        return Arrays.asList(new Object[][] {
                {
                        "basic connection with default schema",
                        "h", 10000, "default",
                        "jdbc:hive2://h:10000/default;auth=noSasl"
                },
                {
                        "connection without schema",
                        "h", 10000, null,
                        "jdbc:hive2://h:10000/;auth=noSasl"
                },
                {
                        "connection with custom database",
                        "h", 10000, "mydb",
                        "jdbc:hive2://h:10000/mydb;auth=noSasl"
                }
        });
    }

    @Test
    public void testGenerateJdbcUrl() {
        HiveConnectionExtension extension = new HiveConnectionExtension();

        // No extra JDBC parameters -- the extension should append default auth=noSasl
        JdbcUrlProperty properties = new JdbcUrlProperty(host, port, schema, new HashMap<>());

        String actualUrl = extension.generateJdbcUrl(properties);
        Assert.assertEquals(testName, expectedUrl, actualUrl);
    }

    @Test
    public void testDriverClassName() {
        HiveConnectionExtension extension = new HiveConnectionExtension();
        Assert.assertEquals("org.apache.hive.jdbc.HiveDriver", extension.getDriverClassName());
    }
}
