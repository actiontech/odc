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
import java.sql.Connection;
import java.sql.ResultSet;

class RedisStatementHandler implements InvocationHandler {
    private final Connection connection;
    private final RedisSessionContext context;
    private final RedisCommandParser parser = new RedisCommandParser();
    private final RedisResultMapper resultMapper = new RedisResultMapper();
    private ResultSet currentResultSet;
    private int updateCount = -1;
    private int queryTimeout;
    private int maxRows;

    RedisStatementHandler(Connection connection, RedisSessionContext context) {
        this.connection = connection;
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
            return "RedisJdbcStatement[" + context.getConnectionId() + "]";
        }
        if ("execute".equals(name)) {
            return execute((String) args[0]);
        }
        if ("executeQuery".equals(name)) {
            execute((String) args[0]);
            return currentResultSet;
        }
        if ("getResultSet".equals(name)) {
            return currentResultSet;
        }
        if ("getUpdateCount".equals(name)) {
            return updateCount;
        }
        if ("getMoreResults".equals(name)) {
            return false;
        }
        if ("setQueryTimeout".equals(name)) {
            queryTimeout = (Integer) args[0];
            return null;
        }
        if ("getQueryTimeout".equals(name)) {
            return queryTimeout;
        }
        if ("setMaxRows".equals(name)) {
            maxRows = (Integer) args[0];
            return null;
        }
        if ("getMaxRows".equals(name)) {
            return maxRows;
        }
        if ("getConnection".equals(name)) {
            return connection;
        }
        if ("close".equals(name) || "clearWarnings".equals(name)) {
            return null;
        }
        if ("getWarnings".equals(name)) {
            return null;
        }
        throw new UnsupportedOperationException("Redis JDBC statement method not supported: " + name);
    }

    private boolean execute(String text) throws Exception {
        RedisParsedCommand command = parser.parse(text);
        Object reply = context.getClient().command(command.parts());
        RedisTabularResult result = resultMapper.map(command, reply);
        currentResultSet = RedisBridgeUtil.toResultSet(result);
        updateCount = command.isWrite() ? 1 : -1;
        return true;
    }
}
