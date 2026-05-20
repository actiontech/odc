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

import java.sql.Connection;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Map-case unit tests pinning every method on {@link HiveSessionExtension} that exists purely to
 * surface "Hive does not support this" to the UI. Each entry below names one extension method; when
 * the contract drifts (e.g. someone implements {@code killQuery} for Hive 5) the test must be
 * updated together with the corresponding UI feature flag — that is the only safe migration path
 * for compat-RISK R-4.2 / R-7.1.
 */
public class HiveSessionExtensionTest {

    private HiveSessionExtension ext;
    private final Connection nullConnection = null; // method only consults arguments, not the conn

    @Before
    public void setUp() {
        ext = new HiveSessionExtension();
    }

    @Test
    public void getConnectionId_throwsUnsupported() {
        try {
            // safe to pass a dummy Connection-typed expression because the method short-circuits
            ext.getConnectionId(new DummyConnection());
            Assert.fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            Assert.assertEquals("Hive session management not supported", e.getMessage());
        }
    }

    @Test
    public void killQuery_throwsUnsupported() {
        try {
            ext.killQuery(new DummyConnection(), "anything");
            Assert.fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            Assert.assertEquals("Hive session management not supported", e.getMessage());
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void getKillQuerySql_throwsUnsupported() {
        ext.getKillQuerySql("any");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void getKillSessionSql_throwsUnsupported() {
        ext.getKillSessionSql("any");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void getVariable_throwsUnsupported() {
        ext.getVariable(nullConnection, "hive.execution.engine");
    }

    @Test
    public void getAlterVariableStatement_returnsSetForm() {
        Assert.assertEquals("SET hive.execution.engine=mr",
                ext.getAlterVariableStatement("session", "hive.execution.engine", "mr"));
        // scope is intentionally ignored (Hive only has session conf + global hive-site.xml)
        Assert.assertEquals("SET hive.execution.engine=tez",
                ext.getAlterVariableStatement("global", "hive.execution.engine", "tez"));
    }

    @Test
    public void setClientInfo_returnsFalse() {
        Assert.assertFalse(ext.setClientInfo(nullConnection, null));
    }

    @Test
    public void switchSchema_emptyOrNull_isNoOp() throws Exception {
        // Both branches must early-return without touching the connection. Passing a null
        // Connection would NPE if the implementation forgot the guard.
        ext.switchSchema(null, null);
        ext.switchSchema(null, "");
    }

    /**
     * Stand-in {@link Connection} where every default method is enough for the tested branch — we never
     * actually call any JDBC method, the extension methods short-circuit on the unsupported path before
     * touching the connection.
     */
    private static class DummyConnection implements Connection {
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
            return sql;
        }

        @Override
        public void setAutoCommit(boolean autoCommit) {}

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
        public void setReadOnly(boolean readOnly) {}

        @Override
        public boolean isReadOnly() {
            return false;
        }

        @Override
        public void setCatalog(String catalog) {}

        @Override
        public String getCatalog() {
            return null;
        }

        @Override
        public void setTransactionIsolation(int level) {}

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
        public java.sql.Statement createStatement(int resultSetType, int resultSetConcurrency) {
            return null;
        }

        @Override
        public java.sql.PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) {
            return null;
        }

        @Override
        public java.sql.CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) {
            return null;
        }

        @Override
        public java.util.Map<String, Class<?>> getTypeMap() {
            return null;
        }

        @Override
        public void setTypeMap(java.util.Map<String, Class<?>> map) {}

        @Override
        public void setHoldability(int holdability) {}

        @Override
        public int getHoldability() {
            return 0;
        }

        @Override
        public java.sql.Savepoint setSavepoint() {
            return null;
        }

        @Override
        public java.sql.Savepoint setSavepoint(String name) {
            return null;
        }

        @Override
        public void rollback(java.sql.Savepoint savepoint) {}

        @Override
        public void releaseSavepoint(java.sql.Savepoint savepoint) {}

        @Override
        public java.sql.Statement createStatement(int rsType, int rsConcurrency, int rsHoldability) {
            return null;
        }

        @Override
        public java.sql.PreparedStatement prepareStatement(String sql, int rsType, int rsConcurrency,
                int rsHoldability) {
            return null;
        }

        @Override
        public java.sql.CallableStatement prepareCall(String sql, int rsType, int rsConcurrency, int rsHoldability) {
            return null;
        }

        @Override
        public java.sql.PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) {
            return null;
        }

        @Override
        public java.sql.PreparedStatement prepareStatement(String sql, int[] columnIndexes) {
            return null;
        }

        @Override
        public java.sql.PreparedStatement prepareStatement(String sql, String[] columnNames) {
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
        public boolean isValid(int timeout) {
            return false;
        }

        @Override
        public void setClientInfo(String name, String value) {}

        @Override
        public void setClientInfo(java.util.Properties properties) {}

        @Override
        public String getClientInfo(String name) {
            return null;
        }

        @Override
        public java.util.Properties getClientInfo() {
            return null;
        }

        @Override
        public java.sql.Array createArrayOf(String typeName, Object[] elements) {
            return null;
        }

        @Override
        public java.sql.Struct createStruct(String typeName, Object[] attributes) {
            return null;
        }

        @Override
        public void setSchema(String schema) {}

        @Override
        public String getSchema() {
            return null;
        }

        @Override
        public void abort(java.util.concurrent.Executor executor) {}

        @Override
        public void setNetworkTimeout(java.util.concurrent.Executor executor, int milliseconds) {}

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
