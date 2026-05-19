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

/**
 * DB2 stats accessor implementation (B-08 / B-S4).
 *
 * <p>
 * {@link #listAllSessions()} 走 {@code SYSIBMADM.MON_GET_CONNECTION} 表函数；
 * {@link #getTableStats(String, String)} 走 {@code SYSCAT.TABLES} 的 CARD / NPAGES 字段 （详见
 * docs/spec/design.md §7.2）。
 *
 * @since ODC_release_4.3.4 (Issue dms-ee#839)
 */
public class Db2StatsAccessor implements DBStatsAccessor {

    protected final JdbcOperations jdbcOperations;

    public Db2StatsAccessor(@NonNull JdbcOperations jdbcOperations) {
        this.jdbcOperations = jdbcOperations;
    }

    @Override
    public DBTableStats getTableStats(@NonNull String schema, @NonNull String tableName) {
        String sql = "SELECT CARD AS rowCount, NPAGES AS pages "
                + "FROM SYSCAT.TABLES WHERE TABSCHEMA = ? AND TABNAME = ?";
        try {
            return jdbcOperations.queryForObject(sql, new Object[] {schema, tableName}, (rs, rowNum) -> {
                DBTableStats stats = new DBTableStats();
                long card = rs.getLong("rowCount");
                stats.setRowCount(card < 0 ? 0L : card);
                // DB2 page size default 4KB; we approximate via NPAGES * 4096 (admin-tunable, best-effort)
                long pages = rs.getLong("pages");
                stats.setDataSizeInBytes(pages < 0 ? 0L : pages * 4096L);
                return stats;
            });
        } catch (Exception e) {
            return new DBTableStats();
        }
    }

    @Override
    public List<DBSession> listAllSessions() {
        // SYSIBMADM.MON_GET_CONNECTION(NULL,-2) 返回当前数据库所有活动连接
        String sql = "SELECT APPLICATION_HANDLE AS id, "
                + "SESSION_AUTH_ID AS username, "
                + "CLIENT_HOSTNAME AS host, "
                + "APPLICATION_NAME AS command, "
                + "APPL_STATUS AS state, "
                + "TOTAL_RQST_TIME AS executeTime "
                + "FROM TABLE(SYSIBMADM.MON_GET_CONNECTION(NULL,-2))";
        return jdbcOperations.query(sql, (rs, rowNum) -> {
            DBSession session = new DBSession();
            session.setId(String.valueOf(rs.getLong("id")));
            session.setUsername(rs.getString("username"));
            session.setHost(rs.getString("host"));
            session.setCommand(rs.getString("command"));
            session.setState(rs.getString("state"));
            long executeTimeMs = rs.getLong("executeTime");
            session.setExecuteTime((int) Math.max(0, executeTimeMs / 1000));
            return session;
        });
    }

    @Override
    public DBSession currentSession() {
        // VALUES APPLICATION_ID() 返回当前会话 application id；用 MON_GET_CONNECTION 当前句柄筛选
        String sql = "SELECT APPLICATION_HANDLE AS id, "
                + "SESSION_AUTH_ID AS username, "
                + "CLIENT_HOSTNAME AS host, "
                + "APPLICATION_NAME AS command, "
                + "APPL_STATUS AS state, "
                + "TOTAL_RQST_TIME AS executeTime "
                + "FROM TABLE(SYSIBMADM.MON_GET_CONNECTION(NULL,-2)) "
                + "WHERE APPLICATION_HANDLE = (SELECT APPLICATION_HANDLE FROM "
                + "TABLE(MON_GET_CONNECTION(CONNECTION_HANDLE(),-2)) FETCH FIRST 1 ROWS ONLY)";
        try {
            return jdbcOperations.queryForObject(sql, (rs, rowNum) -> {
                DBSession session = new DBSession();
                session.setId(String.valueOf(rs.getLong("id")));
                session.setUsername(rs.getString("username"));
                session.setHost(rs.getString("host"));
                session.setCommand(rs.getString("command"));
                session.setState(rs.getString("state"));
                long executeTimeMs = rs.getLong("executeTime");
                session.setExecuteTime((int) Math.max(0, executeTimeMs / 1000));
                return session;
            });
        } catch (Exception e) {
            return new DBSession();
        }
    }

}
