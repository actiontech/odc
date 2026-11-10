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
package com.oceanbase.odc.plugin.connect.clickhouse;

import java.sql.Connection;
import java.sql.SQLException;

import org.pf4j.Extension;

import com.oceanbase.odc.common.util.JdbcOperationsUtil;
import com.oceanbase.odc.core.shared.constant.OdcConstants;
import com.oceanbase.odc.plugin.connect.model.DBClientInfo;
import com.oceanbase.odc.plugin.connect.obmysql.OBMySQLSessionExtension;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * ClickHouse session extension: USE database / currentDatabase().
 */
@Slf4j
@Extension
public class ClickHouseSessionExtension extends OBMySQLSessionExtension {

    @Override
    public void switchSchema(Connection connection, String schemaName) throws SQLException {
        String currentSchema = getCurrentSchema(connection);
        if (currentSchema != null && currentSchema.equals(schemaName)) {
            return;
        }
        JdbcOperationsUtil.getJdbcOperations(connection).execute("USE `" + schemaName + "`");
    }

    @Override
    public String getCurrentSchema(Connection connection) {
        try {
            return JdbcOperationsUtil.getJdbcOperations(connection)
                    .queryForObject("SELECT currentDatabase()", String.class);
        } catch (Exception e) {
            log.warn("Failed to get current database from ClickHouse", e);
            return OdcConstants.CLICKHOUSE_DEFAULT_SCHEMA;
        }
    }

    @Override
    public String getConnectionId(Connection connection) {
        return "";
    }

    @Override
    public String getKillQuerySql(@NonNull String connectionId) {
        throw new UnsupportedOperationException("ClickHouse does not support kill query via this interface");
    }

    @Override
    public String getKillSessionSql(@NonNull String connectionId) {
        throw new UnsupportedOperationException("ClickHouse does not support kill session via this interface");
    }

    @Override
    public boolean setClientInfo(Connection connection, DBClientInfo clientInfo) {
        return false;
    }
}
