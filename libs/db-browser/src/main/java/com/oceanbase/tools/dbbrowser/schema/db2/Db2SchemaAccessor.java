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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcOperations;

import com.oceanbase.tools.dbbrowser.model.DBColumnGroupElement;
import com.oceanbase.tools.dbbrowser.model.DBConstraintType;
import com.oceanbase.tools.dbbrowser.model.DBDatabase;
import com.oceanbase.tools.dbbrowser.model.DBFunction;
import com.oceanbase.tools.dbbrowser.model.DBMViewRefreshParameter;
import com.oceanbase.tools.dbbrowser.model.DBMViewRefreshRecord;
import com.oceanbase.tools.dbbrowser.model.DBMViewRefreshRecordParam;
import com.oceanbase.tools.dbbrowser.model.DBMaterializedView;
import com.oceanbase.tools.dbbrowser.model.DBObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;
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
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * DB2 schema accessor implementation (B-06 / B-07).
 *
 * <p>
 * 主用 SYSCAT.* 视图（DB2 11.5 默认）；仅实现本期需要的 6 类查询：列 schema / 列 table / 列 view / 列 column / 列 index / 列
 * constraint，详见 docs/spec/design.md §6，外加 fix-H 补齐的 4 个聚合路径必经方法： {@link #getDatabase(String)} /
 * {@link #listAllUserViews(String)} / {@link #listAllSystemViews(String)} /
 * {@link #listTableColumns(String, java.util.List)}。
 *
 * <p>
 * 其余接口方法在 fix-H 之前曾抛 {@code UnsupportedOperationException}，但被 ODC 上层
 * (OBMySQLTableExtension.getDetail / DBMetadataController#listIdentities) 聚合调用时会折叠成整页 HTTP 500。
 * fix-H 起改为返回空集合 / null / false（参见 case-2-3 / case-2-4 round-fix-G 复测证据，episodic
 * {@code odc_db2_fixG_round_bugD_unsupported_methods_2026-05-19.md}）。 行为契约：空 List/Map 表示"该类对象在 DB2
 * 暂不可见 "；null 仅用于单对象 getX，控制器层会将其映射为 404 而非 500。
 *
 * @since ODC_release_4.3.4 (Issue dms-ee#839)
 */
@Slf4j
public class Db2SchemaAccessor implements DBSchemaAccessor {

    /**
     * DB2 11.5 system-schema blacklist (12 entries, single source of truth). Used by both
     * {@link #showDatabases()} (filters SYSCAT.SCHEMATA.SCHEMANAME) and the
     * {@link #listAllUserViews(String)} / {@link #listAllSystemViews(String)} pair (filters
     * SYSCAT.VIEWS.VIEWSCHEMA — DB2 system views always live in one of these schemas in 11.5).
     *
     * <p>
     * fix-G bug C — must filter by SCHEMANAME / VIEWSCHEMA, never DEFINER: in DB2 11.5 several system
     * schemas (NULLID, SQLJ, SYSTOOLS) are created by the instance owner (e.g. db2inst1) so a
     * DEFINER-based filter leaks them into the user tree.
     */
    static final String SYSTEM_SCHEMA_BLACKLIST_SQL_LITERAL =
            "'SYSIBM','SYSCAT','SYSIBMADM','SYSIBMINTERNAL','SYSIBMTS','SYSFUN','SYSPROC',"
                    + "'SYSSTAT','SYSTOOLS','SYSPUBLIC','NULLID','SQLJ'";

    protected final JdbcOperations jdbcOperations;

    public Db2SchemaAccessor(@NonNull JdbcOperations jdbcOperations) {
        this.jdbcOperations = jdbcOperations;
    }

    @Override
    public List<String> showDatabases() {
        // fix-G bug C: design.md §6 prescribes a 12-entry system-schema blacklist (see
        // SYSTEM_SCHEMA_BLACKLIST_SQL_LITERAL). The original implementation filtered by
        // SYSCAT.SCHEMATA.DEFINER, but on DB2 11.5 several system schemas (NULLID, SQLJ,
        // SYSTOOLS) are *created* by the instance owner (e.g. db2inst1) rather than SYSIBM,
        // so DEFINER NOT IN(...) lets them slip through into the user schema list. The blacklist
        // must therefore match SCHEMANAME directly. See batch-3 round-2 evidence in case-2-1.md.
        String sql = "SELECT TRIM(SCHEMANAME) AS SCHEMA_NAME FROM SYSCAT.SCHEMATA "
                + "WHERE SCHEMANAME NOT IN (" + SYSTEM_SCHEMA_BLACKLIST_SQL_LITERAL + ") "
                + "ORDER BY SCHEMANAME";
        return jdbcOperations.queryForList(sql, String.class);
    }

    @Override
    public DBDatabase getDatabase(String schemaName) {
        // fix-H bug D: ODC `DBTableController#getTable` -> `OBMySQLTableExtension.getDetail` aggregates
        // multiple SchemaAccessor calls (incl. getDatabase) and any UnsupportedOperationException
        // collapses the whole 5-tab table-detail page with HTTP 500. DB2 conceptually has only a single
        // catalog per JDBC URL, so DB2 ≈ Oracle/PostgreSQL where "schema" is the database identity end
        // users see. Return a minimal POJO with id=name=schemaName so the upstream aggregator can
        // proceed; charset/collation/size are not surfaced in the DB2 schema page anyway.
        DBDatabase database = new DBDatabase();
        database.setId(schemaName);
        database.setName(schemaName);
        return database;
    }

    @Override
    public List<DBDatabase> listDatabases() {
        List<String> schemas = showDatabases();
        List<DBDatabase> result = new ArrayList<>(schemas.size());
        for (String schema : schemas) {
            DBDatabase db = new DBDatabase();
            db.setId(schema);
            db.setName(schema);
            result.add(db);
        }
        return result;
    }

    @Override
    public void switchDatabase(String schemaName) {
        // fix-H bug D: void; ODC default catalog/schema is fixed at JDBC connect time for DB2 (set via
        // `currentSchema` URL property by Db2ConnectionExtension). No-op here so that any upstream
        // call from a generic flow doesn't 500 — the JDBC session is already on the desired schema.
        // If a future flow truly needs schema switch mid-session, this can be replaced with
        // `SET SCHEMA ?` (DB2 dialect).
    }

    @Override
    public List<DBObjectIdentity> listUsers() {
        // fix-H bug D: ODC user-picker for "grant/revoke on object" is not exposed for DB2 in this
        // release. Return empty so upstream UI shows "no users" rather than collapsing the page.
        return Collections.emptyList();
    }

    @Override
    public List<String> showTablesLike(String schemaName, String tableNameLike) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT TABNAME FROM SYSCAT.TABLES ");
        sb.append("WHERE TABSCHEMA = ? AND TYPE IN ('T','S','U') ");
        if (tableNameLike != null && !tableNameLike.isEmpty()) {
            sb.append("AND TABNAME LIKE ? ");
        }
        sb.append("ORDER BY TABNAME");
        if (tableNameLike != null && !tableNameLike.isEmpty()) {
            return jdbcOperations.queryForList(sb.toString(), String.class, schemaName, tableNameLike);
        }
        return jdbcOperations.queryForList(sb.toString(), String.class, schemaName);
    }

    @Override
    public List<DBObjectIdentity> listTables(String schemaName, String tableNameLike) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT TABSCHEMA, TABNAME FROM SYSCAT.TABLES ");
        sb.append("WHERE TABSCHEMA = ? AND TYPE IN ('T','S','U') ");
        Object[] args;
        if (tableNameLike != null && !tableNameLike.isEmpty()) {
            sb.append("AND TABNAME LIKE ? ");
            args = new Object[] {schemaName, tableNameLike};
        } else {
            args = new Object[] {schemaName};
        }
        sb.append("ORDER BY TABNAME");
        return jdbcOperations.query(sb.toString(), args,
                (rs, rowNum) -> DBObjectIdentity.of(rs.getString(1).trim(), DBObjectType.TABLE,
                        rs.getString(2).trim()));
    }

    @Override
    public List<String> showExternalTablesLike(String schemaName, String tableNameLike) {
        return Collections.emptyList();
    }

    @Override
    public List<DBObjectIdentity> listExternalTables(String schemaName, String tableNameLike) {
        return Collections.emptyList();
    }

    @Override
    public boolean isExternalTable(String schemaName, String tableName) {
        return false;
    }

    @Override
    public boolean syncExternalTableFiles(String schemaName, String tableName) {
        // fix-H bug D: DB2 has no external-table sync. listExternalTables() already returns empty,
        // so this should never be reachable in practice; return false defensively.
        return false;
    }

    @Override
    public List<DBObjectIdentity> listViews(String schemaName) {
        // fix-I: SYSCAT.VIEWS exposes the view name in column VIEWNAME, not TABNAME (TABNAME is the
        // SYSCAT.TABLES column — both views inherit some columns but VIEWS does not surface TABNAME
        // in DB2 11.5). The earlier "SELECT VIEWSCHEMA, TABNAME ..." form was inherited verbatim
        // from a stale skeleton and produced SQLCODE=-206 (SQLERRMC=TABNAME) the moment the v1 view
        // controller wired up through fix-I and tried to list views for the "视图" tree node.
        String sql = "SELECT VIEWSCHEMA, VIEWNAME FROM SYSCAT.VIEWS WHERE VIEWSCHEMA = ? ORDER BY VIEWNAME";
        return jdbcOperations.query(sql, new Object[] {schemaName},
                (rs, rowNum) -> DBObjectIdentity.of(rs.getString(1).trim(), DBObjectType.VIEW,
                        rs.getString(2).trim()));
    }

    @Override
    public List<DBObjectIdentity> listAllViews(String viewNameLike) {
        // fix-H bug D: union of user + system views — DBMetadataController#listIdentities?type=VIEW
        // calls this method when ODC asks for all visible views across schemas.
        List<DBObjectIdentity> result = new ArrayList<>();
        result.addAll(listAllUserViews(viewNameLike));
        result.addAll(listAllSystemViews(viewNameLike));
        return result;
    }

    @Override
    public List<DBObjectIdentity> listAllUserViews(String viewNameLike) {
        // fix-H bug D: SQL autocomplete + cross-schema view picker call this on every prefix.
        // Same SYSTEM-schema blacklist as showDatabases() (see SYSTEM_SCHEMA_BLACKLIST_SQL_LITERAL).
        // VIEWSCHEMA is the column on SYSCAT.VIEWS; the column on SYSCAT.SCHEMATA is SCHEMANAME — the
        // two are independent but the blacklist values match by design (DB2 system schemas always own
        // their system views in DB2 11.5; see SYSCAT.VIEWS rows where VIEWSCHEMA='SYSCAT'/'SYSIBM').
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT VIEWSCHEMA, VIEWNAME FROM SYSCAT.VIEWS ");
        sb.append("WHERE VIEWSCHEMA NOT IN (").append(SYSTEM_SCHEMA_BLACKLIST_SQL_LITERAL).append(") ");
        Object[] args;
        if (viewNameLike != null && !viewNameLike.isEmpty()) {
            sb.append("AND VIEWNAME LIKE ? ");
            args = new Object[] {viewNameLike};
        } else {
            args = new Object[] {};
        }
        sb.append("ORDER BY VIEWSCHEMA, VIEWNAME");
        return jdbcOperations.query(sb.toString(), args,
                (rs, rowNum) -> DBObjectIdentity.of(rs.getString(1).trim(), DBObjectType.VIEW,
                        rs.getString(2).trim()));
    }

    @Override
    public List<DBObjectIdentity> listAllSystemViews(String viewNameLike) {
        // fix-H bug D: inverse of listAllUserViews — DBMetadataController surfaces system views in a
        // separate node so users can read schema metadata directly. VIEWSCHEMA IN (...) is the inverse
        // of the user-view filter; same 12-entry blacklist.
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT VIEWSCHEMA, VIEWNAME FROM SYSCAT.VIEWS ");
        sb.append("WHERE VIEWSCHEMA IN (").append(SYSTEM_SCHEMA_BLACKLIST_SQL_LITERAL).append(") ");
        Object[] args;
        if (viewNameLike != null && !viewNameLike.isEmpty()) {
            sb.append("AND VIEWNAME LIKE ? ");
            args = new Object[] {viewNameLike};
        } else {
            args = new Object[] {};
        }
        sb.append("ORDER BY VIEWSCHEMA, VIEWNAME");
        return jdbcOperations.query(sb.toString(), args,
                (rs, rowNum) -> DBObjectIdentity.of(rs.getString(1).trim(), DBObjectType.VIEW,
                        rs.getString(2).trim()));
    }

    @Override
    public List<String> showSystemViews(String schemaName) {
        // fix-H bug D: ODC autocomplete sometimes hits this single-schema variant. Return system-view
        // names within the given schema only (DB2 system schemas like SYSCAT/SYSIBM own dozens).
        String sql = "SELECT VIEWNAME FROM SYSCAT.VIEWS WHERE VIEWSCHEMA = ? ORDER BY VIEWNAME";
        return jdbcOperations.queryForList(sql, String.class, schemaName);
    }

    // fix-H bug D: DB2 11.5 has MQTs (materialized query tables) but the ODC MView UI surface is not
    // wired up in this release (out of scope per design.md §6). Return empty / false rather than
    // throwing so the materialized-view tree node, if ever rendered, simply shows nothing instead of
    // collapsing the parent page with HTTP 500.
    @Override
    public List<DBObjectIdentity> listMViews(String schemaName) {
        return Collections.emptyList();
    }

    @Override
    public List<DBObjectIdentity> listAllMViewsLike(String mViewNameLike) {
        return Collections.emptyList();
    }

    @Override
    public Boolean refreshMVData(DBMViewRefreshParameter parameter) {
        return Boolean.FALSE;
    }

    @Override
    public DBMaterializedView getMView(String schemaName, String mViewName) {
        // No DB2 materialized-view surface in this release; null is acceptable for object-detail
        // endpoints — DBTableController shapes null as 404 rather than 500.
        return null;
    }

    @Override
    public List<DBTableConstraint> listMViewConstraints(String schemaName, String mViewName) {
        return Collections.emptyList();
    }

    @Override
    public List<DBMViewRefreshRecord> listMViewRefreshRecords(DBMViewRefreshRecordParam param) {
        return Collections.emptyList();
    }

    @Override
    public List<DBTableIndex> listMViewIndexes(String schemaName, String mViewName) {
        return Collections.emptyList();
    }

    // fix-H bug D: DB2 11.5 exposes registry/session variables via SYSPROC.* but the ODC variables
    // page is not wired for DB2 in this release. Return empty so the page (if reached) shows "no
    // variables" rather than 500.
    @Override
    public List<DBVariable> showVariables() {
        return Collections.emptyList();
    }

    @Override
    public List<DBVariable> showSessionVariables() {
        return Collections.emptyList();
    }

    @Override
    public List<DBVariable> showGlobalVariables() {
        return Collections.emptyList();
    }

    @Override
    public List<String> showCharset() {
        return Collections.emptyList();
    }

    @Override
    public List<String> showCollation() {
        return Collections.emptyList();
    }

    // fix-H bug D: DB2 has functions/procedures/packages/triggers/types/sequences/synonyms via
    // SYSCAT.ROUTINES / SYSCAT.TRIGGERS / SYSCAT.SEQUENCES / SYSCAT.PACKAGES — out of scope for this
    // release (design.md §6 covers only schemas/tables/views/columns/indexes/constraints). Return
    // empty so the corresponding "PL objects" tree nodes simply render no children rather than
    // collapsing the parent resource tree with HTTP 500.
    @Override
    public List<DBPLObjectIdentity> listFunctions(String schemaName) {
        return Collections.emptyList();
    }

    @Override
    public List<DBPLObjectIdentity> listProcedures(String schemaName) {
        return Collections.emptyList();
    }

    @Override
    public List<DBPLObjectIdentity> listPackages(String schemaName) {
        return Collections.emptyList();
    }

    @Override
    public List<DBPLObjectIdentity> listPackageBodies(String schemaName) {
        return Collections.emptyList();
    }

    @Override
    public List<DBPLObjectIdentity> listTriggers(String schemaName) {
        return Collections.emptyList();
    }

    @Override
    public List<DBPLObjectIdentity> listTypes(String schemaName) {
        return Collections.emptyList();
    }

    @Override
    public List<DBObjectIdentity> listSequences(String schemaName) {
        return Collections.emptyList();
    }

    @Override
    public List<DBObjectIdentity> listSynonyms(String schemaName, DBSynonymType synonymType) {
        return Collections.emptyList();
    }

    @Override
    public Map<String, List<DBTableColumn>> listTableColumns(String schemaName, List<String> tableNames) {
        // fix-H bug D: aggregator path (OBMySQLTableExtension.getDetail) calls this batch variant for
        // every table-detail page render. Each unsupported call collapses the whole 5-tab page with
        // HTTP 500. Loop over single-table variant — DB2 11.5 SYSCAT.COLUMNS lookup is index-backed and
        // the typical "table tabs open" call set is N<=1 anyway. If a future caller passes a large list
        // and profiling shows a hot spot, this can be rewritten to a single `WHERE TABNAME IN (?, ...)`
        // round-trip without altering the contract.
        Map<String, List<DBTableColumn>> result = new java.util.LinkedHashMap<>();
        if (tableNames == null || tableNames.isEmpty()) {
            return result;
        }
        for (String tableName : tableNames) {
            result.put(tableName, listTableColumns(schemaName, tableName));
        }
        return result;
    }

    @Override
    public List<DBTableColumn> listTableColumns(String schemaName, String tableName) {
        String sql = "SELECT COLNAME, TYPENAME, LENGTH, SCALE, NULLS, DEFAULT, REMARKS, COLNO "
                + "FROM SYSCAT.COLUMNS WHERE TABSCHEMA = ? AND TABNAME = ? ORDER BY COLNO";
        return jdbcOperations.query(sql, new Object[] {schemaName, tableName}, (rs, rowNum) -> {
            DBTableColumn column = new DBTableColumn();
            column.setSchemaName(schemaName);
            column.setTableName(tableName);
            column.setName(rs.getString("COLNAME"));
            column.setTypeName(rs.getString("TYPENAME"));
            long len = rs.getLong("LENGTH");
            column.setMaxLength(len);
            column.setPrecision(len);
            column.setScale(rs.getInt("SCALE"));
            column.setNullable(!"N".equalsIgnoreCase(rs.getString("NULLS")));
            column.setDefaultValue(rs.getString("DEFAULT"));
            column.setComment(rs.getString("REMARKS"));
            column.setOrdinalPosition(rs.getInt("COLNO"));
            return column;
        });
    }

    // fix-H bug D: "Basic" column variants are an autocomplete/SQL-console optimization that returns a
    // light-weight projection per schema. ODC falls back gracefully when these return empty (it uses
    // the heavier per-table listTableColumns path), so empty is a safe degradation. Returning empty
    // keeps the autocomplete dropdown free of DB2 columns rather than crashing the SQL console.
    @Override
    public Map<String, List<DBTableColumn>> listBasicTableColumns(String schemaName) {
        return Collections.emptyMap();
    }

    @Override
    public List<DBTableColumn> listBasicTableColumns(String schemaName, String tableName) {
        return Collections.emptyList();
    }

    @Override
    public Map<String, List<DBTableColumn>> listBasicViewColumns(String schemaName) {
        return Collections.emptyMap();
    }

    @Override
    public List<DBTableColumn> listBasicViewColumns(String schemaName, String viewName) {
        return Collections.emptyList();
    }

    @Override
    public Map<String, List<DBTableColumn>> listBasicExternalTableColumns(String schemaName) {
        return Collections.emptyMap();
    }

    @Override
    public List<DBTableColumn> listBasicExternalTableColumns(String schemaName, String externalTableName) {
        return Collections.emptyList();
    }

    @Override
    public Map<String, List<DBTableColumn>> listBasicMViewColumns(String schemaName) {
        return Collections.emptyMap();
    }

    @Override
    public List<DBTableColumn> listBasicMViewColumns(String schemaName, String externalTableName) {
        return Collections.emptyList();
    }

    @Override
    public Map<String, List<DBTableColumn>> listBasicColumnsInfo(String schemaName) {
        return Collections.emptyMap();
    }

    // fix-H bug D: batch index/constraint/options/partition variants — ODC aggregator path uses these
    // when caching schema-wide metadata. Per-table variants below are implemented; empty here means
    // ODC will fall back to per-table lookups on demand (slower but correct).
    @Override
    public Map<String, List<DBTableIndex>> listTableIndexes(String schemaName) {
        return Collections.emptyMap();
    }

    @Override
    public Map<String, List<DBTableConstraint>> listTableConstraints(String schemaName) {
        return Collections.emptyMap();
    }

    @Override
    public Map<String, DBTableOptions> listTableOptions(String schemaName) {
        return Collections.emptyMap();
    }

    @Override
    public Map<String, DBTablePartition> listTablePartitions(@NonNull String schemaName, List<String> tableNames) {
        // DB2 partitioned-table feature is out of scope; empty map indicates "no table has partitions"
        // which is a safe truth for non-partitioned DB2 tables (and matches the common DB2 11.5 default).
        return Collections.emptyMap();
    }

    @Override
    public List<DBTablePartition> listTableRangePartitionInfo(String tenantName) {
        return Collections.emptyList();
    }

    @Override
    public List<DBTableSubpartitionDefinition> listSubpartitions(String schemaName, String tableName) {
        return Collections.emptyList();
    }

    @Override
    public Boolean isLowerCaseTableName() {
        return false;
    }

    @Override
    public List<DBObjectIdentity> listPartitionTables(String partitionMethod) {
        // DB2 partitioned tables out of scope; see listTablePartitions().
        return Collections.emptyList();
    }

    @Override
    public List<DBTableConstraint> listTableConstraints(String schemaName, String tableName) {
        String sql = "SELECT TABSCHEMA, TABNAME, CONSTNAME, TYPE FROM SYSCAT.TABCONST "
                + "WHERE TABSCHEMA = ? AND TABNAME = ? ORDER BY CONSTNAME";
        return jdbcOperations.query(sql, new Object[] {schemaName, tableName}, (rs, rowNum) -> {
            DBTableConstraint constraint = new DBTableConstraint();
            constraint.setSchemaName(rs.getString("TABSCHEMA"));
            constraint.setTableName(rs.getString("TABNAME"));
            constraint.setName(rs.getString("CONSTNAME"));
            String type = rs.getString("TYPE");
            constraint.setType(mapDb2ConstraintType(type));
            return constraint;
        });
    }

    private DBConstraintType mapDb2ConstraintType(String db2Type) {
        if (db2Type == null) {
            return DBConstraintType.UNKNOWN;
        }
        switch (db2Type.trim().toUpperCase()) {
            case "P":
                return DBConstraintType.PRIMARY_KEY;
            case "U":
                return DBConstraintType.UNIQUE_KEY;
            case "F":
                return DBConstraintType.FOREIGN_KEY;
            case "K":
                return DBConstraintType.CHECK;
            default:
                return DBConstraintType.UNKNOWN;
        }
    }

    @Override
    public DBTablePartition getPartition(String schemaName, String tableName) {
        // No partition for DB2 in this release; null tells the upstream "no partition info available"
        // (DBTableService treats null partition as not-partitioned, not as an error).
        return null;
    }

    @Override
    public List<DBTableIndex> listTableIndexes(String schemaName, String tableName) {
        String sql = "SELECT INDSCHEMA, INDNAME, TABSCHEMA, TABNAME, UNIQUERULE "
                + "FROM SYSCAT.INDEXES WHERE TABSCHEMA = ? AND TABNAME = ? ORDER BY INDNAME";
        return jdbcOperations.query(sql, new Object[] {schemaName, tableName}, (rs, rowNum) -> {
            DBTableIndex index = new DBTableIndex();
            index.setSchemaName(rs.getString("INDSCHEMA"));
            index.setName(rs.getString("INDNAME"));
            index.setTableName(rs.getString("TABNAME"));
            String uniqueRule = rs.getString("UNIQUERULE");
            // DB2 UNIQUERULE: D=Duplicates allowed, U=Unique, P=Primary
            index.setUnique(uniqueRule != null && !"D".equalsIgnoreCase(uniqueRule.trim()));
            return index;
        });
    }

    @Override
    public String getTableDDL(String schemaName, String tableName) {
        // fix-H bug D: DB2 DDL extraction (db2look or SYSPROC.DB2LK_GENERATE_DDL) is out of scope for
        // this release (design.md §6 excludes "DDL export"). Returning empty string instead of null
        // because some callers do `.contains(...)` on the result.
        return "";
    }

    @Override
    public DBTableOptions getTableOptions(String schemaName, String tableName) {
        // ODC table-detail "Options" sub-tab tolerates null (treats it as "no options to display").
        return null;
    }

    @Override
    public DBTableOptions getTableOptions(String schemaName, String tableName, String ddl) {
        return null;
    }

    @Override
    public List<DBColumnGroupElement> listTableColumnGroups(String schemaName, String tableName) {
        // DB2 doesn't have OB-style column groups; return empty.
        return Collections.emptyList();
    }

    // fix-H bug D: per-object getX methods — out of scope for current release. ODC controller layer
    // shapes null as 404 (not 500), so returning null is the safe degradation that matches the empty
    // list* contract above.
    @Override
    public DBView getView(String schemaName, String viewName) {
        return null;
    }

    @Override
    public DBFunction getFunction(String schemaName, String functionName) {
        return null;
    }

    @Override
    public DBProcedure getProcedure(String schemaName, String procedureName) {
        return null;
    }

    @Override
    public DBPackage getPackage(String schemaName, String packageName) {
        return null;
    }

    @Override
    public DBTrigger getTrigger(String schemaName, String packageName) {
        return null;
    }

    @Override
    public DBType getType(String schemaName, String typeName) {
        return null;
    }

    @Override
    public DBSequence getSequence(String schemaName, String sequenceName) {
        return null;
    }

    @Override
    public DBSynonym getSynonym(String schemaName, String synonymName, DBSynonymType synonymType) {
        return null;
    }

    @Override
    public Map<String, DBTable> getTables(String schemaName, List<String> tableNames) {
        // fix-H bug D: aggregator path. Per-table DBTable assembly for DB2 happens via the dedicated
        // Db2TableExtension (schema-plugin-db2) which orchestrates columns + indexes + constraints
        // calls on this accessor — the batch path here is not used by the DB2 plugin. Return empty
        // map so upstream paths (if any) see "no preloaded tables" and fall back to per-table calls.
        return Collections.emptyMap();
    }
}
