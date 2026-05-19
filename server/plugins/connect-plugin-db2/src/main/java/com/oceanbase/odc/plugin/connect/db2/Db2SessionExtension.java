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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.pf4j.Extension;

import com.oceanbase.odc.common.util.StringUtils;
import com.oceanbase.odc.plugin.connect.model.DBClientInfo;
import com.oceanbase.odc.plugin.connect.obmysql.OBMySQLSessionExtension;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * DB2 session extension.
 *
 * <p>
 * Key behaviours (design.md §2.3 / §7.2 / §11.1 R-03):
 *
 * <ul>
 * <li>{@link #getCurrentSchema(Connection)} — {@code VALUES CURRENT SCHEMA}</li>
 * <li>{@link #getConnectionId(Connection)} — three-level fallback:
 * <ol>
 * <li>{@code VALUES APPLICATION_ID()} (non-blank text id)</li>
 * <li>{@code SELECT APPLICATION_HANDLE FROM TABLE(MON_GET_CONNECTION(CONNECTION_HANDLE(),-2))}</li>
 * <li>{@code Integer.toHexString(connection.hashCode())} (non-null sentinel)</li>
 * </ol>
 * Each level swallows {@link SQLException} and falls through; the contract is that the returned
 * value is <strong>never null and never blank</strong>.</li>
 * <li>{@link #getKillSessionSql(String)} —
 * {@code CALL SYSPROC.ADMIN_CMD('FORCE APPLICATION (<id>)')}</li>
 * <li>{@link #getKillQuerySql(String)} — same as kill session (DB2 has no kill-query
 * primitive)</li>
 * <li>{@link #setClientInfo} — return {@code false} (handled via initializers in
 * {@link Db2ConnectionExtension#getConnectionInitializers()} instead)</li>
 * </ul>
 *
 * @author actiontech-zihan
 * @since 4.3.4
 */
@Slf4j
@Extension
public class Db2SessionExtension extends OBMySQLSessionExtension {

    @Override
    public String getCurrentSchema(Connection connection) {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("VALUES CURRENT SCHEMA")) {
            if (resultSet.next()) {
                String schema = resultSet.getString(1);
                return schema == null ? null : schema.trim();
            }
            return null;
        } catch (SQLException e) {
            log.warn("DB2 getCurrentSchema failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Three-level fallback so the returned connection id is <strong>never null and never
     * blank</strong>. This is a hard contract from design.md §11.1 R-03 ("getConnectionId 必须非空兜底"):
     * downstream KILL routing (see {@link #getKillSessionSql(String)}) requires a non-empty token.
     */
    @Override
    public String getConnectionId(Connection connection) {
        // level 1: VALUES APPLICATION_ID()
        String id = queryFirstColumnQuietly(connection, "VALUES APPLICATION_ID()");
        if (StringUtils.isNotBlank(id)) {
            return id.trim();
        }
        // level 2: MON_GET_CONNECTION → APPLICATION_HANDLE
        id = queryFirstColumnQuietly(connection,
                "SELECT APPLICATION_HANDLE FROM TABLE(MON_GET_CONNECTION(CONNECTION_HANDLE(),-2))");
        if (StringUtils.isNotBlank(id)) {
            return id.trim();
        }
        // level 3: hashCode-based non-null sentinel
        return Integer.toHexString(connection == null ? 0 : connection.hashCode());
    }

    /**
     * DB2 kill session uses the administrative procedure {@code SYSPROC.ADMIN_CMD} to issue a
     * {@code FORCE APPLICATION} command. The executing account must hold {@code SYSADM} or
     * {@code SYSCTRL}; permission verification is out of scope for unit tests.
     */
    @Override
    public String getKillSessionSql(@NonNull String connectionId) {
        return "CALL SYSPROC.ADMIN_CMD('FORCE APPLICATION (" + connectionId + ")')";
    }

    /**
     * DB2 has no independent "kill query" primitive; we reuse the kill-session SQL (matches the
     * approach SqlServerSessionExtension takes).
     */
    @Override
    public String getKillQuerySql(@NonNull String connectionId) {
        return getKillSessionSql(connectionId);
    }

    @Override
    public boolean setClientInfo(Connection connection, DBClientInfo clientInfo) {
        // DB2 sets client info via Db2ConnectionExtension.getConnectionInitializers() / setClientInfo
        // (which is wrapped in try/catch). Returning false here avoids invoking the OB-MySQL
        // dbms_application_info PL/SQL block which would throw on a real DB2 server.
        return false;
    }

    private static String queryFirstColumnQuietly(Connection connection, String sql) {
        if (connection == null) {
            return null;
        }
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            if (resultSet.next()) {
                return resultSet.getString(1);
            }
            return null;
        } catch (SQLException e) {
            log.warn("DB2 fallback query failed; sql={}, reason={}", sql, e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("DB2 fallback query unexpected error; sql={}, reason={}", sql, e.getMessage());
            return null;
        }
    }

}
