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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;

import com.oceanbase.tools.dbbrowser.model.DBSession;
import com.oceanbase.tools.dbbrowser.model.DBTableStats;

/**
 * Mock-only unit tests for {@link Db2StatsAccessor}.
 *
 * <p>
 * 遵守 plan.md §3.2.2 边界：禁止真实 JDBC 连接 / 禁止 H2 容器。仅验证 RowMapper 映射逻辑与字段映射 （design.md §7.2 / §10.1）。
 *
 * @since ODC_release_4.3.4 (Issue dms-ee#839)
 */
public class Db2StatsAccessorTest {

    private JdbcOperations jdbcOperations;
    private Db2StatsAccessor accessor;

    @Before
    public void setUp() {
        this.jdbcOperations = mock(JdbcOperations.class);
        this.accessor = new Db2StatsAccessor(jdbcOperations);
    }

    // --------------------------- listAllSessions ---------------------------

    /**
     * Case listAllSessions_sqlShape: fix-M（dms-ee#839）回归——验证 SQL 文本不再带 {@code SYSIBMADM.} 限定符、不再用 DB2
     * 不存在的 {@code APPL_STATUS} 列，并改用 {@code WORKLOAD_OCCURRENCE_STATE} + COALESCE CLIENT_HOSTNAME /
     * CLIENT_IPADDR；防止未来 refactor 静默回归到 SQLCODE=-440 / -206。
     */
    @Test
    public void listAllSessions_sqlShape() throws SQLException {
        mockQueryWithoutArgs(Collections.emptyList());
        accessor.listAllSessions();
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(jdbcOperations).query(sqlCaptor.capture(), any(RowMapper.class));
        String sql = sqlCaptor.getValue();
        Assert.assertFalse("listAllSessions SQL must NOT contain SYSIBMADM.MON_GET_CONNECTION (SQLCODE=-440)",
                sql.contains("SYSIBMADM.MON_GET_CONNECTION"));
        Assert.assertFalse(
                "listAllSessions SQL must NOT reference APPL_STATUS (SQLCODE=-206, column not in MON_GET_CONNECTION)",
                sql.contains("APPL_STATUS"));
        Assert.assertTrue("listAllSessions SQL must use TABLE(MON_GET_CONNECTION(NULL,-2))",
                sql.contains("TABLE(MON_GET_CONNECTION(NULL,-2))"));
        Assert.assertTrue("listAllSessions SQL must select WORKLOAD_OCCURRENCE_STATE AS state",
                sql.contains("WORKLOAD_OCCURRENCE_STATE AS state"));
        Assert.assertTrue("listAllSessions SQL must COALESCE host across CLIENT_HOSTNAME / CLIENT_IPADDR",
                sql.contains("COALESCE(CLIENT_HOSTNAME, CLIENT_IPADDR) AS host"));
    }

    /**
     * Case listAllSessions_mapsMonGetConnectionRows: 模拟 MON_GET_CONNECTION 返回 3 行， 期望 size==3、字段
     * id/username/host/command/state 被正确映射、executeTime 由 ms 换算为秒。
     */
    @Test
    public void listAllSessions_mapsMonGetConnectionRows() throws SQLException {
        Map<String, Object> r1 = sessionRow(101L, "DB2INST1", "10.0.0.1", "ODC", "UOWEXEC", 2500L);
        Map<String, Object> r2 = sessionRow(102L, "APPUSER", "10.0.0.2", "JDBC", "UOWWAIT", 0L);
        Map<String, Object> r3 = sessionRow(103L, "DB2INST1", "10.0.0.3", "ADMIN", "CONNECTED", 60000L);
        mockQueryWithoutArgs(Arrays.asList(r1, r2, r3));

        List<DBSession> sessions = accessor.listAllSessions();

        Assert.assertEquals(3, sessions.size());
        Assert.assertEquals("101", sessions.get(0).getId());
        Assert.assertEquals("DB2INST1", sessions.get(0).getUsername());
        Assert.assertEquals("10.0.0.1", sessions.get(0).getHost());
        Assert.assertEquals("ODC", sessions.get(0).getCommand());
        Assert.assertEquals("UOWEXEC", sessions.get(0).getState());
        // 2500ms / 1000 = 2s
        Assert.assertEquals(Integer.valueOf(2), sessions.get(0).getExecuteTime());
        // 0ms = 0s
        Assert.assertEquals(Integer.valueOf(0), sessions.get(1).getExecuteTime());
        // 60000ms / 1000 = 60s
        Assert.assertEquals(Integer.valueOf(60), sessions.get(2).getExecuteTime());
    }

    /**
     * Case listAllSessions_negativeExecuteTimeClampedToZero: 模拟 executeTime 为负数（边界），期望 clamp 到 0。
     */
    @Test
    public void listAllSessions_negativeExecuteTimeClampedToZero() throws SQLException {
        Map<String, Object> row = sessionRow(200L, "DB2INST1", "10.0.0.4", "ODC", "UOWEXEC", -1000L);
        mockQueryWithoutArgs(Collections.singletonList(row));

        List<DBSession> sessions = accessor.listAllSessions();
        Assert.assertEquals(1, sessions.size());
        Assert.assertEquals(Integer.valueOf(0), sessions.get(0).getExecuteTime());
    }

    /**
     * Case listAllSessions_emptyResult: 模拟空结果，期望返回空列表（不抛异常）。
     */
    @Test
    public void listAllSessions_emptyResult() throws SQLException {
        mockQueryWithoutArgs(Collections.emptyList());
        List<DBSession> sessions = accessor.listAllSessions();
        Assert.assertNotNull(sessions);
        Assert.assertTrue(sessions.isEmpty());
    }

    // --------------------------- currentSession ---------------------------

    /**
     * Case currentSession_sqlShape: fix-M 回归——验证 currentSession SQL 不带 {@code SYSIBMADM.} 限定符、不再用
     * {@code CONNECTION_HANDLE()}（在 DB2 v11.5 不存在，SQLCODE=-440），改用 {@code MON_GET_APPLICATION_HANDLE()}
     * 标量函数定位当前句柄；不引用 {@code APPL_STATUS}。
     */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void currentSession_sqlShape() throws SQLException {
        when(jdbcOperations.queryForObject(anyString(), any(RowMapper.class))).thenReturn(new DBSession());
        accessor.currentSession();
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(jdbcOperations).queryForObject(sqlCaptor.capture(), any(RowMapper.class));
        String sql = sqlCaptor.getValue();
        Assert.assertFalse("currentSession SQL must NOT contain SYSIBMADM.MON_GET_CONNECTION",
                sql.contains("SYSIBMADM.MON_GET_CONNECTION"));
        Assert.assertFalse(
                "currentSession SQL must NOT reference CONNECTION_HANDLE() (function not exists, SQLCODE=-440)",
                sql.contains("CONNECTION_HANDLE()"));
        Assert.assertFalse("currentSession SQL must NOT reference APPL_STATUS",
                sql.contains("APPL_STATUS"));
        Assert.assertTrue("currentSession SQL must use MON_GET_APPLICATION_HANDLE() for current handle",
                sql.contains("MON_GET_APPLICATION_HANDLE()"));
        Assert.assertTrue("currentSession SQL must use TABLE(MON_GET_CONNECTION(MON_GET_APPLICATION_HANDLE(),-2))",
                sql.contains("TABLE(MON_GET_CONNECTION(MON_GET_APPLICATION_HANDLE(),-2))"));
        Assert.assertTrue("currentSession SQL must limit to 1 row",
                sql.contains("FETCH FIRST 1 ROWS ONLY"));
    }

    /**
     * Case currentSession_mapsRow: 模拟 1 行返回，验证 RowMapper 字段映射与 ms→s 换算。
     */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void currentSession_mapsRow() throws SQLException {
        Map<String, Object> row = sessionRow(909L, "DB2INST1", "10.0.0.9", "ODC", "UOWEXEC", 3500L);
        when(jdbcOperations.queryForObject(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(1);
            ResultSet rs = mockResultSet(row);
            return mapper.mapRow(rs, 0);
        });

        DBSession s = accessor.currentSession();
        Assert.assertNotNull(s);
        Assert.assertEquals("909", s.getId());
        Assert.assertEquals("DB2INST1", s.getUsername());
        Assert.assertEquals("10.0.0.9", s.getHost());
        Assert.assertEquals("ODC", s.getCommand());
        Assert.assertEquals("UOWEXEC", s.getState());
        // 3500ms / 1000 = 3s
        Assert.assertEquals(Integer.valueOf(3), s.getExecuteTime());
    }

    /**
     * Case currentSession_returnsEmptyOnException: 任何 JDBC 异常按 SqlServerStatsAccessor 模式返回空
     * DBSession，不冒泡。
     */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void currentSession_returnsEmptyOnException() {
        when(jdbcOperations.queryForObject(anyString(), any(RowMapper.class)))
                .thenThrow(new RuntimeException("DB2 SQL error"));
        DBSession s = accessor.currentSession();
        Assert.assertNotNull(s);
        Assert.assertNull(s.getId());
        Assert.assertNull(s.getUsername());
    }

    // --------------------------- getTableStats ---------------------------

    /**
     * Case getTableStats_mapCases: card / pages 三组场景（典型 / 空表 / 未收集统计的负数），期望 rowCount 与 dataSizeInBytes
     * 按 NPAGES*4096 映射；负数 clamp 到 0。
     */
    @Test
    public void getTableStats_mapCases() {
        Map<String, long[]> cases = new LinkedHashMap<>();
        // value = [card, pages, expectedRowCount, expectedSizeInBytes]
        cases.put("typical_table", new long[] {1000L, 50L, 1000L, 50L * 4096L});
        cases.put("empty_table", new long[] {0L, 0L, 0L, 0L});
        cases.put("uncollected_stats_negative_card_and_pages", new long[] {-1L, -1L, 0L, 0L});

        for (Map.Entry<String, long[]> entry : cases.entrySet()) {
            long card = entry.getValue()[0];
            long pages = entry.getValue()[1];
            long expectedRow = entry.getValue()[2];
            long expectedSize = entry.getValue()[3];

            JdbcOperations localJdbc = mock(JdbcOperations.class);
            Db2StatsAccessor localAccessor = new Db2StatsAccessor(localJdbc);

            when(localJdbc.queryForObject(anyString(), any(Object[].class), any(RowMapper.class)))
                    .thenAnswer(invocation -> {
                        @SuppressWarnings("unchecked")
                        RowMapper<DBTableStats> mapper = invocation.getArgument(2);
                        ResultSet rs = mock(ResultSet.class);
                        when(rs.getLong("rowCount")).thenReturn(card);
                        when(rs.getLong("pages")).thenReturn(pages);
                        return mapper.mapRow(rs, 0);
                    });

            DBTableStats stats = localAccessor.getTableStats("DB2INST1", "T1");

            Assert.assertEquals("case=" + entry.getKey() + ", rowCount",
                    Long.valueOf(expectedRow), stats.getRowCount());
            Assert.assertEquals("case=" + entry.getKey() + ", dataSizeInBytes",
                    Long.valueOf(expectedSize), stats.getDataSizeInBytes());
        }
    }

    /**
     * Case getTableStats_returnsEmptyOnException: JDBC 抛任何异常时不冒泡，按 SqlServerStatsAccessor 模式返回空
     * DBTableStats（rowCount / dataSizeInBytes 都为 null）。
     */
    @Test
    public void getTableStats_returnsEmptyOnException() {
        when(jdbcOperations.queryForObject(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenThrow(new RuntimeException("DB2 SQL error"));
        DBTableStats stats = accessor.getTableStats("DB2INST1", "T1");
        Assert.assertNotNull(stats);
        Assert.assertNull(stats.getRowCount());
        Assert.assertNull(stats.getDataSizeInBytes());
    }

    // --------------------------- helpers ---------------------------

    private Map<String, Object> sessionRow(long id, String username, String host, String command,
            String state, long executeTimeMs) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("username", username);
        row.put("host", host);
        row.put("command", command);
        row.put("state", state);
        row.put("executeTime", executeTimeMs);
        return row;
    }

    /**
     * Stub {@code query(sql, rowMapper)}（无 vararg 入参）映射给定行集。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void mockQueryWithoutArgs(List<Map<String, Object>> rows) throws SQLException {
        when(jdbcOperations.query(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(1);
            List<Object> out = new ArrayList<>(rows.size());
            for (int i = 0; i < rows.size(); i++) {
                ResultSet rs = mockResultSet(rows.get(i));
                out.add(mapper.mapRow(rs, i));
            }
            return out;
        });
    }

    private ResultSet mockResultSet(Map<String, Object> row) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        for (Map.Entry<String, Object> e : row.entrySet()) {
            Object v = e.getValue();
            String col = e.getKey();
            when(rs.getString(col)).thenReturn(v == null ? null : v.toString());
            when(rs.getInt(col)).thenReturn(v instanceof Number ? ((Number) v).intValue() : 0);
            when(rs.getLong(col)).thenReturn(v instanceof Number ? ((Number) v).longValue() : 0L);
        }
        return rs;
    }
}
