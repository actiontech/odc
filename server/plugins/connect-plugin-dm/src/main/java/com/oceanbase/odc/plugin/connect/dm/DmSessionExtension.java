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
package com.oceanbase.odc.plugin.connect.dm;

import java.sql.Connection;
import java.sql.SQLException;

import org.pf4j.Extension;

import com.oceanbase.odc.common.util.JdbcOperationsUtil;
import com.oceanbase.odc.plugin.connect.model.DBClientInfo;
import com.oceanbase.odc.plugin.connect.obmysql.OBMySQLSessionExtension;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Session extension for DM (Dameng) database.
 * <p>
 * DM uses {@code SET SCHEMA} for schema switching (design decision D-008) and {@code SESSID()} for
 * session ID retrieval.
 * </p>
 *
 * @author
 * @since ODC_release_4.3.4
 */
@Slf4j
@Extension
public class DmSessionExtension extends OBMySQLSessionExtension {

    @Override
    public void switchSchema(Connection connection, String schemaName) throws SQLException {
        String currentSchema = getCurrentSchema(connection);
        if (currentSchema != null && currentSchema.equals(schemaName)) {
            return;
        }
        String sql = "SET SCHEMA " + schemaName;
        JdbcOperationsUtil.getJdbcOperations(connection).execute(sql);
    }

    @Override
    public String getCurrentSchema(Connection connection) {
        String querySql = "SELECT SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') FROM DUAL";
        try {
            return JdbcOperationsUtil.getJdbcOperations(connection)
                    .queryForObject(querySql, String.class);
        } catch (Exception e) {
            log.warn("Failed to get current schema using SYS_CONTEXT, trying fallback", e);
            try {
                return JdbcOperationsUtil.getJdbcOperations(connection)
                        .queryForObject("SELECT CURRENT_SCHEMA()", String.class);
            } catch (Exception ex) {
                log.warn("Failed to get current schema using CURRENT_SCHEMA()", ex);
                return null;
            }
        }
    }

    @Override
    public String getConnectionId(Connection connection) {
        String querySql = "SELECT SESSID()";
        try {
            return JdbcOperationsUtil.getJdbcOperations(connection)
                    .queryForObject(querySql, String.class);
        } catch (Exception e) {
            log.warn("Failed to get connection ID from DM using SESSID()", e);
            return "";
        }
    }

    @Override
    public String getKillQuerySql(@NonNull String connectionId) {
        return getKillSessionSql(connectionId);
    }

    @Override
    public String getKillSessionSql(@NonNull String connectionId) {
        return "CALL SP_CLOSE_SESSION(" + connectionId + ")";
    }

    @Override
    public String getVariable(Connection connection, String variableName) {
        // DM does not support SHOW SESSION VARIABLES syntax like MySQL.
        // Use V$PARAMETER system view instead.
        String querySql = "SELECT PARA_VALUE FROM V$PARAMETER WHERE PARA_NAME = '" + variableName + "'";
        try {
            return JdbcOperationsUtil.getJdbcOperations(connection)
                    .queryForObject(querySql, String.class);
        } catch (Exception e) {
            log.warn("Failed to get variable {} from DM, message={}", variableName, e.getMessage());
        }
        return null;
    }

    @Override
    public String getAlterVariableStatement(String variableScope, String variableName, String variableValue) {
        // DM uses SP_SET_PARA_VALUE to modify system parameters
        return String.format("CALL SP_SET_PARA_VALUE(1, '%s', %s)", variableName, variableValue);
    }

    @Override
    public boolean setClientInfo(Connection connection, DBClientInfo clientInfo) {
        return false;
    }
}
