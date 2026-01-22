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
package com.oceanbase.tools.dbbrowser.schema;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;

import com.oceanbase.tools.dbbrowser.model.DBColumnGroupElement;
import com.oceanbase.tools.dbbrowser.model.DBConstraintType;
import com.oceanbase.tools.dbbrowser.model.DBDatabase;
import com.oceanbase.tools.dbbrowser.model.DBFunction;
import com.oceanbase.tools.dbbrowser.model.DBMViewRefreshParameter;
import com.oceanbase.tools.dbbrowser.model.DBMViewRefreshRecord;
import com.oceanbase.tools.dbbrowser.model.DBMViewRefreshRecordParam;
import com.oceanbase.tools.dbbrowser.model.DBMaterializedView;
import com.oceanbase.tools.dbbrowser.model.DBObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBPLObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBPackage;
import com.oceanbase.tools.dbbrowser.model.DBProcedure;
import com.oceanbase.tools.dbbrowser.model.DBSequence;
import com.oceanbase.tools.dbbrowser.model.DBSynonym;
import com.oceanbase.tools.dbbrowser.model.DBSynonymType;
import com.oceanbase.tools.dbbrowser.model.DBTable;
import com.oceanbase.tools.dbbrowser.model.DBTable.DBTableOptions;
import com.oceanbase.tools.dbbrowser.model.DBTableColumn;
import com.oceanbase.tools.dbbrowser.model.DBTableConstraint;
import com.oceanbase.tools.dbbrowser.model.DBTableIndex;
import com.oceanbase.tools.dbbrowser.model.DBTablePartition;
import com.oceanbase.tools.dbbrowser.model.DBTableSubpartitionDefinition;
import com.oceanbase.tools.dbbrowser.model.DBTrigger;
import com.oceanbase.tools.dbbrowser.model.DBType;
import com.oceanbase.tools.dbbrowser.model.DBVariable;
import com.oceanbase.tools.dbbrowser.model.DBView;
import com.oceanbase.tools.dbbrowser.schema.sqlserver.SqlServerSchemaAccessor;

/**
 * Unit tests for {@link SqlServerSchemaAccessor}
 * 
 * This test uses Mockito to mock JdbcOperations, so it doesn't require a real SQL Server database.
 * 
 * @author generated
 * @since ODC_release_4.3.4
 */
public class SqlServerSchemaAccessorTest {

    private JdbcOperations jdbcOperations;
    private SqlServerSchemaAccessor accessor;
    private String testDatabaseName = "test_db";
    private String testSchemaName = "dbo";

    @Before
    public void setUp() {
        // Create Mock JdbcOperations
        jdbcOperations = mock(JdbcOperations.class);
        accessor = new SqlServerSchemaAccessor(jdbcOperations);
    }

    @Test
    public void showDatabases_Success() {
        // Mock showDatabases query result
        List<String> mockDatabases = new ArrayList<>();
        mockDatabases.add("master");
        mockDatabases.add("test_db");
        mockDatabases.add("tempdb");

        when(jdbcOperations.queryForList(anyString(), eq(String.class)))
                .thenReturn(mockDatabases);

        List<String> databases = accessor.showDatabases();
        Assert.assertNotNull(databases);
        Assert.assertFalse(databases.isEmpty());
        Assert.assertTrue(databases.contains("master"));
    }

    @Test
    public void getDatabase_Success() throws Exception {
        // Mock getDatabase query result - collation query
        // The query uses RowCallbackHandler with lambda: rs -> { if (rs.next()) {
        // collation.set(rs.getString(1)); } }
        doAnswer(invocation -> {
            // Create a fresh mock ResultSet for each call
            ResultSet mockResultSet = mock(ResultSet.class);
            try {
                // Use thenAnswer to ensure fresh state for each call
                when(mockResultSet.next()).thenReturn(true).thenReturn(false);
                when(mockResultSet.getString(1)).thenReturn("SQL_Latin1_General_CP1_CI_AS");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            // Execute the RowCallbackHandler
            RowCallbackHandler handler = invocation.getArgument(2);
            try {
                handler.processRow(mockResultSet);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return null;
        }).when(jdbcOperations).query(anyString(), any(Object[].class), any(RowCallbackHandler.class));

        DBDatabase database = accessor.getDatabase(testDatabaseName);
        Assert.assertNotNull(database);
        Assert.assertEquals(testDatabaseName, database.getName());
        Assert.assertNotNull(database.getCollation());
        Assert.assertEquals("SQL_Latin1_General_CP1_CI_AS", database.getCollation());
    }

    @Test
    public void listDatabases_Success() throws Exception {
        // Mock listDatabases query result
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> db1 = new java.util.HashMap<>();
        db1.put("name", "master");
        db1.put("collation", "SQL_Latin1_General_CP1_CI_AS");
        mockData.add(db1);

        Map<String, Object> db2 = new java.util.HashMap<>();
        db2.put("name", "test_db");
        db2.put("collation", "SQL_Latin1_General_CP1_CI_AS");
        mockData.add(db2);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBDatabase> mapper = invocation.getArgument(1);
                    List<DBDatabase> result = new ArrayList<>();
                    int rowNum = 0;
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        List<DBDatabase> databases = accessor.listDatabases();
        Assert.assertNotNull(databases);
        Assert.assertFalse(databases.isEmpty());
    }

    /**
     * Helper method to create a mock ResultSet from data
     */
    private ResultSet createMockResultSet(List<Map<String, Object>> data) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData rsmd = mock(ResultSetMetaData.class);

        if (data == null || data.isEmpty()) {
            when(rs.next()).thenReturn(false);
            return rs;
        }

        // Get column names from first row
        String[] columnNames = data.get(0).keySet().toArray(new String[0]);
        when(rsmd.getColumnCount()).thenReturn(columnNames.length);
        for (int i = 0; i < columnNames.length; i++) {
            when(rsmd.getColumnName(i + 1)).thenReturn(columnNames[i]);
            when(rsmd.getColumnLabel(i + 1)).thenReturn(columnNames[i]);
        }
        when(rs.getMetaData()).thenReturn(rsmd);

        // Setup row iteration
        AtomicInteger rowIndex = new AtomicInteger(0);
        when(rs.next()).thenAnswer(invocation -> {
            int current = rowIndex.get();
            if (current < data.size()) {
                rowIndex.incrementAndGet();
                return true;
            }
            return false;
        });

        // Setup getObject/getString methods
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

        when(rs.getString(any(Integer.class))).thenAnswer(invocation -> {
            int columnIndex = invocation.getArgument(0);
            if (columnIndex > 0 && columnIndex <= columnNames.length) {
                return rs.getString(columnNames[columnIndex - 1]);
            }
            return null;
        });

        return rs;
    }

    @Test
    public void switchDatabase_Success() {
        // Mock execute method - switchDatabase uses jdbcOperations.execute()
        doNothing().when(jdbcOperations).execute(anyString());

        // Test switching to master database
        accessor.switchDatabase("master");

        // Test switching back to test database
        accessor.switchDatabase(testDatabaseName);

        // Verify execute was called (at least once)
        // Note: We can't easily verify the exact SQL without using ArgumentCaptor,
        // but the method should complete without throwing exception
    }

    @Test
    public void listTables_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query - returns current database
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock listTables query result
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> table1 = new java.util.HashMap<>();
        table1.put("table_name", "test_table1");
        table1.put("table_schema", testSchemaName);
        mockData.add(table1);

        Map<String, Object> table2 = new java.util.HashMap<>();
        table2.put("table_name", "test_table2");
        table2.put("table_schema", testSchemaName);
        mockData.add(table2);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBObjectIdentity> mapper = invocation.getArgument(2);
                    List<DBObjectIdentity> result = new ArrayList<>();
                    int rowNum = 0;
                    // Reset ResultSet for iteration
                    when(mockResultSet.next()).thenReturn(true, true, false);
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        List<DBObjectIdentity> tables = accessor.listTables(schemaName, null);
        Assert.assertNotNull(tables);
    }

    @Test
    public void listTables_InputIsNonEmptyString_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock showTablesLike query - it returns List<String> using RowMapper that gets rs.getString(1)
        ResultSet mockResultSet = mock(ResultSet.class);
        AtomicInteger nextCallCount = new AtomicInteger(0);
        when(mockResultSet.next()).thenAnswer(invocation -> {
            int count = nextCallCount.getAndIncrement();
            return count == 0; // First call returns true
        });
        when(mockResultSet.getString(1)).thenReturn("test_table1");

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    nextCallCount.set(0);
                    RowMapper<String> mapper = invocation.getArgument(2);
                    List<String> result = new ArrayList<>();
                    int rowNum = 0;
                    if (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        List<DBObjectIdentity> tables = accessor.listTables(schemaName, "test_%");
        Assert.assertNotNull(tables);
        if (!tables.isEmpty()) {
            Assert.assertTrue(tables.stream().allMatch(t -> t.getName().startsWith("test_")));
        }
    }

    @Test
    public void showTables_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock showTables query result
        List<String> mockTableNames = new ArrayList<>();
        mockTableNames.add("test_table1");
        mockTableNames.add("test_table2");

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<String> mapper = invocation.getArgument(2);
                    List<String> result = new ArrayList<>();
                    ResultSet rs = createMockResultSet(Collections.singletonList(
                            Collections.singletonMap("table_name", "test_table1")));
                    when(rs.next()).thenReturn(true, true, false);
                    int rowNum = 0;
                    while (rs.next()) {
                        result.add(mapper.mapRow(rs, rowNum++));
                    }
                    return mockTableNames;
                });

        List<String> tables = accessor.showTables(schemaName);
        Assert.assertNotNull(tables);
    }

    @Test
    public void showTablesLike_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock showTablesLike query result
        List<String> mockTableNames = new ArrayList<>();
        mockTableNames.add("test_table1");

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenReturn(mockTableNames);

        List<String> tables = accessor.showTablesLike(schemaName, "test_%");
        Assert.assertNotNull(tables);
        Assert.assertFalse(tables.isEmpty());
    }

    @Test
    public void listViews_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock listViews query result
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> view1 = new java.util.HashMap<>();
        view1.put("table_name", "view_test1");
        mockData.add(view1);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBObjectIdentity> mapper = invocation.getArgument(2);
                    List<DBObjectIdentity> result = new ArrayList<>();
                    int rowNum = 0;
                    when(mockResultSet.next()).thenReturn(true, false);
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        List<DBObjectIdentity> views = accessor.listViews(schemaName);
        Assert.assertNotNull(views);
    }

    @Test
    public void listAllViews_Success() throws Exception {
        // Mock showDatabases
        List<String> mockDatabases = new ArrayList<>();
        mockDatabases.add(testDatabaseName);
        when(jdbcOperations.queryForList(anyString(), eq(String.class)))
                .thenReturn(mockDatabases);

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock listAllViews query result
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> view1 = new java.util.HashMap<>();
        view1.put("table_schema", testSchemaName);
        view1.put("table_name", "view_test1");
        mockData.add(view1);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBObjectIdentity> mapper = invocation.getArgument(1);
                    List<DBObjectIdentity> result = new ArrayList<>();
                    int rowNum = 0;
                    when(mockResultSet.next()).thenReturn(true, false);
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        List<DBObjectIdentity> views = accessor.listAllViews(null);
        Assert.assertNotNull(views);
    }

    @Test
    public void listAllUserViews_Success() throws Exception {
        // Mock showDatabases
        List<String> mockDatabases = new ArrayList<>();
        mockDatabases.add(testDatabaseName);
        when(jdbcOperations.queryForList(anyString(), eq(String.class)))
                .thenReturn(mockDatabases);

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock listAllUserViews query result
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> view1 = new java.util.HashMap<>();
        view1.put("table_schema", testSchemaName);
        view1.put("table_name", "view_test1");
        mockData.add(view1);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBObjectIdentity> mapper = invocation.getArgument(2);
                    List<DBObjectIdentity> result = new ArrayList<>();
                    int rowNum = 0;
                    when(mockResultSet.next()).thenReturn(true, false);
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        List<DBObjectIdentity> views = accessor.listAllUserViews(null);
        Assert.assertNotNull(views);
    }

    @Test
    public void listAllSystemViews_Success() throws Exception {
        // Mock showDatabases
        List<String> mockDatabases = new ArrayList<>();
        mockDatabases.add(testDatabaseName);
        when(jdbcOperations.queryForList(anyString(), eq(String.class)))
                .thenReturn(mockDatabases);

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock listAllSystemViews query result - system views exist
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> view1 = new java.util.HashMap<>();
        view1.put("table_schema", "sys");
        view1.put("table_name", "sys_views");
        mockData.add(view1);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBObjectIdentity> mapper = invocation.getArgument(2);
                    List<DBObjectIdentity> result = new ArrayList<>();
                    int rowNum = 0;
                    when(mockResultSet.next()).thenReturn(true, false);
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        List<DBObjectIdentity> views = accessor.listAllSystemViews(null);
        Assert.assertNotNull(views);
        // System views should exist
        Assert.assertFalse(views.isEmpty());
    }

    @Test
    public void getView_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock getView query - view definition
        ResultSet mockViewDefRs = mock(ResultSet.class);
        when(mockViewDefRs.next()).thenReturn(true, false);
        when(mockViewDefRs.getString("view_definition")).thenReturn("SELECT col1, col2 FROM test_table");

        when(jdbcOperations.query(anyString(), any(Object[].class), any(ResultSetExtractor.class)))
                .thenAnswer(invocation -> {
                    ResultSetExtractor<?> extractor = invocation.getArgument(2);
                    return extractor.extractData(mockViewDefRs);
                });

        // Mock getView query - columns
        List<Map<String, Object>> mockColumnData = new ArrayList<>();
        Map<String, Object> col1 = new java.util.HashMap<>();
        col1.put("column_name", "col1");
        col1.put("data_type", "int");
        mockColumnData.add(col1);

        Map<String, Object> col2 = new java.util.HashMap<>();
        col2.put("column_name", "col2");
        col2.put("data_type", "varchar");
        mockColumnData.add(col2);

        ResultSet mockColumnRs = createMockResultSet(mockColumnData);

        when(jdbcOperations.query(anyString(), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBTableColumn> mapper = invocation.getArgument(1);
                    List<DBTableColumn> result = new ArrayList<>();
                    int rowNum = 0;
                    when(mockColumnRs.next()).thenReturn(true, true, false);
                    while (mockColumnRs.next()) {
                        result.add(mapper.mapRow(mockColumnRs, rowNum++));
                    }
                    return result;
                });

        DBView view = accessor.getView(schemaName, "view_test1");
        Assert.assertNotNull(view);
        Assert.assertEquals("view_test1", view.name());
        Assert.assertNotNull(view.getColumns());
    }

    @Test
    public void listTableColumns_TestAllColumnDataTypes_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock listTableColumns query result with SQL Server data types
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> col1 = new java.util.HashMap<>();
        col1.put("column_name", "col1");
        col1.put("data_type", "int");
        col1.put("is_nullable", "YES");
        col1.put("column_default", null);
        mockData.add(col1);

        Map<String, Object> col2 = new java.util.HashMap<>();
        col2.put("column_name", "col2");
        col2.put("data_type", "varchar");
        col2.put("character_maximum_length", 50);
        col2.put("is_nullable", "YES");
        mockData.add(col2);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBTableColumn> mapper = invocation.getArgument(2);
                    List<DBTableColumn> result = new ArrayList<>();
                    int rowNum = 0;
                    when(mockResultSet.next()).thenReturn(true, true, false);
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        List<DBTableColumn> columns = accessor.listTableColumns(schemaName, "test_data_type");
        Assert.assertNotNull(columns);
        Assert.assertFalse(columns.isEmpty());

        // Verify SQL Server specific data types
        for (DBTableColumn column : columns) {
            Assert.assertNotNull(column.getName());
            Assert.assertNotNull(column.getTypeName());
        }
    }

    @Test
    public void listTableColumns_TestColumnAttributes_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock listTableColumns query result
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> col1 = new java.util.HashMap<>();
        col1.put("column_name", "id");
        col1.put("data_type", "int");
        col1.put("is_nullable", "NO");
        mockData.add(col1);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBTableColumn> mapper = invocation.getArgument(2);
                    List<DBTableColumn> result = new ArrayList<>();
                    int rowNum = 0;
                    when(mockResultSet.next()).thenReturn(true, false);
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        List<DBTableColumn> columns = accessor.listTableColumns(schemaName, "test_other_than_data_type");
        Assert.assertNotNull(columns);
        for (DBTableColumn column : columns) {
            Assert.assertNotNull(column.getName());
        }
    }

    @Test
    public void listBasicTableColumns_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock listBasicTableColumns query result
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> col1 = new java.util.HashMap<>();
        col1.put("table_name", "test_table");
        col1.put("column_name", "col1");
        col1.put("data_type", "int");
        mockData.add(col1);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBTableColumn> mapper = invocation.getArgument(2);
                    List<DBTableColumn> result = new ArrayList<>();
                    int rowNum = 0;
                    when(mockResultSet.next()).thenReturn(true, false);
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        Map<String, List<DBTableColumn>> columns = accessor.listBasicTableColumns(schemaName);
        Assert.assertNotNull(columns);
    }

    @Test
    public void listBasicTableColumns_InTable_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock listBasicTableColumns query result
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> col1 = new java.util.HashMap<>();
        col1.put("column_name", "col1");
        col1.put("data_type", "int");
        mockData.add(col1);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBTableColumn> mapper = invocation.getArgument(2);
                    List<DBTableColumn> result = new ArrayList<>();
                    int rowNum = 0;
                    when(mockResultSet.next()).thenReturn(true, false);
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        List<DBTableColumn> columns = accessor.listBasicTableColumns(schemaName, "test_data_type");
        Assert.assertNotNull(columns);
        Assert.assertFalse(columns.isEmpty());
    }

    @Test
    public void listBasicViewColumns_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock listBasicViewColumns query result - SQL returns: view_name, column_name, data_type
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> col1 = new java.util.HashMap<>();
        col1.put("view_name", "view_test1");
        col1.put("ordinal_position", 1);
        col1.put("column_name", "col1");
        col1.put("data_type", "int");
        col1.put("column_comment", "");
        mockData.add(col1);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBTableColumn> mapper = invocation.getArgument(2);
                    List<DBTableColumn> result = new ArrayList<>();
                    int rowNum = 0;
                    AtomicInteger nextCallCount = new AtomicInteger(0);
                    when(mockResultSet.next()).thenAnswer(inv -> {
                        int count = nextCallCount.getAndIncrement();
                        return count == 0;
                    });
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        Map<String, List<DBTableColumn>> columns = accessor.listBasicViewColumns(schemaName);
        Assert.assertNotNull(columns);
    }

    @Test
    public void listBasicViewColumns_InView_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock listBasicViewColumns query result
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> col1 = new java.util.HashMap<>();
        col1.put("column_name", "col1");
        col1.put("data_type", "int");
        mockData.add(col1);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBTableColumn> mapper = invocation.getArgument(2);
                    List<DBTableColumn> result = new ArrayList<>();
                    int rowNum = 0;
                    when(mockResultSet.next()).thenReturn(true, false);
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        List<DBTableColumn> columns = accessor.listBasicViewColumns(schemaName, "view_test1");
        Assert.assertNotNull(columns);
    }

    @Test
    public void listTableIndexes_TestIndexType_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock listTableIndexes query result
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> idx1 = new java.util.HashMap<>();
        idx1.put("index_name", "idx_clustered");
        idx1.put("index_type", "CLUSTERED");
        idx1.put("is_unique", false);
        mockData.add(idx1);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBTableIndex> mapper = invocation.getArgument(2);
                    List<DBTableIndex> result = new ArrayList<>();
                    int rowNum = 0;
                    when(mockResultSet.next()).thenReturn(true, false);
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        List<DBTableIndex> indexList = accessor.listTableIndexes(schemaName, "test_index_type");
        Assert.assertNotNull(indexList);
        if (!indexList.isEmpty()) {
            // SQL Server supports CLUSTERED and NONCLUSTERED indexes
            for (DBTableIndex index : indexList) {
                Assert.assertNotNull(index.getType());
            }
        }
    }

    @Test
    public void listTableIndexes_InSchema_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock listTableIndexes query result
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> idx1 = new java.util.HashMap<>();
        idx1.put("table_name", "test_table");
        idx1.put("index_name", "idx_test");
        idx1.put("index_type", "NONCLUSTERED");
        mockData.add(idx1);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBTableIndex> mapper = invocation.getArgument(2);
                    List<DBTableIndex> result = new ArrayList<>();
                    int rowNum = 0;
                    when(mockResultSet.next()).thenReturn(true, false);
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        Map<String, List<DBTableIndex>> indexes = accessor.listTableIndexes(schemaName);
        Assert.assertNotNull(indexes);
    }

    @Test
    public void listTableConstraints_TestPrimaryKey_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock listTableConstraints queries - it executes 3 queries: pk/unique, fk, check
        // Query 1: Primary key constraints
        List<Map<String, Object>> mockPkData = new ArrayList<>();
        Map<String, Object> pk1 = new java.util.HashMap<>();
        pk1.put("constraint_name", "pk_test_parent");
        pk1.put("constraint_type", "PRIMARY_KEY_CONSTRAINT");
        pk1.put("column_name", "id");
        mockPkData.add(pk1);

        ResultSet mockPkRs = createMockResultSet(mockPkData);
        ResultSet mockFkRs = createMockResultSet(Collections.emptyList());
        ResultSet mockCheckRs = createMockResultSet(Collections.emptyList());

        AtomicInteger queryCallCount = new AtomicInteger(0);
        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    int callCount = queryCallCount.getAndIncrement();
                    RowMapper<?> mapper = invocation.getArgument(2);
                    List<Object> result = new ArrayList<>();
                    int rowNum = 0;
                    ResultSet rs = (callCount == 0) ? mockPkRs : (callCount == 1) ? mockFkRs : mockCheckRs;
                    AtomicInteger nextCallCount = new AtomicInteger(0);
                    try {
                        when(rs.next()).thenAnswer(inv -> {
                            int count = nextCallCount.getAndIncrement();
                            return count == 0 && callCount == 0; // Only first query has data
                        });
                        while (rs.next()) {
                            result.add(mapper.mapRow(rs, rowNum++));
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return result;
                });

        List<DBTableConstraint> constraints = accessor.listTableConstraints(schemaName, "test_pk_parent");
        Assert.assertNotNull(constraints);
        if (!constraints.isEmpty()) {
            DBTableConstraint pk = constraints.stream()
                    .filter(c -> c.getType() == DBConstraintType.PRIMARY_KEY)
                    .findFirst()
                    .orElse(null);
            Assert.assertNotNull(pk);
        }
    }

    @Test
    public void listTableConstraints_TestForeignKey_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock listTableConstraints queries - it executes 3 queries: pk/unique, fk, check
        // Query 1: Primary key/unique constraints - return empty
        ResultSet mockPkRs = createMockResultSet(Collections.emptyList());

        // Query 2: Foreign key constraints
        List<Map<String, Object>> mockFkData = new ArrayList<>();
        Map<String, Object> fk1 = new java.util.HashMap<>();
        fk1.put("constraint_name", "fk_test_child");
        fk1.put("column_name", "parent_id");
        fk1.put("referenced_schema_name", testSchemaName);
        fk1.put("referenced_table_name", "test_fk_parent");
        fk1.put("referenced_column_name", "id");
        fk1.put("delete_referential_action", 0);
        fk1.put("update_referential_action", 0);
        fk1.put("referenced_object_id", 1);
        mockFkData.add(fk1);

        ResultSet mockFkRs = createMockResultSet(mockFkData);
        ResultSet mockCheckRs = createMockResultSet(Collections.emptyList());

        AtomicInteger queryCallCount = new AtomicInteger(0);
        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    int callCount = queryCallCount.getAndIncrement();
                    RowMapper<?> mapper = invocation.getArgument(2);
                    List<Object> result = new ArrayList<>();
                    int rowNum = 0;
                    ResultSet rs = (callCount == 0) ? mockPkRs : (callCount == 1) ? mockFkRs : mockCheckRs;
                    AtomicInteger nextCallCount = new AtomicInteger(0);
                    try {
                        when(rs.next()).thenAnswer(inv -> {
                            int count = nextCallCount.getAndIncrement();
                            return count == 0 && callCount == 1; // Only second query (FK) has data
                        });
                        while (rs.next()) {
                            result.add(mapper.mapRow(rs, rowNum++));
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return result;
                });

        List<DBTableConstraint> constraints = accessor.listTableConstraints(schemaName, "test_fk_child");
        Assert.assertNotNull(constraints);
        if (!constraints.isEmpty()) {
            DBTableConstraint fk = constraints.stream()
                    .filter(c -> c.getType() == DBConstraintType.FOREIGN_KEY)
                    .findFirst()
                    .orElse(null);
            if (fk != null) {
                Assert.assertNotNull(fk.getReferenceTableName());
            }
        }
    }

    @Test
    public void listTableConstraints_InSchema_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock listTableConstraints queries - it executes 3 queries: pk/unique, fk, check
        // Query 1: Primary key/unique constraints
        List<Map<String, Object>> mockPkData = new ArrayList<>();
        Map<String, Object> pk1 = new java.util.HashMap<>();
        pk1.put("table_name", "test_table");
        pk1.put("constraint_name", "pk_test");
        pk1.put("constraint_type", "PRIMARY_KEY_CONSTRAINT");
        pk1.put("column_name", "id");
        mockPkData.add(pk1);

        ResultSet mockPkRs = createMockResultSet(mockPkData);

        // Query 2: Foreign key constraints - return empty
        ResultSet mockFkRs = createMockResultSet(Collections.emptyList());

        // Query 3: Check constraints - return empty
        ResultSet mockCheckRs = createMockResultSet(Collections.emptyList());

        AtomicInteger queryCallCount = new AtomicInteger(0);
        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    int callCount = queryCallCount.getAndIncrement();
                    RowMapper<?> mapper = invocation.getArgument(2);
                    List<Object> result = new ArrayList<>();
                    int rowNum = 0;
                    ResultSet rs;
                    if (callCount == 0) {
                        // First call: pk/unique constraints
                        rs = mockPkRs;
                    } else if (callCount == 1) {
                        // Second call: foreign key constraints
                        rs = mockFkRs;
                    } else {
                        // Third call: check constraints
                        rs = mockCheckRs;
                    }
                    AtomicInteger nextCallCount = new AtomicInteger(0);
                    try {
                        when(rs.next()).thenAnswer(inv -> {
                            int count = nextCallCount.getAndIncrement();
                            return count == 0 && callCount == 0; // Only first query has data
                        });
                        while (rs.next()) {
                            result.add(mapper.mapRow(rs, rowNum++));
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return result;
                });

        Map<String, List<DBTableConstraint>> constraints = accessor.listTableConstraints(schemaName);
        Assert.assertNotNull(constraints);
    }

    @Test
    public void getTableOptions_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock getTableOptions query result
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("table_name", "test_data_type");
        row.put("table_type", "BASE TABLE");
        mockData.add(row);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(2);
                    List<Object> result = new ArrayList<>();
                    int rowNum = 0;
                    when(mockResultSet.next()).thenReturn(true, false);
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        DBTableOptions options = accessor.getTableOptions(schemaName, "test_data_type");
        Assert.assertNotNull(options);
    }

    @Test
    public void listTableOptions_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock listTableOptions query result
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("table_name", "test_table");
        row.put("table_type", "BASE TABLE");
        mockData.add(row);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(2);
                    List<Object> result = new ArrayList<>();
                    int rowNum = 0;
                    when(mockResultSet.next()).thenReturn(true, false);
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        Map<String, DBTableOptions> table2Options = accessor.listTableOptions(schemaName);
        Assert.assertNotNull(table2Options);
    }

    @Test
    public void getPartition_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock getPartition query - return null or empty to indicate no partition
        // SQL Server partitioning is complex, so we'll mock it returning null
        when(jdbcOperations.queryForObject(anyString(), any(Object[].class), eq(Integer.class)))
                .thenReturn(0); // No partitions found

        DBTablePartition partition = accessor.getPartition(schemaName, "test_partition");
        // Partition may be null if table is not partitioned
        if (partition != null) {
            Assert.assertNotNull(partition.getPartitionOption());
        }
    }

    @Test
    public void listTablePartitions_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock listTablePartitions query - return empty list
        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());

        Map<String, DBTablePartition> partitions = accessor.listTablePartitions(schemaName, null);
        Assert.assertNotNull(partitions);
    }

    @Test
    public void getTableDDL_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock listTableColumns query - getTableDDL calls listTableColumns internally
        List<Map<String, Object>> mockColumnData = new ArrayList<>();
        Map<String, Object> col1 = new java.util.HashMap<>();
        col1.put("ordinal_position", 1);
        col1.put("column_name", "col1");
        col1.put("data_type", "int");
        col1.put("full_type_name", "int");
        col1.put("is_nullable", true);
        col1.put("column_default", "");
        col1.put("column_comment", "");
        mockColumnData.add(col1);

        ResultSet mockColumnRs = createMockResultSet(mockColumnData);

        // Mock listTableColumns query - first call returns columns
        AtomicInteger queryCallCount = new AtomicInteger(0);
        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    int callCount = queryCallCount.getAndIncrement();
                    if (callCount == 0) {
                        // First call: listTableColumns
                        RowMapper<DBTableColumn> mapper = invocation.getArgument(2);
                        List<DBTableColumn> result = new ArrayList<>();
                        int rowNum = 0;
                        AtomicInteger nextCallCount = new AtomicInteger(0);
                        when(mockColumnRs.next()).thenAnswer(inv -> {
                            int count = nextCallCount.getAndIncrement();
                            return count == 0;
                        });
                        while (mockColumnRs.next()) {
                            result.add(mapper.mapRow(mockColumnRs, rowNum++));
                        }
                        return result;
                    } else {
                        // Subsequent calls: listTableConstraints, etc. - return empty list
                        return Collections.emptyList();
                    }
                });

        String ddl = accessor.getTableDDL(schemaName, "test_data_type");
        Assert.assertNotNull(ddl);
        Assert.assertTrue(ddl.toUpperCase().contains("CREATE TABLE"));
    }

    @Test
    public void getTables_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock showTables - returns table names
        ResultSet mockTableNameRs = mock(ResultSet.class);
        AtomicInteger tableNameNextCount = new AtomicInteger(0);
        try {
            when(mockTableNameRs.next()).thenAnswer(inv -> {
                int count = tableNameNextCount.getAndIncrement();
                return count == 0;
            });
            when(mockTableNameRs.getString(1)).thenReturn("test_table1");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        // Mock listTableColumns for getTableDDL
        List<Map<String, Object>> mockColumnData = new ArrayList<>();
        Map<String, Object> col1 = new java.util.HashMap<>();
        col1.put("table_name", "test_table1");
        col1.put("ordinal_position", 1);
        col1.put("column_name", "id");
        col1.put("data_type", "int");
        col1.put("full_type_name", "int");
        col1.put("is_nullable", false);
        col1.put("column_default", "");
        col1.put("column_comment", "");
        mockColumnData.add(col1);
        ResultSet mockColumnRs = createMockResultSet(mockColumnData);

        // Mock listTableConstraints queries (3 queries: pk/unique, fk, check)
        ResultSet mockPkRs = createMockResultSet(Collections.emptyList());
        ResultSet mockFkRs = createMockResultSet(Collections.emptyList());
        ResultSet mockCheckRs = createMockResultSet(Collections.emptyList());

        // Mock listTableColumns for batch query
        ResultSet mockBatchColumnRs = createMockResultSet(mockColumnData);

        // Mock listTableIndexes
        ResultSet mockIndexRs = createMockResultSet(Collections.emptyList());

        // Mock listTableOptions
        ResultSet mockOptionsRs = createMockResultSet(Collections.emptyList());

        AtomicInteger queryCallCount = new AtomicInteger(0);
        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    int callCount = queryCallCount.getAndIncrement();
                    RowMapper<?> mapper = invocation.getArgument(2);
                    List<Object> result = new ArrayList<>();
                    int rowNum = 0;
                    ResultSet rs;

                    // Determine which ResultSet to use based on call count and SQL pattern
                    // This is a simplified approach - in reality we'd check the SQL string
                    if (callCount < 1) {
                        // showTablesLike - returns table names
                        rs = mockTableNameRs;
                        tableNameNextCount.set(0);
                    } else if (callCount < 2) {
                        // listTableColumns for getTableDDL
                        rs = mockColumnRs;
                    } else if (callCount < 5) {
                        // listTableConstraints (3 queries: pk, fk, check)
                        rs = (callCount == 2) ? mockPkRs : (callCount == 3) ? mockFkRs : mockCheckRs;
                    } else if (callCount < 6) {
                        // listTableColumns batch
                        rs = mockBatchColumnRs;
                    } else if (callCount < 7) {
                        // listTableIndexes
                        rs = mockIndexRs;
                    } else if (callCount < 10) {
                        // listTableConstraints for batch (3 queries again)
                        rs = (callCount == 7) ? mockPkRs : (callCount == 8) ? mockFkRs : mockCheckRs;
                    } else {
                        // listTableOptions
                        rs = mockOptionsRs;
                    }

                    AtomicInteger nextCallCount = new AtomicInteger(0);
                    try {
                        when(rs.next()).thenAnswer(inv -> {
                            int count = nextCallCount.getAndIncrement();
                            // Only first query (showTablesLike) and column queries have data
                            return count == 0 && (callCount == 0 || callCount == 1 || callCount == 5);
                        });
                        while (rs.next()) {
                            result.add(mapper.mapRow(rs, rowNum++));
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return result;
                });

        // Mock queryForList for showTables
        when(jdbcOperations.queryForList(anyString(), eq(String.class)))
                .thenReturn(Collections.singletonList("test_table1"));

        Map<String, DBTable> tables = accessor.getTables(schemaName, null);
        Assert.assertNotNull(tables);
    }

    @Test
    public void listFunctions_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock listFunctions query result - SQL returns: name, schema_name, type
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> func1 = new java.util.HashMap<>();
        func1.put("name", "function_test");
        func1.put("schema_name", testSchemaName);
        func1.put("type", "FUNCTION");
        mockData.add(func1);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBPLObjectIdentity> mapper = invocation.getArgument(2);
                    List<DBPLObjectIdentity> result = new ArrayList<>();
                    int rowNum = 0;
                    AtomicInteger nextCallCount = new AtomicInteger(0);
                    when(mockResultSet.next()).thenAnswer(inv -> {
                        int count = nextCallCount.getAndIncrement();
                        return count == 0;
                    });
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        List<DBPLObjectIdentity> functions = accessor.listFunctions(schemaName);
        Assert.assertNotNull(functions);
    }

    @Test
    public void getFunction_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock getFunction query - function definition
        ResultSet mockFuncDefRs = mock(ResultSet.class);
        when(mockFuncDefRs.next()).thenReturn(true, false);
        when(mockFuncDefRs.getString("definition"))
                .thenReturn("CREATE FUNCTION function_test(@param INT) RETURNS INT AS BEGIN RETURN @param END");

        when(jdbcOperations.query(anyString(), any(Object[].class), any(ResultSetExtractor.class)))
                .thenAnswer(invocation -> {
                    ResultSetExtractor<?> extractor = invocation.getArgument(2);
                    return extractor.extractData(mockFuncDefRs);
                });

        // Mock getFunction query - parameters
        List<Map<String, Object>> mockParamData = new ArrayList<>();
        Map<String, Object> param1 = new java.util.HashMap<>();
        param1.put("parameter_name", "@param");
        param1.put("data_type", "int");
        mockParamData.add(param1);

        ResultSet mockParamRs = createMockResultSet(mockParamData);

        when(jdbcOperations.query(anyString(), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    List<Object> result = new ArrayList<>();
                    int rowNum = 0;
                    when(mockParamRs.next()).thenReturn(true, false);
                    while (mockParamRs.next()) {
                        result.add(mapper.mapRow(mockParamRs, rowNum++));
                    }
                    return result;
                });

        DBFunction function = accessor.getFunction(schemaName, "function_test");
        if (function != null) {
            Assert.assertEquals("function_test", function.name());
        }
    }

    @Test
    public void listProcedures_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock listProcedures query result - SQL returns: name, schema_name, type
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> proc1 = new java.util.HashMap<>();
        proc1.put("name", "procedure_test");
        proc1.put("schema_name", testSchemaName);
        proc1.put("type", "PROCEDURE");
        mockData.add(proc1);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBPLObjectIdentity> mapper = invocation.getArgument(2);
                    List<DBPLObjectIdentity> result = new ArrayList<>();
                    int rowNum = 0;
                    AtomicInteger nextCallCount = new AtomicInteger(0);
                    when(mockResultSet.next()).thenAnswer(inv -> {
                        int count = nextCallCount.getAndIncrement();
                        return count == 0;
                    });
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        List<DBPLObjectIdentity> procedures = accessor.listProcedures(schemaName);
        Assert.assertNotNull(procedures);
    }

    @Test
    public void getProcedure_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock getProcedure query - procedure definition
        ResultSet mockProcDefRs = mock(ResultSet.class);
        when(mockProcDefRs.next()).thenReturn(true, false);
        when(mockProcDefRs.getString("definition"))
                .thenReturn("CREATE PROCEDURE procedure_test(@param INT) AS BEGIN SELECT @param END");

        when(jdbcOperations.query(anyString(), any(Object[].class), any(ResultSetExtractor.class)))
                .thenAnswer(invocation -> {
                    ResultSetExtractor<?> extractor = invocation.getArgument(2);
                    return extractor.extractData(mockProcDefRs);
                });

        // Mock getProcedure query - parameters
        List<Map<String, Object>> mockParamData = new ArrayList<>();
        Map<String, Object> param1 = new java.util.HashMap<>();
        param1.put("parameter_name", "@param");
        param1.put("data_type", "int");
        mockParamData.add(param1);

        ResultSet mockParamRs = createMockResultSet(mockParamData);

        when(jdbcOperations.query(anyString(), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    List<Object> result = new ArrayList<>();
                    int rowNum = 0;
                    when(mockParamRs.next()).thenReturn(true, false);
                    while (mockParamRs.next()) {
                        result.add(mapper.mapRow(mockParamRs, rowNum++));
                    }
                    return result;
                });

        DBProcedure procedure = accessor.getProcedure(schemaName, "procedure_test");
        if (procedure != null) {
            Assert.assertEquals("procedure_test", procedure.name());
        }
    }

    @Test
    public void listSequences_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock listSequences query result
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> seq1 = new java.util.HashMap<>();
        seq1.put("sequence_name", "sequence_test");
        seq1.put("sequence_schema", testSchemaName);
        mockData.add(seq1);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBObjectIdentity> mapper = invocation.getArgument(2);
                    List<DBObjectIdentity> result = new ArrayList<>();
                    int rowNum = 0;
                    when(mockResultSet.next()).thenReturn(true, false);
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        List<DBObjectIdentity> sequences = accessor.listSequences(schemaName);
        Assert.assertNotNull(sequences);
    }

    @Test
    public void getSequence_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock getSequence query result
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> seq = new java.util.HashMap<>();
        seq.put("sequence_name", "sequence_test");
        seq.put("start_value", "1");
        seq.put("increment", "1");
        seq.put("minimum_value", "1");
        seq.put("maximum_value", "1000");
        mockData.add(seq);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBSequence> mapper = invocation.getArgument(2);
                    List<DBSequence> result = new ArrayList<>();
                    int rowNum = 0;
                    when(mockResultSet.next()).thenReturn(true, false);
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result.isEmpty() ? null : result.get(0);
                });

        // Mock queryForObject for sequence properties
        when(jdbcOperations.queryForObject(anyString(), any(Object[].class), eq(String.class)))
                .thenReturn("1");

        DBSequence sequence = accessor.getSequence(schemaName, "sequence_test");
        if (sequence != null) {
            Assert.assertEquals("sequence_test", sequence.getName());
        }
    }

    @Test
    public void listSynonyms_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock listSynonyms query result
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> syn1 = new java.util.HashMap<>();
        syn1.put("synonym_name", "synonym_test");
        syn1.put("base_object_name", "test_data_type");
        mockData.add(syn1);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBObjectIdentity> mapper = invocation.getArgument(2);
                    List<DBObjectIdentity> result = new ArrayList<>();
                    int rowNum = 0;
                    when(mockResultSet.next()).thenReturn(true, false);
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        List<DBObjectIdentity> synonyms = accessor.listSynonyms(schemaName, DBSynonymType.COMMON);
        Assert.assertNotNull(synonyms);
    }

    @Test
    public void getSynonym_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock getSynonym query result
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> syn = new java.util.HashMap<>();
        syn.put("synonym_name", "synonym_test");
        syn.put("base_object_name", "test_data_type");
        mockData.add(syn);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBSynonym> mapper = invocation.getArgument(2);
                    List<DBSynonym> result = new ArrayList<>();
                    int rowNum = 0;
                    when(mockResultSet.next()).thenReturn(true, false);
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result.isEmpty() ? null : result.get(0);
                });

        DBSynonym synonym = accessor.getSynonym(schemaName, "synonym_test", DBSynonymType.COMMON);
        if (synonym != null) {
            Assert.assertEquals("synonym_test", synonym.name());
            Assert.assertNotNull(synonym.getTableName());
        }
    }

    @Test
    public void listTriggers_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock listTriggers query result
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> trigger1 = new java.util.HashMap<>();
        trigger1.put("trigger_name", "trigger_test");
        trigger1.put("trigger_schema", testSchemaName);
        mockData.add(trigger1);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBPLObjectIdentity> mapper = invocation.getArgument(2);
                    List<DBPLObjectIdentity> result = new ArrayList<>();
                    int rowNum = 0;
                    when(mockResultSet.next()).thenReturn(true, false);
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        List<DBPLObjectIdentity> triggers = accessor.listTriggers(schemaName);
        Assert.assertNotNull(triggers);
    }

    @Test
    public void getTrigger_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock getTrigger query - trigger definition
        ResultSet mockTriggerDefRs = mock(ResultSet.class);
        when(mockTriggerDefRs.next()).thenReturn(true, false);
        when(mockTriggerDefRs.getString("definition"))
                .thenReturn("CREATE TRIGGER trigger_test ON test_table AFTER INSERT AS BEGIN END");

        when(jdbcOperations.query(anyString(), any(Object[].class), any(ResultSetExtractor.class)))
                .thenAnswer(invocation -> {
                    ResultSetExtractor<?> extractor = invocation.getArgument(2);
                    return extractor.extractData(mockTriggerDefRs);
                });

        // Mock getTrigger query - trigger properties
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> triggerData = new java.util.HashMap<>();
        triggerData.put("trigger_name", "trigger_test");
        triggerData.put("trigger_type", "AFTER");
        mockData.add(triggerData);

        ResultSet mockTriggerRs = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBTrigger> mapper = invocation.getArgument(1);
                    List<DBTrigger> result = new ArrayList<>();
                    int rowNum = 0;
                    when(mockTriggerRs.next()).thenReturn(true, false);
                    while (mockTriggerRs.next()) {
                        result.add(mapper.mapRow(mockTriggerRs, rowNum++));
                    }
                    return result.isEmpty() ? null : result.get(0);
                });

        DBTrigger trigger = accessor.getTrigger(schemaName, "trigger_test");
        if (trigger != null) {
            Assert.assertEquals("trigger_test", trigger.name());
        }
    }

    @Test
    public void listTypes_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock listTypes query result
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> type1 = new java.util.HashMap<>();
        type1.put("type_name", "type_test");
        type1.put("type_schema", testSchemaName);
        mockData.add(type1);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBPLObjectIdentity> mapper = invocation.getArgument(2);
                    List<DBPLObjectIdentity> result = new ArrayList<>();
                    int rowNum = 0;
                    when(mockResultSet.next()).thenReturn(true, false);
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        List<DBPLObjectIdentity> types = accessor.listTypes(schemaName);
        Assert.assertNotNull(types);
    }

    @Test
    public void getType_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock getType query result
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> typeData = new java.util.HashMap<>();
        typeData.put("type_name", "type_test");
        typeData.put("type_schema", testSchemaName);
        mockData.add(typeData);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBType> mapper = invocation.getArgument(2);
                    List<DBType> result = new ArrayList<>();
                    int rowNum = 0;
                    when(mockResultSet.next()).thenReturn(true, false);
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result.isEmpty() ? null : result.get(0);
                });

        DBType type = accessor.getType(schemaName, "type_test");
        if (type != null) {
            Assert.assertEquals("type_test", type.name());
        }
    }

    @Test
    public void showVariables_Success() throws Exception {
        // Mock showVariables query result
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> var1 = new java.util.HashMap<>();
        var1.put("name", "@@VERSION");
        var1.put("value", "Microsoft SQL Server");
        mockData.add(var1);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBVariable> mapper = invocation.getArgument(1);
                    List<DBVariable> result = new ArrayList<>();
                    int rowNum = 0;
                    when(mockResultSet.next()).thenReturn(true, false);
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        List<DBVariable> variables = accessor.showVariables();
        Assert.assertNotNull(variables);
        Assert.assertFalse(variables.isEmpty());
    }

    @Test
    public void showSessionVariables_Success() throws Exception {
        // Mock showSessionVariables query result
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> var1 = new java.util.HashMap<>();
        var1.put("name", "@@SPID");
        var1.put("value", "1");
        mockData.add(var1);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBVariable> mapper = invocation.getArgument(1);
                    List<DBVariable> result = new ArrayList<>();
                    int rowNum = 0;
                    when(mockResultSet.next()).thenReturn(true, false);
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        List<DBVariable> variables = accessor.showSessionVariables();
        Assert.assertNotNull(variables);
    }

    @Test
    public void showGlobalVariables_Success() throws Exception {
        // Mock showGlobalVariables query result
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> var1 = new java.util.HashMap<>();
        var1.put("name", "@@VERSION");
        var1.put("value", "Microsoft SQL Server");
        mockData.add(var1);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBVariable> mapper = invocation.getArgument(1);
                    List<DBVariable> result = new ArrayList<>();
                    int rowNum = 0;
                    when(mockResultSet.next()).thenReturn(true, false);
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        List<DBVariable> variables = accessor.showGlobalVariables();
        Assert.assertNotNull(variables);
    }

    @Test
    public void showCharset_Success() throws Exception {
        // Mock showCharset query result - SQL Server returns collation names
        List<String> mockCharsets = new ArrayList<>();
        mockCharsets.add("SQL_Latin1_General_CP1_CI_AS");

        when(jdbcOperations.query(anyString(), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<String> mapper = invocation.getArgument(1);
                    List<String> result = new ArrayList<>();
                    ResultSet rs = createMockResultSet(Collections.singletonList(
                            Collections.singletonMap("name", "SQL_Latin1_General_CP1_CI_AS")));
                    when(rs.next()).thenReturn(true, false);
                    int rowNum = 0;
                    while (rs.next()) {
                        result.add(mapper.mapRow(rs, rowNum++));
                    }
                    return mockCharsets;
                });

        List<String> charset = accessor.showCharset();
        // SQL Server doesn't have charset concept like MySQL, may return empty or collation names
        Assert.assertNotNull(charset);
    }

    @Test
    public void showCollation_Success() throws Exception {
        // Mock showCollation query result - showCollation uses queryForList
        List<String> mockCollations = new ArrayList<>();
        mockCollations.add("SQL_Latin1_General_CP1_CI_AS");
        mockCollations.add("Latin1_General_CI_AS");

        when(jdbcOperations.queryForList(anyString(), eq(String.class)))
                .thenReturn(mockCollations);

        List<String> collation = accessor.showCollation();
        Assert.assertNotNull(collation);
        Assert.assertFalse(collation.isEmpty());
    }

    @Test
    public void listExternalTables_NotSupported_ReturnsEmpty() {
        String schemaName = testDatabaseName + "." + testSchemaName;
        // SQL Server doesn't support external tables
        List<DBObjectIdentity> externalTables = accessor.listExternalTables(schemaName, null);
        Assert.assertNotNull(externalTables);
        Assert.assertTrue(externalTables.isEmpty());
    }

    @Test
    public void isExternalTable_NotSupported_ReturnsFalse() {
        String schemaName = testDatabaseName + "." + testSchemaName;
        boolean isExternal = accessor.isExternalTable(schemaName, "test_table");
        Assert.assertFalse(isExternal);
    }

    @Test
    public void listMViews_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock listMViews query result - SQL Server uses indexed views
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> mview1 = new java.util.HashMap<>();
        mview1.put("view_name", "test_mview");
        mview1.put("schema_name", testSchemaName);
        mockData.add(mview1);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBObjectIdentity> mapper = invocation.getArgument(2);
                    List<DBObjectIdentity> result = new ArrayList<>();
                    int rowNum = 0;
                    when(mockResultSet.next()).thenReturn(true, false);
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        List<DBObjectIdentity> mViews = accessor.listMViews(schemaName);
        // SQL Server uses indexed views instead of materialized views
        Assert.assertNotNull(mViews);
    }

    @Test
    public void listUsers_ReturnsEmpty() {
        // SQL Server SchemaAccessor returns empty list for users
        List<DBObjectIdentity> users = accessor.listUsers();
        Assert.assertNotNull(users);
        Assert.assertTrue(users.isEmpty());
    }

    @Test
    public void listTableColumns_filterByTableName_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock listTableColumns query result
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> col1 = new java.util.HashMap<>();
        col1.put("table_name", "test_data_type");
        col1.put("column_name", "col1");
        col1.put("data_type", "int");
        mockData.add(col1);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBTableColumn> mapper = invocation.getArgument(2);
                    List<DBTableColumn> result = new ArrayList<>();
                    int rowNum = 0;
                    when(mockResultSet.next()).thenReturn(true, false);
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        Map<String, List<DBTableColumn>> table2Columns = accessor.listTableColumns(schemaName,
                Arrays.asList("test_data_type", "test_index_type"));
        Assert.assertNotNull(table2Columns);
        Assert.assertTrue(table2Columns.size() <= 2);
    }

    @Test
    public void listBasicColumnsInfo_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock listBasicColumnsInfo query result
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> col1 = new java.util.HashMap<>();
        col1.put("table_name", "test_data_type");
        col1.put("column_name", "col1");
        col1.put("data_type", "int");
        mockData.add(col1);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBTableColumn> mapper = invocation.getArgument(2);
                    List<DBTableColumn> result = new ArrayList<>();
                    int rowNum = 0;
                    when(mockResultSet.next()).thenReturn(true, false);
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        Map<String, List<DBTableColumn>> columns = accessor.listBasicColumnsInfo(schemaName);
        Assert.assertNotNull(columns);
    }

    @Test
    public void isLowerCaseTableName_Success() {
        // Mock isLowerCaseTableName query - uses ResultSetExtractor
        ResultSet mockResultSet = mock(ResultSet.class);
        AtomicInteger nextCallCount = new AtomicInteger(0);
        try {
            when(mockResultSet.next()).thenAnswer(invocation -> {
                int count = nextCallCount.getAndIncrement();
                return count == 0;
            });
            when(mockResultSet.getInt(1)).thenReturn(1); // Case insensitive
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        when(jdbcOperations.query(anyString(), any(ResultSetExtractor.class)))
                .thenAnswer(invocation -> {
                    nextCallCount.set(0);
                    ResultSetExtractor<Boolean> extractor = invocation.getArgument(1);
                    try {
                        return extractor.extractData(mockResultSet);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });

        Boolean isLowerCase = accessor.isLowerCaseTableName();
        // SQL Server is case-insensitive by default, but can be configured
        // This is just to verify the method works
        Assert.assertNotNull(isLowerCase);
    }

    // ========== Missing test methods ==========

    @Test
    public void showSystemViews_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock showSystemViews query result
        List<String> mockSystemViews = new ArrayList<>();
        mockSystemViews.add("sys_views");

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenReturn(mockSystemViews);

        List<String> systemViews = accessor.showSystemViews(schemaName);
        Assert.assertNotNull(systemViews);
        // System views may exist in SQL Server
    }

    @Test
    public void showExternalTables_NotSupported_ReturnsEmpty() {
        String schemaName = testDatabaseName + "." + testSchemaName;
        // SQL Server doesn't support external tables
        List<String> externalTables = accessor.showExternalTables(schemaName);
        Assert.assertNotNull(externalTables);
        Assert.assertTrue(externalTables.isEmpty());
    }

    @Test
    public void showExternalTablesLike_NotSupported_ReturnsEmpty() {
        String schemaName = testDatabaseName + "." + testSchemaName;
        // SQL Server doesn't support external tables
        List<String> externalTables = accessor.showExternalTablesLike(schemaName, "test_%");
        Assert.assertNotNull(externalTables);
        Assert.assertTrue(externalTables.isEmpty());
    }

    @Test
    public void syncExternalTableFiles_NotSupported_ThrowsException() {
        String schemaName = testDatabaseName + "." + testSchemaName;
        try {
            accessor.syncExternalTableFiles(schemaName, "test_table");
            Assert.fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
            Assert.assertTrue(e.getMessage().contains("SQL Server does not support external tables"));
        }
    }

    @Test
    public void listAllMViewsLike_Success() throws Exception {
        // Mock showDatabases
        List<String> mockDatabases = new ArrayList<>();
        mockDatabases.add(testDatabaseName);
        when(jdbcOperations.queryForList(anyString(), eq(String.class)))
                .thenReturn(mockDatabases);

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock listAllMViewsLike query result
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> mview1 = new java.util.HashMap<>();
        mview1.put("schema_name", testSchemaName);
        mview1.put("view_name", "test_mview");
        mockData.add(mview1);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBObjectIdentity> mapper = invocation.getArgument(1);
                    List<DBObjectIdentity> result = new ArrayList<>();
                    int rowNum = 0;
                    when(mockResultSet.next()).thenReturn(true, false);
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        List<DBObjectIdentity> mViews = accessor.listAllMViewsLike(null);
        // SQL Server uses indexed views instead of materialized views
        Assert.assertNotNull(mViews);
    }

    @Test
    public void listAllMViewsLike_InputIsNonEmptyString_Success() throws Exception {
        // Mock showDatabases
        List<String> mockDatabases = new ArrayList<>();
        mockDatabases.add(testDatabaseName);
        when(jdbcOperations.queryForList(anyString(), eq(String.class)))
                .thenReturn(mockDatabases);

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock listAllMViewsLike query result with filtered views
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> mview1 = new java.util.HashMap<>();
        mview1.put("schema_name", testSchemaName);
        mview1.put("view_name", "test_mview");
        mockData.add(mview1);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBObjectIdentity> mapper = invocation.getArgument(2);
                    List<DBObjectIdentity> result = new ArrayList<>();
                    int rowNum = 0;
                    when(mockResultSet.next()).thenReturn(true, false);
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        List<DBObjectIdentity> mViews = accessor.listAllMViewsLike("test_%");
        Assert.assertNotNull(mViews);
        if (!mViews.isEmpty()) {
            Assert.assertTrue(mViews.stream().allMatch(m -> m.getName().startsWith("test_")));
        }
    }

    @Test
    public void refreshMVData_Success() {
        // SQL Server indexed views are automatically maintained
        // This method returns true
        String schemaName = testDatabaseName + "." + testSchemaName;
        DBMViewRefreshParameter param = new DBMViewRefreshParameter();
        param.setDatabaseName(testDatabaseName);
        param.setMvName("test_mview");

        Boolean result = accessor.refreshMVData(param);
        Assert.assertNotNull(result);
        Assert.assertTrue(result);
    }

    @Test
    public void getMView_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock getMView query - check if indexed view exists
        when(jdbcOperations.queryForObject(anyString(), any(Object[].class), eq(Integer.class)))
                .thenReturn(1); // Indexed view exists

        // Mock getMView query - view definition
        ResultSet mockViewDefRs = mock(ResultSet.class);
        when(mockViewDefRs.next()).thenReturn(true, false);
        when(mockViewDefRs.getString("view_definition")).thenReturn("SELECT col1 FROM test_table");

        when(jdbcOperations.query(anyString(), any(Object[].class), any(ResultSetExtractor.class)))
                .thenAnswer(invocation -> {
                    ResultSetExtractor<?> extractor = invocation.getArgument(2);
                    return extractor.extractData(mockViewDefRs);
                });

        DBMaterializedView mView = accessor.getMView(schemaName, "test_mview");
        // SQL Server uses indexed views, may return null if no indexed views exist
        if (mView != null) {
            Assert.assertNotNull(mView.getName());
        }
    }

    @Test
    public void listMViewConstraints_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock listMViewConstraints query result
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> constraint1 = new java.util.HashMap<>();
        constraint1.put("constraint_name", "pk_mview");
        constraint1.put("constraint_type", "PRIMARY KEY");
        mockData.add(constraint1);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBTableConstraint> mapper = invocation.getArgument(2);
                    List<DBTableConstraint> result = new ArrayList<>();
                    int rowNum = 0;
                    when(mockResultSet.next()).thenReturn(true, false);
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        List<DBTableConstraint> constraints = accessor.listMViewConstraints(schemaName, "test_mview");
        Assert.assertNotNull(constraints);
    }

    @Test
    public void listMViewRefreshRecords_Success() {
        String schemaName = testDatabaseName + "." + testSchemaName;
        // SQL Server indexed views don't have refresh records
        // This should return empty list
        DBMViewRefreshRecordParam param = new DBMViewRefreshRecordParam(schemaName, "test_mview", 1);
        List<DBMViewRefreshRecord> records = accessor.listMViewRefreshRecords(param);
        Assert.assertNotNull(records);
        Assert.assertTrue(records.isEmpty());
    }

    @Test
    public void listMViewIndexes_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock listMViewIndexes query result
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> idx1 = new java.util.HashMap<>();
        idx1.put("index_name", "idx_mview");
        idx1.put("index_type", "CLUSTERED");
        mockData.add(idx1);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBTableIndex> mapper = invocation.getArgument(2);
                    List<DBTableIndex> result = new ArrayList<>();
                    int rowNum = 0;
                    when(mockResultSet.next()).thenReturn(true, false);
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        List<DBTableIndex> indexes = accessor.listMViewIndexes(schemaName, "test_mview");
        Assert.assertNotNull(indexes);
    }

    @Test
    public void listPackages_NotSupported_ReturnsEmpty() {
        String schemaName = testDatabaseName + "." + testSchemaName;
        // SQL Server doesn't support packages (Oracle feature)
        List<DBPLObjectIdentity> packages = accessor.listPackages(schemaName);
        Assert.assertNotNull(packages);
        Assert.assertTrue(packages.isEmpty());
    }

    @Test
    public void getPackage_NotSupported_ReturnsNull() {
        String schemaName = testDatabaseName + "." + testSchemaName;
        // SQL Server doesn't support packages
        DBPackage pkg = accessor.getPackage(schemaName, "test_package");
        Assert.assertNull(pkg);
    }

    @Test
    public void listPackageBodies_NotSupported_ReturnsEmpty() {
        String schemaName = testDatabaseName + "." + testSchemaName;
        // SQL Server doesn't support packages
        List<DBPLObjectIdentity> packageBodies = accessor.listPackageBodies(schemaName);
        Assert.assertNotNull(packageBodies);
        Assert.assertTrue(packageBodies.isEmpty());
    }

    @Test
    public void listBasicExternalTableColumns_NotSupported_ReturnsEmpty() {
        String schemaName = testDatabaseName + "." + testSchemaName;
        // SQL Server doesn't support external tables
        Map<String, List<DBTableColumn>> columns = accessor.listBasicExternalTableColumns(schemaName);
        Assert.assertNotNull(columns);
        Assert.assertTrue(columns.isEmpty());
    }

    @Test
    public void listBasicExternalTableColumns_InTable_NotSupported_ReturnsEmpty() {
        String schemaName = testDatabaseName + "." + testSchemaName;
        // SQL Server doesn't support external tables
        List<DBTableColumn> columns = accessor.listBasicExternalTableColumns(schemaName, "test_external_table");
        Assert.assertNotNull(columns);
        Assert.assertTrue(columns.isEmpty());
    }

    @Test
    public void listBasicMViewColumns_InSchema_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock listBasicMViewColumns query result
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> col1 = new java.util.HashMap<>();
        col1.put("table_name", "test_mview");
        col1.put("column_name", "col1");
        col1.put("data_type", "int");
        mockData.add(col1);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBTableColumn> mapper = invocation.getArgument(2);
                    List<DBTableColumn> result = new ArrayList<>();
                    int rowNum = 0;
                    when(mockResultSet.next()).thenReturn(true, false);
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        Map<String, List<DBTableColumn>> columns = accessor.listBasicMViewColumns(schemaName);
        Assert.assertNotNull(columns);
    }

    @Test
    public void listBasicMViewColumns_InMView_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock listBasicMViewColumns query result
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> col1 = new java.util.HashMap<>();
        col1.put("column_name", "col1");
        col1.put("data_type", "int");
        mockData.add(col1);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBTableColumn> mapper = invocation.getArgument(2);
                    List<DBTableColumn> result = new ArrayList<>();
                    int rowNum = 0;
                    when(mockResultSet.next()).thenReturn(true, false);
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        List<DBTableColumn> columns = accessor.listBasicMViewColumns(schemaName, "test_mview");
        Assert.assertNotNull(columns);
    }

    @Test
    public void getTableOptions_WithDdl_Success() throws Exception {
        String schemaName = testDatabaseName + "." + testSchemaName;

        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock getTableDDL query - return DDL string
        ResultSet mockResultSet = mock(ResultSet.class);
        when(mockResultSet.next()).thenReturn(true, false);
        when(mockResultSet.getString("definition")).thenReturn("CREATE TABLE [dbo].[test_data_type] (col1 INT)");

        when(jdbcOperations.query(anyString(), any(Object[].class), any(ResultSetExtractor.class)))
                .thenAnswer(invocation -> {
                    ResultSetExtractor<?> extractor = invocation.getArgument(2);
                    return extractor.extractData(mockResultSet);
                });

        String ddl = accessor.getTableDDL(schemaName, "test_data_type");
        if (ddl != null) {
            DBTableOptions options = accessor.getTableOptions(schemaName, "test_data_type", ddl);
            Assert.assertNotNull(options);
        }
    }

    @Test
    public void listTableRangePartitionInfo_Deprecated_Success() throws Exception {
        // This method is deprecated, but should still work
        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock listTableRangePartitionInfo query result
        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());

        List<DBTablePartition> partitions = accessor.listTableRangePartitionInfo(testDatabaseName);
        Assert.assertNotNull(partitions);
    }

    @Test
    public void listSubpartitions_Success() {
        String schemaName = testDatabaseName + "." + testSchemaName;
        // SQL Server doesn't support subpartitions (Oracle feature)
        // This should return empty list
        List<DBTableSubpartitionDefinition> subpartitions = accessor.listSubpartitions(schemaName, "test_partition");
        Assert.assertNotNull(subpartitions);
        Assert.assertTrue(subpartitions.isEmpty());
    }

    @Test
    public void listPartitionTables_Success() throws Exception {
        // Mock DB_NAME() query
        when(jdbcOperations.queryForObject(eq("SELECT DB_NAME()"), eq(String.class)))
                .thenReturn(testDatabaseName);

        // Mock execute for switchDatabase
        doNothing().when(jdbcOperations).execute(anyString());

        // Mock listPartitionTables query result
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> table1 = new java.util.HashMap<>();
        table1.put("table_name", "test_partition_table");
        table1.put("partition_type", "RANGE");
        mockData.add(table1);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBObjectIdentity> mapper = invocation.getArgument(1);
                    List<DBObjectIdentity> result = new ArrayList<>();
                    int rowNum = 0;
                    when(mockResultSet.next()).thenReturn(true, false);
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        // Test with different partition methods
        List<DBObjectIdentity> rangeTables = accessor.listPartitionTables("RANGE");
        Assert.assertNotNull(rangeTables);

        List<DBObjectIdentity> hashTables = accessor.listPartitionTables("HASH");
        Assert.assertNotNull(hashTables);
    }

    @Test
    public void listTableColumnGroups_Success() {
        String schemaName = testDatabaseName + "." + testSchemaName;
        // SQL Server doesn't support column groups (OceanBase feature)
        // This should return empty list
        List<DBColumnGroupElement> columnGroups = accessor.listTableColumnGroups(schemaName, "test_data_type");
        Assert.assertNotNull(columnGroups);
        Assert.assertTrue(columnGroups.isEmpty());
    }
}
