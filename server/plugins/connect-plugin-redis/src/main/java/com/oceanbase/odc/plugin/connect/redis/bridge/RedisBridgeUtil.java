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
package com.oceanbase.odc.plugin.connect.redis.bridge;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;

public final class RedisBridgeUtil {
    private static final Map<Connection, RedisSessionContext> CONTEXTS = new HashMap<>();

    private RedisBridgeUtil() {}

    public static Connection newConnection(RedisSessionContext context) {
        InvocationHandler handler = new RedisConnectionHandler(context);
        Connection connection = (Connection) Proxy.newProxyInstance(RedisBridgeUtil.class.getClassLoader(),
                new Class[] {Connection.class}, handler);
        synchronized (CONTEXTS) {
            CONTEXTS.put(connection, context);
        }
        return connection;
    }

    public static RedisSessionContext requireContext(Connection connection) {
        RedisSessionContext context =
                findContext(connection, java.util.Collections.newSetFromMap(new IdentityHashMap<>()));
        if (context == null) {
            throw new IllegalStateException("Redis session context not found");
        }
        return context;
    }

    static Statement newStatement(Connection connection, RedisSessionContext context) {
        InvocationHandler handler = new RedisStatementHandler(connection, context);
        return (Statement) Proxy.newProxyInstance(RedisBridgeUtil.class.getClassLoader(), new Class[] {Statement.class},
                handler);
    }

    public static ResultSet toResultSet(RedisTabularResult result) throws SQLException {
        CachedRowSet rowSet = RowSetProvider.newFactory().createCachedRowSet();
        RowSetMetaDataImpl metaData = new RowSetMetaDataImpl();
        metaData.setColumnCount(result.getColumns().size());
        for (int i = 0; i < result.getColumns().size(); i++) {
            int columnIndex = i + 1;
            metaData.setColumnName(columnIndex, result.getColumns().get(i));
            metaData.setColumnLabel(columnIndex, result.getColumns().get(i));
            metaData.setColumnType(columnIndex, Types.VARCHAR);
            String typeName = result.getColumnTypeNames().size() > i ? result.getColumnTypeNames().get(i) : "VARCHAR";
            metaData.setColumnTypeName(columnIndex, typeName == null ? "VARCHAR" : typeName);
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

    private static RedisSessionContext findContext(Object object, java.util.Set<Object> visited) {
        if (object == null || visited.contains(object)) {
            return null;
        }
        visited.add(object);
        synchronized (CONTEXTS) {
            if (CONTEXTS.containsKey(object)) {
                return CONTEXTS.get(object);
            }
        }
        if (Proxy.isProxyClass(object.getClass())) {
            InvocationHandler handler = Proxy.getInvocationHandler(object);
            if (handler instanceof RedisConnectionHandler) {
                return ((RedisConnectionHandler) handler).context;
            }
        }
        if (object instanceof Connection) {
            try {
                Connection unwrapped = ((Connection) object).unwrap(Connection.class);
                if (unwrapped != object) {
                    return findContext(unwrapped, visited);
                }
            } catch (SQLException ignored) {
                return null;
            }
        }
        return null;
    }

    private static class RedisConnectionHandler implements InvocationHandler {
        private final RedisSessionContext context;
        private boolean closed = false;
        private boolean autoCommit = true;

        private RedisConnectionHandler(RedisSessionContext context) {
            this.context = context;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(name)) {
                return proxy == args[0];
            }
            if ("toString".equals(name)) {
                return "RedisJdbcConnection[" + context.getConnectionId() + "]";
            }
            if ("createStatement".equals(name)) {
                return newStatement((Connection) proxy, context);
            }
            if ("close".equals(name)) {
                closed = true;
                context.getClient().close();
                synchronized (CONTEXTS) {
                    CONTEXTS.remove(proxy);
                }
                return null;
            }
            if ("isClosed".equals(name)) {
                return closed;
            }
            if ("isValid".equals(name)) {
                return !closed;
            }
            if ("getAutoCommit".equals(name)) {
                return autoCommit;
            }
            if ("setAutoCommit".equals(name)) {
                autoCommit = (Boolean) args[0];
                return null;
            }
            if ("getCatalog".equals(name) || "getSchema".equals(name)) {
                return context.getCurrentDatabase();
            }
            if ("setCatalog".equals(name) || "setSchema".equals(name)) {
                context.setCurrentDatabase((String) args[0]);
                context.getClient().command("SELECT", context.getCurrentDatabase());
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
            if ("commit".equals(name) || "rollback".equals(name) || "clearWarnings".equals(name)) {
                return null;
            }
            if ("getWarnings".equals(name)) {
                return null;
            }
            throw new UnsupportedOperationException("Redis JDBC connection method not supported: " + name);
        }
    }
}
