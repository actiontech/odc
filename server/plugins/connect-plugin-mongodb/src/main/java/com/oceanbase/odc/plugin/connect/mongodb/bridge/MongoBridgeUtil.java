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
package com.oceanbase.odc.plugin.connect.mongodb.bridge;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;

public final class MongoBridgeUtil {
    private static final Map<Connection, MongoSessionContext> CONTEXTS = new HashMap<>();

    private MongoBridgeUtil() {}

    public static Connection newConnection(MongoSessionContext context) {
        InvocationHandler handler = new MongoConnectionHandler(context);
        Connection connection = (Connection) Proxy.newProxyInstance(MongoBridgeUtil.class.getClassLoader(),
                new Class[] {Connection.class}, handler);
        synchronized (CONTEXTS) {
            CONTEXTS.put(connection, context);
        }
        return connection;
    }

    public static MongoSessionContext requireContext(Connection connection) {
        synchronized (CONTEXTS) {
            MongoSessionContext context = CONTEXTS.get(connection);
            if (context == null) {
                throw new IllegalStateException("MongoDB session context not found");
            }
            return context;
        }
    }

    static Statement newStatement(Connection connection, MongoSessionContext context) {
        InvocationHandler handler = new MongoStatementHandler(connection, context);
        return (Statement) Proxy.newProxyInstance(MongoBridgeUtil.class.getClassLoader(),
                new Class[] {Statement.class}, handler);
    }

    public static ResultSet toResultSet(MongoTabularResult result) throws SQLException {
        CachedRowSet rowSet = RowSetProvider.newFactory().createCachedRowSet();
        RowSetMetaDataImpl metaData = new RowSetMetaDataImpl();
        metaData.setColumnCount(result.getColumns().size());
        for (int i = 0; i < result.getColumns().size(); i++) {
            int columnIndex = i + 1;
            metaData.setColumnName(columnIndex, result.getColumns().get(i));
            metaData.setColumnLabel(columnIndex, result.getColumns().get(i));
            metaData.setColumnType(columnIndex, Types.VARCHAR);
        }
        rowSet.setMetaData(metaData);
        for (List<Object> row : result.getRows()) {
            rowSet.moveToInsertRow();
            for (int i = 0; i < row.size(); i++) {
                Object value = row.get(i);
                rowSet.updateObject(i + 1, value == null ? null : String.valueOf(value));
            }
            rowSet.insertRow();
        }
        rowSet.moveToCurrentRow();
        rowSet.beforeFirst();
        return rowSet;
    }

    private static class MongoConnectionHandler implements InvocationHandler {
        private final MongoSessionContext context;
        private boolean closed = false;
        private boolean autoCommit = true;

        private MongoConnectionHandler(MongoSessionContext context) {
            this.context = context;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if ("createStatement".equals(name)) {
                return newStatement((Connection) proxy, context);
            }
            if ("close".equals(name)) {
                closed = true;
                return null;
            }
            if ("isClosed".equals(name)) {
                return closed;
            }
            if ("getAutoCommit".equals(name)) {
                return autoCommit;
            }
            if ("setAutoCommit".equals(name)) {
                autoCommit = (Boolean) args[0];
                return null;
            }
            if ("getCatalog".equals(name)) {
                return context.getCurrentDatabase();
            }
            if ("setCatalog".equals(name)) {
                context.setCurrentDatabase((String) args[0]);
                return null;
            }
            if ("unwrap".equals(name)) {
                if (((Class<?>) args[0]).isInstance(proxy)) {
                    return proxy;
                }
                throw new SQLException("Unsupported unwrap target");
            }
            if ("isWrapperFor".equals(name)) {
                return ((Class<?>) args[0]).isInstance(proxy);
            }
            if ("getMetaData".equals(name) || "prepareStatement".equals(name) || "prepareCall".equals(name)
                    || "nativeSQL".equals(name) || "commit".equals(name) || "rollback".equals(name)
                    || "setReadOnly".equals(name) || "getWarnings".equals(name) || "clearWarnings".equals(name)) {
                return defaultValue(method.getReturnType());
            }
            throw new UnsupportedOperationException("Unsupported MongoDB JDBC connection method: " + name);
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (type == null || !type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }
}
