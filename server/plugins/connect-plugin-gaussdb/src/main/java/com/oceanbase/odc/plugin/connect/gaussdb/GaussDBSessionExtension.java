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
package com.oceanbase.odc.plugin.connect.gaussdb;

import java.sql.Connection;
import java.sql.SQLException;

import org.pf4j.Extension;

import com.oceanbase.odc.common.util.JdbcOperationsUtil;
import com.oceanbase.odc.plugin.connect.model.DBClientInfo;
import com.oceanbase.odc.plugin.connect.obmysql.OBMySQLSessionExtension;

import lombok.NonNull;

/**
 * Session extension for GaussDB / openGauss connections.
 * <p>
 * GaussDB speaks the PostgreSQL wire protocol but ODC's existing {@code PostgresSessionExtension}
 * is an almost-empty skeleton that only overrides {@link #setClientInfo(Connection, DBClientInfo)}.
 * Inheriting from it would silently fall back to OB-MySQL behaviour for kill-query / switch-schema
 * / current-schema, which would fail on Postgres-protocol back ends. We therefore extend
 * {@link OBMySQLSessionExtension} directly and override the seven session operations with PG-native
 * SQL.
 * <p>
 * Method-level rationale (matches compat_risks CR-4b):
 * <ul>
 * <li>{@link #getKillQuerySql(String)} uses {@code pg_cancel_backend(pid)}; the OB-MySQL
 * {@code KILL QUERY <id>} is invalid on PG protocol.</li>
 * <li>{@link #getKillSessionSql(String)} uses {@code pg_terminate_backend} which closes the
 * backend; {@code KILL <id>} would error on PG.</li>
 * <li>{@link #switchSchema(Connection, String)} uses {@code SET search_path
 *       TO "schema"}; {@link Connection#setCatalog(String)} is not honoured by the PG JDBC
 * driver.</li>
 * <li>{@link #getCurrentSchema(Connection)} returns {@code current_schema()} (PG's analogue of
 * {@code DATABASE()}).</li>
 * <li>{@link #getConnectionId(Connection)} returns {@code pg_backend_pid()::text}; the explicit
 * {@code ::text} cast avoids Java {@code Integer} overflow when PIDs do not fit in 32-bit signed
 * integers on some platforms.</li>
 * <li>{@link #getVariable(Connection, String)} uses {@code current_setting(name)}; OB-MySQL's
 * {@code SHOW SESSION VARIABLES LIKE} is not parseable by PG.</li>
 * <li>{@link #setClientInfo(Connection, DBClientInfo)} is a no-op (returns {@code false}), matching
 * the existing PG plugin behaviour.</li>
 * </ul>
 */
@Extension
public class GaussDBSessionExtension extends OBMySQLSessionExtension {

    @Override
    public String getKillQuerySql(@NonNull String connectionId) {
        return "SELECT pg_cancel_backend(" + connectionId + ")";
    }

    @Override
    public String getKillSessionSql(@NonNull String connectionId) {
        return "SELECT pg_terminate_backend(" + connectionId + ")";
    }

    @Override
    public void switchSchema(Connection connection, String schemaName) throws SQLException {
        String sql = "SET search_path TO " + quoteIdentifier(schemaName);
        JdbcOperationsUtil.getJdbcOperations(connection).execute(sql);
    }

    @Override
    public String getCurrentSchema(Connection connection) {
        return JdbcOperationsUtil.getJdbcOperations(connection)
                .queryForObject("SELECT current_schema()", String.class);
    }

    @Override
    public String getConnectionId(Connection connection) {
        return JdbcOperationsUtil.getJdbcOperations(connection)
                .queryForObject("SELECT pg_backend_pid()::text", String.class);
    }

    @Override
    public String getVariable(Connection connection, String variableName) {
        String sql = "SELECT current_setting('" + variableName + "')";
        return JdbcOperationsUtil.getJdbcOperations(connection).queryForObject(sql, String.class);
    }

    @Override
    public boolean setClientInfo(Connection connection, DBClientInfo clientInfo) {
        return false;
    }

    /**
     * Quote a PostgreSQL identifier with double quotes and escape embedded double quotes by doubling
     * them, e.g. {@code my"name} -> {@code "my""name"}. Inlined here because
     * {@code com.oceanbase.odc.common.util.StringUtils} has no equivalent helper, and per design.md
     * §3.2.3 we must not pull in a new third-party dependency for this single call site.
     */
    private static String quoteIdentifier(String identifier) {
        if (identifier == null) {
            return "\"\"";
        }
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

}
