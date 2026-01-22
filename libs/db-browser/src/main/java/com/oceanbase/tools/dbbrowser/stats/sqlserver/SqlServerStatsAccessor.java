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
package com.oceanbase.tools.dbbrowser.stats.sqlserver;

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
 * @author yizhou.xw
 * @date 2024/12
 * @since ODC_release_4.3.4
 */
public class SqlServerStatsAccessor implements DBStatsAccessor {

    protected final JdbcOperations jdbcOperations;

    public SqlServerStatsAccessor(@NonNull JdbcOperations jdbcOperations) {
        this.jdbcOperations = jdbcOperations;
    }

    @Override
    public DBTableStats getTableStats(@NonNull String schema, @NonNull String tableName) {
        String sql = "SELECT SUM(s.used_page_count) * 8 * 1024 AS dataSizeInBytes, SUM(s.row_count) AS rowCount "
                + "FROM sys.dm_db_partition_stats s "
                + "JOIN sys.tables t ON s.object_id = t.object_id "
                + "JOIN sys.schemas sch ON t.schema_id = sch.schema_id "
                + "WHERE sch.name = ? AND t.name = ? "
                + "GROUP BY t.object_id";
        try {
            return jdbcOperations.queryForObject(sql, new Object[] {schema, tableName}, (rs, rowNum) -> {
                DBTableStats stats = new DBTableStats();
                stats.setDataSizeInBytes(rs.getLong("dataSizeInBytes"));
                stats.setRowCount(rs.getLong("rowCount"));
                return stats;
            });
        } catch (Exception e) {
            return new DBTableStats();
        }
    }

    @Override
    public List<DBSession> listAllSessions() {
        String sql = "SELECT session_id AS id, login_name AS username, status, host_name AS host, "
                + "program_name AS command, last_request_end_time AS executeTime "
                + "FROM sys.dm_exec_sessions";
        return jdbcOperations.query(sql, new SqlServerDBSessionRowMapper());
    }

    @Override
    public DBSession currentSession() {
        String sql = "SELECT session_id AS id, login_name AS username, status, host_name AS host, "
                + "program_name AS command, last_request_end_time AS executeTime "
                + "FROM sys.dm_exec_sessions WHERE session_id = @@SPID";
        return jdbcOperations.queryForObject(sql, new SqlServerDBSessionRowMapper());
    }

    /**
     * Custom RowMapper for SQL Server DBSession to handle datetime to Integer conversion
     */
    private static class SqlServerDBSessionRowMapper implements RowMapper<DBSession> {
        @Override
        public DBSession mapRow(ResultSet rs, int rowNum) throws SQLException {
            DBSession session = new DBSession();
            session.setId(String.valueOf(rs.getInt("id")));
            session.setUsername(rs.getString("username"));
            session.setHost(rs.getString("host"));
            session.setCommand(rs.getString("command"));
            session.setState(rs.getString("status"));

            // Convert datetime to Integer (seconds since last request end time)
            Timestamp lastRequestEndTime = rs.getTimestamp("executeTime");
            if (lastRequestEndTime != null) {
                long seconds = (System.currentTimeMillis() - lastRequestEndTime.getTime()) / 1000;
                session.setExecuteTime((int) Math.max(0, seconds));
            } else {
                session.setExecuteTime(0);
            }

            return session;
        }
    }

}
