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
package com.oceanbase.tools.dbbrowser.schema.db2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;

import com.oceanbase.tools.dbbrowser.model.DBObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;
import com.oceanbase.tools.dbbrowser.model.DBPLObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBSynonymType;

/**
 * Mocked JDBC unit tests for {@link DB2SchemaAccessor} (T-003 commit-2).
 *
 * <p>
 * Verifies:
 * <ul>
 * <li>SYSCAT.* / SYSIBM* SQLs are dispatched to JdbcOperations with the right parameters;</li>
 * <li>System schemas (SYS%, NULLID, SQLJ) are filtered out (compat-RISK-7 / readable
 * behavior);</li>
 * <li>Placeholder methods throw {@link UnsupportedOperationException} with the grep-friendly
 * {@code "Not supported for DB2 yet"} keyword (compat-RISK-10);</li>
 * <li>Static "DB2 has no X" methods return empty collections without touching the database (charset
 * / collation / external tables / mview / partitions).</li>
 * </ul>
 */
public class DB2SchemaAccessorTest {

    private JdbcOperations jdbc;
    private DB2SchemaAccessor accessor;

    @Before
    public void setUp() {
        jdbc = mock(JdbcOperations.class);
        accessor = new DB2SchemaAccessor(jdbc);
    }

    // ---------------------------------------------------------------------------------------
    // Database / schema listing
    // ---------------------------------------------------------------------------------------

    @Test
    public void showDatabases_returnsUserSchemas_excludingSystemSchemas() {
        // jdbcOperations.queryForList(sql, String.class) — accessor uses this overload
        when(jdbc.queryForList(anyString(), eq(String.class)))
                .thenReturn(Arrays.asList("DB2INST1", "APP", "REPORTING"));
        List<String> dbs = accessor.showDatabases();
        assertEquals(Arrays.asList("DB2INST1", "APP", "REPORTING"), dbs);
        // SQL should reference SYSCAT.SCHEMATA + filter SYS%
        verify(jdbc).queryForList(argThat((String s) -> s != null && s.contains("SYSCAT.SCHEMATA")
                && s.contains("SCHEMANAME NOT LIKE 'SYS%'")), eq(String.class));
    }

    @Test
    public void listDatabases_wrapsShowDatabases_intoDBDatabaseEntries() {
        when(jdbc.queryForList(anyString(), eq(String.class)))
                .thenReturn(Arrays.asList("APP", "TEST"));
        assertEquals(2, accessor.listDatabases().size());
        assertEquals("APP", accessor.listDatabases().get(0).getName());
    }

    @Test
    public void switchDatabase_throwsUnsupportedWithDb2Keyword() {
        try {
            accessor.switchDatabase("APP");
            fail("Expected UOE");
        } catch (UnsupportedOperationException ex) {
            assertEquals("Not supported for DB2 yet", ex.getMessage());
        }
    }

    @Test
    public void getDatabase_throwsUnsupportedWithDb2Keyword() {
        try {
            accessor.getDatabase("APP");
            fail("Expected UOE");
        } catch (UnsupportedOperationException ex) {
            assertEquals("Not supported for DB2 yet", ex.getMessage());
        }
    }

    @Test
    public void listUsers_throwsUnsupportedWithDb2Keyword() {
        try {
            accessor.listUsers();
            fail("Expected UOE");
        } catch (UnsupportedOperationException ex) {
            assertEquals("Not supported for DB2 yet", ex.getMessage());
        }
    }

    // ---------------------------------------------------------------------------------------
    // Tables / views
    // ---------------------------------------------------------------------------------------

    @Test
    public void showTables_executesAgainstSyscatTablesType_T() {
        when(jdbc.queryForList(anyString(), eq(String.class), any()))
                .thenReturn(Arrays.asList("EMPLOYEE", "DEPARTMENT"));
        List<String> tables = accessor.showTables("DB2INST1");
        assertEquals(Arrays.asList("EMPLOYEE", "DEPARTMENT"), tables);
        verify(jdbc).queryForList(argThat((String s) -> s != null && s.contains("SYSCAT.TABLES")
                && s.contains("TYPE = 'T'")), eq(String.class), eq((Object) "DB2INST1"));
    }

    @Test
    public void showTablesLike_throwsUnsupportedWithDb2Keyword() {
        try {
            accessor.showTablesLike("APP", "%foo%");
            fail("Expected UOE");
        } catch (UnsupportedOperationException ex) {
            assertEquals("Not supported for DB2 yet", ex.getMessage());
        }
    }

    @Test
    public void listTables_buildsDBObjectIdentityFromShowTables() {
        when(jdbc.queryForList(anyString(), eq(String.class), any()))
                .thenReturn(Arrays.asList("EMPLOYEE"));
        List<DBObjectIdentity> result = accessor.listTables("DB2INST1", null);
        assertEquals(1, result.size());
        assertEquals("EMPLOYEE", result.get(0).getName());
        assertEquals("DB2INST1", result.get(0).getSchemaName());
        assertEquals(DBObjectType.TABLE, result.get(0).getType());
    }

    @Test
    public void showExternalTablesLike_returnsEmptyList_DB2HasNoExternalTables() {
        assertEquals(Collections.emptyList(),
                accessor.showExternalTablesLike("APP", "%"));
        verify(jdbc, never()).queryForList(anyString(), eq(String.class), any());
    }

    @Test
    public void isExternalTable_alwaysFalse_DB2HasNoExternalTables() {
        assertFalse(accessor.isExternalTable("APP", "T"));
        verify(jdbc, never()).queryForList(anyString(), eq(String.class), any());
    }

    @Test
    public void syncExternalTableFiles_throwsUnsupported() {
        try {
            accessor.syncExternalTableFiles("APP", "T");
            fail("Expected UOE");
        } catch (UnsupportedOperationException ex) {
            assertEquals("Not supported for DB2 yet", ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void listViews_executesAgainstSyscatViews_returnsViewIdentities() throws SQLException {
        ResultSet rs1 = mockSingleColumnResultSet("NAME", "EMP_VIEW", "DEPT_VIEW");
        when(jdbc.query(anyString(), any(RowMapper.class), any()))
                .thenAnswer(inv -> {
                    RowMapper<DBObjectIdentity> mapper = inv.getArgument(1);
                    List<DBObjectIdentity> out = new java.util.ArrayList<>();
                    while (rs1.next()) {
                        out.add(mapper.mapRow(rs1, 0));
                    }
                    return out;
                });
        List<DBObjectIdentity> views = accessor.listViews("DB2INST1");
        assertEquals(2, views.size());
        assertEquals("EMP_VIEW", views.get(0).getName());
        assertEquals(DBObjectType.VIEW, views.get(0).getType());
        verify(jdbc).query(argThat((String s) -> s != null && s.contains("SYSCAT.VIEWS")
                && s.contains("VIEWSCHEMA = ?")), any(RowMapper.class), eq((Object) "DB2INST1"));
    }

    @Test
    public void showSystemViews_filtersBySchemaAndSysPrefix() {
        when(jdbc.queryForList(anyString(), eq(String.class), any()))
                .thenReturn(Collections.singletonList("TABLES_VIEW"));
        List<String> systemViews = accessor.showSystemViews("SYSCAT");
        assertEquals(1, systemViews.size());
        verify(jdbc).queryForList(argThat((String s) -> s != null
                && s.contains("VIEWSCHEMA LIKE 'SYS%'")), eq(String.class), eq((Object) "SYSCAT"));
    }

    // ---------------------------------------------------------------------------------------
    // Sequences / synonyms (ALIAS)
    // ---------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    @Test
    public void listSequences_executesAgainstSyscatSequencesType_S() throws SQLException {
        ResultSet rs = mockSingleColumnResultSet("NAME", "EMP_SEQ");
        when(jdbc.query(anyString(), any(RowMapper.class), any()))
                .thenAnswer(inv -> {
                    RowMapper<DBObjectIdentity> mapper = inv.getArgument(1);
                    List<DBObjectIdentity> out = new java.util.ArrayList<>();
                    while (rs.next()) {
                        out.add(mapper.mapRow(rs, 0));
                    }
                    return out;
                });
        List<DBObjectIdentity> result = accessor.listSequences("APP");
        assertEquals(1, result.size());
        assertEquals("EMP_SEQ", result.get(0).getName());
        assertEquals(DBObjectType.SEQUENCE, result.get(0).getType());
        verify(jdbc).query(argThat((String s) -> s != null && s.contains("SYSCAT.SEQUENCES")
                && s.contains("SEQTYPE = 'S'")), any(RowMapper.class), eq((Object) "APP"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void listSynonyms_executesAgainstSyscatTablesType_A() throws SQLException {
        ResultSet rs = mockSingleColumnResultSet("NAME", "EMP_ALIAS");
        when(jdbc.query(anyString(), any(RowMapper.class), any()))
                .thenAnswer(inv -> {
                    RowMapper<DBObjectIdentity> mapper = inv.getArgument(1);
                    List<DBObjectIdentity> out = new java.util.ArrayList<>();
                    while (rs.next()) {
                        out.add(mapper.mapRow(rs, 0));
                    }
                    return out;
                });
        List<DBObjectIdentity> result = accessor.listSynonyms("APP", DBSynonymType.COMMON);
        assertEquals(1, result.size());
        assertEquals("EMP_ALIAS", result.get(0).getName());
        assertEquals(DBObjectType.SYNONYM, result.get(0).getType());
        verify(jdbc).query(argThat((String s) -> s != null && s.contains("SYSCAT.TABLES")
                && s.contains("TYPE = 'A'")), any(RowMapper.class), eq((Object) "APP"));
    }

    // ---------------------------------------------------------------------------------------
    // Routines (functions / procedures / triggers / packages / types)
    // ---------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    @Test
    public void listFunctions_routinetypeF() throws SQLException {
        ResultSet rs = mockSingleColumnResultSet("NAME", "FN1");
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any()))
                .thenAnswer(inv -> {
                    RowMapper<DBPLObjectIdentity> mapper = inv.getArgument(1);
                    List<DBPLObjectIdentity> out = new java.util.ArrayList<>();
                    while (rs.next()) {
                        out.add(mapper.mapRow(rs, 0));
                    }
                    return out;
                });
        List<DBPLObjectIdentity> result = accessor.listFunctions("APP");
        assertEquals(1, result.size());
        assertEquals(DBObjectType.FUNCTION, result.get(0).getType());
        verify(jdbc).query(argThat((String s) -> s != null && s.contains("SYSCAT.ROUTINES")
                && s.contains("ROUTINETYPE = ?")), any(RowMapper.class),
                eq((Object) "APP"), eq((Object) "F"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void listProcedures_routinetypeP() throws SQLException {
        ResultSet rs = mockSingleColumnResultSet("NAME", "PROC1");
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any()))
                .thenAnswer(inv -> {
                    RowMapper<DBPLObjectIdentity> mapper = inv.getArgument(1);
                    List<DBPLObjectIdentity> out = new java.util.ArrayList<>();
                    while (rs.next()) {
                        out.add(mapper.mapRow(rs, 0));
                    }
                    return out;
                });
        List<DBPLObjectIdentity> result = accessor.listProcedures("APP");
        assertEquals(1, result.size());
        assertEquals(DBObjectType.PROCEDURE, result.get(0).getType());
        verify(jdbc).query(argThat((String s) -> s != null && s.contains("SYSCAT.ROUTINES")
                && s.contains("ROUTINETYPE = ?")), any(RowMapper.class),
                eq((Object) "APP"), eq((Object) "P"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void listTriggers_executesAgainstSyscatTriggers() throws SQLException {
        ResultSet rs = mockSingleColumnResultSet("NAME", "T1");
        when(jdbc.query(anyString(), any(RowMapper.class), any()))
                .thenAnswer(inv -> {
                    RowMapper<DBPLObjectIdentity> mapper = inv.getArgument(1);
                    List<DBPLObjectIdentity> out = new java.util.ArrayList<>();
                    while (rs.next()) {
                        out.add(mapper.mapRow(rs, 0));
                    }
                    return out;
                });
        List<DBPLObjectIdentity> result = accessor.listTriggers("APP");
        assertEquals(1, result.size());
        assertEquals(DBObjectType.TRIGGER, result.get(0).getType());
        verify(jdbc).query(argThat((String s) -> s != null && s.contains("SYSCAT.TRIGGERS")
                && s.contains("TRIGSCHEMA = ?")), any(RowMapper.class), eq((Object) "APP"));
    }

    @Test
    public void listPackages_returnsEmptyList_DB2BindPackagesOutOfScope() {
        assertEquals(Collections.emptyList(), accessor.listPackages("APP"));
        assertEquals(Collections.emptyList(), accessor.listPackageBodies("APP"));
        verify(jdbc, never()).queryForList(anyString(), eq(String.class), any());
    }

    @Test
    public void listTypes_returnsEmptyList_DB2UDTOutOfScope() {
        assertEquals(Collections.emptyList(), accessor.listTypes("APP"));
    }

    // ---------------------------------------------------------------------------------------
    // Empty collections for MVP "no such object" cases
    // ---------------------------------------------------------------------------------------

    @Test
    public void listMViews_returnsEmptyList_DB2MQTOutOfScope() {
        assertEquals(Collections.emptyList(), accessor.listMViews("APP"));
        assertEquals(Collections.emptyList(), accessor.listAllMViewsLike("%"));
    }

    @Test
    public void showVariables_returnsEmpty_DB2HasNoMySQLLikeVariables() {
        assertEquals(Collections.emptyList(), accessor.showVariables());
        assertEquals(Collections.emptyList(), accessor.showSessionVariables());
        assertEquals(Collections.emptyList(), accessor.showGlobalVariables());
    }

    @Test
    public void showCharsetAndCollation_returnEmpty_DB2DoesNotSurfaceThem() {
        assertEquals(Collections.emptyList(), accessor.showCharset());
        assertEquals(Collections.emptyList(), accessor.showCollation());
    }

    @Test
    public void listPartitionTables_returnsEmpty() {
        assertEquals(Collections.emptyList(), accessor.listPartitionTables("RANGE"));
    }

    // ---------------------------------------------------------------------------------------
    // Placeholders that throw UOE
    // ---------------------------------------------------------------------------------------

    @Test
    public void unsupportedMethodMatrix_throwsWithDb2Keyword() {
        Runnable[] cases = new Runnable[] {
                () -> accessor.listTableColumns("S", java.util.Collections.singletonList("T")),
                () -> accessor.listTableColumns("S", "T"),
                () -> accessor.listBasicTableColumns("S"),
                () -> accessor.listBasicTableColumns("S", "T"),
                () -> accessor.listBasicViewColumns("S"),
                () -> accessor.listBasicViewColumns("S", "V"),
                () -> accessor.listBasicColumnsInfo("S"),
                () -> accessor.listTableIndexes("S"),
                () -> accessor.listTableConstraints("S"),
                () -> accessor.listTableConstraints("S", "T"),
                () -> accessor.listTableOptions("S"),
                () -> accessor.listTableIndexes("S", "T"),
                () -> accessor.getTableDDL("S", "T"),
                () -> accessor.getTableOptions("S", "T"),
                () -> accessor.getTableOptions("S", "T", "ddl"),
                () -> accessor.getView("S", "V"),
                () -> accessor.getFunction("S", "F"),
                () -> accessor.getProcedure("S", "P"),
                () -> accessor.getPackage("S", "PK"),
                () -> accessor.getTrigger("S", "TR"),
                () -> accessor.getType("S", "TY"),
                () -> accessor.getSequence("S", "SEQ"),
                () -> accessor.getSynonym("S", "SY", DBSynonymType.COMMON),
                () -> accessor.getTables("S", java.util.Collections.singletonList("T"))
        };
        int idx = 0;
        for (Runnable r : cases) {
            try {
                r.run();
                fail("case[" + idx + "] should have thrown");
            } catch (UnsupportedOperationException ex) {
                assertNotNull(ex.getMessage());
                assertTrue("case[" + idx + "] message must contain DB2: " + ex.getMessage(),
                        ex.getMessage().contains("DB2"));
            }
            idx++;
        }
    }

    @Test
    public void isLowerCaseTableName_falseForDB2() {
        assertEquals(Boolean.FALSE, accessor.isLowerCaseTableName());
    }

    // ---------------------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------------------

    private static ResultSet mockSingleColumnResultSet(String columnLabel, String... values)
            throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        // sequence: next() returns true N times then false; getString returns values[0], values[1]...
        Boolean[] nextSeq = new Boolean[values.length + 1];
        for (int i = 0; i < values.length; i++) {
            nextSeq[i] = Boolean.TRUE;
        }
        nextSeq[values.length] = Boolean.FALSE;
        when(rs.next()).thenReturn(nextSeq[0],
                Arrays.copyOfRange(nextSeq, 1, nextSeq.length));
        when(rs.getString(columnLabel)).thenReturn(values[0],
                Arrays.copyOfRange(values, 1, values.length));
        return rs;
    }
}
