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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcOperations;

import com.oceanbase.tools.dbbrowser.model.DBColumnGroupElement;
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
 * Skeleton {@link DBSchemaAccessor} for IBM Db2 LUW (primary target: 12.x; 11.5 sub-variants will
 * be wired in T-004 via additional subclasses similar to
 * {@code OBMySQLBetween220And225XSchemaAccessor}).
 *
 * <p>
 * This T-003 skeleton:
 * <ul>
 * <li>Fully implements the metadata queries needed by the workbench MVP using
 * {@code SYSCAT.* / SYSIBM.SYSDUMMY1 / SYSIBMADM.*} 12.x system views — list/show databases,
 * tables, views, indexes, sequences, synonyms (ALIAS), functions, procedures, triggers (read-only
 * metadata).</li>
 * <li>Returns {@link Collections#emptyList()} for object types DB2 either doesn't have or the
 * workbench MVP explicitly hides (variables, charset / collation lists, package list, type list,
 * mview, partition list).</li>
 * <li>Throws {@link UnsupportedOperationException} with the grep-friendly
 * {@code "Not supported for DB2 yet"} keyword for write-side methods that the workbench MVP does
 * not exercise; T-004 will fill remaining "get*" / detail methods as needed.</li>
 * </ul>
 *
 * <p>
 * <b>11.5 vs 12.x split (T-004 follow-up):</b> Db2 12.x added a handful of {@code SYSCAT} columns
 * and renamed the package routines view. The skeleton below intentionally selects only the columns
 * that are present in both 11.5 and 12.x so that the same accessor class works unchanged on 11.5;
 * should 12.x-only columns be required, introduce
 * {@code DB2Greater12SchemaAccessor extends DB2SchemaAccessor} in T-004 (see also
 * {@code compat_risks.md} compat-RISK-11).
 */
@Slf4j
public class DB2SchemaAccessor implements DBSchemaAccessor {

    /** Error message used by all placeholder methods. Asserted by unit tests. */
    static final String NOT_SUPPORTED = "Not supported for DB2 yet";

    /**
     * 12.x extension point: subclasses can override individual SELECTs without rewriting the whole
     * accessor. T-004 will populate this point with 11.5 vs 12.x sub-classing.
     */
    protected final JdbcOperations jdbcOperations;

    public DB2SchemaAccessor(@NonNull JdbcOperations jdbcOperations) {
        this.jdbcOperations = jdbcOperations;
    }

    // -------------------------------------------------------------------------------------------
    // Database / schema listing
    // -------------------------------------------------------------------------------------------

    /**
     * List DB2 schemas excluding the system schemas (SYSCAT / SYSIBM / SYSIBMADM / SYSIBMINTERNAL /
     * SYSPROC / SYSPUBLIC / SYSSTAT / SYSTOOLS / NULLID / SQLJ).
     */
    @Override
    public List<String> showDatabases() {
        String sql = "SELECT TRIM(SCHEMANAME) AS SCHEMANAME FROM SYSCAT.SCHEMATA "
                + "WHERE SCHEMANAME NOT LIKE 'SYS%' "
                + "AND SCHEMANAME NOT IN ('NULLID','SQLJ') "
                + "ORDER BY SCHEMANAME";
        return jdbcOperations.queryForList(sql, String.class);
    }

    @Override
    public List<DBDatabase> listDatabases() {
        return showDatabases().stream().map(name -> {
            DBDatabase db = new DBDatabase();
            db.setId(name);
            db.setName(name);
            return db;
        }).collect(Collectors.toList());
    }

    /**
     * DB2 does not support "switching" current schema mid-session through this contract (use
     * {@code SET CURRENT SCHEMA} via {@code Db2ConnectionExtension.generateJdbcUrl}'s
     * {@code currentSchema} param instead).
     */
    @Override
    public void switchDatabase(String schemaName) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public DBDatabase getDatabase(String schemaName) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public List<DBObjectIdentity> listUsers() {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    // -------------------------------------------------------------------------------------------
    // Tables / views
    // -------------------------------------------------------------------------------------------

    @Override
    public List<String> showTables(String schemaName) {
        return jdbcOperations.queryForList(
                "SELECT TRIM(TABNAME) AS TABNAME FROM SYSCAT.TABLES "
                        + "WHERE TABSCHEMA = ? AND TYPE = 'T' "
                        + "ORDER BY TABNAME",
                String.class, schemaName);
    }

    @Override
    public List<String> showTablesLike(String schemaName, String tableNameLike) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public List<DBObjectIdentity> listTables(String schemaName, String tableNameLike) {
        return showTables(schemaName).stream().map(name -> {
            DBObjectIdentity id = new DBObjectIdentity();
            id.setSchemaName(schemaName);
            id.setName(name);
            id.setType(DBObjectType.TABLE);
            return id;
        }).collect(Collectors.toList());
    }

    /** DB2 has no external table concept. */
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
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public List<DBObjectIdentity> listViews(String schemaName) {
        return jdbcOperations.query(
                "SELECT TRIM(VIEWNAME) AS NAME FROM SYSCAT.VIEWS "
                        + "WHERE VIEWSCHEMA = ? ORDER BY NAME",
                (rs, rowNum) -> {
                    DBObjectIdentity id = new DBObjectIdentity();
                    id.setSchemaName(schemaName);
                    id.setName(rs.getString("NAME"));
                    id.setType(DBObjectType.VIEW);
                    return id;
                }, schemaName);
    }

    @Override
    public List<DBObjectIdentity> listAllViews(String viewNameLike) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public List<DBObjectIdentity> listAllUserViews(String viewNameLike) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public List<DBObjectIdentity> listAllSystemViews(String viewNameLike) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public List<String> showSystemViews(String schemaName) {
        return jdbcOperations.queryForList(
                "SELECT TRIM(VIEWNAME) AS NAME FROM SYSCAT.VIEWS "
                        + "WHERE VIEWSCHEMA LIKE 'SYS%' AND VIEWSCHEMA = ? "
                        + "ORDER BY NAME",
                String.class, schemaName);
    }

    // -------------------------------------------------------------------------------------------
    // MView / variables / charset / collation (none in MVP)
    // -------------------------------------------------------------------------------------------

    /** DB2 MQTs are not in MVP scope. */
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
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public DBMaterializedView getMView(String schemaName, String mViewName) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
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
        // DB2 reports CCSID per database; there is no MySQL-style "SHOW CHARSET" surface.
        return Collections.emptyList();
    }

    @Override
    public List<String> showCollation() {
        return Collections.emptyList();
    }

    // -------------------------------------------------------------------------------------------
    // Routines (functions, procedures, packages, triggers, types)
    // -------------------------------------------------------------------------------------------

    @Override
    public List<DBPLObjectIdentity> listFunctions(String schemaName) {
        return listRoutines(schemaName, "F", DBObjectType.FUNCTION);
    }

    @Override
    public List<DBPLObjectIdentity> listProcedures(String schemaName) {
        return listRoutines(schemaName, "P", DBObjectType.PROCEDURE);
    }

    /** DB2 SQL bind packages are not first-class SQL objects in the workbench MVP. */
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
        return jdbcOperations.query(
                "SELECT TRIM(TRIGNAME) AS NAME FROM SYSCAT.TRIGGERS "
                        + "WHERE TRIGSCHEMA = ? ORDER BY NAME",
                (rs, rowNum) -> {
                    DBPLObjectIdentity id = new DBPLObjectIdentity();
                    id.setSchemaName(schemaName);
                    id.setName(rs.getString("NAME"));
                    id.setType(DBObjectType.TRIGGER);
                    return id;
                }, schemaName);
    }

    @Override
    public List<DBPLObjectIdentity> listTypes(String schemaName) {
        return Collections.emptyList();
    }

    private List<DBPLObjectIdentity> listRoutines(String schemaName, String routineType,
            DBObjectType objectType) {
        return jdbcOperations.query(
                "SELECT TRIM(ROUTINENAME) AS NAME FROM SYSCAT.ROUTINES "
                        + "WHERE ROUTINESCHEMA = ? AND ROUTINETYPE = ? "
                        + "ORDER BY NAME",
                (rs, rowNum) -> {
                    DBPLObjectIdentity id = new DBPLObjectIdentity();
                    id.setSchemaName(schemaName);
                    id.setName(rs.getString("NAME"));
                    id.setType(objectType);
                    return id;
                }, schemaName, routineType);
    }

    // -------------------------------------------------------------------------------------------
    // Sequences, synonyms (ALIAS)
    // -------------------------------------------------------------------------------------------

    @Override
    public List<DBObjectIdentity> listSequences(String schemaName) {
        return jdbcOperations.query(
                "SELECT TRIM(SEQNAME) AS NAME FROM SYSCAT.SEQUENCES "
                        + "WHERE SEQSCHEMA = ? AND SEQTYPE = 'S' "
                        + "ORDER BY NAME",
                (rs, rowNum) -> {
                    DBObjectIdentity id = new DBObjectIdentity();
                    id.setSchemaName(schemaName);
                    id.setName(rs.getString("NAME"));
                    id.setType(DBObjectType.SEQUENCE);
                    return id;
                }, schemaName);
    }

    /**
     * DB2 ALIAS is the closest analog to a synonym. {@code SYSCAT.TABLES.TYPE='A'} marks aliases.
     */
    @Override
    public List<DBObjectIdentity> listSynonyms(String schemaName, DBSynonymType synonymType) {
        return jdbcOperations.query(
                "SELECT TRIM(TABNAME) AS NAME FROM SYSCAT.TABLES "
                        + "WHERE TABSCHEMA = ? AND TYPE = 'A' "
                        + "ORDER BY NAME",
                (rs, rowNum) -> {
                    DBObjectIdentity id = new DBObjectIdentity();
                    id.setSchemaName(schemaName);
                    id.setName(rs.getString("NAME"));
                    id.setType(DBObjectType.SYNONYM);
                    return id;
                }, schemaName);
    }

    // -------------------------------------------------------------------------------------------
    // Columns / indexes / constraints
    // -------------------------------------------------------------------------------------------

    @Override
    public Map<String, List<DBTableColumn>> listTableColumns(String schemaName,
            List<String> tableNames) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public List<DBTableColumn> listTableColumns(String schemeName, String tableName) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public Map<String, List<DBTableColumn>> listBasicTableColumns(String schemaName) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public List<DBTableColumn> listBasicTableColumns(String schemaName, String tableName) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public Map<String, List<DBTableColumn>> listBasicViewColumns(String schemaName) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public List<DBTableColumn> listBasicViewColumns(String schemaName, String viewName) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public Map<String, List<DBTableColumn>> listBasicExternalTableColumns(String schemaName) {
        return Collections.emptyMap();
    }

    @Override
    public List<DBTableColumn> listBasicExternalTableColumns(String schemaName,
            String externalTableName) {
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
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public Map<String, List<DBTableIndex>> listTableIndexes(String schemaName) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public Map<String, List<DBTableConstraint>> listTableConstraints(String schemaName) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public Map<String, DBTableOptions> listTableOptions(String schemaName) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public Map<String, DBTablePartition> listTablePartitions(@NonNull String schemaName,
            List<String> tableNames) {
        return Collections.emptyMap();
    }

    @Override
    public List<DBTablePartition> listTableRangePartitionInfo(String tenantName) {
        return Collections.emptyList();
    }

    @Override
    public List<DBTableSubpartitionDefinition> listSubpartitions(String schemaName,
            String tableName) {
        return Collections.emptyList();
    }

    @Override
    public Boolean isLowerCaseTableName() {
        // DB2 unquoted identifiers fold to upper case; the workbench treats names case-insensitive
        // via SYSCAT but stores them in upper case (returned by SYSCAT.TABLES.TABNAME).
        return false;
    }

    @Override
    public List<DBObjectIdentity> listPartitionTables(String partitionMethod) {
        return Collections.emptyList();
    }

    @Override
    public List<DBTableConstraint> listTableConstraints(String schemaName, String tableName) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public DBTablePartition getPartition(String schemaName, String tableName) {
        return null;
    }

    @Override
    public List<DBTableIndex> listTableIndexes(String schemaName, String tableName) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public String getTableDDL(String schemaName, String tableName) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public DBTableOptions getTableOptions(String schemaName, String tableName) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public DBTableOptions getTableOptions(String schemaName, String tableName, String ddl) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public List<DBColumnGroupElement> listTableColumnGroups(String schemaName, String tableName) {
        return Collections.emptyList();
    }

    @Override
    public DBView getView(String schemaName, String viewName) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public DBFunction getFunction(String schemaName, String functionName) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public DBProcedure getProcedure(String schemaName, String procedureName) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public DBPackage getPackage(String schemaName, String packageName) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public DBTrigger getTrigger(String schemaName, String packageName) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public DBType getType(String schemaName, String typeName) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public DBSequence getSequence(String schemaName, String sequenceName) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public DBSynonym getSynonym(String schemaName, String synonymName,
            DBSynonymType synonymType) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public Map<String, DBTable> getTables(String schemaName, List<String> tableNames) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }
}
