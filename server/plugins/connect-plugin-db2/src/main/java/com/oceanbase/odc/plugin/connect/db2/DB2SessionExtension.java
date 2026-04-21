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

import java.sql.Connection;
import java.sql.SQLException;

import org.pf4j.Extension;

import com.oceanbase.odc.common.util.JdbcOperationsUtil;
import com.oceanbase.odc.plugin.connect.model.DBClientInfo;
import com.oceanbase.odc.plugin.connect.obmysql.OBMySQLSessionExtension;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Extension
public class DB2SessionExtension extends OBMySQLSessionExtension {

    @Override
    public void switchSchema(Connection connection, String schemaName) throws SQLException {
        String sql = "SET SCHEMA " + schemaName;
        JdbcOperationsUtil.getJdbcOperations(connection).execute(sql);
    }

    @Override
    public String getCurrentSchema(Connection connection) {
        try {
            return JdbcOperationsUtil.getJdbcOperations(connection)
                    .queryForObject("VALUES CURRENT SCHEMA", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String getConnectionId(Connection connection) {
        try {
            return JdbcOperationsUtil.getJdbcOperations(connection)
                    .queryForObject("VALUES APPLICATION_ID()", String.class);
        } catch (Exception e) {
            log.warn("Failed to get DB2 connection ID", e);
            return "";
        }
    }

    @Override
    public String getKillQuerySql(@NonNull String connectionId) {
        // DB2 uses FORCE APPLICATION to terminate a session/query
        return "CALL SYSPROC.ADMIN_CMD('FORCE APPLICATION (" + connectionId + ")')";
    }

    @Override
    public String getKillSessionSql(@NonNull String connectionId) {
        return "CALL SYSPROC.ADMIN_CMD('FORCE APPLICATION (" + connectionId + ")')";
    }

    @Override
    public boolean setClientInfo(Connection connection, DBClientInfo clientInfo) {
        return false;
    }
}
