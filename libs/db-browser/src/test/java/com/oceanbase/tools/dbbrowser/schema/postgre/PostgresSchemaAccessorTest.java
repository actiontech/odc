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
package com.oceanbase.tools.dbbrowser.schema.postgre;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;

import com.oceanbase.tools.dbbrowser.model.DBConstraintType;
import com.oceanbase.tools.dbbrowser.model.DBFunction;
import com.oceanbase.tools.dbbrowser.model.DBIndexType;
import com.oceanbase.tools.dbbrowser.model.DBObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;
import com.oceanbase.tools.dbbrowser.model.DBPLObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBProcedure;
import com.oceanbase.tools.dbbrowser.model.DBSequence;
import com.oceanbase.tools.dbbrowser.model.DBTable.DBTableOptions;
import com.oceanbase.tools.dbbrowser.model.DBTableColumn;
import com.oceanbase.tools.dbbrowser.model.DBTableConstraint;
import com.oceanbase.tools.dbbrowser.model.DBTableIndex;
import com.oceanbase.tools.dbbrowser.model.DBTablePartition;
import com.oceanbase.tools.dbbrowser.model.DBTablePartitionType;
import com.oceanbase.tools.dbbrowser.model.DBVariable;
import com.oceanbase.tools.dbbrowser.model.DBView;

/**
 * Unit tests for {@link PostgresSchemaAccessor}
 *
 * This test uses Mockito to mock JdbcOperations, so it doesn't require a real PostgreSQL database.
 */
public class PostgresSchemaAccessorTest {

    private JdbcOperations jdbcOperations;
    private PostgresSchemaAccessor accessor;
    private String testSchemaName = "public";

    @Before
    public void setUp() {
        jdbcOperations = mock(JdbcOperations.class);
        accessor = new PostgresSchemaAccessor(jdbcOperations);
    }

    // ============== listTables Tests ==============

    @Test
    public void listTables_Success() throws Exception {
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> table1 = new HashMap<>();
        table1.put("table_name", "users");
        table1.put("table_comment", "User table");
        mockData.add(table1);

        Map<String, Object> table2 = new HashMap<>();
        table2.put("table_name", "orders");
        table2.put("table_comment", "Order table");
        mockData.add(table2);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBObjectIdentity> mapper = invocation.getArgument(2);
                    List<DBObjectIdentity> result = new ArrayList<>();
                    int rowNum = 0;
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        List<DBObjectIdentity> tables = accessor.listTables(testSchemaName, null);

        Assert.assertNotNull(tables);
        Assert.assertEquals(2, tables.size());
        Assert.assertEquals("users", tables.get(0).getName());
        Assert.assertEquals(DBObjectType.TABLE, tables.get(0).getType());
    }

    @Test
    public void listTables_WithLike_Success() throws Exception {
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> table1 = new HashMap<>();
        table1.put("table_name", "user_accounts");
        table1.put("table_comment", null);
        mockData.add(table1);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBObjectIdentity> mapper = invocation.getArgument(2);
                    List<DBObjectIdentity> result = new ArrayList<>();
                    int rowNum = 0;
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        List<DBObjectIdentity> tables = accessor.listTables(testSchemaName, "user%");

        Assert.assertNotNull(tables);
        Assert.assertEquals(1, tables.size());
    }

    // ============== listTableColumns Tests ==============

    @Test
    public void listTableColumns_Success() throws Exception {
        // Path-A / information_schema implementation: query(sql, RowMapper) twice
        // (PK probe + columns). Align mocks with positional ResultSet getters.
        when(jdbcOperations.queryForObject("SELECT current_database()", String.class)).thenReturn("testdb");

        List<Map<Integer, Object>> mockData = new ArrayList<>();

        Map<Integer, Object> col1 = new HashMap<>();
        col1.put(1, "id");
        col1.put(2, "integer");
        col1.put(3, "int4");
        col1.put(4, null);
        col1.put(5, null);
        col1.put(6, null);
        col1.put(7, "NO");
        col1.put(8, "nextval('users_id_seq'::regclass)");
        col1.put(9, 1);
        mockData.add(col1);

        Map<Integer, Object> col2 = new HashMap<>();
        col2.put(1, "name");
        col2.put(2, "character varying");
        col2.put(3, "varchar");
        col2.put(4, 100);
        col2.put(5, null);
        col2.put(6, null);
        col2.put(7, "YES");
        col2.put(8, null);
        col2.put(9, 2);
        mockData.add(col2);

        Map<Integer, Object> col3 = new HashMap<>();
        col3.put(1, "price");
        col3.put(2, "numeric");
        col3.put(3, "numeric");
        col3.put(4, null);
        col3.put(5, 10);
        col3.put(6, 2);
        col3.put(7, "YES");
        col3.put(8, null);
        col3.put(9, 3);
        mockData.add(col3);

        when(jdbcOperations.query(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            RowMapper mapper = invocation.getArgument(1);
            if (sql.contains("table_constraints")) {
                return Collections.emptyList();
            }
            if (sql.contains("information_schema.columns")) {
                ResultSet rs = createPositionalMockResultSet(mockData);
                List result = new ArrayList<>();
                int rowNum = 0;
                while (rs.next()) {
                    result.add(mapper.mapRow(rs, rowNum++));
                }
                return result;
            }
            return Collections.emptyList();
        });

        List<DBTableColumn> columns = accessor.listTableColumns(testSchemaName, "users");

        Assert.assertNotNull(columns);
        Assert.assertEquals(3, columns.size());

        Assert.assertEquals("id", columns.get(0).getName());
        Assert.assertEquals("int4", columns.get(0).getTypeName());
        Assert.assertFalse(columns.get(0).getNullable());

        Assert.assertEquals("name", columns.get(1).getName());
        Assert.assertTrue(columns.get(1).getNullable());
        Assert.assertEquals(Long.valueOf(100), columns.get(1).getMaxLength());

        Assert.assertEquals("price", columns.get(2).getName());
        Assert.assertEquals(Long.valueOf(10), columns.get(2).getPrecision());
        Assert.assertEquals(Integer.valueOf(2), columns.get(2).getScale());
    }

    @Test
    public void listBasicTableColumns_Success() throws Exception {
        List<Map<String, Object>> mockData = new ArrayList<>();

        Map<String, Object> col1 = new HashMap<>();
        col1.put("table_name", "users");
        col1.put("ordinal_position", 1);
        col1.put("column_name", "id");
        col1.put("data_type", "integer");
        col1.put("type_name", "int4");
        col1.put("column_comment", null);
        mockData.add(col1);

        Map<String, Object> col2 = new HashMap<>();
        col2.put("table_name", "users");
        col2.put("ordinal_position", 2);
        col2.put("column_name", "name");
        col2.put("data_type", "varchar");
        col2.put("type_name", "varchar");
        col2.put("column_comment", "Name");
        mockData.add(col2);

        Map<String, Object> col3 = new HashMap<>();
        col3.put("table_name", "orders");
        col3.put("ordinal_position", 1);
        col3.put("column_name", "id");
        col3.put("data_type", "bigint");
        col3.put("type_name", "int8");
        col3.put("column_comment", null);
        mockData.add(col3);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBTableColumn> mapper = invocation.getArgument(2);
                    List<DBTableColumn> result = new ArrayList<>();
                    int rowNum = 0;
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        Map<String, List<DBTableColumn>> tableColumns = accessor.listBasicTableColumns(testSchemaName);

        Assert.assertNotNull(tableColumns);
        Assert.assertEquals(2, tableColumns.size());
        Assert.assertTrue(tableColumns.containsKey("users"));
        Assert.assertTrue(tableColumns.containsKey("orders"));
        Assert.assertEquals(2, tableColumns.get("users").size());
    }

    // ============== getTableOptions Tests ==============

    @Test
    public void getTableOptions_Success() throws Exception {
        doAnswer(invocation -> {
            ResultSet mockResultSet = mock(ResultSet.class);
            when(mockResultSet.next()).thenReturn(true).thenReturn(false);
            when(mockResultSet.getString("table_comment")).thenReturn("User information table");
            RowCallbackHandler handler = invocation.getArgument(2);
            handler.processRow(mockResultSet);
            return null;
        }).when(jdbcOperations).query(anyString(), any(Object[].class), any(RowCallbackHandler.class));

        DBTableOptions options = accessor.getTableOptions(testSchemaName, "users");

        Assert.assertNotNull(options);
        Assert.assertEquals("User information table", options.getComment());
    }

    @Test
    public void getTableOptions_NoComment_Success() throws Exception {
        doAnswer(invocation -> {
            ResultSet mockResultSet = mock(ResultSet.class);
            when(mockResultSet.next()).thenReturn(true).thenReturn(false);
            when(mockResultSet.getString("table_comment")).thenReturn(null);
            RowCallbackHandler handler = invocation.getArgument(2);
            handler.processRow(mockResultSet);
            return null;
        }).when(jdbcOperations).query(anyString(), any(Object[].class), any(RowCallbackHandler.class));

        DBTableOptions options = accessor.getTableOptions(testSchemaName, "users");

        Assert.assertNotNull(options);
        Assert.assertNull(options.getComment());
    }

    // ============== listTableIndexes Tests ==============

    @Test
    public void listTableIndexes_Success() throws Exception {
        List<Map<String, Object>> mockData = new ArrayList<>();

        Map<String, Object> idx1Col1 = new HashMap<>();
        idx1Col1.put("index_name", "users_pkey");
        idx1Col1.put("is_unique", true);
        idx1Col1.put("is_primary", true);
        idx1Col1.put("index_type", "btree");
        idx1Col1.put("index_definition", "CREATE UNIQUE INDEX users_pkey ON public.users USING btree (id)");
        idx1Col1.put("index_comment", null);
        idx1Col1.put("column_name", "id");
        idx1Col1.put("column_position", 1);
        mockData.add(idx1Col1);

        Map<String, Object> idx2Col1 = new HashMap<>();
        idx2Col1.put("index_name", "users_name_idx");
        idx2Col1.put("is_unique", false);
        idx2Col1.put("is_primary", false);
        idx2Col1.put("index_type", "btree");
        idx2Col1.put("index_definition", "CREATE INDEX users_name_idx ON public.users USING btree (name)");
        idx2Col1.put("index_comment", "Name index");
        idx2Col1.put("column_name", "name");
        idx2Col1.put("column_position", 1);
        mockData.add(idx2Col1);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<Void> mapper = invocation.getArgument(2);
                    int rowNum = 0;
                    while (mockResultSet.next()) {
                        mapper.mapRow(mockResultSet, rowNum++);
                    }
                    return null;
                });

        List<DBTableIndex> indexes = accessor.listTableIndexes(testSchemaName, "users");

        Assert.assertNotNull(indexes);
        Assert.assertEquals(2, indexes.size());

        DBTableIndex pkIndex = indexes.stream()
                .filter(i -> "users_pkey".equals(i.getName()))
                .findFirst().orElse(null);
        Assert.assertNotNull(pkIndex);
        Assert.assertTrue(pkIndex.getPrimary());
        Assert.assertTrue(pkIndex.getUnique());
        Assert.assertEquals(DBIndexType.UNIQUE, pkIndex.getType());

        DBTableIndex normalIndex = indexes.stream()
                .filter(i -> "users_name_idx".equals(i.getName()))
                .findFirst().orElse(null);
        Assert.assertNotNull(normalIndex);
        Assert.assertFalse(normalIndex.getPrimary());
        Assert.assertFalse(normalIndex.getUnique());
    }

    // ============== listTableConstraints Tests ==============

    @Test
    public void listTableConstraints_PrimaryKey_Success() throws Exception {
        List<Map<String, Object>> pkData = new ArrayList<>();
        Map<String, Object> pkCol = new HashMap<>();
        pkCol.put("constraint_name", "users_pkey");
        pkCol.put("constraint_type", "p");
        pkCol.put("column_name", "id");
        pkData.add(pkCol);

        ResultSet pkResultSet = createMockResultSet(pkData);
        ResultSet emptyResultSet = createMockResultSet(new ArrayList<>());

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    RowMapper<Void> mapper = invocation.getArgument(2);
                    ResultSet rs = sql.contains("contype IN") ? pkResultSet : emptyResultSet;
                    int rowNum = 0;
                    while (rs.next()) {
                        mapper.mapRow(rs, rowNum++);
                    }
                    return null;
                });

        List<DBTableConstraint> constraints = accessor.listTableConstraints(testSchemaName, "users");

        Assert.assertNotNull(constraints);
        Assert.assertEquals(1, constraints.size());

        DBTableConstraint pk = constraints.get(0);
        Assert.assertEquals("users_pkey", pk.getName());
        Assert.assertEquals(DBConstraintType.PRIMARY_KEY, pk.getType());
        Assert.assertEquals(1, pk.getColumnNames().size());
        Assert.assertEquals("id", pk.getColumnNames().get(0));
    }

    @Test
    public void listTableConstraints_ForeignKey_Success() throws Exception {
        List<Map<String, Object>> fkData = new ArrayList<>();
        Map<String, Object> fkCol = new HashMap<>();
        fkCol.put("constraint_name", "orders_user_id_fkey");
        fkCol.put("column_name", "user_id");
        fkCol.put("referenced_schema_name", "public");
        fkCol.put("referenced_table_name", "users");
        fkCol.put("referenced_column_name", "id");
        fkCol.put("update_action", "a");
        fkCol.put("delete_action", "c");
        fkData.add(fkCol);

        ResultSet fkResultSet = createMockResultSet(fkData);
        ResultSet emptyResultSet = createMockResultSet(new ArrayList<>());

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    RowMapper<Void> mapper = invocation.getArgument(2);
                    ResultSet rs = sql.contains("contype = 'f'") ? fkResultSet : emptyResultSet;
                    int rowNum = 0;
                    while (rs.next()) {
                        mapper.mapRow(rs, rowNum++);
                    }
                    return null;
                });

        List<DBTableConstraint> constraints = accessor.listTableConstraints(testSchemaName, "orders");

        Assert.assertNotNull(constraints);
        Assert.assertEquals(1, constraints.size());

        DBTableConstraint fk = constraints.get(0);
        Assert.assertEquals("orders_user_id_fkey", fk.getName());
        Assert.assertEquals(DBConstraintType.FOREIGN_KEY, fk.getType());
        Assert.assertEquals("users", fk.getReferenceTableName());
    }

    @Test
    public void listTableConstraints_Check_Success() throws Exception {
        List<Map<String, Object>> checkData = new ArrayList<>();
        Map<String, Object> checkConstraint = new HashMap<>();
        checkConstraint.put("constraint_name", "users_age_check");
        checkConstraint.put("constraint_definition", "CHECK ((age >= 0))");
        checkData.add(checkConstraint);

        ResultSet checkResultSet = createMockResultSet(checkData);
        ResultSet emptyResultSet = createMockResultSet(new ArrayList<>());

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    RowMapper<Void> mapper = invocation.getArgument(2);
                    ResultSet rs = sql.contains("contype = 'c'") ? checkResultSet : emptyResultSet;
                    int rowNum = 0;
                    while (rs.next()) {
                        mapper.mapRow(rs, rowNum++);
                    }
                    return null;
                });

        List<DBTableConstraint> constraints = accessor.listTableConstraints(testSchemaName, "users");

        Assert.assertNotNull(constraints);

        DBTableConstraint check = constraints.stream()
                .filter(c -> c.getType() == DBConstraintType.CHECK)
                .findFirst().orElse(null);
        Assert.assertNotNull(check);
        Assert.assertEquals("users_age_check", check.getName());
    }

    // ============== getPartition Tests ==============

    @Test
    public void getPartition_RangePartition_Success() throws Exception {
        AtomicInteger queryCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            int count = queryCount.getAndIncrement();
            ResultSet mockResultSet = mock(ResultSet.class);
            if (count == 0) {
                // First query: partition check
                when(mockResultSet.next()).thenReturn(true).thenReturn(false);
                when(mockResultSet.getString("relkind")).thenReturn("p");
                when(mockResultSet.getString("partstrat")).thenReturn("r");
            } else {
                // Subsequent queries: partition key and definitions
                when(mockResultSet.next()).thenReturn(false);
            }
            RowCallbackHandler handler = invocation.getArgument(2);
            handler.processRow(mockResultSet);
            return null;
        }).when(jdbcOperations).query(anyString(), any(Object[].class), any(RowCallbackHandler.class));

        // Mock partition definitions query with RowMapper
        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenReturn(null);

        DBTablePartition partition = accessor.getPartition(testSchemaName, "events");

        Assert.assertNotNull(partition);
        Assert.assertEquals(DBTablePartitionType.RANGE, partition.getPartitionOption().getType());
    }

    @Test
    public void getPartition_NotPartitioned_ReturnsNull() throws Exception {
        doAnswer(invocation -> {
            ResultSet mockResultSet = mock(ResultSet.class);
            when(mockResultSet.next()).thenReturn(true).thenReturn(false);
            when(mockResultSet.getString("relkind")).thenReturn("r");
            when(mockResultSet.getString("partstrat")).thenReturn(null);
            RowCallbackHandler handler = invocation.getArgument(2);
            handler.processRow(mockResultSet);
            return null;
        }).when(jdbcOperations).query(anyString(), any(Object[].class), any(RowCallbackHandler.class));

        DBTablePartition partition = accessor.getPartition(testSchemaName, "users");

        Assert.assertNull(partition);
    }

    // ============== getTableDDL Tests ==============

    @Test
    public void getTableDDL_Success() throws Exception {
        when(jdbcOperations.queryForObject("SELECT current_database()", String.class)).thenReturn("testdb");

        List<Map<Integer, Object>> columnsData = new ArrayList<>();
        Map<Integer, Object> col1 = new HashMap<>();
        col1.put(1, "id");
        col1.put(2, "integer");
        col1.put(3, "int4");
        col1.put(4, null);
        col1.put(5, null);
        col1.put(6, null);
        col1.put(7, "NO");
        col1.put(8, "nextval('users_id_seq'::regclass)");
        col1.put(9, 1);
        columnsData.add(col1);

        when(jdbcOperations.query(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            RowMapper mapper = invocation.getArgument(1);
            if (sql.contains("information_schema.columns")) {
                ResultSet rs = createPositionalMockResultSet(columnsData);
                List result = new ArrayList<>();
                int rowNum = 0;
                while (rs.next()) {
                    result.add(mapper.mapRow(rs, rowNum++));
                }
                return result;
            }
            // PK probe → empty
            return Collections.emptyList();
        });

        // constraints / indexes use Object[] + RowMapper
        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());

        // getTableOptions uses ResultSetExtractor lambda
        when(jdbcOperations.query(anyString(), any(Object[].class),
                any(org.springframework.jdbc.core.ResultSetExtractor.class))).thenReturn(null);

        String ddl = accessor.getTableDDL(testSchemaName, "users");

        Assert.assertNotNull(ddl);
        Assert.assertTrue(ddl.contains("CREATE TABLE"));
        Assert.assertTrue(ddl.contains("\"id\""));
        Assert.assertTrue(ddl.contains("int4") || ddl.contains("integer"));
        Assert.assertTrue(ddl.contains("NOT NULL"));
    }

    // ============== listViews Tests ==============

    @Test
    public void listViews_Success() throws Exception {
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> view1 = new HashMap<>();
        view1.put("view_name", "user_view");
        mockData.add(view1);

        Map<String, Object> view2 = new HashMap<>();
        view2.put("view_name", "order_view");
        mockData.add(view2);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBObjectIdentity> mapper = invocation.getArgument(2);
                    List<DBObjectIdentity> result = new ArrayList<>();
                    int rowNum = 0;
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        List<DBObjectIdentity> views = accessor.listViews(testSchemaName);

        Assert.assertNotNull(views);
        Assert.assertEquals(2, views.size());
        Assert.assertEquals("user_view", views.get(0).getName());
        Assert.assertEquals(DBObjectType.VIEW, views.get(0).getType());
    }

    // ============== listFunctions Tests ==============

    @Test
    public void listFunctions_Success() throws Exception {
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> func1 = new HashMap<>();
        func1.put("function_name", "calculate_total");
        func1.put("arguments", "order_id integer");
        func1.put("return_type", "numeric");
        mockData.add(func1);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBPLObjectIdentity> mapper = invocation.getArgument(2);
                    List<DBPLObjectIdentity> result = new ArrayList<>();
                    int rowNum = 0;
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        List<DBPLObjectIdentity> functions = accessor.listFunctions(testSchemaName);

        Assert.assertNotNull(functions);
        Assert.assertEquals(1, functions.size());
        Assert.assertEquals("calculate_total", functions.get(0).getName());
        Assert.assertEquals(DBObjectType.FUNCTION, functions.get(0).getType());
    }

    // ============== listProcedures Tests ==============

    @Test
    public void listProcedures_Success() throws Exception {
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> proc1 = new HashMap<>();
        proc1.put("procedure_name", "process_order");
        mockData.add(proc1);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBPLObjectIdentity> mapper = invocation.getArgument(2);
                    List<DBPLObjectIdentity> result = new ArrayList<>();
                    int rowNum = 0;
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        List<DBPLObjectIdentity> procedures = accessor.listProcedures(testSchemaName);

        Assert.assertNotNull(procedures);
        Assert.assertEquals(1, procedures.size());
        Assert.assertEquals("process_order", procedures.get(0).getName());
        Assert.assertEquals(DBObjectType.PROCEDURE, procedures.get(0).getType());
    }

    // ============== listSequences Tests ==============

    @Test
    public void listSequences_Success() throws Exception {
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> seq1 = new HashMap<>();
        seq1.put("sequence_name", "user_id_seq");
        mockData.add(seq1);

        Map<String, Object> seq2 = new HashMap<>();
        seq2.put("sequence_name", "order_id_seq");
        mockData.add(seq2);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBObjectIdentity> mapper = invocation.getArgument(2);
                    List<DBObjectIdentity> result = new ArrayList<>();
                    int rowNum = 0;
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        List<DBObjectIdentity> sequences = accessor.listSequences(testSchemaName);

        Assert.assertNotNull(sequences);
        Assert.assertEquals(2, sequences.size());
        Assert.assertEquals("user_id_seq", sequences.get(0).getName());
        Assert.assertEquals(DBObjectType.SEQUENCE, sequences.get(0).getType());
    }

    // ============== showVariables Tests ==============

    @Test
    public void showVariables_Success() throws Exception {
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> var1 = new HashMap<>();
        var1.put("name", "work_mem");
        var1.put("value", "4MB");
        mockData.add(var1);

        Map<String, Object> var2 = new HashMap<>();
        var2.put("name", "shared_buffers");
        var2.put("value", "128MB");
        mockData.add(var2);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBVariable> mapper = invocation.getArgument(1);
                    List<DBVariable> result = new ArrayList<>();
                    int rowNum = 0;
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        List<DBVariable> variables = accessor.showVariables();

        Assert.assertNotNull(variables);
        Assert.assertEquals(2, variables.size());
        Assert.assertEquals("work_mem", variables.get(0).getName());
        Assert.assertEquals("4MB", variables.get(0).getValue());
    }

    // ============== showSessionVariables Tests ==============

    @Test
    public void showSessionVariables_Success() throws Exception {
        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> var1 = new HashMap<>();
        var1.put("name", "work_mem");
        var1.put("value", "4MB");
        mockData.add(var1);

        ResultSet mockResultSet = createMockResultSet(mockData);

        when(jdbcOperations.query(anyString(), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DBVariable> mapper = invocation.getArgument(1);
                    List<DBVariable> result = new ArrayList<>();
                    int rowNum = 0;
                    while (mockResultSet.next()) {
                        result.add(mapper.mapRow(mockResultSet, rowNum++));
                    }
                    return result;
                });

        List<DBVariable> variables = accessor.showSessionVariables();

        Assert.assertNotNull(variables);
        Assert.assertEquals(1, variables.size());
        Assert.assertEquals("work_mem", variables.get(0).getName());
    }

    // ============== getFunction Tests ==============

    @Test
    public void getFunction_Success() throws Exception {
        doAnswer(invocation -> {
            ResultSet mockResultSet = mock(ResultSet.class);
            when(mockResultSet.next()).thenReturn(true).thenReturn(false);
            when(mockResultSet.getString("function_name")).thenReturn("calculate_total");
            when(mockResultSet.getString("function_ddl")).thenReturn(
                    "CREATE FUNCTION calculate_total() RETURNS numeric AS $$ BEGIN RETURN 0; END; $$ LANGUAGE plpgsql");
            when(mockResultSet.getString("return_type")).thenReturn("numeric");
            when(mockResultSet.getString("arguments")).thenReturn("");
            RowCallbackHandler handler = invocation.getArgument(2);
            handler.processRow(mockResultSet);
            return null;
        }).when(jdbcOperations).query(anyString(), any(Object[].class), any(RowCallbackHandler.class));

        DBFunction function = accessor.getFunction(testSchemaName, "calculate_total");

        Assert.assertNotNull(function);
        Assert.assertEquals("calculate_total", function.getFunName());
        Assert.assertEquals("numeric", function.getReturnType());
    }

    // ============== getProcedure Tests ==============

    @Test
    public void getProcedure_Success() throws Exception {
        doAnswer(invocation -> {
            ResultSet mockResultSet = mock(ResultSet.class);
            when(mockResultSet.next()).thenReturn(true).thenReturn(false);
            when(mockResultSet.getString("procedure_name")).thenReturn("process_order");
            when(mockResultSet.getString("procedure_ddl"))
                    .thenReturn("CREATE PROCEDURE process_order() AS $$ BEGIN NULL; END; $$ LANGUAGE plpgsql");
            when(mockResultSet.getString("arguments")).thenReturn("");
            RowCallbackHandler handler = invocation.getArgument(2);
            handler.processRow(mockResultSet);
            return null;
        }).when(jdbcOperations).query(anyString(), any(Object[].class), any(RowCallbackHandler.class));

        DBProcedure procedure = accessor.getProcedure(testSchemaName, "process_order");

        Assert.assertNotNull(procedure);
        Assert.assertEquals("process_order", procedure.getProName());
    }

    // ============== getView Tests ==============

    @Test
    public void getView_Success() throws Exception {
        doAnswer(invocation -> {
            ResultSet mockResultSet = mock(ResultSet.class);
            when(mockResultSet.next()).thenReturn(true).thenReturn(false);
            when(mockResultSet.getString("table_schema")).thenReturn(testSchemaName);
            when(mockResultSet.getString("check_option")).thenReturn("NONE");
            when(mockResultSet.getString("is_updatable")).thenReturn("YES");
            when(mockResultSet.getString("view_definition")).thenReturn("SELECT * FROM users");
            RowCallbackHandler handler = invocation.getArgument(2);
            handler.processRow(mockResultSet);
            return null;
        }).when(jdbcOperations).query(anyString(), any(Object[].class), any(RowCallbackHandler.class));

        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenReturn(new ArrayList<>());

        DBView view = accessor.getView(testSchemaName, "user_view");

        Assert.assertNotNull(view);
        Assert.assertEquals("user_view", view.getViewName());
        Assert.assertTrue(view.isUpdatable());
    }

    // ============== getSequence Tests ==============

    @Test
    public void getSequence_Success() throws Exception {
        // Mock first query to check sequence exists
        doAnswer(invocation -> {
            ResultSet mockResultSet = mock(ResultSet.class);
            when(mockResultSet.next()).thenReturn(true).thenReturn(false);
            RowCallbackHandler handler = invocation.getArgument(2);
            handler.processRow(mockResultSet);
            return null;
        }).when(jdbcOperations).query(anyString(), any(Object[].class), any(RowCallbackHandler.class));

        DBSequence sequence = accessor.getSequence(testSchemaName, "user_id_seq");

        Assert.assertNotNull(sequence);
        Assert.assertEquals("user_id_seq", sequence.getName());
    }

    /**
     * AC-RV-BE-003: Path A {@code catalog.schema} must use resolved objectSchema in last_value SQL and
     * CREATE/ALTER DDL (never quote {@code "db_a.app_a"}).
     */
    @Test
    public void getSequence_catalogDotSchema_usesObjectSchemaInSqlAndDdl() throws Exception {
        when(jdbcOperations.queryForObject("SELECT current_database()", String.class)).thenReturn("other_db");

        doAnswer(invocation -> {
            Object[] args = invocation.getArgument(1);
            Assert.assertEquals("app_a", args[0]);
            Assert.assertEquals("user_id_seq", args[1]);
            return null;
        }).when(jdbcOperations).query(anyString(), any(Object[].class), any(RowCallbackHandler.class));

        ArgumentCaptor<String> valueSqlCaptor = ArgumentCaptor.forClass(String.class);
        doAnswer(invocation -> null).when(jdbcOperations).query(valueSqlCaptor.capture(),
                any(RowCallbackHandler.class));

        DBSequence sequence = accessor.getSequence("db_a.app_a", "user_id_seq");

        Assert.assertNotNull(sequence);
        Assert.assertTrue(valueSqlCaptor.getValue().contains("\"app_a\".\"user_id_seq\""));
        Assert.assertFalse(valueSqlCaptor.getValue().contains("\"db_a.app_a\""));
        Assert.assertNotNull(sequence.getDdl());
        Assert.assertTrue(sequence.getDdl().contains("\"app_a\".\"user_id_seq\""));
        Assert.assertFalse(sequence.getDdl().contains("\"db_a.app_a\""));
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

        when(rs.getBoolean(anyString())).thenAnswer(invocation -> {
            Object value = rs.getObject(invocation.getArgument(0));
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
            return false;
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

        return rs;
    }

    /**
     * ResultSet mock driven by 1-based positional maps (for information_schema column queries).
     */
    private ResultSet createPositionalMockResultSet(List<Map<Integer, Object>> data) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        AtomicInteger currentRow = new AtomicInteger(-1);

        when(rs.next()).thenAnswer(invocation -> {
            int next = currentRow.incrementAndGet();
            return next < data.size();
        });

        when(rs.getString(anyInt())).thenAnswer(invocation -> {
            int idx = invocation.getArgument(0);
            Object value = data.get(currentRow.get()).get(idx);
            return value != null ? value.toString() : null;
        });

        when(rs.getObject(anyInt())).thenAnswer(invocation -> {
            int idx = invocation.getArgument(0);
            return data.get(currentRow.get()).get(idx);
        });

        when(rs.getInt(anyInt())).thenAnswer(invocation -> {
            int idx = invocation.getArgument(0);
            Object value = data.get(currentRow.get()).get(idx);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return 0;
        });

        return rs;
    }

    // ============== resolveObjectSchema (Path A) ==============

    @Test
    public void resolveObjectSchema_catalogDotSchema_returnsSchemaPart() {
        Assert.assertEquals("biz_schema", accessor.resolveObjectSchema("testdb.biz_schema"));
    }

    @Test
    public void resolveObjectSchema_legacyCatalogOnly_fallsBackToPublic() {
        when(jdbcOperations.queryForObject("SELECT current_database()", String.class)).thenReturn("testdb");
        Assert.assertEquals("public", accessor.resolveObjectSchema("testdb"));
    }

    @Test
    public void resolveObjectSchema_plainSchema_keptAsIs() {
        when(jdbcOperations.queryForObject("SELECT current_database()", String.class)).thenReturn("testdb");
        Assert.assertEquals("biz_schema", accessor.resolveObjectSchema("biz_schema"));
    }
}
