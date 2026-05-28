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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.springframework.jdbc.BadSqlGrammarException;
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
import com.oceanbase.tools.dbbrowser.util.StringUtils;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PostgresSchemaAccessor implements DBSchemaAccessor {

    protected JdbcOperations jdbcOperations;

    public PostgresSchemaAccessor(@NonNull JdbcOperations jdbcOperations) {
        this.jdbcOperations = jdbcOperations;
    }

    @Override
    public List<String> showDatabases() {
        String sql = "SELECT schema_name FROM information_schema.schemata "
                + "where schema_name not like 'pg_%' "
                + "and schema_name <> 'information_schema'";
        return jdbcOperations.queryForList(sql, String.class);
    }

    @Override
    public DBDatabase getDatabase(String schemaName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBDatabase> listDatabases() {
        List<String> schemas = showDatabases();
        String sql = "SELECT "
                + "    datcollate AS collation, "
                + "    pg_encoding_to_char(encoding) AS charset "
                + "FROM pg_database "
                + "WHERE datname = current_database();";
        AtomicReference<String> charset = new AtomicReference<>();
        AtomicReference<String> collation = new AtomicReference<>();
        jdbcOperations.query(sql, rs -> {
            collation.set(rs.getString(1));
            charset.set(rs.getString(2));
        });
        return schemas.stream().map(schema -> {
            DBDatabase database = new DBDatabase();
            database.setId(schema);
            database.setName(schema);
            database.setCollation(collation.get());
            database.setCharset(charset.get());
            return database;
        }).collect(Collectors.toList());
    }

    @Override
    public void switchDatabase(String schemaName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBObjectIdentity> listUsers() {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<String> showTables(String schemaName) {
        StringBuilder sb = new StringBuilder();
        sb.append("select table_name from information_schema.tables where table_schema = ");
        sb.append("'").append(schemaName).append("'");
        sb.append(" and table_type = 'BASE TABLE' ");
        sb.append(" and table_name not in (SELECT relname FROM pg_class c ");
        sb.append(" JOIN pg_inherits i ON c.oid = i.inhrelid);");
        List<String> tableNames;
        try {
            tableNames = jdbcOperations.query(sb.toString(), (rs, rowNum) -> rs.getString(1));
        } catch (BadSqlGrammarException e) {
            if (StringUtils.containsIgnoreCase(e.getMessage(), "Unknown schema")) {
                return Collections.emptyList();
            }
            throw e;
        }
        return tableNames;
    }

    @Override
    public List<String> showTablesLike(String schemaName, String tableNameLike) {
        // Implementation backed by information_schema. Wildcards (%, _) follow the same
        // semantics as SQL LIKE; an empty/blank pattern returns all tables in the schema.
        StringBuilder sb = new StringBuilder();
        sb.append("select table_name from information_schema.tables where table_schema = ");
        sb.append("'").append(schemaName).append("'");
        sb.append(" and table_type = 'BASE TABLE'");
        if (StringUtils.isNotBlank(tableNameLike)) {
            sb.append(" and table_name like ");
            sb.append("'").append(tableNameLike).append("'");
        }
        sb.append(";");
        try {
            return jdbcOperations.query(sb.toString(), (rs, rowNum) -> rs.getString(1));
        } catch (BadSqlGrammarException e) {
            if (StringUtils.containsIgnoreCase(e.getMessage(), "Unknown schema")) {
                return Collections.emptyList();
            }
            throw e;
        }
    }

    @Override
    public List<DBObjectIdentity> listTables(String schemaName, String tableNameLike) {
        // Drive the result entirely off information_schema; if schemaName is blank we list
        // tables across all user schemas (excluding pg_* / information_schema), which matches
        // the contract used by DBIdentitiesService.listTables.
        StringBuilder sb = new StringBuilder();
        sb.append("select table_schema, table_name from information_schema.tables where table_type = 'BASE TABLE'");
        sb.append(" and table_schema not like 'pg_%' and table_schema <> 'information_schema'");
        if (StringUtils.isNotBlank(schemaName)) {
            sb.append(" and table_schema = '").append(schemaName).append("'");
        }
        if (StringUtils.isNotBlank(tableNameLike)) {
            sb.append(" and table_name like '").append(tableNameLike).append("'");
        }
        sb.append(" order by table_schema, table_name;");
        try {
            return jdbcOperations.query(sb.toString(), (rs, rowNum) -> {
                DBObjectIdentity identity = new DBObjectIdentity();
                identity.setType(DBObjectType.TABLE);
                identity.setSchemaName(rs.getString(1));
                identity.setName(rs.getString(2));
                return identity;
            });
        } catch (BadSqlGrammarException e) {
            if (StringUtils.containsIgnoreCase(e.getMessage(), "Unknown schema")) {
                return Collections.emptyList();
            }
            throw e;
        }
    }

    @Override
    public List<String> showExternalTablesLike(String schemaName, String tableNameLike) {
        // PostgreSQL/GaussDB don't expose ODC-style external tables; return an empty list so
        // callers that probe metadata (workbench identities, DBIdentitiesService.listExternalTables)
        // can degrade gracefully instead of bubbling an HTTP 500 to the UI.
        return Collections.emptyList();
    }

    @Override
    public List<DBObjectIdentity> listExternalTables(String schemaName, String tableNameLike) {
        // See showExternalTablesLike comment above; return empty for the same reason.
        return Collections.emptyList();
    }

    @Override
    public boolean isExternalTable(String schemaName, String tableName) {
        return false;
    }

    @Override
    public boolean syncExternalTableFiles(String schemaName, String tableName) {
        // PostgreSQL/GaussDB don't model external tables in the ODC sense; nothing to sync.
        return false;
    }

    @Override
    public List<DBObjectIdentity> listViews(String schemaName) {
        if (StringUtils.isBlank(schemaName)) {
            return Collections.emptyList();
        }
        String sql = "select table_schema, table_name from information_schema.views "
                + "where table_schema = '" + schemaName + "' "
                + "order by table_name;";
        try {
            return jdbcOperations.query(sql, (rs, rowNum) -> {
                DBObjectIdentity identity = new DBObjectIdentity();
                identity.setType(DBObjectType.VIEW);
                identity.setSchemaName(rs.getString(1));
                identity.setName(rs.getString(2));
                return identity;
            });
        } catch (BadSqlGrammarException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public List<DBObjectIdentity> listAllViews(String viewNameLike) {
        StringBuilder sb = new StringBuilder();
        sb.append("select table_schema, table_name from information_schema.views ");
        sb.append("where table_schema not like 'pg_%' and table_schema <> 'information_schema'");
        if (StringUtils.isNotBlank(viewNameLike)) {
            sb.append(" and table_name like '").append(viewNameLike).append("'");
        }
        sb.append(" order by table_schema, table_name;");
        try {
            return jdbcOperations.query(sb.toString(), (rs, rowNum) -> {
                DBObjectIdentity identity = new DBObjectIdentity();
                identity.setType(DBObjectType.VIEW);
                identity.setSchemaName(rs.getString(1));
                identity.setName(rs.getString(2));
                return identity;
            });
        } catch (BadSqlGrammarException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public List<DBObjectIdentity> listAllUserViews(String viewNameLike) {
        // Same as listAllViews — PG doesn't strongly distinguish user vs all here; we already
        // filter out pg_* / information_schema in listAllViews.
        return listAllViews(viewNameLike);
    }

    @Override
    public List<DBObjectIdentity> listAllSystemViews(String viewNameLike) {
        // System views in PG live in pg_catalog & information_schema. Returning an empty list
        // is safe for the workbench identities API (the user-facing tree does not surface them).
        return Collections.emptyList();
    }

    @Override
    public List<String> showSystemViews(String schemaName) {
        return Collections.emptyList();
    }

    @Override
    public List<DBObjectIdentity> listMViews(String schemaName) {
        // Materialized views — query pg_matviews; if it doesn't exist (very old PG) fall back
        // to an empty list rather than 500-ing the workbench identities API.
        if (StringUtils.isBlank(schemaName)) {
            return Collections.emptyList();
        }
        String sql = "select schemaname, matviewname from pg_matviews "
                + "where schemaname = '" + schemaName + "' "
                + "order by matviewname;";
        try {
            return jdbcOperations.query(sql, (rs, rowNum) -> {
                DBObjectIdentity identity = new DBObjectIdentity();
                identity.setType(DBObjectType.MATERIALIZED_VIEW);
                identity.setSchemaName(rs.getString(1));
                identity.setName(rs.getString(2));
                return identity;
            });
        } catch (BadSqlGrammarException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public List<DBObjectIdentity> listAllMViewsLike(String mViewNameLike) {
        StringBuilder sb = new StringBuilder();
        sb.append("select schemaname, matviewname from pg_matviews ");
        sb.append("where schemaname not like 'pg_%' and schemaname <> 'information_schema'");
        if (StringUtils.isNotBlank(mViewNameLike)) {
            sb.append(" and matviewname like '").append(mViewNameLike).append("'");
        }
        sb.append(" order by schemaname, matviewname;");
        try {
            return jdbcOperations.query(sb.toString(), (rs, rowNum) -> {
                DBObjectIdentity identity = new DBObjectIdentity();
                identity.setType(DBObjectType.MATERIALIZED_VIEW);
                identity.setSchemaName(rs.getString(1));
                identity.setName(rs.getString(2));
                return identity;
            });
        } catch (BadSqlGrammarException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public Boolean refreshMVData(DBMViewRefreshParameter parameter) {
        throw new UnsupportedOperationException("not support yet");
    }

    @Override
    public DBMaterializedView getMView(String schemaName, String mViewName) {
        throw new UnsupportedOperationException("not support yet");
    }

    @Override
    public List<DBTableConstraint> listMViewConstraints(String schemaName, String mViewName) {
        throw new UnsupportedOperationException("not support yet");
    }

    @Override
    public List<DBMViewRefreshRecord> listMViewRefreshRecords(DBMViewRefreshRecordParam param) {
        throw new UnsupportedOperationException("not support yet");
    }

    @Override
    public List<DBTableIndex> listMViewIndexes(String schemaName, String mViewName) {
        throw new UnsupportedOperationException("not support yet");
    }

    @Override
    public List<DBVariable> showVariables() {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBVariable> showSessionVariables() {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBVariable> showGlobalVariables() {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<String> showCharset() {
        String sql = "select DISTINCT pg_encoding_to_char(collencoding) from pg_collation;";
        return jdbcOperations.queryForList(sql, String.class);
    }

    @Override
    public List<String> showCollation() {
        String sql = "select DISTINCT collname  from pg_collation;";
        return jdbcOperations.queryForList(sql, String.class);
    }

    @Override
    public List<DBPLObjectIdentity> listFunctions(String schemaName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBPLObjectIdentity> listProcedures(String schemaName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBPLObjectIdentity> listPackages(String schemaName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBPLObjectIdentity> listPackageBodies(String schemaName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBPLObjectIdentity> listTriggers(String schemaName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBPLObjectIdentity> listTypes(String schemaName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBObjectIdentity> listSequences(String schemaName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBObjectIdentity> listSynonyms(String schemaName,
            DBSynonymType synonymType) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public Map<String, List<DBTableColumn>> listTableColumns(
            String schemaName, List<String> tableNames) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBTableColumn> listTableColumns(String schemeName, String tableName) {
        // Build a DBTableColumn list from information_schema.columns. We deliberately use only
        // information_schema (portable across PostgreSQL / openGauss / GaussDB) plus a single
        // pg_class/pg_index probe for the PRIMARY KEY flag. Columns that aren't part of any
        // primary key get KeyType.NONE so the UI renders them normally.
        if (StringUtils.isBlank(schemeName) || StringUtils.isBlank(tableName)) {
            return Collections.emptyList();
        }
        // Step 1: pull the PK column names so we can stamp KeyType.PRI on the matching DBTableColumn.
        java.util.Set<String> pkColumns = new java.util.HashSet<>();
        String pkSql = "select kcu.column_name "
                + "from information_schema.table_constraints tc "
                + "join information_schema.key_column_usage kcu "
                + "  on tc.constraint_name = kcu.constraint_name "
                + " and tc.table_schema = kcu.table_schema "
                + " and tc.table_name = kcu.table_name "
                + "where tc.constraint_type = 'PRIMARY KEY' "
                + " and tc.table_schema = '" + schemeName + "' "
                + " and tc.table_name = '" + tableName + "';";
        try {
            pkColumns.addAll(jdbcOperations.query(pkSql, (rs, rowNum) -> rs.getString(1)));
        } catch (BadSqlGrammarException ignored) {
            // Swallow — proceed without PK info rather than failing the whole metadata read.
        }

        // Step 2: read the column list itself.
        String sql = "select column_name, data_type, udt_name, character_maximum_length, "
                + "numeric_precision, numeric_scale, is_nullable, column_default, "
                + "ordinal_position "
                + "from information_schema.columns "
                + "where table_schema = '" + schemeName + "' and table_name = '" + tableName + "' "
                + "order by ordinal_position;";
        try {
            return jdbcOperations.query(sql, (rs, rowNum) -> {
                DBTableColumn column = new DBTableColumn();
                column.setSchemaName(schemeName);
                column.setTableName(tableName);
                String name = rs.getString(1);
                column.setName(name);
                String dataType = rs.getString(2);
                String udtName = rs.getString(3);
                // udt_name is the most specific type (e.g. "varchar", "int4", "jsonb",
                // "_text" for arrays). Prefer it over data_type for ODC's column rendering,
                // but fall back to data_type when udt_name is null.
                String typeName = udtName != null ? udtName : dataType;
                column.setTypeName(typeName);
                // PG returns these length / precision / scale columns as Number subclasses
                // (Integer for openGauss / Long for some PG drivers). Use Number#xxxValue() so
                // we don't ClassCastException when the JDBC driver hands us an Integer where
                // we expected a Long (the bug seen in the initial 5104c79a build).
                Number charMaxLenObj = (Number) rs.getObject(4);
                Number numericPrecisionObj = (Number) rs.getObject(5);
                Number numericScaleObj = (Number) rs.getObject(6);
                column.setFullTypeName(
                        buildFullTypeName(typeName, charMaxLenObj, numericPrecisionObj, numericScaleObj));
                if (charMaxLenObj != null) {
                    column.setMaxLength(charMaxLenObj.longValue());
                }
                if (numericPrecisionObj != null) {
                    column.setPrecision(numericPrecisionObj.longValue());
                }
                if (numericScaleObj != null) {
                    column.setScale(numericScaleObj.intValue());
                }
                String isNullable = rs.getString(7);
                column.setNullable("YES".equalsIgnoreCase(isNullable));
                column.setDefaultValue(rs.getString(8));
                column.setOrdinalPosition(rs.getInt(9));
                if (pkColumns.contains(name)) {
                    column.setKeyType(DBTableColumn.KeyType.PRI);
                }
                return column;
            });
        } catch (BadSqlGrammarException e) {
            return Collections.emptyList();
        }
    }

    /**
     * Build a human-readable full type name. Mirrors information_schema conventions:
     * <ul>
     * <li>{@code varchar(255)} when only character_maximum_length is set</li>
     * <li>{@code numeric(10,2)} when numeric_precision + numeric_scale are set</li>
     * <li>{@code numeric(10)} when only numeric_precision is set</li>
     * <li>plain type name otherwise (e.g. {@code jsonb}, {@code timestamptz})</li>
     * </ul>
     */
    private String buildFullTypeName(String typeName, Number charMaxLen, Number numericPrecision,
            Number numericScale) {
        if (charMaxLen != null) {
            return typeName + "(" + charMaxLen.longValue() + ")";
        }
        if (numericPrecision != null) {
            if (numericScale != null) {
                return typeName + "(" + numericPrecision.longValue() + "," + numericScale.intValue() + ")";
            }
            return typeName + "(" + numericPrecision.longValue() + ")";
        }
        return typeName;
    }

    @Override
    public Map<String, List<DBTableColumn>> listBasicTableColumns(String schemaName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBTableColumn> listBasicTableColumns(String schemaName, String tableName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public Map<String, List<DBTableColumn>> listBasicViewColumns(String schemaName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBTableColumn> listBasicViewColumns(String schemaName, String viewName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public Map<String, List<DBTableColumn>> listBasicExternalTableColumns(String schemaName) {
        throw new UnsupportedOperationException("not support yet");
    }

    @Override
    public List<DBTableColumn> listBasicExternalTableColumns(String schemaName, String externalTableName) {
        throw new UnsupportedOperationException("not support yet");
    }

    @Override
    public Map<String, List<DBTableColumn>> listBasicMViewColumns(String schemaName) {
        throw new UnsupportedOperationException("not support yet");
    }

    @Override
    public List<DBTableColumn> listBasicMViewColumns(String schemaName, String externalTableName) {
        throw new UnsupportedOperationException("not support yet");
    }

    @Override
    public Map<String, List<DBTableColumn>> listBasicColumnsInfo(String schemaName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public Map<String, List<DBTableIndex>> listTableIndexes(String schemaName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public Map<String, List<DBTableConstraint>> listTableConstraints(
            String schemaName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public Map<String, DBTableOptions> listTableOptions(
            String schemaName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public Map<String, DBTablePartition> listTablePartitions(
            @NonNull String schemaName, List<String> tableNames) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBTablePartition> listTableRangePartitionInfo(String tenantName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBTableSubpartitionDefinition> listSubpartitions(
            String schemaName, String tableName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public Boolean isLowerCaseTableName() {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBObjectIdentity> listPartitionTables(String partitionMethod) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBTableConstraint> listTableConstraints(String schemaName, String tableName) {
        // Read constraints from information_schema, grouping by constraint name + type.
        if (StringUtils.isBlank(schemaName) || StringUtils.isBlank(tableName)) {
            return Collections.emptyList();
        }
        String sql = "select tc.constraint_name, tc.constraint_type, kcu.column_name, "
                + "       kcu.ordinal_position, ccu.table_schema, ccu.table_name, ccu.column_name "
                + "  from information_schema.table_constraints tc "
                + "  left join information_schema.key_column_usage kcu "
                + "    on tc.constraint_name = kcu.constraint_name "
                + "   and tc.table_schema = kcu.table_schema "
                + "   and tc.table_name = kcu.table_name "
                + "  left join information_schema.constraint_column_usage ccu "
                + "    on tc.constraint_name = ccu.constraint_name "
                + "   and tc.constraint_schema = ccu.constraint_schema "
                + " where tc.table_schema = '" + schemaName + "' "
                + "   and tc.table_name = '" + tableName + "' "
                + " order by tc.constraint_name, kcu.ordinal_position;";
        java.util.LinkedHashMap<String, DBTableConstraint> byName = new java.util.LinkedHashMap<>();
        try {
            jdbcOperations.query(sql, rs -> {
                String constraintName = rs.getString(1);
                String constraintType = rs.getString(2);
                String columnName = rs.getString(3);
                String refSchema = rs.getString(5);
                String refTable = rs.getString(6);
                String refColumn = rs.getString(7);
                DBTableConstraint constraint = byName.computeIfAbsent(constraintName, k -> {
                    DBTableConstraint c = new DBTableConstraint();
                    c.setSchemaName(schemaName);
                    c.setTableName(tableName);
                    c.setName(k);
                    c.setColumnNames(new ArrayList<>());
                    c.setReferenceColumnNames(new ArrayList<>());
                    if ("PRIMARY KEY".equalsIgnoreCase(constraintType)) {
                        c.setType(DBConstraintType.PRIMARY_KEY);
                    } else if ("UNIQUE".equalsIgnoreCase(constraintType)) {
                        c.setType(DBConstraintType.UNIQUE_KEY);
                    } else if ("FOREIGN KEY".equalsIgnoreCase(constraintType)) {
                        c.setType(DBConstraintType.FOREIGN_KEY);
                    } else if ("CHECK".equalsIgnoreCase(constraintType)) {
                        c.setType(DBConstraintType.CHECK);
                    } else {
                        c.setType(DBConstraintType.UNKNOWN);
                    }
                    return c;
                });
                if (columnName != null && !constraint.getColumnNames().contains(columnName)) {
                    constraint.getColumnNames().add(columnName);
                }
                if (constraint.getType() == DBConstraintType.FOREIGN_KEY) {
                    if (refSchema != null) {
                        constraint.setReferenceSchemaName(refSchema);
                    }
                    if (refTable != null) {
                        constraint.setReferenceTableName(refTable);
                    }
                    if (refColumn != null && !constraint.getReferenceColumnNames().contains(refColumn)) {
                        constraint.getReferenceColumnNames().add(refColumn);
                    }
                }
            });
        } catch (BadSqlGrammarException e) {
            return Collections.emptyList();
        }
        return new ArrayList<>(byName.values());
    }

    @Override
    public DBTablePartition getPartition(String schemaName, String tableName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBTableIndex> listTableIndexes(String schemaName, String tableName) {
        // Read indexes via pg_indexes. We do NOT try to populate every field (e.g.
        // index_type/algorithm or partial expressions) — only the visible flags the UI shows.
        if (StringUtils.isBlank(schemaName) || StringUtils.isBlank(tableName)) {
            return Collections.emptyList();
        }
        // pg_indexes gives us name + indexdef per index; combine with pg_index/pg_class for
        // unique + primary flags.
        String sql = "select i.relname as index_name, idx.indisunique, idx.indisprimary, "
                + "       array_to_string(array(select pg_get_indexdef(idx.indexrelid, k + 1, true) "
                + "                              from generate_subscripts(idx.indkey, 1) as k "
                + "                              order by k), ', ') as columns "
                + "  from pg_index idx "
                + "  join pg_class i on i.oid = idx.indexrelid "
                + "  join pg_class t on t.oid = idx.indrelid "
                + "  join pg_namespace n on n.oid = t.relnamespace "
                + " where n.nspname = '" + schemaName + "' and t.relname = '" + tableName + "' "
                + " order by i.relname;";
        try {
            return jdbcOperations.query(sql, (rs, rowNum) -> {
                DBTableIndex index = new DBTableIndex();
                index.setSchemaName(schemaName);
                index.setTableName(tableName);
                index.setName(rs.getString(1));
                index.setUnique(rs.getBoolean(2));
                index.setPrimary(rs.getBoolean(3));
                index.setNonUnique(!rs.getBoolean(2));
                String columnsCsv = rs.getString(4);
                if (columnsCsv != null && !columnsCsv.isEmpty()) {
                    List<String> cols = new ArrayList<>();
                    for (String c : columnsCsv.split(",")) {
                        cols.add(c.trim());
                    }
                    index.setColumnNames(cols);
                } else {
                    index.setColumnNames(Collections.emptyList());
                }
                return index;
            });
        } catch (BadSqlGrammarException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public String getTableDDL(String schemaName, String tableName) {
        // PostgreSQL does not expose a native SHOW CREATE TABLE; we synthesise a best-effort
        // CREATE TABLE based on information_schema. This is intentionally simple — enough for
        // the UI to display a readable "structure" view, not a perfect round-trip-able DDL.
        if (StringUtils.isBlank(schemaName) || StringUtils.isBlank(tableName)) {
            return "";
        }
        List<DBTableColumn> columns = listTableColumns(schemaName, tableName);
        if (columns.isEmpty()) {
            return "-- (no columns found for " + schemaName + "." + tableName + ")";
        }
        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE \"").append(schemaName).append("\".\"").append(tableName).append("\" (\n");
        for (int i = 0; i < columns.size(); i++) {
            DBTableColumn c = columns.get(i);
            ddl.append("  \"").append(c.getName()).append("\" ");
            ddl.append(c.getFullTypeName() != null ? c.getFullTypeName() : c.getTypeName());
            if (Boolean.FALSE.equals(c.getNullable())) {
                ddl.append(" NOT NULL");
            }
            if (c.getDefaultValue() != null && !c.getDefaultValue().isEmpty()) {
                ddl.append(" DEFAULT ").append(c.getDefaultValue());
            }
            if (i < columns.size() - 1) {
                ddl.append(",");
            }
            ddl.append("\n");
        }
        // Append primary key if any
        List<String> pkCols = columns.stream()
                .filter(c -> c.getKeyType() == DBTableColumn.KeyType.PRI)
                .map(DBTableColumn::getName)
                .collect(Collectors.toList());
        if (!pkCols.isEmpty()) {
            ddl.append(",  PRIMARY KEY (");
            ddl.append(pkCols.stream().map(c -> "\"" + c + "\"").collect(Collectors.joining(", ")));
            ddl.append(")\n");
        }
        ddl.append(");");
        return ddl.toString();
    }

    @Override
    public DBTableOptions getTableOptions(String schemaName, String tableName) {
        // PostgreSQL doesn't expose MySQL-style table options; return a minimal struct so the
        // workbench JSON response is well-formed. (DBTableOptions has no schemaName slot; the
        // owning DBTable already carries the schema, so we just leave engine/charset etc. null.)
        return new DBTableOptions();
    }

    @Override
    public DBTableOptions getTableOptions(String schemaName, String tableName, String ddl) {
        return getTableOptions(schemaName, tableName);
    }

    @Override
    public List<DBColumnGroupElement> listTableColumnGroups(String schemaName,
            String tableName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public DBView getView(String schemaName, String viewName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public DBFunction getFunction(String schemaName, String functionName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public DBProcedure getProcedure(String schemaName, String procedureName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public DBPackage getPackage(String schemaName, String packageName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public DBTrigger getTrigger(String schemaName, String packageName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public DBType getType(String schemaName, String typeName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public DBSequence getSequence(String schemaName, String sequenceName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public DBSynonym getSynonym(String schemaName, String synonymName,
            DBSynonymType synonymType) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public Map<String, DBTable> getTables(String schemaName,
            List<String> tableNames) {
        throw new UnsupportedOperationException("Not supported yet");
    }
}
