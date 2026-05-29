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
package com.oceanbase.odc.plugin.connect.hana;

import java.sql.Connection;
import java.sql.SQLException;

import org.pf4j.Extension;

import com.oceanbase.odc.common.util.JdbcOperationsUtil;
import com.oceanbase.odc.plugin.connect.model.DBClientInfo;
import com.oceanbase.odc.plugin.connect.obmysql.OBMySQLSessionExtension;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * SAP HANA session extension.
 * <p>
 * Provides HANA-specific implementations for schema switching, connection ID retrieval, and
 * session/query termination.
 *
 * @since ODC_release_4.3.4
 */
@Slf4j
@Extension
public class HanaSessionExtension extends OBMySQLSessionExtension {

    @Override
    public void switchSchema(Connection connection, String schemaName) throws SQLException {
        String currentSchema = getCurrentSchema(connection);
        if (currentSchema != null && currentSchema.equals(schemaName)) {
            return;
        }
        // HANA uses SET SCHEMA with double-quoted identifiers
        String sql = "SET SCHEMA \"" + schemaName + "\"";
        JdbcOperationsUtil.getJdbcOperations(connection).execute(sql);
    }

    @Override
    public String getCurrentSchema(Connection connection) {
        String querySql = "SELECT CURRENT_SCHEMA FROM DUMMY";
        try {
            return JdbcOperationsUtil.getJdbcOperations(connection)
                    .queryForObject(querySql, String.class);
        } catch (Exception e) {
            log.warn("Failed to get current schema from HANA", e);
            return null;
        }
    }

    @Override
    public String getConnectionId(Connection connection) {
        String querySql = "SELECT CONNECTION_ID FROM SYS.M_CONNECTIONS WHERE OWN = 'TRUE'";
        try {
            return JdbcOperationsUtil.getJdbcOperations(connection)
                    .queryForObject(querySql, String.class);
        } catch (Exception e) {
            log.warn("Failed to get connection ID from HANA", e);
            return "";
        }
    }

    @Override
    public String getKillSessionSql(@NonNull String connectionId) {
        return "ALTER SYSTEM DISCONNECT SESSION '" + connectionId + "'";
    }

    @Override
    public String getKillQuerySql(@NonNull String connectionId) {
        return "ALTER SYSTEM CANCEL SESSION '" + connectionId + "'";
    }

    @Override
    public String getVariable(Connection connection, String variableName) {
        // HANA does not have session variables in the MySQL sense
        return null;
    }

    @Override
    public String getAlterVariableStatement(String variableScope, String variableName, String variableValue) {
        // HANA does not support ALTER SESSION SET in the same way
        return null;
    }

    @Override
    public boolean setClientInfo(Connection connection, DBClientInfo clientInfo) {
        // HANA does not support setting client info via DBMS_APPLICATION_INFO
        return false;
    }
}
