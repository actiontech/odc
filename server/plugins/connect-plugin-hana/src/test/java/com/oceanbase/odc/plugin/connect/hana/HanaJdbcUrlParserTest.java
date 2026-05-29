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
package com.oceanbase.odc.plugin.connect.hana;

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
 * Unit tests for {@link HanaJdbcUrlParser}.
 * <p>
 * Uses parameterized map-case style to cover standard URL, URL with parameters,
 * currentSchema handling, and userName-based schema fallback.
 * </p>
 *
 * @since ODC_release_4.3.4
 */
@RunWith(Parameterized.class)
public class HanaJdbcUrlParserTest {

    private final String description;
    private final String jdbcUrl;
    private final String userName;
    private final String expectedHost;
    private final int expectedPort;
    private final String expectedSchema;
    private final int expectedParamCount;

    public HanaJdbcUrlParserTest(String description, String jdbcUrl, String userName,
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

                {"standard URL without parameters, with userName",
                        "jdbc:sap://192.168.1.100:30015/", "testuser",
                        "192.168.1.100", 30015, "TESTUSER", 0},

                {"standard URL without trailing slash, with userName",
                        "jdbc:sap://192.168.1.100:30015", "admin",
                        "192.168.1.100", 30015, "ADMIN", 0},

                {"URL with databaseName parameter",
                        "jdbc:sap://10.0.0.1:30015/?databaseName=HDB", "sapuser",
                        "10.0.0.1", 30015, "SAPUSER", 1},

                {"URL with currentSchema parameter overrides userName",
                        "jdbc:sap://myhost:30015/?currentSchema=MY_SCHEMA", "otheruser",
                        "myhost", 30015, "MY_SCHEMA", 0},

                {"URL with multiple parameters",
                        "jdbc:sap://hana-server:30041/?encrypt=true&validateCertificate=false&reconnect=true",
                        "admin",
                        "hana-server", 30041, "ADMIN", 3},

                {"URL with no parameters and no userName falls back to default",
                        "jdbc:sap://localhost:30015/", null,
                        "localhost", 30015, OdcConstants.HANA_DEFAULT_SCHEMA, 0},

                {"URL with currentSchema and additional params",
                        "jdbc:sap://db.example.com:39015/?currentSchema=PROD_SCHEMA&encrypt=true",
                        null,
                        "db.example.com", 39015, "PROD_SCHEMA", 1},

                {"URL with non-default port",
                        "jdbc:sap://hana-node:39041/", "hdbadm",
                        "hana-node", 39041, "HDBADM", 0},
        });
    }

    @Test
    public void testParseUrl() throws SQLException {
        JdbcUrlParser parser = new HanaJdbcUrlParser(jdbcUrl, userName);

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
    public static class HanaJdbcUrlParserEdgeCaseTest {

        @Rule
        public ExpectedException thrown = ExpectedException.none();

        @Test
        public void createParser_invalidJdbcPrefix_expThrown() throws SQLException {
            String jdbcUrl = "jdbc:mysql://0.0.0.0:1234";
            thrown.expect(IllegalArgumentException.class);
            thrown.expectMessage("Invalid JDBC URL for SAP HANA");
            new HanaJdbcUrlParser(jdbcUrl, null);
        }

        @Test
        public void createParser_hanaPrefix_expThrown() throws SQLException {
            // "jdbc:hana://" is NOT the correct prefix; it must be "jdbc:sap://"
            String jdbcUrl = "jdbc:hana://host:30015/";
            thrown.expect(IllegalArgumentException.class);
            thrown.expectMessage("Invalid JDBC URL for SAP HANA");
            new HanaJdbcUrlParser(jdbcUrl, null);
        }

        @Test
        public void createParser_missingPort_expThrown() throws SQLException {
            String jdbcUrl = "jdbc:sap://hostname";
            thrown.expect(SQLException.class);
            thrown.expectMessage("Failed to parse");
            new HanaJdbcUrlParser(jdbcUrl, null);
        }

        @Test
        public void createParser_invalidPort_expThrown() throws SQLException {
            String jdbcUrl = "jdbc:sap://hostname:abc";
            thrown.expect(SQLException.class);
            thrown.expectMessage("Invalid port");
            new HanaJdbcUrlParser(jdbcUrl, null);
        }

        @Test
        public void getParameters_withCurrentSchema_schemaExcluded() throws SQLException {
            JdbcUrlParser parser = new HanaJdbcUrlParser(
                    "jdbc:sap://host:30015/?currentSchema=MYDB&encrypt=true", null);
            Map<String, Object> params = parser.getParameters();
            Assert.assertFalse("currentSchema should not be in parameters",
                    params.containsKey("currentSchema"));
            Assert.assertEquals("true", params.get("encrypt"));
        }

        @Test
        public void getSchema_noSchemaParam_noUserName_returnsDefault() throws SQLException {
            JdbcUrlParser parser = new HanaJdbcUrlParser("jdbc:sap://host:30015/", null);
            Assert.assertEquals(OdcConstants.HANA_DEFAULT_SCHEMA, parser.getSchema());
        }

        @Test
        public void getSchema_noSchemaParam_withUserName_returnsUpperCase() throws SQLException {
            JdbcUrlParser parser = new HanaJdbcUrlParser("jdbc:sap://host:30015/", "sapUser");
            Assert.assertEquals("SAPUSER", parser.getSchema());
        }

        @Test
        public void getHostAddresses_ipv4Address() throws SQLException {
            JdbcUrlParser parser = new HanaJdbcUrlParser("jdbc:sap://192.168.1.1:30015/", null);
            List<HostAddress> expected = new ArrayList<>();
            expected.add(new HostAddress("192.168.1.1", 30015));
            Assert.assertEquals(expected, parser.getHostAddresses());
        }

        @Test
        public void parseUrl_emptyQueryString() throws SQLException {
            // URL with '?' but no actual parameters
            JdbcUrlParser parser = new HanaJdbcUrlParser("jdbc:sap://host:30015/?", "user1");
            Assert.assertEquals(0, parser.getParameters().size());
            Assert.assertEquals("USER1", parser.getSchema());
        }
    }
}
