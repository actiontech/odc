/*
 * Copyright (c) 2024 OceanBase.
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

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oceanbase.odc.plugin.connect.api.HostAddress;

/**
 * Parameterized tests for {@link HiveJdbcUrlParser}.
 * Covers design.md section 5.2.7: standard URL, no parameters, no database, multiple parameters.
 *
 * @since ODC_release_4.3.4
 */
@RunWith(Parameterized.class)
public class HiveJdbcUrlParserTest {

    private final String testName;
    private final String jdbcUrl;
    private final String expectedHost;
    private final int expectedPort;
    private final String expectedDatabase;
    private final int expectedParamCount;

    public HiveJdbcUrlParserTest(String testName, String jdbcUrl,
            String expectedHost, int expectedPort, String expectedDatabase,
            int expectedParamCount) {
        this.testName = testName;
        this.jdbcUrl = jdbcUrl;
        this.expectedHost = expectedHost;
        this.expectedPort = expectedPort;
        this.expectedDatabase = expectedDatabase;
        this.expectedParamCount = expectedParamCount;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> testCases() {
        return Arrays.asList(new Object[][] {
                {
                        "standard URL with auth param",
                        "jdbc:hive2://host:10000/mydb;auth=noSasl",
                        "host", 10000, "mydb", 1
                },
                {
                        "URL without parameters",
                        "jdbc:hive2://host:10000/mydb",
                        "host", 10000, "mydb", 0
                },
                {
                        "URL without database (empty path)",
                        "jdbc:hive2://host:10000/",
                        "host", 10000, "", 0
                },
                {
                        "URL with multiple parameters",
                        "jdbc:hive2://host:10000/mydb;auth=noSasl;transportMode=binary",
                        "host", 10000, "mydb", 2
                }
        });
    }

    @Test
    public void testParse() throws SQLException {
        HiveJdbcUrlParser parser = new HiveJdbcUrlParser(jdbcUrl, null);

        HostAddress address = parser.getHostAddresses().get(0);
        Assert.assertEquals(testName + " - host mismatch", expectedHost, address.getHost());
        Assert.assertEquals(testName + " - port mismatch", expectedPort, address.getPort().intValue());
        Assert.assertEquals(testName + " - database mismatch", expectedDatabase, parser.getSchema());
        Assert.assertEquals(testName + " - param count mismatch",
                expectedParamCount, parser.getParameters().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidPrefix() throws SQLException {
        new HiveJdbcUrlParser("jdbc:mysql://host:3306/db", null);
    }
}
