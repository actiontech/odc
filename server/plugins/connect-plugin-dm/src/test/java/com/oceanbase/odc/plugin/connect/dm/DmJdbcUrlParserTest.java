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
package com.oceanbase.odc.plugin.connect.dm;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oceanbase.odc.core.shared.constant.OdcConstants;
import com.oceanbase.odc.plugin.connect.api.HostAddress;
import com.oceanbase.odc.plugin.connect.api.JdbcUrlParser;

/**
 * Unit tests for {@link DmJdbcUrlParser}.
 * <p>
 * Uses parameterized map-case style to cover various URL formats.
 * </p>
 *
 * @author
 * @since ODC_release_4.3.4
 */
@RunWith(Parameterized.class)
public class DmJdbcUrlParserTest {

    private final String description;
    private final String jdbcUrl;
    private final String userName;
    private final String expectedHost;
    private final int expectedPort;
    private final String expectedSchema;
    private final int expectedParamCount;

    public DmJdbcUrlParserTest(String description, String jdbcUrl, String userName,
            String expectedHost, int expectedPort, String expectedSchema, int expectedParamCount) {
        this.description = description;
        this.jdbcUrl = jdbcUrl;
        this.userName = userName;
        this.expectedHost = expectedHost;
        this.expectedPort = expectedPort;
        this.expectedSchema = expectedSchema;
        this.expectedParamCount = expectedParamCount;
    }

    @Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                // description, jdbcUrl, userName, expectedHost, expectedPort, expectedSchema, expectedParamCount
                {"basic URL without parameters",
                        "jdbc:dm://192.168.1.100:5236", null,
                        "192.168.1.100", 5236, OdcConstants.DM_DEFAULT_SCHEMA, 0},
                {"URL with schema parameter",
                        "jdbc:dm://10.0.0.1:5236?schema=TESTDB", null,
                        "10.0.0.1", 5236, "TESTDB", 0},
                {"URL with multiple parameters",
                        "jdbc:dm://myhost:5236?schema=MYSCHEMA&loginTimeout=30&socketTimeout=60", null,
                        "myhost", 5236, "MYSCHEMA", 2},
                {"URL with only non-schema parameters",
                        "jdbc:dm://localhost:5236?loginTimeout=30&socketTimeout=60", null,
                        "localhost", 5236, OdcConstants.DM_DEFAULT_SCHEMA, 2},
                {"URL with default port",
                        "jdbc:dm://dm-server:15236", "admin",
                        "dm-server", 15236, OdcConstants.DM_DEFAULT_SCHEMA, 0},
        });
    }

    @Test
    public void testParseUrl() throws SQLException {
        JdbcUrlParser parser = new DmJdbcUrlParser(jdbcUrl, userName);

        List<HostAddress> addresses = parser.getHostAddresses();
        Assert.assertEquals(1, addresses.size());
        Assert.assertEquals(expectedHost, addresses.get(0).getHost());
        Assert.assertEquals(Integer.valueOf(expectedPort), addresses.get(0).getPort());
        Assert.assertEquals(expectedSchema, parser.getSchema());
        Assert.assertEquals(expectedParamCount, parser.getParameters().size());
    }

    /**
     * Non-parameterized tests for edge cases and error handling.
     */
    public static class DmJdbcUrlParserEdgeCaseTest {

        @Rule
        public ExpectedException thrown = ExpectedException.none();

        @Test
        public void createParser_invalidJdbcUrl_expThrown() throws SQLException {
            String jdbcUrl = "jdbc:mysql://0.0.0.0:1234";
            thrown.expect(IllegalArgumentException.class);
            thrown.expectMessage("Invalid JDBC URL for DM: " + jdbcUrl);
            new DmJdbcUrlParser(jdbcUrl, null);
        }

        @Test
        public void createParser_missingPort_expThrown() throws SQLException {
            String jdbcUrl = "jdbc:dm://hostname";
            thrown.expect(SQLException.class);
            thrown.expectMessage("Failed to parse");
            new DmJdbcUrlParser(jdbcUrl, null);
        }

        @Test
        public void getParameters_withSchemaParam_schemaExcluded() throws SQLException {
            JdbcUrlParser parser = new DmJdbcUrlParser(
                    "jdbc:dm://host:5236?schema=MYDB&timeout=30", null);
            Map<String, Object> params = parser.getParameters();
            Assert.assertFalse("schema should not be in parameters", params.containsKey("schema"));
            Assert.assertEquals("30", params.get("timeout"));
        }

        @Test
        public void getSchema_noSchemaParam_returnsDefault() throws SQLException {
            JdbcUrlParser parser = new DmJdbcUrlParser("jdbc:dm://host:5236", null);
            Assert.assertEquals(OdcConstants.DM_DEFAULT_SCHEMA, parser.getSchema());
        }

        @Test
        public void getHostAddresses_ipv4Address() throws SQLException {
            JdbcUrlParser parser = new DmJdbcUrlParser("jdbc:dm://192.168.1.1:5236", null);
            List<HostAddress> expected = new ArrayList<>();
            expected.add(new HostAddress("192.168.1.1", 5236));
            Assert.assertEquals(expected, parser.getHostAddresses());
        }
    }
}
