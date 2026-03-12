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
package com.oceanbase.tools.dbbrowser.stats.postgres;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;

import com.oceanbase.tools.dbbrowser.model.DBSession;
import com.oceanbase.tools.dbbrowser.model.DBSession.DBTransState;
import com.oceanbase.tools.dbbrowser.model.DBTableStats;
import com.oceanbase.tools.dbbrowser.stats.DBStatsAccessor;
import com.oceanbase.tools.dbbrowser.util.PostgresSqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;

import lombok.NonNull;

/**
 * PostgreSQL implementation of {@link DBStatsAccessor}.
 *
 * <p>
 * Provides table statistics and session information using PostgreSQL system catalogs:
 * <ul>
 * <li>Table stats: {@code pg_stat_user_tables}, {@code pg_total_relation_size()}</li>
 * <li>Sessions: {@code pg_stat_activity}</li>
 * </ul>
 * </p>
 *
 * @author odc
 * @since ODC_release_4.3.4
 */
public class PostgresStatsAccessor implements DBStatsAccessor {

    protected final JdbcOperations jdbcOperations;

    /**
     * Query for listing all sessions from pg_stat_activity. Maps pg_stat_activity columns to DBSession
     * fields.
     */
    private static final String QUERY_ALL_SESSIONS =
            "SELECT pid, usename, datname, state, query, client_addr, "
                    + "EXTRACT(EPOCH FROM (now() - query_start))::bigint AS execute_time "
                    + "FROM pg_stat_activity";

    /**
     * Query for current session using pg_backend_pid().
     */
    private static final String QUERY_CURRENT_SESSION =
            QUERY_ALL_SESSIONS + " WHERE pid = pg_backend_pid()";

    public PostgresStatsAccessor(@NonNull JdbcOperations jdbcOperations) {
        this.jdbcOperations = jdbcOperations;
    }

    @Override
    public DBTableStats getTableStats(@NonNull String schema, @NonNull String tableName) {
        String sql = sqlBuilder()
                .append("SELECT COALESCE(s.n_live_tup, c.reltuples::bigint) AS row_count, ")
                .append("pg_total_relation_size(c.oid) AS data_size_in_bytes ")
                .append("FROM pg_class c ")
                .append("LEFT JOIN pg_stat_user_tables s ON c.oid = s.relid ")
                .append("JOIN pg_namespace n ON c.relnamespace = n.oid ")
                .append("WHERE n.nspname = ").value(schema)
                .append(" AND c.relname = ").value(tableName)
                .toString();

        DBTableStats stats = new DBTableStats();
        jdbcOperations.query(sql, rs -> {
            stats.setRowCount(parseLongSafely(rs, "row_count"));
            stats.setDataSizeInBytes(parseLongSafely(rs, "data_size_in_bytes"));
        });
        return stats;
    }

    @Override
    public List<DBSession> listAllSessions() {
        return jdbcOperations.query(QUERY_ALL_SESSIONS, new PostgresDBSessionRowMapper());
    }

    @Override
    public DBSession currentSession() {
        List<DBSession> sessions = jdbcOperations.query(QUERY_CURRENT_SESSION, new PostgresDBSessionRowMapper());
        return CollectionUtils.isEmpty(sessions) ? DBSession.unknown() : sessions.get(0);
    }

    protected SqlBuilder sqlBuilder() {
        return new PostgresSqlBuilder();
    }

    /**
     * Parse Long value safely, handling null values.
     */
    private Long parseLongSafely(ResultSet rs, String columnName) throws SQLException {
        Object value = rs.getObject(columnName);
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * Custom RowMapper for PostgreSQL DBSession.
     *
     * <p>
     * Maps pg_stat_activity columns to DBSession fields:
     * <ul>
     * <li>pid -> id (backend process ID)</li>
     * <li>usename -> username</li>
     * <li>datname -> databaseName</li>
     * <li>state -> state</li>
     * <li>query -> latestQueries</li>
     * <li>client_addr -> host</li>
     * <li>execute_time (computed) -> executeTime</li>
     * </ul>
     * </p>
     *
     * <p>
     * Transaction state mapping:
     * <ul>
     * <li>idle -> IDLE</li>
     * <li>active -> ACTIVE</li>
     * <li>idle in transaction -> ACTIVE</li>
     * <li>others -> UNKNOWN</li>
     * </ul>
     * </p>
     */
    private static class PostgresDBSessionRowMapper implements RowMapper<DBSession> {

        @Override
        public DBSession mapRow(ResultSet rs, int rowNum) throws SQLException {
            DBSession session = new DBSession();
            session.setId(String.valueOf(rs.getLong("pid")));
            session.setUsername(rs.getString("usename"));
            session.setDatabaseName(rs.getString("datname"));
            session.setState(rs.getString("state"));
            session.setLatestQueries(rs.getString("query"));

            // Handle client_addr - PostgreSQL returns java.sql.SQLException for null
            try {
                Object clientAddr = rs.getObject("client_addr");
                if (clientAddr != null) {
                    session.setHost(clientAddr.toString());
                }
            } catch (SQLException e) {
                // client_addr might be null, ignore
                session.setHost(null);
            }

            // Execute time in seconds
            Long executeTime = (Long) rs.getObject("execute_time");
            session.setExecuteTime(executeTime != null ? executeTime.intValue() : 0);

            // Map PostgreSQL state to DBTransState
            session.setTransState(mapTransState(session.getState()));

            // PostgreSQL doesn't have these concepts
            session.setTransId(null);
            session.setSqlId(null);
            session.setTraceId(null);
            session.setActiveQueries(null);
            session.setSvrIp(null);
            session.setProxyHost(null);

            // Command is not directly available, could use query type but set null for simplicity
            session.setCommand(null);

            return session;
        }

        /**
         * Map PostgreSQL session state to DBTransState.
         *
         * @param state PostgreSQL session state from pg_stat_activity
         * @return corresponding DBTransState
         */
        private DBTransState mapTransState(String state) {
            if (state == null) {
                return DBTransState.UNKNOWN;
            }
            switch (state.toLowerCase()) {
                case "idle":
                    return DBTransState.IDLE;
                case "active":
                case "idle in transaction":
                case "idle in transaction (aborted)":
                    return DBTransState.ACTIVE;
                default:
                    return DBTransState.UNKNOWN;
            }
        }
    }
}
