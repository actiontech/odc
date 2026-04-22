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
package com.oceanbase.tools.dbbrowser.stats.db2;

import java.util.List;

import org.springframework.jdbc.core.JdbcOperations;

import com.oceanbase.tools.dbbrowser.model.DBSession;
import com.oceanbase.tools.dbbrowser.model.DBTableStats;
import com.oceanbase.tools.dbbrowser.stats.DBStatsAccessor;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * DB2-specific implementation of {@link DBStatsAccessor}. Uses DB2 SYSCAT catalog views and
 * SYSPROC.MON_GET_CONNECTION for session/stats queries instead of SQL Server system views.
 */
@Slf4j
public class DB2StatsAccessor implements DBStatsAccessor {

    protected final JdbcOperations jdbcOperations;

    public DB2StatsAccessor(@NonNull JdbcOperations jdbcOperations) {
        this.jdbcOperations = jdbcOperations;
    }

    @Override
    public DBTableStats getTableStats(@NonNull String schema, @NonNull String tableName) {
        String sql = "SELECT CARD AS ROW_COUNT, FPAGES * PAGESIZE AS DATA_SIZE_IN_BYTES"
                + " FROM SYSCAT.TABLES t"
                + " JOIN SYSCAT.TABLESPACES ts ON t.TBSPACE = ts.TBSPACE"
                + " WHERE t.TABSCHEMA = ? AND t.TABNAME = ?";
        try {
            return jdbcOperations.queryForObject(sql, new Object[] {schema, tableName}, (rs, rowNum) -> {
                DBTableStats stats = new DBTableStats();
                long rowCount = rs.getLong("ROW_COUNT");
                stats.setRowCount(rowCount >= 0 ? rowCount : null);
                long dataSize = rs.getLong("DATA_SIZE_IN_BYTES");
                stats.setDataSizeInBytes(dataSize >= 0 ? dataSize : null);
                return stats;
            });
        } catch (Exception e) {
            log.debug("Failed to get DB2 table stats for {}.{}, returning empty stats", schema, tableName, e);
            return new DBTableStats();
        }
    }

    @Override
    public List<DBSession> listAllSessions() {
        String sql = "SELECT APPLICATION_HANDLE AS ID,"
                + " AUTHID AS USERNAME,"
                + " MEMBER AS HOST,"
                + " APPLICATION_NAME AS COMMAND,"
                + " 'CONNECTED' AS STATUS"
                + " FROM TABLE(MON_GET_CONNECTION(CAST(NULL AS BIGINT), -1))";
        try {
            return jdbcOperations.query(sql, (rs, rowNum) -> {
                DBSession session = new DBSession();
                session.setId(String.valueOf(rs.getLong("ID")));
                session.setUsername(rs.getString("USERNAME"));
                session.setHost(String.valueOf(rs.getInt("HOST")));
                session.setCommand(rs.getString("COMMAND"));
                session.setState(rs.getString("STATUS"));
                session.setExecuteTime(0);
                return session;
            });
        } catch (Exception e) {
            log.warn("Failed to list DB2 sessions via MON_GET_CONNECTION, trying SYSIBMADM fallback", e);
            return listAllSessionsFallback();
        }
    }

    /**
     * Fallback for environments where MON_GET_CONNECTION is not available. Uses SYSIBMADM.APPLICATIONS
     * which is available on most DB2 versions.
     */
    private List<DBSession> listAllSessionsFallback() {
        String sql = "SELECT AGENT_ID AS ID,"
                + " AUTHID AS USERNAME,"
                + " CLIENT_NNAME AS HOST,"
                + " APPL_NAME AS COMMAND,"
                + " APPL_STATUS AS STATUS"
                + " FROM SYSIBMADM.APPLICATIONS";
        try {
            return jdbcOperations.query(sql, (rs, rowNum) -> {
                DBSession session = new DBSession();
                session.setId(String.valueOf(rs.getLong("ID")));
                session.setUsername(rs.getString("USERNAME"));
                String host = rs.getString("HOST");
                session.setHost(host != null ? host.trim() : "");
                session.setCommand(rs.getString("COMMAND"));
                session.setState(rs.getString("STATUS"));
                session.setExecuteTime(0);
                return session;
            });
        } catch (Exception e) {
            log.warn("Failed to list DB2 sessions via SYSIBMADM.APPLICATIONS fallback", e);
            return java.util.Collections.emptyList();
        }
    }

    @Override
    public DBSession currentSession() {
        String sql = "VALUES APPLICATION_ID()";
        try {
            String appId = jdbcOperations.queryForObject(sql, String.class);
            DBSession session = new DBSession();
            session.setId(appId != null ? appId.trim() : "");
            session.setUsername("");
            session.setHost("");
            session.setCommand("");
            session.setState("CONNECTED");
            session.setExecuteTime(0);

            // Try to get more details for the current session
            try {
                String detailSql = "SELECT AUTHID, APPLICATION_NAME"
                        + " FROM TABLE(MON_GET_CONNECTION(CAST(NULL AS BIGINT), -1))"
                        + " WHERE APPLICATION_ID = ?";
                jdbcOperations.query(detailSql, new Object[] {appId}, rs -> {
                    session.setUsername(rs.getString("AUTHID"));
                    session.setCommand(rs.getString("APPLICATION_NAME"));
                });
            } catch (Exception ignore) {
                // Best-effort enrichment
            }
            return session;
        } catch (Exception e) {
            log.warn("Failed to get current DB2 session", e);
            return DBSession.unknown();
        }
    }
}
