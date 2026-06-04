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
package com.oceanbase.odc.plugin.connect.redis;

import java.sql.Connection;
import java.sql.SQLException;

import org.pf4j.Extension;

import com.oceanbase.odc.plugin.connect.api.SessionExtensionPoint;
import com.oceanbase.odc.plugin.connect.model.DBClientInfo;
import com.oceanbase.odc.plugin.connect.redis.bridge.RedisBridgeUtil;
import com.oceanbase.odc.plugin.connect.redis.bridge.RedisSessionContext;

import lombok.NonNull;

@Extension
public class RedisSessionExtension implements SessionExtensionPoint {
    @Override
    public void switchSchema(Connection connection, String schemaName) throws SQLException {
        try {
            RedisSessionContext context = RedisBridgeUtil.requireContext(connection);
            context.setCurrentDatabase(schemaName);
            context.getClient().command("SELECT", context.getCurrentDatabase());
        } catch (Exception e) {
            throw new SQLException("Failed to select Redis database", e);
        }
    }

    @Override
    public String getCurrentSchema(Connection connection) {
        return RedisBridgeUtil.requireContext(connection).getCurrentDatabase();
    }

    @Override
    public String getVariable(Connection connection, String variableName) {
        if ("version".equalsIgnoreCase(variableName)) {
            return RedisBridgeUtil.requireContext(connection).getServerVersion();
        }
        return null;
    }

    @Override
    public String getAlterVariableStatement(String variableScope, String variableName, String variableValue) {
        throw new UnsupportedOperationException("Redis does not support altering session variables via SQL");
    }

    @Override
    public boolean setClientInfo(Connection connection, DBClientInfo clientInfo) {
        return false;
    }

    @Override
    public String getConnectionId(@NonNull Connection connection) {
        return RedisBridgeUtil.requireContext(connection).getConnectionId();
    }

    @Override
    public void killQuery(@NonNull Connection connection, @NonNull String connectionId) {
        throw new UnsupportedOperationException("Redis plugin does not support kill query");
    }

    @Override
    public String getKillQuerySql(@NonNull String connectionId) {
        throw new UnsupportedOperationException("Redis plugin does not support kill query");
    }

    @Override
    public String getKillSessionSql(@NonNull String connectionId) {
        throw new UnsupportedOperationException("Redis plugin does not support kill session");
    }
}
