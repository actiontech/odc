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
import java.sql.SQLException;

import org.pf4j.Extension;

import com.oceanbase.odc.common.util.JdbcOperationsUtil;
import com.oceanbase.odc.plugin.connect.model.DBClientInfo;
import com.oceanbase.odc.plugin.connect.obmysql.OBMySQLSessionExtension;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Hive session extension. Hive does not support kill session/query or client info.
 *
 * @since ODC_release_4.3.4
 */
@Slf4j
@Extension
public class HiveSessionExtension extends OBMySQLSessionExtension {

    @Override
    public void switchSchema(Connection connection, String schemaName) throws SQLException {
        String currentSchema = getCurrentSchema(connection);
        if (currentSchema != null && currentSchema.equals(schemaName)) {
            return;
        }
        String sql = "USE " + schemaName;
        JdbcOperationsUtil.getJdbcOperations(connection).execute(sql);
    }

    @Override
    public String getCurrentSchema(Connection connection) {
        String querySql = "SELECT current_database()";
        try {
            return JdbcOperationsUtil.getJdbcOperations(connection)
                    .queryForObject(querySql, String.class);
        } catch (Exception e) {
            log.warn("Failed to get current schema from Hive", e);
            return "default";
        }
    }

    @Override
    public String getConnectionId(Connection connection) {
        // Hive does not have a session/connection ID equivalent to MySQL's connection_id()
        // or SQL Server's @@SPID
        return "";
    }

    @Override
    public String getKillQuerySql(@NonNull String connectionId) {
        throw new UnsupportedOperationException("Hive does not support kill query");
    }

    @Override
    public String getKillSessionSql(@NonNull String connectionId) {
        throw new UnsupportedOperationException("Hive does not support kill session");
    }

    @Override
    public boolean setClientInfo(Connection connection, DBClientInfo clientInfo) {
        // Hive does not support setting client info
        return false;
    }
}
