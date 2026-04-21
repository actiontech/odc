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
package com.oceanbase.tools.dbbrowser.stats.dm;

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
 * DM (DaMeng) database stats accessor. Uses DM-specific system views: V$SESSIONS for session info,
 * DBA_TABLES for table stats.
 */
public class DmStatsAccessor implements DBStatsAccessor {

    protected final JdbcOperations jdbcOperations;

    public DmStatsAccessor(@NonNull JdbcOperations jdbcOperations) {
        this.jdbcOperations = jdbcOperations;
    }

    @Override
    public DBTableStats getTableStats(@NonNull String schema, @NonNull String tableName) {
        String sql = "SELECT NUM_ROWS FROM SYS.ALL_TABLES WHERE OWNER = ? AND TABLE_NAME = ?";
        try {
            DBTableStats stats = new DBTableStats();
            Long rowCount = jdbcOperations.query(sql, new Object[] {schema, tableName}, rs -> {
                if (!rs.next()) {
                    return null;
                }
                return rs.getLong("NUM_ROWS");
            });
            stats.setRowCount(rowCount);
            return stats;
        } catch (Exception e) {
            return new DBTableStats();
        }
    }

    @Override
    public List<DBSession> listAllSessions() {
        String sql = "SELECT "
                + "SESS_ID AS ID, "
                + "USER_NAME AS USERNAME, "
                + "CURR_SCH AS DATABASE_NAME, "
                + "STATE, "
                + "CLNT_IP AS HOST, "
                + "SQL_TEXT AS LATEST_QUERIES, "
                + "LAST_RECV_TIME AS EXECUTE_TIME "
                + "FROM V$SESSIONS";
        return jdbcOperations.query(sql, new DmDBSessionRowMapper());
    }

    @Override
    public DBSession currentSession() {
        String sql = "SELECT "
                + "SESS_ID AS ID, "
                + "USER_NAME AS USERNAME, "
                + "CURR_SCH AS DATABASE_NAME, "
                + "STATE, "
                + "CLNT_IP AS HOST, "
                + "SQL_TEXT AS LATEST_QUERIES, "
                + "LAST_RECV_TIME AS EXECUTE_TIME "
                + "FROM V$SESSIONS WHERE SESS_ID = SESSID()";
        return jdbcOperations.queryForObject(sql, new DmDBSessionRowMapper());
    }

    /**
     * Custom RowMapper for DM DBSession
     */
    private static class DmDBSessionRowMapper implements RowMapper<DBSession> {
        @Override
        public DBSession mapRow(ResultSet rs, int rowNum) throws SQLException {
            DBSession session = new DBSession();
            session.setId(String.valueOf(rs.getLong("ID")));
            session.setUsername(rs.getString("USERNAME"));
            session.setDatabaseName(rs.getString("DATABASE_NAME"));
            session.setState(rs.getString("STATE"));
            session.setHost(rs.getString("HOST"));
            session.setLatestQueries(rs.getString("LATEST_QUERIES"));

            Timestamp lastRecvTime = rs.getTimestamp("EXECUTE_TIME");
            if (lastRecvTime != null) {
                long seconds = (System.currentTimeMillis() - lastRecvTime.getTime()) / 1000;
                session.setExecuteTime((int) Math.max(0, seconds));
            } else {
                session.setExecuteTime(0);
            }
            return session;
        }
    }
}
