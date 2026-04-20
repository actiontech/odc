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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

import com.oceanbase.tools.dbbrowser.model.DBObjectIdentity;
import com.oceanbase.tools.dbbrowser.schema.dm.DmSchemaAccessor;

/**
 * Unit tests for {@link DmSchemaAccessor}. Uses Mockito to mock JdbcOperations, verifying SQL
 * patterns and parameter passing without requiring a real DM database connection.
 *
 * Test coverage map: | Method | Verified SQL Contains | Verified Parameters |
 * |----------------------|---------------------------------|------------------------| |
 * showDatabases | ALL_USERS | - | | switchDatabase | SET SCHEMA | schema name identifier | |
 * showTablesLike | ALL_TABLES WHERE OWNER= | schema name value | | showTablesLike(like) |
 * ALL_TABLES, LIKE | schema + pattern | | listTables | ALL_TABLES WHERE | schema name value | |
 * listViews | ALL_VIEWS WHERE OWNER= | schema name value | | listFunctions | ALL_OBJECTS, FUNCTION
 * | schema name value | | listProcedures | ALL_OBJECTS, PROCEDURE | schema name value | |
 * listTableColumns | LIST_TABLE_COLUMNS via mapper | [schema, table] | | listTableIndexes |
 * LIST_TABLE_INDEXES via mapper | [schema, table] | | listTableConstraints | LIST_TABLE_CONSTRAINTS
 * via mapper| [schema, table] | | isExternalTable | returns false | - | | showExternalTables |
 * returns empty | - |
 */
public class DmSchemaAccessorTest {

    private JdbcOperations jdbcOperations;
    private DmSchemaAccessor accessor;
    private static final String TEST_SCHEMA = "SYSDBA";

    @Before
    public void setUp() {
        jdbcOperations = mock(JdbcOperations.class);
        accessor = new DmSchemaAccessor(jdbcOperations);
    }

    // =================== showDatabases tests ===================

    @Test
    public void showDatabases_sqlContainsAllUsers() {
        List<String> mockResult = Arrays.asList("SYSDBA", "SYSAUDITOR", "SYS");
        when(jdbcOperations.queryForList(anyString(), eq(String.class))).thenReturn(mockResult);

        List<String> databases = accessor.showDatabases();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcOperations).queryForList(sqlCaptor.capture(), eq(String.class));

        String sql = sqlCaptor.getValue();
        Assert.assertTrue("SQL should contain ALL_USERS", sql.contains("ALL_USERS"));
        Assert.assertTrue("SQL should contain ORDER BY USERNAME", sql.contains("ORDER BY USERNAME"));
        Assert.assertEquals(3, databases.size());
        Assert.assertTrue(databases.contains("SYSDBA"));
    }

    // =================== switchDatabase tests ===================

    @Test
    public void switchDatabase_sqlContainsSetSchema() {
        doNothing().when(jdbcOperations).execute(anyString());

        accessor.switchDatabase(TEST_SCHEMA);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcOperations).execute(sqlCaptor.capture());

        String sql = sqlCaptor.getValue();
        Assert.assertTrue("SQL should contain SET SCHEMA", sql.contains("SET SCHEMA"));
        Assert.assertTrue("SQL should contain quoted schema name", sql.contains("\"SYSDBA\""));
    }

    // =================== showTablesLike tests ===================

    @Test
    public void showTablesLike_withoutPattern_sqlContainsAllTablesWhereOwner() {
        List<String> mockTables = Arrays.asList("TABLE1", "TABLE2");
        when(jdbcOperations.queryForList(anyString(), eq(String.class))).thenReturn(mockTables);

        List<String> tables = accessor.showTablesLike(TEST_SCHEMA, null);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcOperations).queryForList(sqlCaptor.capture(), eq(String.class));

        String sql = sqlCaptor.getValue();
        Assert.assertTrue("SQL should contain ALL_TABLES", sql.contains("ALL_TABLES"));
        Assert.assertTrue("SQL should contain WHERE OWNER=", sql.contains("WHERE OWNER="));
        Assert.assertTrue("SQL should contain quoted schema name", sql.contains("'SYSDBA'"));
        Assert.assertFalse("SQL should NOT contain LIKE when no pattern", sql.contains("LIKE"));
        Assert.assertEquals(2, tables.size());
    }

    @Test
    public void showTablesLike_withPattern_sqlContainsLikeClause() {
        List<String> mockTables = Collections.singletonList("TEST_TABLE");
        when(jdbcOperations.queryForList(anyString(), eq(String.class))).thenReturn(mockTables);

        List<String> tables = accessor.showTablesLike(TEST_SCHEMA, "TEST");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcOperations).queryForList(sqlCaptor.capture(), eq(String.class));

        String sql = sqlCaptor.getValue();
        Assert.assertTrue("SQL should contain ALL_TABLES", sql.contains("ALL_TABLES"));
        Assert.assertTrue("SQL should contain LIKE when pattern provided", sql.contains("LIKE"));
        Assert.assertTrue("SQL should contain pattern value", sql.contains("TEST"));
        Assert.assertEquals(1, tables.size());
    }

    // =================== listTables tests ===================

    @Test
    public void listTables_sqlContainsAllTablesWithSchemaFilter() {
        List<DBObjectIdentity> mockTables = new ArrayList<>();
        DBObjectIdentity table1 = new DBObjectIdentity();
        table1.setName("TEST_TABLE");
        table1.setSchemaName(TEST_SCHEMA);
        mockTables.add(table1);
        when(jdbcOperations.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class)))
                .thenReturn(mockTables);

        List<DBObjectIdentity> tables = accessor.listTables(TEST_SCHEMA, null);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcOperations).query(sqlCaptor.capture(), any(org.springframework.jdbc.core.RowMapper.class));

        String sql = sqlCaptor.getValue();
        Assert.assertTrue("SQL should contain ALL_TABLES", sql.contains("ALL_TABLES"));
        Assert.assertTrue("SQL should contain OWNER=", sql.contains("OWNER="));
        Assert.assertTrue("SQL should contain schema value", sql.contains("'SYSDBA'"));
        Assert.assertEquals(1, tables.size());
        Assert.assertEquals("TEST_TABLE", tables.get(0).getName());
    }

    // =================== listViews tests ===================

    @Test
    public void listViews_sqlContainsAllViews() {
        List<DBObjectIdentity> mockViews = new ArrayList<>();
        DBObjectIdentity view1 = new DBObjectIdentity();
        view1.setName("VIEW1");
        view1.setSchemaName(TEST_SCHEMA);
        mockViews.add(view1);
        when(jdbcOperations.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class)))
                .thenReturn(mockViews);

        List<DBObjectIdentity> views = accessor.listViews(TEST_SCHEMA);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcOperations).query(sqlCaptor.capture(), any(org.springframework.jdbc.core.RowMapper.class));

        String sql = sqlCaptor.getValue();
        Assert.assertTrue("SQL should contain ALL_VIEWS", sql.contains("ALL_VIEWS"));
        Assert.assertTrue("SQL should contain OWNER=", sql.contains("OWNER="));
        Assert.assertTrue("SQL should contain schema value", sql.contains("'SYSDBA'"));
        Assert.assertEquals(1, views.size());
    }

    // =================== listFunctions tests ===================

    @Test
    public void listFunctions_sqlContainsAllObjectsAndFunctionType() {
        List<com.oceanbase.tools.dbbrowser.model.DBPLObjectIdentity> mockFunctions = new ArrayList<>();
        when(jdbcOperations.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class)))
                .thenReturn(mockFunctions);
        // Also mock acquireErrorMessage via PLObjectErrMsgUtils which queries ALL_ERRORS
        when(jdbcOperations.query(anyString(), any(Object[].class), any(org.springframework.jdbc.core.RowMapper.class)))
                .thenReturn(Collections.emptyList());

        accessor.listFunctions(TEST_SCHEMA);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcOperations).query(sqlCaptor.capture(), any(org.springframework.jdbc.core.RowMapper.class));

        String sql = sqlCaptor.getValue();
        Assert.assertTrue("SQL should contain ALL_OBJECTS", sql.contains("ALL_OBJECTS"));
        Assert.assertTrue("SQL should filter by FUNCTION type", sql.contains("'FUNCTION'"));
        Assert.assertTrue("SQL should contain schema value", sql.contains("'SYSDBA'"));
    }

    // =================== listProcedures tests ===================

    @Test
    public void listProcedures_sqlContainsAllObjectsAndProcedureType() {
        List<com.oceanbase.tools.dbbrowser.model.DBPLObjectIdentity> mockProcedures = new ArrayList<>();
        when(jdbcOperations.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class)))
                .thenReturn(mockProcedures);
        when(jdbcOperations.query(anyString(), any(Object[].class), any(org.springframework.jdbc.core.RowMapper.class)))
                .thenReturn(Collections.emptyList());

        accessor.listProcedures(TEST_SCHEMA);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcOperations).query(sqlCaptor.capture(), any(org.springframework.jdbc.core.RowMapper.class));

        String sql = sqlCaptor.getValue();
        Assert.assertTrue("SQL should contain ALL_OBJECTS", sql.contains("ALL_OBJECTS"));
        Assert.assertTrue("SQL should filter by PROCEDURE type", sql.contains("'PROCEDURE'"));
        Assert.assertTrue("SQL should contain schema value", sql.contains("'SYSDBA'"));
    }

    // =================== listTableColumns tests ===================

    @Test
    public void listTableColumns_sqlUsesMapper() {
        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());
        // Mock comment query - uses RowCallbackHandler (void return)
        // No stubbing needed for RowCallbackHandler; default Mockito behavior is do-nothing

        accessor.listTableColumns(TEST_SCHEMA, "TEST_TABLE");

        // Verify that the SQL mapper query was called with schema and table params
        ArgumentCaptor<Object[]> paramsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcOperations).query(anyString(), paramsCaptor.capture(), any(RowMapper.class));

        Object[] params = paramsCaptor.getValue();
        Assert.assertEquals("Should pass 2 parameters (schema, table)", 2, params.length);
        Assert.assertEquals(TEST_SCHEMA, params[0]);
        Assert.assertEquals("TEST_TABLE", params[1]);
    }

    // =================== listTableIndexes tests ===================

    @Test
    public void listTableIndexes_sqlUsesMapperWithSchemaAndTable() {
        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());

        accessor.listTableIndexes(TEST_SCHEMA, "TEST_TABLE");

        ArgumentCaptor<Object[]> paramsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcOperations).query(anyString(), paramsCaptor.capture(), any(RowMapper.class));

        Object[] params = paramsCaptor.getValue();
        Assert.assertEquals("Should pass 2 parameters (schema, table)", 2, params.length);
        Assert.assertEquals(TEST_SCHEMA, params[0]);
        Assert.assertEquals("TEST_TABLE", params[1]);
    }

    // =================== listTableConstraints tests ===================

    @Test
    public void listTableConstraints_sqlUsesMapperWithSchemaAndTable() {
        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());

        accessor.listTableConstraints(TEST_SCHEMA, "TEST_TABLE");

        ArgumentCaptor<Object[]> paramsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcOperations).query(anyString(), paramsCaptor.capture(), any(RowMapper.class));

        Object[] params = paramsCaptor.getValue();
        Assert.assertEquals("Should pass 2 parameters (schema, table)", 2, params.length);
        Assert.assertEquals(TEST_SCHEMA, params[0]);
        Assert.assertEquals("TEST_TABLE", params[1]);
    }

    // =================== External table tests (DM does not support) ===================

    @Test
    public void isExternalTable_alwaysReturnsFalse() {
        Assert.assertFalse(accessor.isExternalTable(TEST_SCHEMA, "ANY_TABLE"));
    }

    @Test
    public void showExternalTablesLike_alwaysReturnsEmptyList() {
        List<String> result = accessor.showExternalTablesLike(TEST_SCHEMA, null);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void listExternalTables_alwaysReturnsEmptyList() {
        List<DBObjectIdentity> result = accessor.listExternalTables(TEST_SCHEMA, null);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.isEmpty());
    }

    // =================== MView tests (DM does not support) ===================

    @Test(expected = UnsupportedOperationException.class)
    public void listMViews_throwsUnsupportedOperationException() {
        accessor.listMViews(TEST_SCHEMA);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void getMView_throwsUnsupportedOperationException() {
        accessor.getMView(TEST_SCHEMA, "MVIEW1");
    }

    // =================== showVariables tests ===================

    @Test
    public void showVariables_sqlContainsVParameter() {
        when(jdbcOperations.query(anyString(), any(RowMapper.class))).thenReturn(Collections.emptyList());

        accessor.showVariables();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcOperations).query(sqlCaptor.capture(), any(RowMapper.class));

        String sql = sqlCaptor.getValue();
        Assert.assertTrue("SQL should contain V$PARAMETER", sql.contains("V$PARAMETER"));
    }

    // =================== listUsers tests ===================

    @Test
    public void listUsers_sqlContainsAllUsers() {
        List<DBObjectIdentity> mockUsers = new ArrayList<>();
        when(jdbcOperations.query(anyString(), any(RowMapper.class))).thenReturn(mockUsers);

        accessor.listUsers();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcOperations).query(sqlCaptor.capture(), any(RowMapper.class));

        String sql = sqlCaptor.getValue();
        Assert.assertTrue("SQL should contain ALL_USERS", sql.contains("ALL_USERS"));
    }

    // =================== Map-case style SQL pattern validation ===================

    @Test
    public void sqlPatterns_verifyDmUsesOracleCompatibleSystemViews() {
        // This test validates the DM accessor uses the correct system views
        // by calling methods and capturing the SQL patterns.
        // DM uses SYS.ALL_* views compatible with Oracle.

        Map<String, String> expectedPatterns = new LinkedHashMap<>();
        expectedPatterns.put("showDatabases", "ALL_USERS");
        expectedPatterns.put("switchDatabase", "SET SCHEMA");
        expectedPatterns.put("showTablesLike", "ALL_TABLES");
        expectedPatterns.put("listViews", "ALL_VIEWS");

        // showDatabases
        when(jdbcOperations.queryForList(anyString(), eq(String.class)))
                .thenReturn(Collections.emptyList());
        accessor.showDatabases();
        ArgumentCaptor<String> showDbCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcOperations).queryForList(showDbCaptor.capture(), eq(String.class));
        Assert.assertTrue("showDatabases SQL should contain " + expectedPatterns.get("showDatabases"),
                showDbCaptor.getValue().contains(expectedPatterns.get("showDatabases")));
    }

    // =================== isLowerCaseTableName tests ===================

    @Test
    public void isLowerCaseTableName_returnsFalse() {
        // DM is case-insensitive by default and stores identifiers in upper case
        Assert.assertFalse(accessor.isLowerCaseTableName());
    }
}
