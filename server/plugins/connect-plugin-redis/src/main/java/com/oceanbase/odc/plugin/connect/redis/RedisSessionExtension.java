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

import lombok.NonNull;

/**
 * Redis session extension. Redis does not support most session
 * operations (schema switching, variable queries, kill query, etc.),
 * so this implementation provides no-op defaults.
 */
@Extension
public class RedisSessionExtension implements SessionExtensionPoint {

    @Override
    public void switchSchema(Connection connection, String schemaName) throws SQLException {
        // Redis uses SELECT db_index, not relevant for schema switching
    }

    @Override
    public String getCurrentSchema(Connection connection) {
        return null;
    }

    @Override
    public String getVariable(Connection connection, String variableName) {
        return null;
    }

    @Override
    public String getAlterVariableStatement(String variableScope, String variableName, String variableValue) {
        return "";
    }

    @Override
    public boolean setClientInfo(Connection connection, DBClientInfo clientInfo) {
        return false;
    }

    @Override
    public String getConnectionId(@NonNull Connection connection) {
        return "redis-session";
    }

    @Override
    public void killQuery(@NonNull Connection connection, @NonNull String connectionId) {
        // Not supported for Redis
    }

    @Override
    public String getKillQuerySql(@NonNull String connectionId) {
        return "";
    }

    @Override
    public String getKillSessionSql(@NonNull String connectionId) {
        return "";
    }
}
