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
package com.oceanbase.tools.dbbrowser.stats.hana;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;

import com.oceanbase.tools.dbbrowser.model.DBSession;
import com.oceanbase.tools.dbbrowser.model.DBTableStats;
import com.oceanbase.tools.dbbrowser.stats.DBStatsAccessor;

import lombok.NonNull;

/**
 * SAP HANA database stats accessor. Uses HANA-specific system views:
 * SYS.M_TABLE_PERSISTENCE_STATISTICS for table stats, SYS.M_CONNECTIONS for session info.
 */
public class HanaStatsAccessor implements DBStatsAccessor {

    protected final JdbcOperations jdbcOperations;

    public HanaStatsAccessor(@NonNull JdbcOperations jdbcOperations) {
        this.jdbcOperations = jdbcOperations;
    }

    @Override
    public DBTableStats getTableStats(@NonNull String schema, @NonNull String tableName) {
        String sql = "SELECT TABLE_SIZE, RECORD_COUNT"
                + " FROM SYS.M_TABLE_PERSISTENCE_STATISTICS"
                + " WHERE SCHEMA_NAME = ? AND TABLE_NAME = ?";
        try {
            return jdbcOperations.queryForObject(sql, new Object[] {schema, tableName}, (rs, rowNum) -> {
                DBTableStats stats = new DBTableStats();
                stats.setDataSizeInBytes(rs.getLong("TABLE_SIZE"));
                stats.setRowCount(rs.getLong("RECORD_COUNT"));
                return stats;
            });
        } catch (Exception e) {
            return new DBTableStats();
        }
    }

    @Override
    public List<DBSession> listAllSessions() {
        String sql = "SELECT CONNECTION_ID, CONNECTION_STATUS, USER_NAME,"
                + " CURRENT_SCHEMA_NAME, CLIENT_HOST, CURRENT_STATEMENT_ID,"
                + " START_TIME"
                + " FROM SYS.M_CONNECTIONS"
                + " WHERE CONNECTION_TYPE = 'Remote'";
        return jdbcOperations.query(sql, new HanaDBSessionRowMapper());
    }

    @Override
    public DBSession currentSession() {
        String sql = "SELECT CONNECTION_ID, CONNECTION_STATUS, USER_NAME,"
                + " CURRENT_SCHEMA_NAME, CLIENT_HOST, CURRENT_STATEMENT_ID,"
                + " START_TIME"
                + " FROM SYS.M_CONNECTIONS"
                + " WHERE OWN = 'TRUE'";
        return jdbcOperations.queryForObject(sql, new HanaDBSessionRowMapper());
    }

    /**
     * Custom RowMapper for HANA DBSession
     */
    private static class HanaDBSessionRowMapper implements RowMapper<DBSession> {
        @Override
        public DBSession mapRow(ResultSet rs, int rowNum) throws SQLException {
            DBSession session = new DBSession();
            session.setId(String.valueOf(rs.getLong("CONNECTION_ID")));
            session.setUsername(rs.getString("USER_NAME"));
            session.setDatabaseName(rs.getString("CURRENT_SCHEMA_NAME"));
            session.setState(rs.getString("CONNECTION_STATUS"));
            session.setHost(rs.getString("CLIENT_HOST"));
            session.setCommand(rs.getString("CURRENT_STATEMENT_ID"));

            Timestamp startTime = rs.getTimestamp("START_TIME");
            if (startTime != null) {
                long seconds = (System.currentTimeMillis() - startTime.getTime()) / 1000;
                session.setExecuteTime((int) Math.max(0, seconds));
            } else {
                session.setExecuteTime(0);
            }

            return session;
        }
    }
}
