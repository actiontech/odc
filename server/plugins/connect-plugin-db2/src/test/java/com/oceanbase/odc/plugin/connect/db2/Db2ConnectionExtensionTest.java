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

import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.oceanbase.odc.core.shared.constant.ErrorCodes;
import com.oceanbase.odc.plugin.connect.api.TestResult;
import com.oceanbase.odc.plugin.connect.model.JdbcUrlProperty;

public class Db2ConnectionExtensionTest {

    private final Db2ConnectionExtension extension = new Db2ConnectionExtension();

    @Test
    public void getDriverClassName_returnsIBMDriver() {
        Assert.assertEquals("com.ibm.db2.jcc.DB2Driver", extension.getDriverClassName());
    }

    @Test
    public void getConnectionInitializers_returnsEmpty() {
        Assert.assertTrue(extension.getConnectionInitializers().isEmpty());
    }

    @Test
    public void generateJdbcUrl_minimal_returnsHostPortCatalog() {
        JdbcUrlProperty props = new JdbcUrlProperty("10.186.16.126", 50000, null, null, null, null, "testdb");
        String url = extension.generateJdbcUrl(props);
        Assert.assertEquals(
                "jdbc:db2://10.186.16.126:50000/testdb:retrieveMessagesFromServerOnGetMessage=true;",
                url);
    }

    @Test
    public void generateJdbcUrl_withDefaultSchema_appendsCurrentSchema() {
        JdbcUrlProperty props = new JdbcUrlProperty("h", 50000, "DB2INST1", null, null, null, "TESTDB");
        String url = extension.generateJdbcUrl(props);
        Assert.assertTrue("must include currentSchema=DB2INST1, got " + url,
                url.contains("currentSchema=DB2INST1"));
        Assert.assertTrue("must include retrieveMessagesFromServerOnGetMessage=true, got " + url,
                url.contains("retrieveMessagesFromServerOnGetMessage=true"));
        Assert.assertTrue("must start with prefix, got " + url, url.startsWith("jdbc:db2://h:50000/TESTDB:"));
    }

    @Test
    public void generateJdbcUrl_withExtraParams_paramsPreserved() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("sslConnection", "true");
        params.put("sslTrustStoreLocation", "/etc/db2/trust.jks");
        JdbcUrlProperty props = new JdbcUrlProperty("h", 50000, null, params, null, null, "TESTDB");
        String url = extension.generateJdbcUrl(props);
        Assert.assertTrue(url.contains("sslConnection=true"));
        Assert.assertTrue(url.contains("sslTrustStoreLocation=/etc/db2/trust.jks"));
        Assert.assertTrue(url.contains("retrieveMessagesFromServerOnGetMessage=true"));
    }

    /**
     * 用户已经显式传 currentSchema 时，不被 defaultSchema 覆盖。
     */
    @Test
    public void generateJdbcUrl_userParamCurrentSchema_notOverwritten() {
        Map<String, String> params = new HashMap<>();
        params.put("currentSchema", "FROM_PARAMS");
        JdbcUrlProperty props = new JdbcUrlProperty("h", 50000, "SHOULD_NOT_WIN", params, null, null, "TESTDB");
        String url = extension.generateJdbcUrl(props);
        Assert.assertTrue(url.contains("currentSchema=FROM_PARAMS"));
        Assert.assertFalse(url.contains("currentSchema=SHOULD_NOT_WIN"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void generateJdbcUrl_emptyHost_throwIllegalArgument() {
        JdbcUrlProperty props = new JdbcUrlProperty("dummy", 50000, null, null, null, null, "testdb");
        props.setHost("");
        extension.generateJdbcUrl(props);
    }

    @Test(expected = IllegalArgumentException.class)
    public void generateJdbcUrl_emptyCatalog_throwIllegalArgument() {
        JdbcUrlProperty props = new JdbcUrlProperty("h", 50000, null, null, null, null, "");
        extension.generateJdbcUrl(props);
    }

    @Test
    public void appendDefaultJdbcUrlParameters_emptyInput_returnsMapWithRetrieveMessagesFlag() {
        Map<String, String> result = invokeAppend(new HashMap<>());
        Assert.assertEquals("true", result.get("retrieveMessagesFromServerOnGetMessage"));
    }

    @Test
    public void appendDefaultJdbcUrlParameters_nullInput_returnsNonNullMap() {
        Map<String, String> result = invokeAppend(null);
        Assert.assertNotNull(result);
        Assert.assertEquals("true", result.get("retrieveMessagesFromServerOnGetMessage"));
    }

    @Test
    public void appendDefaultJdbcUrlParameters_existingRetrieveMessages_preserved() {
        Map<String, String> input = new HashMap<>();
        input.put("retrieveMessagesFromServerOnGetMessage", "false");
        Map<String, String> result = invokeAppend(input);
        Assert.assertEquals("false", result.get("retrieveMessagesFromServerOnGetMessage"));
    }

    /**
     * 反射调用 protected 方法（OBMySQLConnectionExtension 同包 protected）。
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> invokeAppend(Map<String, String> input) {
        try {
            java.lang.reflect.Method m = Db2ConnectionExtension.class
                    .getDeclaredMethod("appendDefaultJdbcUrlParameters", Map.class);
            m.setAccessible(true);
            return (Map<String, String>) m.invoke(extension, input);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    // -------------------------------------------------------------------------------------------
    // T-003 commit-3: real connectivity test() coverage via a stubbed openConnection.
    // -------------------------------------------------------------------------------------------
    //
    // The IBM JCC driver jar is provided=true so it is NOT on the test classpath. We test
    // Db2ConnectionExtension.test() with a subclass that overrides openConnection() to return a
    // mocked Connection or throw a canned SQLException. This avoids touching java.sql.DriverManager
    // entirely (which can be polluted by other drivers in the maven test classpath such as
    // MariaDB / oceanbase-client; observed real-network DNS lookups when going through DriverManager).

    @Test
    public void test_happyPath_executesSelect1FromSysDummy1_returnsSuccess() throws SQLException {
        Connection conn = Mockito.mock(Connection.class);
        Statement stmt = Mockito.mock(Statement.class);
        Mockito.when(conn.createStatement()).thenReturn(stmt);
        Mockito.when(stmt.execute(Mockito.anyString())).thenReturn(true);

        StubbingExtension ext = new StubbingExtension();
        ext.connectionToReturn = conn;
        TestResult result = ext.test("jdbc:db2://h:50000/testdb", new Properties(), 1, null);

        Assert.assertTrue("expected active=true happy-path, got " + result, result.isActive());
        Mockito.verify(stmt).execute("SELECT 1 FROM SYSIBM.SYSDUMMY1");
    }

    @Test
    public void test_authenticationFailure_classifiedAsAccessDenied() {
        StubbingExtension ext = new StubbingExtension();
        // IBM JCC reports auth failure with sqlstate=28000 / message containing "authorization"
        ext.exceptionToThrow = new SQLException(
                "Connection authorization failure: invalid credentials for user 'db2inst1'",
                "28000", -4214);
        TestResult result = ext.test("jdbc:db2://h:50000/testdb", new Properties(), 1, null);

        Assert.assertFalse(result.isActive());
        Assert.assertEquals(ErrorCodes.ObAccessDenied, result.getErrorCode());
    }

    @Test
    public void test_networkTimeout_classifiedAsHostUnreachable() {
        SQLException ex = new SQLException("Connection timed out: connect");
        ex.initCause(new java.net.SocketTimeoutException("connect timed out"));
        StubbingExtension ext = new StubbingExtension();
        ext.exceptionToThrow = ex;
        TestResult result = ext.test("jdbc:db2://10.0.0.99:50000/testdb", new Properties(), 1, null);

        Assert.assertFalse(result.isActive());
        Assert.assertEquals(ErrorCodes.ConnectionHostUnreachable, result.getErrorCode());
    }

    @Test
    public void test_unknownHost_classifiedAsUnknownHost() {
        SQLException ex = new SQLException("Communication link failure: name or service not known");
        ex.initCause(new UnknownHostException("no.such.host"));
        StubbingExtension ext = new StubbingExtension();
        ext.exceptionToThrow = ex;
        TestResult result = ext.test("jdbc:db2://no.such.host:50000/testdb", new Properties(), 1, null);

        Assert.assertFalse(result.isActive());
        Assert.assertEquals(ErrorCodes.ConnectionUnknownHost, result.getErrorCode());
    }

    @Test
    public void test_missingJccDriver_failsFastWithReadableError() {
        Db2ConnectionExtension ext = new Db2ConnectionExtension() {
            @Override
            protected boolean isDriverClassAvailable() {
                return false;
            }
        };
        TestResult result = ext.test("jdbc:db2://h:50000/testdb", new Properties(), 1, null);
        Assert.assertFalse(result.isActive());
        Assert.assertEquals(ErrorCodes.Unknown, result.getErrorCode());
        Assert.assertTrue("error message must mention IBM JCC / db2jcc4.jar to be operator-friendly",
                String.join(" ", result.getArgs()).contains("db2jcc4.jar")
                        || String.join(" ", result.getArgs()).contains("IBM JCC"));
    }

    @Test
    public void executeTestSqls_usesDb2DialectSelectFromSysIbmSysDummy1() throws SQLException {
        Statement stmt = Mockito.mock(Statement.class);
        StubbingExtension ext = new StubbingExtension();
        java.lang.reflect.Method m;
        try {
            m = Db2ConnectionExtension.class.getDeclaredMethod("executeTestSqls", Statement.class);
            m.setAccessible(true);
            m.invoke(ext, stmt);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        Mockito.verify(stmt).execute("SELECT 1 FROM SYSIBM.SYSDUMMY1");
    }

    @Test
    public void test_initScriptFailure_classifiedAsInitScriptFailed() throws SQLException {
        Connection conn = Mockito.mock(Connection.class);
        Statement stmt = Mockito.mock(Statement.class);
        Mockito.when(conn.createStatement()).thenReturn(stmt);
        StubbingExtension ext = new StubbingExtension();
        ext.connectionToReturn = conn;

        com.oceanbase.odc.core.datasource.ConnectionInitializer failing =
                (c) -> {
                    throw new SQLException("init script broken");
                };
        TestResult result = ext.test("jdbc:db2://h:50000/testdb", new Properties(), 1,
                java.util.Collections.singletonList(failing));

        Assert.assertFalse(result.isActive());
        Assert.assertEquals(ErrorCodes.ConnectionInitScriptFailed, result.getErrorCode());
    }

    /**
     * Test extension that overrides {@link #openConnection} so unit tests don't depend on the global
     * {@link java.sql.DriverManager} / a real driver / a real network.
     */
    private static class StubbingExtension extends Db2ConnectionExtension {
        Connection connectionToReturn;
        SQLException exceptionToThrow;

        @Override
        protected boolean isDriverClassAvailable() {
            return true;
        }

        @Override
        protected Connection openConnection(String jdbcUrl, Properties properties)
                throws SQLException {
            if (exceptionToThrow != null) {
                throw exceptionToThrow;
            }
            if (connectionToReturn == null) {
                throw new SQLException("StubbingExtension: no canned connection / throw");
            }
            return connectionToReturn;
        }
    }
}
