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
package com.oceanbase.tools.dbbrowser.stats;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;

import com.oceanbase.tools.dbbrowser.model.DBSession;
import com.oceanbase.tools.dbbrowser.model.DBSession.DBTransState;
import com.oceanbase.tools.dbbrowser.model.DBTableStats;
import com.oceanbase.tools.dbbrowser.stats.postgres.PostgresStatsAccessor;

/**
 * Unit tests for {@link PostgresStatsAccessor}
 *
 * <p>
 * This test uses Mockito to mock JdbcOperations, so it doesn't require a real PostgreSQL database.
 * </p>
 *
 * @author odc
 * @since ODC_release_4.3.4
 */
public class PostgresStatsAccessorTest {

    private JdbcOperations jdbcOperations;
    private PostgresStatsAccessor accessor;

    @Before
    public void setUp() {
        jdbcOperations = mock(JdbcOperations.class);
        accessor = new PostgresStatsAccessor(jdbcOperations);
    }

    // ============== getTableStats Tests ==============

    @Test
    public void getTableStats_Success() throws Exception {
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("row_count", 1000L);
        row.put("data_size_in_bytes", 81920L);
        mockData.add(row);

        ResultSet mockResultSet = createMockResultSet(mockData);

        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            while (mockResultSet.next()) {
                handler.processRow(mockResultSet);
            }
            return null;
        }).when(jdbcOperations).query(anyString(), any(RowCallbackHandler.class));

        DBTableStats stats = accessor.getTableStats("public", "users");

        Assert.assertNotNull(stats);
        Assert.assertEquals(Long.valueOf(1000L), stats.getRowCount());
        Assert.assertEquals(Long.valueOf(81920L), stats.getDataSizeInBytes());
    }

    @Test
    public void getTableStats_NullValues_ReturnsZero() throws Exception {
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("row_count", null);
        row.put("data_size_in_bytes", null);
        mockData.add(row);

        ResultSet mockResultSet = createMockResultSet(mockData);

        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            while (mockResultSet.next()) {
                handler.processRow(mockResultSet);
            }
            return null;
        }).when(jdbcOperations).query(anyString(), any(RowCallbackHandler.class));

        DBTableStats stats = accessor.getTableStats("public", "users");

        Assert.assertNotNull(stats);
        Assert.assertEquals(Long.valueOf(0L), stats.getRowCount());
        Assert.assertEquals(Long.valueOf(0L), stats.getDataSizeInBytes());
    }

    @Test
    public void getTableStats_EmptyResult_ReturnsEmptyStats() throws Exception {
        ResultSet mockResultSet = createMockResultSet(new ArrayList<>());

        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            while (mockResultSet.next()) {
                handler.processRow(mockResultSet);
            }
            return null;
        }).when(jdbcOperations).query(anyString(), any(RowCallbackHandler.class));

        DBTableStats stats = accessor.getTableStats("public", "nonexistent_table");

        Assert.assertNotNull(stats);
        Assert.assertNull(stats.getRowCount());
        Assert.assertNull(stats.getDataSizeInBytes());
    }

    @Test
    public void getTableStats_WithNumericValues() throws Exception {
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        // Test with integer values (PostgreSQL may return different numeric types)
        row.put("row_count", 500);
        row.put("data_size_in_bytes", 40960);
        mockData.add(row);

        ResultSet mockResultSet = createMockResultSet(mockData);

        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            while (mockResultSet.next()) {
                handler.processRow(mockResultSet);
            }
            return null;
        }).when(jdbcOperations).query(anyString(), any(RowCallbackHandler.class));

        DBTableStats stats = accessor.getTableStats("public", "orders");

        Assert.assertNotNull(stats);
        Assert.assertEquals(Long.valueOf(500L), stats.getRowCount());
        Assert.assertEquals(Long.valueOf(40960L), stats.getDataSizeInBytes());
    }

    // ============== listAllSessions Tests ==============

    @Test
    public void listAllSessions_Success() throws Exception {
        List<Map<String, Object>> mockData = new ArrayList<>();

        Map<String, Object> session1 = new HashMap<>();
        session1.put("pid", 12345L);
        session1.put("usename", "postgres");
        session1.put("datname", "testdb");
        session1.put("state", "active");
        session1.put("query", "SELECT * FROM users");
        session1.put("client_addr", "192.168.1.100");
        session1.put("execute_time", 10L);
        mockData.add(session1);

        Map<String, Object> session2 = new HashMap<>();
        session2.put("pid", 12346L);
        session2.put("usename", "admin");
        session2.put("datname", "mydb");
        session2.put("state", "idle");
        session2.put("query", "SELECT 1");
        session2.put("client_addr", "192.168.1.101");
        session2.put("execute_time", 0L);
        mockData.add(session2);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<DBSession> mapper = invocation.getArgument(1);
                    List<DBSession> result = new ArrayList<>();
                    int rowNum = 0;
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        List<DBSession> sessions = accessor.listAllSessions();

        Assert.assertNotNull(sessions);
        Assert.assertEquals(2, sessions.size());

        DBSession s1 = sessions.get(0);
        Assert.assertEquals("12345", s1.getId());
        Assert.assertEquals("postgres", s1.getUsername());
        Assert.assertEquals("testdb", s1.getDatabaseName());
        Assert.assertEquals("active", s1.getState());
        Assert.assertEquals("SELECT * FROM users", s1.getLatestQueries());
        Assert.assertEquals("192.168.1.100", s1.getHost());
        Assert.assertEquals(Integer.valueOf(10), s1.getExecuteTime());
        Assert.assertEquals(DBTransState.ACTIVE, s1.getTransState());

        DBSession s2 = sessions.get(1);
        Assert.assertEquals("12346", s2.getId());
        Assert.assertEquals("idle", s2.getState());
        Assert.assertEquals(DBTransState.IDLE, s2.getTransState());
    }

    @Test
    public void listAllSessions_StateTransitions() throws Exception {
        List<Map<String, Object>> mockData = new ArrayList<>();

        // idle in transaction -> ACTIVE
        Map<String, Object> session1 = new HashMap<>();
        session1.put("pid", 111L);
        session1.put("usename", "user1");
        session1.put("datname", "db1");
        session1.put("state", "idle in transaction");
        session1.put("query", "BEGIN");
        session1.put("client_addr", null);
        session1.put("execute_time", 30L);
        mockData.add(session1);

        // idle in transaction (aborted) -> ACTIVE
        Map<String, Object> session2 = new HashMap<>();
        session2.put("pid", 112L);
        session2.put("usename", "user2");
        session2.put("datname", "db2");
        session2.put("state", "idle in transaction (aborted)");
        session2.put("query", "BEGIN; SELECT 1/0;");
        session2.put("client_addr", null);
        session2.put("execute_time", 60L);
        mockData.add(session2);

        // null state -> UNKNOWN
        Map<String, Object> session3 = new HashMap<>();
        session3.put("pid", 113L);
        session3.put("usename", "user3");
        session3.put("datname", "db3");
        session3.put("state", null);
        session3.put("query", null);
        session3.put("client_addr", null);
        session3.put("execute_time", null);
        mockData.add(session3);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<DBSession> mapper = invocation.getArgument(1);
                    List<DBSession> result = new ArrayList<>();
                    int rowNum = 0;
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        List<DBSession> sessions = accessor.listAllSessions();

        Assert.assertEquals(3, sessions.size());
        Assert.assertEquals(DBTransState.ACTIVE, sessions.get(0).getTransState());
        Assert.assertEquals(DBTransState.ACTIVE, sessions.get(1).getTransState());
        Assert.assertEquals(DBTransState.UNKNOWN, sessions.get(2).getTransState());
    }

    @Test
    public void listAllSessions_EmptyList() throws Exception {
        ResultSet mockResultSet = createMockResultSet(new ArrayList<>());

        when(jdbcOperations.query(anyString(), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<DBSession> mapper = invocation.getArgument(1);
                    List<DBSession> result = new ArrayList<>();
                    int rowNum = 0;
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        List<DBSession> sessions = accessor.listAllSessions();

        Assert.assertNotNull(sessions);
        Assert.assertTrue(sessions.isEmpty());
    }

    // ============== currentSession Tests ==============

    @Test
    public void currentSession_Success() throws Exception {
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> session = new HashMap<>();
        session.put("pid", 99999L);
        session.put("usename", "testuser");
        session.put("datname", "testdb");
        session.put("state", "active");
        session.put("query", "SELECT pg_backend_pid()");
        session.put("client_addr", "127.0.0.1");
        session.put("execute_time", 0L);
        mockData.add(session);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<DBSession> mapper = invocation.getArgument(1);
                    List<DBSession> result = new ArrayList<>();
                    int rowNum = 0;
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        DBSession currentSession = accessor.currentSession();

        Assert.assertNotNull(currentSession);
        Assert.assertEquals("99999", currentSession.getId());
        Assert.assertEquals("testuser", currentSession.getUsername());
        Assert.assertEquals("testdb", currentSession.getDatabaseName());
        Assert.assertEquals("active", currentSession.getState());
        Assert.assertEquals(DBTransState.ACTIVE, currentSession.getTransState());
    }

    @Test
    public void currentSession_NoResult_ReturnsUnknown() throws Exception {
        ResultSet mockResultSet = createMockResultSet(new ArrayList<>());

        when(jdbcOperations.query(anyString(), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<DBSession> mapper = invocation.getArgument(1);
                    List<DBSession> result = new ArrayList<>();
                    int rowNum = 0;
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        DBSession currentSession = accessor.currentSession();

        Assert.assertNotNull(currentSession);
        Assert.assertEquals(DBTransState.UNKNOWN, currentSession.getTransState());
    }

    @Test
    public void currentSession_NullFields() throws Exception {
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> session = new HashMap<>();
        session.put("pid", 123L);
        session.put("usename", null);
        session.put("datname", null);
        session.put("state", null);
        session.put("query", null);
        session.put("client_addr", null);
        session.put("execute_time", null);
        mockData.add(session);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<DBSession> mapper = invocation.getArgument(1);
                    List<DBSession> result = new ArrayList<>();
                    int rowNum = 0;
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        DBSession currentSession = accessor.currentSession();

        Assert.assertNotNull(currentSession);
        Assert.assertEquals("123", currentSession.getId());
        Assert.assertNull(currentSession.getUsername());
        Assert.assertNull(currentSession.getDatabaseName());
        Assert.assertEquals(Integer.valueOf(0), currentSession.getExecuteTime());
        Assert.assertEquals(DBTransState.UNKNOWN, currentSession.getTransState());
    }

    // ============== SQL Generation Tests ==============

    @Test
    public void getTableStats_SqlContainsExpectedKeywords() throws Exception {
        List<Map<String, Object>> mockData = new ArrayList<>();
        mockData.add(new HashMap<>());

        ResultSet mockResultSet = createMockResultSet(mockData);

        final String[] capturedSql = new String[1];

        doAnswer(invocation -> {
            capturedSql[0] = invocation.getArgument(0);
            RowCallbackHandler handler = invocation.getArgument(1);
            while (mockResultSet.next()) {
                handler.processRow(mockResultSet);
            }
            return null;
        }).when(jdbcOperations).query(anyString(), any(RowCallbackHandler.class));

        accessor.getTableStats("public", "users");

        Assert.assertNotNull(capturedSql[0]);
        // Verify SQL uses PostgreSQL system tables
        Assert.assertTrue("SQL should contain pg_class", capturedSql[0].contains("pg_class"));
        Assert.assertTrue("SQL should contain pg_stat_user_tables", capturedSql[0].contains("pg_stat_user_tables"));
        Assert.assertTrue("SQL should contain pg_total_relation_size",
                capturedSql[0].contains("pg_total_relation_size"));
        Assert.assertTrue("SQL should contain pg_namespace", capturedSql[0].contains("pg_namespace"));
    }

    @Test
    public void listAllSessions_SqlContainsPgStatActivity() throws Exception {
        ResultSet mockResultSet = createMockResultSet(new ArrayList<>());

        final String[] capturedSql = new String[1];

        when(jdbcOperations.query(anyString(), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    capturedSql[0] = invocation.getArgument(0);
                    @SuppressWarnings("unchecked")
                    RowMapper<DBSession> mapper = invocation.getArgument(1);
                    List<DBSession> result = new ArrayList<>();
                    int rowNum = 0;
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        accessor.listAllSessions();

        Assert.assertNotNull(capturedSql[0]);
        Assert.assertTrue("SQL should contain pg_stat_activity", capturedSql[0].contains("pg_stat_activity"));
        Assert.assertTrue("SQL should contain pg_backend_pid", capturedSql[0].contains("pg_backend_pid()") == false);
    }

    @Test
    public void currentSession_SqlContainsPgBackendPid() throws Exception {
        ResultSet mockResultSet = createMockResultSet(new ArrayList<>());

        final String[] capturedSql = new String[1];

        when(jdbcOperations.query(anyString(), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    capturedSql[0] = invocation.getArgument(0);
                    @SuppressWarnings("unchecked")
                    RowMapper<DBSession> mapper = invocation.getArgument(1);
                    List<DBSession> result = new ArrayList<>();
                    int rowNum = 0;
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        accessor.currentSession();

        Assert.assertNotNull(capturedSql[0]);
        Assert.assertTrue("SQL should contain pg_stat_activity", capturedSql[0].contains("pg_stat_activity"));
        Assert.assertTrue("SQL should filter by pg_backend_pid()", capturedSql[0].contains("pg_backend_pid()"));
    }

    // ============== Helper Methods ==============

    private ResultSet createMockResultSet(List<Map<String, Object>> data) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData rsmd = mock(ResultSetMetaData.class);

        if (data == null || data.isEmpty()) {
            when(rs.next()).thenReturn(false);
            return rs;
        }

        String[] columnNames = data.get(0).keySet().toArray(new String[0]);
        when(rsmd.getColumnCount()).thenReturn(columnNames.length);
        for (int i = 0; i < columnNames.length; i++) {
            when(rsmd.getColumnName(i + 1)).thenReturn(columnNames[i]);
            when(rsmd.getColumnLabel(i + 1)).thenReturn(columnNames[i]);
        }
        when(rs.getMetaData()).thenReturn(rsmd);

        AtomicInteger rowIndex = new AtomicInteger(0);
        when(rs.next()).thenAnswer(invocation -> {
            int current = rowIndex.get();
            if (current < data.size()) {
                rowIndex.incrementAndGet();
                return true;
            }
            return false;
        });

        when(rs.getObject(anyString())).thenAnswer(invocation -> {
            String columnName = invocation.getArgument(0);
            int currentRow = rowIndex.get() - 1;
            if (currentRow >= 0 && currentRow < data.size()) {
                return data.get(currentRow).get(columnName);
            }
            return null;
        });

        when(rs.getString(anyString())).thenAnswer(invocation -> {
            Object value = rs.getObject(invocation.getArgument(0));
            return value != null ? value.toString() : null;
        });

        when(rs.getLong(anyString())).thenAnswer(invocation -> {
            Object value = rs.getObject(invocation.getArgument(0));
            if (value instanceof Long) {
                return (Long) value;
            }
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            return 0L;
        });

        when(rs.getInt(anyString())).thenAnswer(invocation -> {
            Object value = rs.getObject(invocation.getArgument(0));
            if (value instanceof Integer) {
                return (Integer) value;
            }
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return 0;
        });

        return rs;
    }
}
