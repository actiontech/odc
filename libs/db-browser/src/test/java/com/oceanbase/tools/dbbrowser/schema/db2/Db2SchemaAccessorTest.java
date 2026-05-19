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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;

import com.oceanbase.tools.dbbrowser.model.DBConstraintType;
import com.oceanbase.tools.dbbrowser.model.DBDatabase;
import com.oceanbase.tools.dbbrowser.model.DBObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;
import com.oceanbase.tools.dbbrowser.model.DBTableColumn;
import com.oceanbase.tools.dbbrowser.model.DBTableConstraint;
import com.oceanbase.tools.dbbrowser.model.DBTableIndex;

/**
 * Mock-only unit tests for {@link Db2SchemaAccessor}.
 *
 * <p>
 * 测试用例按 map case 形式组织，每个用例用"用例名 → 输入 → mock ResultSet → 期望"四要素描述。 严格遵守 plan.md §3.2.2 "禁止真实 JDBC 连接
 * / 禁止 H2 容器"的边界。
 *
 * <p>
 * Db2SchemaAccessor 内部统一使用 {@code query(String sql, Object[] args, RowMapper)} 旧 API （与
 * {@code SqlServerSchemaAccessor} 测试模式一致），故本测试桩这一签名。
 *
 * @since ODC_release_4.3.4 (Issue dms-ee#839)
 */
public class Db2SchemaAccessorTest {

    private JdbcOperations jdbcOperations;
    private Db2SchemaAccessor accessor;

    @Before
    public void setUp() {
        this.jdbcOperations = mock(JdbcOperations.class);
        this.accessor = new Db2SchemaAccessor(jdbcOperations);
    }

    /**
     * Case showDatabases_filtersSystemSchemas: 模拟 SYSCAT.SCHEMATA 已被 SQL WHERE 过滤后只剩用户 schema， 期望直接返回
     * jdbcOperations.queryForList 的结果。
     */
    @Test
    public void showDatabases_filtersSystemSchemas() {
        List<String> userSchemas = Arrays.asList("DB2INST1", "MY_APP");
        when(jdbcOperations.queryForList(anyString(), eq(String.class))).thenReturn(userSchemas);

        List<String> result = accessor.showDatabases();

        Assert.assertNotNull(result);
        Assert.assertEquals(2, result.size());
        Assert.assertTrue(result.contains("DB2INST1"));
        Assert.assertTrue(result.contains("MY_APP"));
    }

    /**
     * fix-G bug C regression: design.md §6 mandates filtering by SCHEMANAME (not DEFINER) and lists 12
     * system schemas (the original 11 plus SQLJ). Before fix-G the SQL filtered by
     * {@code DEFINER NOT IN ('SYSIBM','SYSCAT',...)} which let NULLID / SYSTOOLS / SQLJ leak into the
     * user tree because their DEFINER is the instance owner (e.g. db2inst1), not SYSIBM. The SQL is the
     * single source of truth for this filter — this test pins the expected predicate shape so anyone
     * refactoring the accessor can't silently regress to DEFINER-based filtering.
     */
    @Test
    public void showDatabases_sqlFiltersBySchemaNameWithFullBlacklist() {
        when(jdbcOperations.queryForList(anyString(), eq(String.class)))
                .thenReturn(Arrays.asList("DB2INST1"));

        accessor.showDatabases();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcOperations).queryForList(sqlCaptor.capture(), eq(String.class));
        String sql = sqlCaptor.getValue();

        // Filter dimension must be SCHEMANAME, not DEFINER (the bug C regression).
        Assert.assertTrue("SQL should filter by SCHEMANAME, was: " + sql,
                sql.contains("SCHEMANAME NOT IN"));
        Assert.assertFalse("SQL must not filter by DEFINER (would leak NULLID/SYSTOOLS): " + sql,
                sql.contains("DEFINER NOT IN"));
        // Every entry in the design.md blacklist must appear in the SQL.
        String[] blacklist = {"SYSIBM", "SYSCAT", "SYSIBMADM", "SYSIBMINTERNAL", "SYSIBMTS",
                "SYSFUN", "SYSPROC", "SYSSTAT", "SYSTOOLS", "SYSPUBLIC", "NULLID", "SQLJ"};
        for (String name : blacklist) {
            Assert.assertTrue("blacklist entry '" + name + "' missing in SQL: " + sql,
                    sql.contains("'" + name + "'"));
        }
    }

    /**
     * Case listTables_returnsTableIdentities: 模拟 SYSCAT.TABLES 返回 2 行 TABLE。listTables 内部使用
     * rs.getString(1) / rs.getString(2) 列序号读取，故 mock 用列序号方式。
     */
    @Test
    public void listTables_returnsTableIdentities() throws SQLException {
        Map<Integer, Object> r1 = new LinkedHashMap<>();
        r1.put(1, "DB2INST1");
        r1.put(2, "ORDERS");
        Map<Integer, Object> r2 = new LinkedHashMap<>();
        r2.put(1, "DB2INST1");
        r2.put(2, "ORDER_ITEMS");
        stubQueryByIndex(Arrays.asList(r1, r2));

        List<DBObjectIdentity> tables = accessor.listTables("DB2INST1", null);

        Assert.assertEquals(2, tables.size());
        Assert.assertEquals(DBObjectType.TABLE, tables.get(0).getType());
        Assert.assertEquals("ORDERS", tables.get(0).getName());
        Assert.assertEquals("DB2INST1", tables.get(0).getSchemaName());
        Assert.assertEquals("ORDER_ITEMS", tables.get(1).getName());
    }

    /**
     * Case listColumns_populatesAllFields: 模拟 SYSCAT.COLUMNS 1 行典型列，期望
     * colName/typeName/length/scale/nullable/default/comment/ordinalPosition 均被正确填充；NULLS='N' 解释为
     * nullable=false。
     */
    @Test
    public void listColumns_populatesAllFields() throws SQLException {
        Map<String, Object> r1 = new LinkedHashMap<>();
        r1.put("COLNAME", "ID");
        r1.put("TYPENAME", "INTEGER");
        r1.put("LENGTH", 4L);
        r1.put("SCALE", 0);
        r1.put("NULLS", "N");
        r1.put("DEFAULT", null);
        r1.put("REMARKS", "primary id");
        r1.put("COLNO", 0);
        stubQueryByName(Arrays.asList(r1));

        List<DBTableColumn> columns = accessor.listTableColumns("DB2INST1", "ORDERS");

        Assert.assertEquals(1, columns.size());
        DBTableColumn col = columns.get(0);
        Assert.assertEquals("ID", col.getName());
        Assert.assertEquals("INTEGER", col.getTypeName());
        Assert.assertEquals(Long.valueOf(4L), col.getMaxLength());
        Assert.assertEquals(Integer.valueOf(0), col.getScale());
        Assert.assertEquals(Boolean.FALSE, col.getNullable());
        Assert.assertEquals("DB2INST1", col.getSchemaName());
        Assert.assertEquals("ORDERS", col.getTableName());
        Assert.assertEquals(Integer.valueOf(0), col.getOrdinalPosition());
        Assert.assertEquals("primary id", col.getComment());
    }

    /**
     * Case listViews_returnsViewIdentities: 模拟 SYSCAT.VIEWS 1 行，期望 type=VIEW，schema/name 正确。
     */
    @Test
    public void listViews_returnsViewIdentities() throws SQLException {
        Map<Integer, Object> r1 = new LinkedHashMap<>();
        r1.put(1, "DB2INST1");
        r1.put(2, "V_ORDER_SUMMARY");
        stubQueryByIndex(Arrays.asList(r1));

        List<DBObjectIdentity> views = accessor.listViews("DB2INST1");

        Assert.assertEquals(1, views.size());
        Assert.assertEquals(DBObjectType.VIEW, views.get(0).getType());
        Assert.assertEquals("V_ORDER_SUMMARY", views.get(0).getName());
        Assert.assertEquals("DB2INST1", views.get(0).getSchemaName());
    }

    /**
     * Case listTableIndexes_uniqueRuleMapping: 模拟 SYSCAT.INDEXES 2 行 UNIQUERULE='P' 与 'D'， 期望前者
     * unique=true（P/U 都视为 unique），后者 unique=false。
     */
    @Test
    public void listTableIndexes_uniqueRuleMapping() throws SQLException {
        Map<String, Object> primary = new LinkedHashMap<>();
        primary.put("INDSCHEMA", "DB2INST1");
        primary.put("INDNAME", "PK_ORDERS");
        primary.put("TABSCHEMA", "DB2INST1");
        primary.put("TABNAME", "ORDERS");
        primary.put("UNIQUERULE", "P");
        Map<String, Object> duplicate = new LinkedHashMap<>();
        duplicate.put("INDSCHEMA", "DB2INST1");
        duplicate.put("INDNAME", "IDX_ORDERS_DATE");
        duplicate.put("TABSCHEMA", "DB2INST1");
        duplicate.put("TABNAME", "ORDERS");
        duplicate.put("UNIQUERULE", "D");
        stubQueryByName(Arrays.asList(primary, duplicate));

        List<DBTableIndex> indexes = accessor.listTableIndexes("DB2INST1", "ORDERS");

        Assert.assertEquals(2, indexes.size());
        Assert.assertEquals("PK_ORDERS", indexes.get(0).getName());
        Assert.assertTrue("UNIQUERULE=P should map to unique=true", indexes.get(0).getUnique());
        Assert.assertEquals("IDX_ORDERS_DATE", indexes.get(1).getName());
        Assert.assertFalse("UNIQUERULE=D should map to unique=false", indexes.get(1).getUnique());
    }

    /**
     * Case listTableConstraints_typeMapping: 模拟 SYSCAT.TABCONST 3 行 TYPE='P'/'U'/'F'， 期望分别映射为
     * PRIMARY_KEY / UNIQUE_KEY / FOREIGN_KEY。
     */
    @Test
    public void listTableConstraints_typeMapping() throws SQLException {
        Map<String, Object> pk = new LinkedHashMap<>();
        pk.put("TABSCHEMA", "DB2INST1");
        pk.put("TABNAME", "ORDERS");
        pk.put("CONSTNAME", "PK_ORDERS");
        pk.put("TYPE", "P");
        Map<String, Object> uk = new LinkedHashMap<>();
        uk.put("TABSCHEMA", "DB2INST1");
        uk.put("TABNAME", "ORDERS");
        uk.put("CONSTNAME", "UK_ORDERS_CODE");
        uk.put("TYPE", "U");
        Map<String, Object> fk = new LinkedHashMap<>();
        fk.put("TABSCHEMA", "DB2INST1");
        fk.put("TABNAME", "ORDERS");
        fk.put("CONSTNAME", "FK_ORDERS_USER");
        fk.put("TYPE", "F");
        stubQueryByName(Arrays.asList(pk, uk, fk));

        List<DBTableConstraint> constraints = accessor.listTableConstraints("DB2INST1", "ORDERS");

        Assert.assertEquals(3, constraints.size());
        Assert.assertEquals(DBConstraintType.PRIMARY_KEY, constraints.get(0).getType());
        Assert.assertEquals("PK_ORDERS", constraints.get(0).getName());
        Assert.assertEquals(DBConstraintType.UNIQUE_KEY, constraints.get(1).getType());
        Assert.assertEquals(DBConstraintType.FOREIGN_KEY, constraints.get(2).getType());
    }

    // -------------------- fix-H bug D regression: 4 core methods --------------------

    /**
     * fix-H bug D: {@link Db2SchemaAccessor#getDatabase(String)} previously threw
     * UnsupportedOperationException, which collapsed the table-detail page (DBTableController#getTable
     * -> OBMySQLTableExtension.getDetail aggregates this) with HTTP 500. Must return a minimal
     * DBDatabase POJO with id=name=schemaName so the aggregator can proceed.
     */
    @Test
    public void getDatabase_returnsMinimalPojoForDb2() {
        DBDatabase db = accessor.getDatabase("DB2INST1");

        Assert.assertNotNull("getDatabase must not throw or return null for DB2 — see fix-H bug D", db);
        Assert.assertEquals("DB2INST1", db.getId());
        Assert.assertEquals("DB2INST1", db.getName());
    }

    /**
     * fix-H bug D: {@link Db2SchemaAccessor#listAllUserViews(String)} must filter by VIEWSCHEMA NOT IN
     * (12-entry system-schema blacklist). This is the same blacklist as showDatabases() — single source
     * of truth lives in SYSTEM_SCHEMA_BLACKLIST_SQL_LITERAL.
     */
    @Test
    public void listAllUserViews_filtersBySystemSchemaBlacklist() throws SQLException {
        Map<Integer, Object> r1 = new LinkedHashMap<>();
        r1.put(1, "DB2INST1");
        r1.put(2, "V_ORDER_SUMMARY");
        stubQueryByIndex(Arrays.asList(r1));

        List<DBObjectIdentity> views = accessor.listAllUserViews(null);

        Assert.assertEquals(1, views.size());
        Assert.assertEquals(DBObjectType.VIEW, views.get(0).getType());
        Assert.assertEquals("V_ORDER_SUMMARY", views.get(0).getName());
        Assert.assertEquals("DB2INST1", views.get(0).getSchemaName());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcOperations).query(sqlCaptor.capture(), any(Object[].class), any(RowMapper.class));
        String sql = sqlCaptor.getValue();
        Assert.assertTrue("must select from SYSCAT.VIEWS: " + sql, sql.contains("SYSCAT.VIEWS"));
        Assert.assertTrue("must filter by VIEWSCHEMA NOT IN (...): " + sql,
                sql.contains("VIEWSCHEMA NOT IN"));
        // Spot-check the 3 most-leaked entries from batch-3 round-2 evidence.
        Assert.assertTrue("blacklist must include NULLID: " + sql, sql.contains("'NULLID'"));
        Assert.assertTrue("blacklist must include SYSTOOLS: " + sql, sql.contains("'SYSTOOLS'"));
        Assert.assertTrue("blacklist must include SQLJ: " + sql, sql.contains("'SQLJ'"));
    }

    /**
     * fix-H bug D: {@link Db2SchemaAccessor#listAllSystemViews(String)} is the inverse — VIEWSCHEMA IN
     * (...) — to surface DB2 system views (SYSCAT.* / SYSIBM.*) under a dedicated tree node.
     */
    @Test
    public void listAllSystemViews_filtersBySystemSchemaInclusion() throws SQLException {
        Map<Integer, Object> r1 = new LinkedHashMap<>();
        r1.put(1, "SYSCAT");
        r1.put(2, "TABLES");
        stubQueryByIndex(Arrays.asList(r1));

        List<DBObjectIdentity> views = accessor.listAllSystemViews(null);

        Assert.assertEquals(1, views.size());
        Assert.assertEquals(DBObjectType.VIEW, views.get(0).getType());
        Assert.assertEquals("TABLES", views.get(0).getName());
        Assert.assertEquals("SYSCAT", views.get(0).getSchemaName());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcOperations).query(sqlCaptor.capture(), any(Object[].class), any(RowMapper.class));
        String sql = sqlCaptor.getValue();
        Assert.assertTrue("must be the inclusion variant (VIEWSCHEMA IN), not NOT IN: " + sql,
                sql.contains("VIEWSCHEMA IN ("));
        Assert.assertFalse("must NOT be the user-view variant (VIEWSCHEMA NOT IN): " + sql,
                sql.contains("VIEWSCHEMA NOT IN"));
    }

    /**
     * fix-H bug D: batch {@link Db2SchemaAccessor#listTableColumns(String, List)} previously threw and
     * collapsed the aggregator path (OBMySQLTableExtension.getDetail). Must loop the per-table variant
     * and return a Map keyed by table name. Empty/null input must return an empty Map, not throw.
     */
    @Test
    public void listTableColumnsBatch_loopsPerTableAndKeysByName() throws SQLException {
        // Stub the per-table query (called twice — once per requested table).
        Map<String, Object> idCol = new LinkedHashMap<>();
        idCol.put("COLNAME", "ID");
        idCol.put("TYPENAME", "INTEGER");
        idCol.put("LENGTH", 4L);
        idCol.put("SCALE", 0);
        idCol.put("NULLS", "N");
        idCol.put("DEFAULT", null);
        idCol.put("REMARKS", "");
        idCol.put("COLNO", 0);
        stubQueryByName(Arrays.asList(idCol));

        Map<String, List<DBTableColumn>> result =
                accessor.listTableColumns("DB2INST1", Arrays.asList("ORDERS", "USERS"));

        Assert.assertEquals(2, result.size());
        Assert.assertTrue("must contain key 'ORDERS'", result.containsKey("ORDERS"));
        Assert.assertTrue("must contain key 'USERS'", result.containsKey("USERS"));
        Assert.assertEquals(1, result.get("ORDERS").size());
        Assert.assertEquals("ID", result.get("ORDERS").get(0).getName());
    }

    /**
     * fix-H bug D: batch listTableColumns with empty / null input must return an empty Map (not throw)
     * — ODC sometimes calls with an empty list when no tables are pre-selected.
     */
    @Test
    public void listTableColumnsBatch_emptyInputReturnsEmptyMap() {
        // Explicit List<String> typing required to disambiguate the (String, String) /
        // (String, List<String>) overloads.
        List<String> emptyTables = new ArrayList<>();
        Map<String, List<DBTableColumn>> empty = accessor.listTableColumns("DB2INST1", emptyTables);
        Assert.assertNotNull(empty);
        Assert.assertTrue(empty.isEmpty());

        Map<String, List<DBTableColumn>> nullIn =
                accessor.listTableColumns("DB2INST1", (List<String>) null);
        Assert.assertNotNull(nullIn);
        Assert.assertTrue(nullIn.isEmpty());
    }

    /**
     * fix-H bug D: regression guard — the previously-thrown placeholders that ODC aggregator paths call
     * must now degrade to empty / false / null instead of UnsupportedOperationException. Spot-check the
     * highest-impact entries (listMViews / listFunctions / listProcedures / listSequences /
     * listSynonyms / showVariables / listBasicTableColumns(schema) / getTables(schema,list) /
     * showSystemViews-via-schema).
     */
    @Test
    public void unsupportedPlaceholders_degradeToEmptyInsteadOfThrowing() throws SQLException {
        // showSystemViews single-schema variant is implemented as an actual SQL query, so stub it.
        when(jdbcOperations.queryForList(anyString(), eq(String.class), eq("DB2INST1")))
                .thenReturn(java.util.Collections.<String>emptyList());

        // None of these calls should throw UnsupportedOperationException any more.
        Assert.assertTrue(accessor.listMViews("DB2INST1").isEmpty());
        Assert.assertTrue(accessor.listAllMViewsLike("X%").isEmpty());
        Assert.assertTrue(accessor.listFunctions("DB2INST1").isEmpty());
        Assert.assertTrue(accessor.listProcedures("DB2INST1").isEmpty());
        Assert.assertTrue(accessor.listPackages("DB2INST1").isEmpty());
        Assert.assertTrue(accessor.listTriggers("DB2INST1").isEmpty());
        Assert.assertTrue(accessor.listSequences("DB2INST1").isEmpty());
        Assert.assertTrue(accessor.listSynonyms("DB2INST1", null).isEmpty());
        Assert.assertTrue(accessor.showVariables().isEmpty());
        Assert.assertTrue(accessor.listBasicTableColumns("DB2INST1").isEmpty());
        Assert.assertTrue(accessor.getTables("DB2INST1", java.util.Collections.<String>emptyList())
                .isEmpty());
        Assert.assertTrue(accessor.listTableIndexes("DB2INST1").isEmpty());
        Assert.assertTrue(accessor.listTableConstraints("DB2INST1").isEmpty());
        Assert.assertTrue(accessor.listTableOptions("DB2INST1").isEmpty());
        Assert.assertTrue(accessor.listSubpartitions("DB2INST1", "ORDERS").isEmpty());
        Assert.assertTrue(accessor.showSystemViews("DB2INST1").isEmpty());

        // Single-object getX must return null (controller maps null -> 404, not 500).
        Assert.assertNull(accessor.getView("DB2INST1", "V"));
        Assert.assertNull(accessor.getFunction("DB2INST1", "F"));
        Assert.assertNull(accessor.getProcedure("DB2INST1", "P"));
        Assert.assertNull(accessor.getSequence("DB2INST1", "S"));
        Assert.assertNull(accessor.getPartition("DB2INST1", "T"));

        // Booleans / primitives.
        Assert.assertEquals(Boolean.FALSE, accessor.refreshMVData(null));
        Assert.assertFalse(accessor.syncExternalTableFiles("DB2INST1", "EXT"));

        // switchDatabase is void; just verify it doesn't throw.
        accessor.switchDatabase("DB2INST1");
    }

    // -------------------- Helpers --------------------

    /**
     * 桩 {@code query(String sql, Object[] args, RowMapper)}（与 SqlServerSchemaAccessorTest 同模式）。
     * 用于按列名读取的访问路径（rs.getString("COLNAME") 等）。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubQueryByName(List<Map<String, Object>> rows) throws SQLException {
        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(2);
                    List<Object> out = new ArrayList<>(rows.size());
                    for (int i = 0; i < rows.size(); i++) {
                        ResultSet rs = mockResultSetByName(rows.get(i));
                        out.add(mapper.mapRow(rs, i));
                    }
                    return out;
                });
    }

    /**
     * 桩 {@code query(String sql, Object[] args, RowMapper)}，但 ResultSet 按列序号（int）读取。 用于按
     * rs.getString(1) / rs.getString(2) 访问的路径（listTables / listViews）。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubQueryByIndex(List<Map<Integer, Object>> rows) throws SQLException {
        when(jdbcOperations.query(anyString(), any(Object[].class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(2);
                    List<Object> out = new ArrayList<>(rows.size());
                    for (int i = 0; i < rows.size(); i++) {
                        ResultSet rs = mockResultSetByIndex(rows.get(i));
                        out.add(mapper.mapRow(rs, i));
                    }
                    return out;
                });
    }

    private ResultSet mockResultSetByName(Map<String, Object> row) throws SQLException {
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

    private ResultSet mockResultSetByIndex(Map<Integer, Object> row) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        for (Map.Entry<Integer, Object> e : row.entrySet()) {
            int idx = e.getKey();
            Object v = e.getValue();
            when(rs.getString(idx)).thenReturn(v == null ? null : v.toString());
        }
        return rs;
    }
}
