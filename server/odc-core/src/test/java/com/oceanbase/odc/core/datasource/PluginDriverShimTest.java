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
package com.oceanbase.odc.core.datasource;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

import org.junit.Assert;
import org.junit.Test;

/**
 * Pinning tests for {@link PluginDriverShim}. The shim's only contract is "every method delegates
 * to the underlying driver and the advertised delegate class name is honored". A regression here
 * means {@code DriverManager.getConnection(...)} silently fails, succeeds against the wrong driver,
 * or {@link BaseClassBasedDataSource} cannot match the shim by class name during sync.
 */
public class PluginDriverShimTest {

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullDelegate_rejected() {
        new PluginDriverShim(null, "org.example.Driver");
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullClassName_rejected() {
        new PluginDriverShim(new StubDriver(), null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_emptyClassName_rejected() {
        new PluginDriverShim(new StubDriver(), "");
    }

    @Test
    public void getDelegateClassName_returnsConstructorArg() {
        Assert.assertEquals("org.apache.hive.jdbc.HiveDriver",
                new PluginDriverShim(new StubDriver(), "org.apache.hive.jdbc.HiveDriver").getDelegateClassName());
    }

    @Test
    public void connect_delegatesToUnderlyingDriver() throws SQLException {
        StubDriver delegate = new StubDriver();
        Connection canned = new MarkerConnection();
        delegate.cannedConnection = canned;
        Properties props = new Properties();
        props.setProperty("user", "hive");
        String url = "jdbc:hive2://10.186.63.138:40100/default";

        PluginDriverShim shim = new PluginDriverShim(delegate, "org.apache.hive.jdbc.HiveDriver");
        Connection actual = shim.connect(url, props);

        Assert.assertSame(canned, actual);
        Assert.assertEquals(url, delegate.lastConnectUrl);
        Assert.assertSame(props, delegate.lastConnectProps);
    }

    @Test
    public void acceptsURL_delegatesToUnderlyingDriver() throws SQLException {
        StubDriver delegate = new StubDriver();
        delegate.cannedAcceptsURL = true;
        PluginDriverShim shim = new PluginDriverShim(delegate, "org.apache.hive.jdbc.HiveDriver");
        Assert.assertTrue(shim.acceptsURL("jdbc:hive2://h:1"));
        Assert.assertEquals("jdbc:hive2://h:1", delegate.lastAcceptsURL);
    }

    @Test
    public void getPropertyInfo_delegatesToUnderlyingDriver() throws SQLException {
        StubDriver delegate = new StubDriver();
        delegate.cannedPropertyInfo = new DriverPropertyInfo[] {new DriverPropertyInfo("user", "")};
        PluginDriverShim shim = new PluginDriverShim(delegate, "org.example.Driver");
        Properties props = new Properties();
        DriverPropertyInfo[] actual = shim.getPropertyInfo("jdbc:url", props);
        Assert.assertSame(delegate.cannedPropertyInfo, actual);
        Assert.assertEquals("jdbc:url", delegate.lastGetPropertyInfoUrl);
        Assert.assertSame(props, delegate.lastGetPropertyInfoProps);
    }

    @Test
    public void getMajorVersion_delegatesToUnderlyingDriver() {
        StubDriver delegate = new StubDriver();
        delegate.cannedMajor = 4;
        Assert.assertEquals(4, new PluginDriverShim(delegate, "org.example.Driver").getMajorVersion());
    }

    @Test
    public void getMinorVersion_delegatesToUnderlyingDriver() {
        StubDriver delegate = new StubDriver();
        delegate.cannedMinor = 7;
        Assert.assertEquals(7, new PluginDriverShim(delegate, "org.example.Driver").getMinorVersion());
    }

    @Test
    public void jdbcCompliant_delegatesToUnderlyingDriver() {
        StubDriver delegate = new StubDriver();
        delegate.cannedJdbcCompliant = true;
        Assert.assertTrue(new PluginDriverShim(delegate, "org.example.Driver").jdbcCompliant());
    }

    @Test
    public void getParentLogger_delegatesToUnderlyingDriver() throws SQLFeatureNotSupportedException {
        StubDriver delegate = new StubDriver();
        Logger logger = new PluginDriverShim(delegate, "org.example.Driver").getParentLogger();
        Assert.assertNotNull(logger);
        Assert.assertEquals("stub", logger.getName());
    }

    private static class StubDriver implements Driver {
        Connection cannedConnection;
        boolean cannedAcceptsURL;
        DriverPropertyInfo[] cannedPropertyInfo;
        int cannedMajor;
        int cannedMinor;
        boolean cannedJdbcCompliant;

        String lastConnectUrl;
        Properties lastConnectProps;
        String lastAcceptsURL;
        String lastGetPropertyInfoUrl;
        Properties lastGetPropertyInfoProps;

        @Override
        public Connection connect(String url, Properties info) {
            this.lastConnectUrl = url;
            this.lastConnectProps = info;
            return cannedConnection;
        }

        @Override
        public boolean acceptsURL(String url) {
            this.lastAcceptsURL = url;
            return cannedAcceptsURL;
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
            this.lastGetPropertyInfoUrl = url;
            this.lastGetPropertyInfoProps = info;
            return cannedPropertyInfo;
        }

        @Override
        public int getMajorVersion() {
            return cannedMajor;
        }

        @Override
        public int getMinorVersion() {
            return cannedMinor;
        }

        @Override
        public boolean jdbcCompliant() {
            return cannedJdbcCompliant;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return Logger.getLogger("stub");
        }
    }

    /** Marker only - identity equality is the assertion. */
    private static class MarkerConnection implements Connection {
        @Override
        public java.sql.Statement createStatement() {
            return null;
        }

        @Override
        public java.sql.PreparedStatement prepareStatement(String sql) {
            return null;
        }

        @Override
        public java.sql.CallableStatement prepareCall(String sql) {
            return null;
        }

        @Override
        public String nativeSQL(String sql) {
            return null;
        }

        @Override
        public void setAutoCommit(boolean a) {}

        @Override
        public boolean getAutoCommit() {
            return false;
        }

        @Override
        public void commit() {}

        @Override
        public void rollback() {}

        @Override
        public void close() {}

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public java.sql.DatabaseMetaData getMetaData() {
            return null;
        }

        @Override
        public void setReadOnly(boolean r) {}

        @Override
        public boolean isReadOnly() {
            return false;
        }

        @Override
        public void setCatalog(String c) {}

        @Override
        public String getCatalog() {
            return null;
        }

        @Override
        public void setTransactionIsolation(int l) {}

        @Override
        public int getTransactionIsolation() {
            return 0;
        }

        @Override
        public java.sql.SQLWarning getWarnings() {
            return null;
        }

        @Override
        public void clearWarnings() {}

        @Override
        public java.sql.Statement createStatement(int a, int b) {
            return null;
        }

        @Override
        public java.sql.PreparedStatement prepareStatement(String s, int a, int b) {
            return null;
        }

        @Override
        public java.sql.CallableStatement prepareCall(String s, int a, int b) {
            return null;
        }

        @Override
        public java.util.Map<String, Class<?>> getTypeMap() {
            return null;
        }

        @Override
        public void setTypeMap(java.util.Map<String, Class<?>> m) {}

        @Override
        public void setHoldability(int h) {}

        @Override
        public int getHoldability() {
            return 0;
        }

        @Override
        public java.sql.Savepoint setSavepoint() {
            return null;
        }

        @Override
        public java.sql.Savepoint setSavepoint(String n) {
            return null;
        }

        @Override
        public void rollback(java.sql.Savepoint s) {}

        @Override
        public void releaseSavepoint(java.sql.Savepoint s) {}

        @Override
        public java.sql.Statement createStatement(int a, int b, int c) {
            return null;
        }

        @Override
        public java.sql.PreparedStatement prepareStatement(String s, int a, int b, int c) {
            return null;
        }

        @Override
        public java.sql.CallableStatement prepareCall(String s, int a, int b, int c) {
            return null;
        }

        @Override
        public java.sql.PreparedStatement prepareStatement(String s, int[] a) {
            return null;
        }

        @Override
        public java.sql.PreparedStatement prepareStatement(String s, String[] a) {
            return null;
        }

        @Override
        public java.sql.PreparedStatement prepareStatement(String s, int a) {
            return null;
        }

        @Override
        public java.sql.Clob createClob() {
            return null;
        }

        @Override
        public java.sql.Blob createBlob() {
            return null;
        }

        @Override
        public java.sql.NClob createNClob() {
            return null;
        }

        @Override
        public java.sql.SQLXML createSQLXML() {
            return null;
        }

        @Override
        public boolean isValid(int t) {
            return false;
        }

        @Override
        public void setClientInfo(String n, String v) {}

        @Override
        public void setClientInfo(Properties p) {}

        @Override
        public String getClientInfo(String n) {
            return null;
        }

        @Override
        public Properties getClientInfo() {
            return null;
        }

        @Override
        public java.sql.Array createArrayOf(String t, Object[] e) {
            return null;
        }

        @Override
        public java.sql.Struct createStruct(String t, Object[] a) {
            return null;
        }

        @Override
        public void setSchema(String s) {}

        @Override
        public String getSchema() {
            return null;
        }

        @Override
        public void abort(java.util.concurrent.Executor e) {}

        @Override
        public void setNetworkTimeout(java.util.concurrent.Executor e, int m) {}

        @Override
        public int getNetworkTimeout() {
            return 0;
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            return null;
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
