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
 * {@link #listAllSessions()} 走 {@code TABLE(MON_GET_CONNECTION(NULL,-2))} 表函数（schema = SYSPROC，按
 * DB2 表函数解析规则可不带限定符；显式写 {@code SYSIBMADM.MON_GET_CONNECTION} 会触发 SQLCODE=-440 / SQLSTATE=42884
 * FUNCTION 找不到，因为 {@code SYSIBMADM} 里只有 MON_* 视图而不存在该表函数）；{@link #currentSession()} 走
 * {@code MON_GET_APPLICATION_HANDLE()} 标量函数定位当前句柄（{@code CONNECTION_HANDLE()} 不存在）；
 * {@link #getTableStats(String, String)} 走 {@code SYSCAT.TABLES} 的 CARD / NPAGES 字段（详见
 * docs/spec/design.md §7.2）。
 *
 * <p>
 * 字段映射：{@code APPL_STATUS} 在 MON_GET_CONNECTION 不存在（SQLCODE=-206），用
 * {@code WORKLOAD_OCCURRENCE_STATE} 作为 session.state；{@code CLIENT_HOSTNAME} 在 DB2 默认空，COALESCE 回退到
 * {@code CLIENT_IPADDR}。
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
        // MON_GET_CONNECTION 是 SYSPROC 表函数，按 DB2 解析规则可不带限定符；显式写 SYSIBMADM.* 会触发
        // SQLCODE=-440（SYSIBMADM 里只有 MON_* 视图，不存在该表函数）。
        // 字段：APPL_STATUS 在 MON_GET_CONNECTION 不存在，用 WORKLOAD_OCCURRENCE_STATE 表示会话状态；
        // CLIENT_HOSTNAME 在 DB2 默认空，回退到 CLIENT_IPADDR。
        String sql = "SELECT APPLICATION_HANDLE AS id, "
                + "SESSION_AUTH_ID AS username, "
                + "COALESCE(CLIENT_HOSTNAME, CLIENT_IPADDR) AS host, "
                + "APPLICATION_NAME AS command, "
                + "WORKLOAD_OCCURRENCE_STATE AS state, "
                + "TOTAL_RQST_TIME AS executeTime "
                + "FROM TABLE(MON_GET_CONNECTION(NULL,-2))";
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
        // 用 MON_GET_APPLICATION_HANDLE() 标量函数取当前会话句柄，再调 MON_GET_CONNECTION 拿元数据。
        // 之前用的 CONNECTION_HANDLE() 在 DB2 v11.5 不存在（SQLCODE=-440），SYSIBMADM 限定符同样错误。
        String sql = "SELECT APPLICATION_HANDLE AS id, "
                + "SESSION_AUTH_ID AS username, "
                + "COALESCE(CLIENT_HOSTNAME, CLIENT_IPADDR) AS host, "
                + "APPLICATION_NAME AS command, "
                + "WORKLOAD_OCCURRENCE_STATE AS state, "
                + "TOTAL_RQST_TIME AS executeTime "
                + "FROM TABLE(MON_GET_CONNECTION(MON_GET_APPLICATION_HANDLE(),-2)) "
                + "FETCH FIRST 1 ROWS ONLY";
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
