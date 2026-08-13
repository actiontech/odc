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
package com.oceanbase.odc.plugin.connect.kingbase;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;

import org.pf4j.Extension;

import com.oceanbase.odc.common.util.JdbcOperationsUtil;
import com.oceanbase.odc.common.util.StringUtils;
import com.oceanbase.odc.plugin.connect.model.DBClientInfo;
import com.oceanbase.odc.plugin.connect.obmysql.OBMySQLSessionExtension;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Session extension for KingBase (oracle mode).
 * <p>
 * Design preferred {@code ALTER SESSION SET CURRENT_SCHEMA}, but KingBaseES oracle mode on the
 * verify instance rejects that grammar ({@code syntax error at or near "CURRENT_SCHEMA"}). Working
 * equivalent is PostgreSQL-style {@code SET search_path TO ...} (not {@code SET SCHEMA}).
 * </p>
 */
@Slf4j
@Extension
public class KingBaseSessionExtension extends OBMySQLSessionExtension {

    @Override
    public void switchSchema(Connection connection, String schemaName) throws SQLException {
        String bare = unquote(schemaName);
        String currentSchema = getCurrentSchema(connection);
        if (currentSchema != null && currentSchema.equalsIgnoreCase(bare)) {
            return;
        }
        // Prefer unquoted identifier so search_path folds consistently with ALL_USERS names.
        String sql = "SET search_path TO " + sanitizeSearchPathIdent(bare);
        JdbcOperationsUtil.getJdbcOperations(connection).execute(sql);
    }

    @Override
    public String getCurrentSchema(Connection connection) {
        try {
            String path = JdbcOperationsUtil.getJdbcOperations(connection)
                    .queryForObject("SHOW search_path", String.class);
            if (path != null && !path.isEmpty()) {
                String first = path.split(",")[0].trim();
                return unquote(first);
            }
        } catch (Exception e) {
            log.warn("Failed to get KingBase search_path", e);
        }
        try {
            return JdbcOperationsUtil.getJdbcOperations(connection)
                    .queryForObject("SELECT SYS_CONTEXT('userenv','CURRENT_SCHEMA') FROM DUAL", String.class);
        } catch (Exception e) {
            log.warn("Failed to get current schema from KingBase SYS_CONTEXT", e);
            return null;
        }
    }

    @Override
    public String getConnectionId(Connection connection) {
        // Prefer PG backend pid: kill uses pg_terminate_backend(id). SYS_CONTEXT SESSIONID may be null
        // on KingBase oracle mode and would fail BaseSqlExecuteCallable Verify.notNull(ConnectionId).
        try {
            String pid = JdbcOperationsUtil.getJdbcOperations(connection)
                    .queryForObject("SELECT pg_backend_pid()", String.class);
            if (pid != null && !pid.isEmpty()) {
                return pid;
            }
        } catch (Exception e) {
            log.warn("Failed to get KingBase connection id via pg_backend_pid()", e);
        }
        try {
            String sessionId = JdbcOperationsUtil.getJdbcOperations(connection)
                    .queryForObject("SELECT SYS_CONTEXT('USERENV', 'SESSIONID') FROM DUAL", String.class);
            if (sessionId != null && !sessionId.isEmpty()) {
                return sessionId;
            }
        } catch (Exception e) {
            log.warn("Failed to get KingBase connection id via SYS_CONTEXT", e);
        }
        return "0";
    }

    @Override
    public String getKillQuerySql(@NonNull String connectionId) {
        return getKillSessionSql(connectionId);
    }

    @Override
    public String getKillSessionSql(@NonNull String connectionId) {
        return "SELECT pg_terminate_backend(" + connectionId + ")";
    }

    @Override
    public String getVariable(Connection connection, String variableName) {
        return null;
    }

    @Override
    public String getAlterVariableStatement(String variableScope, String variableName, String variableValue) {
        return null;
    }

    @Override
    public boolean setClientInfo(Connection connection, DBClientInfo clientInfo) {
        return false;
    }

    private static String unquote(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    /**
     * Allow only safe identifier chars for search_path (no quotes / injection).
     */
    private static String sanitizeSearchPathIdent(String name) {
        if (StringUtils.isBlank(name)) {
            return name;
        }
        String bare = unquote(name).trim();
        if (!bare.matches("[A-Za-z_][A-Za-z0-9_$#]*")) {
            throw new IllegalArgumentException("Illegal schema name for KingBase search_path: " + name);
        }
        return bare.toLowerCase(Locale.ROOT);
    }
}
