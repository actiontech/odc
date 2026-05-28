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
package com.oceanbase.tools.dbbrowser.schema.hive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
import com.oceanbase.tools.dbbrowser.schema.hive.HiveSchemaUtil.DescribeRow;
import com.oceanbase.tools.dbbrowser.schema.hive.HiveSchemaUtil.HiveTableMetadata;
import com.oceanbase.tools.dbbrowser.util.HiveSqlBuilder;
import com.oceanbase.tools.dbbrowser.util.StringUtils;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link DBSchemaAccessor} implementation for Apache Hive (target version: 4.2.0).
 * <p>
 * Uses Hive-specific {@code SHOW} and {@code DESCRIBE} commands via {@link JdbcOperations}. Schema
 * metadata is parsed by {@link HiveSchemaUtil}. Unsupported features (indexes, constraints,
 * procedures, functions, etc.) return empty collections.
 * </p>
 *
 * @since ODC_release_4.3.4
 */
@Slf4j
public class HiveSchemaAccessor implements DBSchemaAccessor {

    private static final String DEFAULT_DATABASE = "default";

    protected JdbcOperations jdbcOperations;

    public HiveSchemaAccessor(@NonNull JdbcOperations jdbcOperations) {
        this.jdbcOperations = jdbcOperations;
    }

    // =================== Database operations ===================

    @Override
    public List<String> showDatabases() {
        try {
            return jdbcOperations.query(HiveSchemaUtil.SHOW_DATABASES,
                    (rs, rowNum) -> rs.getString("database_name"));
        } catch (Exception e) {
            log.warn("Failed to execute SHOW DATABASES", e);
            return Collections.emptyList();
        }
    }

    @Override
    public DBDatabase getDatabase(String schemaName) {
        String dbName = resolveSchemaName(schemaName);
        DBDatabase database = new DBDatabase();
        database.setId(dbName);
        database.setName(dbName);
        return database;
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

    @Override
    public void switchDatabase(String schemaName) {
        String dbName = resolveSchemaName(schemaName);
        try {
            jdbcOperations.execute("USE " + quoteIdentifier(dbName));
        } catch (Exception e) {
            log.error("Failed to switch database: " + dbName, e);
            throw new RuntimeException("Failed to switch database: " + dbName, e);
        }
    }

    @Override
    public List<DBObjectIdentity> listUsers() {
        // Hive does not have a user management concept at the JDBC level
        return Collections.emptyList();
    }

    // =================== Table operations ===================

    @Override
    public List<String> showTablesLike(String schemaName, String tableNameLike) {
        String dbName = resolveSchemaName(schemaName);
        try {
            String sql;
            if (StringUtils.isNotBlank(tableNameLike)) {
                sql = HiveSchemaUtil.SHOW_TABLES_IN + quoteIdentifier(dbName)
                        + " LIKE '" + escapeLikePattern(tableNameLike) + "'";
            } else {
                sql = HiveSchemaUtil.SHOW_TABLES_IN + quoteIdentifier(dbName);
            }
            return jdbcOperations.query(sql, (rs, rowNum) -> rs.getString("tab_name"));
        } catch (Exception e) {
            log.warn("Failed to show tables in database: " + dbName, e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<DBObjectIdentity> listTables(String schemaName, String tableNameLike) {
        String dbName = resolveSchemaName(schemaName);
        return showTablesLike(schemaName, tableNameLike).stream().map(name -> {
            DBObjectIdentity identity = new DBObjectIdentity();
            identity.setSchemaName(dbName);
            identity.setName(name);
            identity.setType(DBObjectType.TABLE);
            return identity;
        }).collect(Collectors.toList());
    }

    @Override
    public List<String> showExternalTablesLike(String schemaName, String tableNameLike) {
        // Hive supports external tables, but SHOW TABLES does not distinguish them.
        // External table detection is done via DESCRIBE FORMATTED (Table Type: EXTERNAL_TABLE).
        // Return empty here; callers should use getTable() to check Table Type.
        return Collections.emptyList();
    }

    @Override
    public List<DBObjectIdentity> listExternalTables(String schemaName, String tableNameLike) {
        return Collections.emptyList();
    }

    @Override
    public boolean isExternalTable(String schemaName, String tableName) {
        try {
            HiveTableMetadata metadata = describeFormattedTable(schemaName, tableName);
            String tableType = metadata.getTableProperties().get("Table Type");
            return "EXTERNAL_TABLE".equals(tableType);
        } catch (Exception e) {
            log.warn("Failed to check if table is external: " + schemaName + "." + tableName, e);
            return false;
        }
    }

    @Override
    public boolean syncExternalTableFiles(String schemaName, String tableName) {
        throw new UnsupportedOperationException("Hive does not support syncing external table files");
    }

    // =================== View operations ===================

    @Override
    public List<DBObjectIdentity> listViews(String schemaName) {
        String dbName = resolveSchemaName(schemaName);
        try {
            String sql = HiveSchemaUtil.SHOW_VIEWS_IN + quoteIdentifier(dbName);
            return jdbcOperations.query(sql, (rs, rowNum) -> {
                DBObjectIdentity identity = new DBObjectIdentity();
                identity.setSchemaName(dbName);
                identity.setName(rs.getString(1));
                identity.setType(DBObjectType.VIEW);
                return identity;
            });
        } catch (Exception e) {
            log.warn("Failed to show views in database: " + dbName, e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<DBObjectIdentity> listAllViews(String viewNameLike) {
        // Hive does not have a global all-views query; iterate databases
        List<DBObjectIdentity> result = new ArrayList<>();
        for (String db : showDatabases()) {
            result.addAll(listViews(db));
        }
        return result;
    }

    @Override
    public List<DBObjectIdentity> listAllUserViews(String viewNameLike) {
        // Hive does not distinguish user/system views
        return listAllViews(viewNameLike);
    }

    @Override
    public List<DBObjectIdentity> listAllSystemViews(String viewNameLike) {
        // Hive does not have system views
        return Collections.emptyList();
    }

    @Override
    public List<String> showSystemViews(String schemaName) {
        // Hive does not have system views
        return Collections.emptyList();
    }

    @Override
    public DBView getView(String schemaName, String viewName) {
        String dbName = resolveSchemaName(schemaName);
        DBView view = new DBView();
        view.setViewName(viewName);
        view.setSchemaName(dbName);
        try {
            String ddl = getViewDDL(dbName, viewName);
            view.setDdl(ddl);
        } catch (Exception e) {
            log.warn("Failed to get view DDL: " + dbName + "." + viewName, e);
        }
        try {
            view.setColumns(listTableColumns(dbName, viewName));
        } catch (Exception e) {
            log.warn("Failed to get view columns: " + dbName + "." + viewName, e);
        }
        return view;
    }

    /**
     * Get view DDL using SHOW CREATE VIEW.
     */
    public String getViewDDL(String schemaName, String viewName) {
        String dbName = resolveSchemaName(schemaName);
        HiveSqlBuilder sb = new HiveSqlBuilder();
        sb.append(HiveSchemaUtil.SHOW_CREATE_VIEW);
        sb.schemaPrefixIfNotBlank(dbName);
        sb.identifier(viewName);
        try {
            List<String> lines = jdbcOperations.query(sb.toString(),
                    (rs, rowNum) -> rs.getString(1));
            return String.join("\n", lines);
        } catch (Exception e) {
            log.warn("Failed to get view DDL: " + dbName + "." + viewName, e);
            return "";
        }
    }

    // =================== Materialized view operations (unsupported) ===================

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
        throw new UnsupportedOperationException("Hive does not support materialized views");
    }

    @Override
    public DBMaterializedView getMView(String schemaName, String mViewName) {
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

    // =================== Variable and charset operations (unsupported) ===================

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

    // =================== PL object operations (unsupported) ===================

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

    // =================== Column operations ===================

    @Override
    public Map<String, List<DBTableColumn>> listTableColumns(String schemaName,
            List<String> tableNames) {
        Map<String, List<DBTableColumn>> result = new HashMap<>();
        if (tableNames == null || tableNames.isEmpty()) {
            return result;
        }
        for (String tableName : tableNames) {
            try {
                List<DBTableColumn> columns = listTableColumns(schemaName, tableName);
                if (!columns.isEmpty()) {
                    result.put(tableName, columns);
                }
            } catch (Exception e) {
                log.warn("Failed to list columns for table: " + schemaName + "." + tableName, e);
            }
        }
        return result;
    }

    @Override
    public List<DBTableColumn> listTableColumns(String schemaName, String tableName) {
        String dbName = resolveSchemaName(schemaName);
        HiveSqlBuilder sb = new HiveSqlBuilder();
        sb.append(HiveSchemaUtil.DESCRIBE);
        sb.schemaPrefixIfNotBlank(dbName);
        sb.identifier(tableName);
        try {
            List<DescribeRow> rows = jdbcOperations.query(sb.toString(), (rs, rowNum) ->
                    new DescribeRow(
                            rs.getString("col_name"),
                            rs.getString("data_type"),
                            rs.getString("comment")));
            // Parse using the state machine to correctly separate regular and partition columns
            HiveTableMetadata metadata = HiveSchemaUtil.parseDescribeFormatted(rows);
            // For listTableColumns, return all columns (regular + partition)
            List<DBTableColumn> allColumns = new ArrayList<>(metadata.getColumns());
            allColumns.addAll(metadata.getPartitionColumns());
            // Set schema and table name on each column
            for (DBTableColumn col : allColumns) {
                col.setSchemaName(dbName);
                col.setTableName(tableName);
            }
            return allColumns;
        } catch (Exception e) {
            log.warn("Failed to describe table: " + dbName + "." + tableName, e);
            return Collections.emptyList();
        }
    }

    @Override
    public Map<String, List<DBTableColumn>> listBasicTableColumns(String schemaName) {
        Map<String, List<DBTableColumn>> result = new HashMap<>();
        List<String> tables = showTables(schemaName);
        for (String tableName : tables) {
            try {
                List<DBTableColumn> columns = listBasicTableColumns(schemaName, tableName);
                if (!columns.isEmpty()) {
                    result.put(tableName, columns);
                }
            } catch (Exception e) {
                log.warn("Failed to list basic columns for table: " + schemaName + "." + tableName,
                        e);
            }
        }
        return result;
    }

    @Override
    public List<DBTableColumn> listBasicTableColumns(String schemaName, String tableName) {
        // In Hive, DESCRIBE returns basic column info; reuse listTableColumns
        return listTableColumns(schemaName, tableName);
    }

    @Override
    public Map<String, List<DBTableColumn>> listBasicViewColumns(String schemaName) {
        Map<String, List<DBTableColumn>> result = new HashMap<>();
        List<DBObjectIdentity> views = listViews(schemaName);
        for (DBObjectIdentity view : views) {
            try {
                List<DBTableColumn> columns = listBasicViewColumns(schemaName, view.getName());
                if (!columns.isEmpty()) {
                    result.put(view.getName(), columns);
                }
            } catch (Exception e) {
                log.warn("Failed to list basic view columns: " + schemaName + "." + view.getName(),
                        e);
            }
        }
        return result;
    }

    @Override
    public List<DBTableColumn> listBasicViewColumns(String schemaName, String viewName) {
        // Hive views share the same DESCRIBE command as tables
        return listTableColumns(schemaName, viewName);
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
        // Combine table and view columns
        Map<String, List<DBTableColumn>> result = new HashMap<>();
        result.putAll(listBasicTableColumns(schemaName));
        result.putAll(listBasicViewColumns(schemaName));
        return result;
    }

    // =================== Index operations (unsupported) ===================

    @Override
    public Map<String, List<DBTableIndex>> listTableIndexes(String schemaName) {
        return Collections.emptyMap();
    }

    @Override
    public List<DBTableIndex> listTableIndexes(String schemaName, String tableName) {
        return Collections.emptyList();
    }

    // =================== Constraint operations (unsupported) ===================

    @Override
    public Map<String, List<DBTableConstraint>> listTableConstraints(String schemaName) {
        return Collections.emptyMap();
    }

    @Override
    public List<DBTableConstraint> listTableConstraints(String schemaName, String tableName) {
        return Collections.emptyList();
    }

    // =================== Table options and partition operations ===================

    @Override
    public Map<String, DBTableOptions> listTableOptions(String schemaName) {
        return Collections.emptyMap();
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
        // Hive is case-insensitive for identifiers, typically stores in lowercase
        return true;
    }

    @Override
    public List<DBObjectIdentity> listPartitionTables(String partitionMethod) {
        return Collections.emptyList();
    }

    @Override
    public DBTablePartition getPartition(String schemaName, String tableName) {
        // Hive partitions could be listed, but the partition model differs from RDBMS
        return null;
    }

    @Override
    public String getTableDDL(String schemaName, String tableName) {
        String dbName = resolveSchemaName(schemaName);
        HiveSqlBuilder sb = new HiveSqlBuilder();
        sb.append(HiveSchemaUtil.SHOW_CREATE_TABLE);
        sb.schemaPrefixIfNotBlank(dbName);
        sb.identifier(tableName);
        try {
            List<String> lines = jdbcOperations.query(sb.toString(),
                    (rs, rowNum) -> rs.getString(1));
            return String.join("\n", lines);
        } catch (Exception e) {
            log.warn("Failed to get table DDL: " + dbName + "." + tableName, e);
            return "";
        }
    }

    @Override
    public DBTableOptions getTableOptions(String schemaName, String tableName) {
        try {
            HiveTableMetadata metadata = describeFormattedTable(schemaName, tableName);
            DBTableOptions options = new DBTableOptions();
            options.setComment(metadata.getTableProperties().get("Comment"));
            String createTime = metadata.getTableProperties().get("CreateTime");
            if (StringUtils.isNotBlank(createTime)) {
                try {
                    options.setCreateTime(
                            new java.sql.Timestamp(
                                    new java.text.SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy",
                                            java.util.Locale.US)
                                            .parse(createTime)
                                            .getTime()));
                } catch (Exception e) {
                    log.debug("Failed to parse CreateTime: " + createTime, e);
                }
            }
            return options;
        } catch (Exception e) {
            log.warn("Failed to get table options: " + schemaName + "." + tableName, e);
            return new DBTableOptions();
        }
    }

    @Override
    public DBTableOptions getTableOptions(String schemaName, String tableName, String ddl) {
        // Hive does not parse options from DDL; use the standard method
        return getTableOptions(schemaName, tableName);
    }

    @Override
    public List<DBColumnGroupElement> listTableColumnGroups(String schemaName, String tableName) {
        return Collections.emptyList();
    }

    // =================== Full table metadata ===================

    @Override
    public Map<String, DBTable> getTables(String schemaName, List<String> tableNames) {
        Map<String, DBTable> result = new HashMap<>();

        if (tableNames == null || tableNames.isEmpty()) {
            tableNames = showTables(schemaName);
        }
        if (tableNames.isEmpty()) {
            return result;
        }

        for (String tableName : tableNames) {
            try {
                DBTable table = buildDBTable(schemaName, tableName);
                if (table != null) {
                    result.put(tableName, table);
                }
            } catch (Exception e) {
                log.warn("Failed to get table: " + schemaName + "." + tableName, e);
            }
        }
        return result;
    }

    // =================== PL object detail operations (unsupported) ===================

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
    public DBSynonym getSynonym(String schemaName, String synonymName,
            DBSynonymType synonymType) {
        return null;
    }

    // =================== Internal helper methods ===================

    /**
     * Resolves the schema name, defaulting to "default" if blank.
     */
    private String resolveSchemaName(String schemaName) {
        return StringUtils.isNotBlank(schemaName) ? schemaName : DEFAULT_DATABASE;
    }

    /**
     * Quotes a Hive identifier with backticks.
     */
    private String quoteIdentifier(String identifier) {
        HiveSqlBuilder sb = new HiveSqlBuilder();
        sb.identifier(identifier);
        return sb.toString();
    }

    /**
     * Escapes special characters in a LIKE pattern for Hive. Hive LIKE supports {@code %} and
     * {@code _} as wildcards.
     */
    private String escapeLikePattern(String pattern) {
        if (pattern == null) {
            return null;
        }
        // Escape single quotes for SQL injection safety
        return pattern.replace("'", "''");
    }

    /**
     * Executes DESCRIBE FORMATTED and parses the result using the state machine parser.
     */
    private HiveTableMetadata describeFormattedTable(String schemaName, String tableName) {
        String dbName = resolveSchemaName(schemaName);
        HiveSqlBuilder sb = new HiveSqlBuilder();
        sb.append(HiveSchemaUtil.DESCRIBE_FORMATTED);
        sb.schemaPrefixIfNotBlank(dbName);
        sb.identifier(tableName);

        List<DescribeRow> rows = jdbcOperations.query(sb.toString(), (rs, rowNum) ->
                new DescribeRow(
                        rs.getString("col_name"),
                        rs.getString("data_type"),
                        rs.getString("comment")));
        return HiveSchemaUtil.parseDescribeFormatted(rows);
    }

    /**
     * Builds a full DBTable object from DESCRIBE FORMATTED output.
     */
    private DBTable buildDBTable(String schemaName, String tableName) {
        String dbName = resolveSchemaName(schemaName);
        HiveTableMetadata metadata = describeFormattedTable(dbName, tableName);

        DBTable table = new DBTable();
        table.setSchemaName(dbName);
        table.setName(tableName);
        table.setOwner(metadata.getTableProperties().get("Owner"));

        // Set table type
        String tableType = metadata.getTableProperties().get("Table Type");
        if ("EXTERNAL_TABLE".equals(tableType)) {
            table.setType(DBObjectType.TABLE);
        } else {
            table.setType(DBObjectType.TABLE);
        }

        // Set columns (regular + partition)
        List<DBTableColumn> allColumns = new ArrayList<>(metadata.getColumns());
        allColumns.addAll(metadata.getPartitionColumns());
        for (DBTableColumn col : allColumns) {
            col.setSchemaName(dbName);
            col.setTableName(tableName);
        }
        table.setColumns(allColumns);

        // Indexes and constraints are not supported in Hive
        table.setIndexes(Collections.emptyList());
        table.setConstraints(Collections.emptyList());

        // Set table options
        DBTableOptions options = new DBTableOptions();
        options.setComment(metadata.getTableProperties().get("Comment"));
        String createTime = metadata.getTableProperties().get("CreateTime");
        if (StringUtils.isNotBlank(createTime)) {
            try {
                options.setCreateTime(
                        new java.sql.Timestamp(
                                new java.text.SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy",
                                        java.util.Locale.US)
                                        .parse(createTime)
                                        .getTime()));
            } catch (Exception e) {
                log.debug("Failed to parse CreateTime: " + createTime, e);
            }
        }
        table.setTableOptions(options);

        // Set DDL
        try {
            table.setDDL(getTableDDL(dbName, tableName));
        } catch (Exception e) {
            log.warn("Failed to get DDL for table: " + dbName + "." + tableName, e);
        }

        return table;
    }
}
