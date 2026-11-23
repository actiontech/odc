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
package com.oceanbase.tools.dbbrowser.schema.clickhouse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

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
import com.oceanbase.tools.dbbrowser.model.DBTableColumn.KeyType;
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

/**
 * Minimal {@link DBSchemaAccessor} for ClickHouse (S2 §8.11): columns via {@code system.columns},
 * PRIMARY/ORDER BY key columns mapped to {@link DBConstraintType#PRIMARY_KEY} for S4 write-back.
 */
@Slf4j
public class ClickHouseSchemaAccessor implements DBSchemaAccessor {

    private static final String DEFAULT_DATABASE = "default";

    protected final JdbcOperations jdbcOperations;

    public ClickHouseSchemaAccessor(@NonNull JdbcOperations jdbcOperations) {
        this.jdbcOperations = jdbcOperations;
    }

    @Override
    public List<String> showDatabases() {
        try {
            return jdbcOperations.query("SHOW DATABASES", (rs, rowNum) -> rs.getString(1));
        } catch (Exception e) {
            log.warn("Failed to execute SHOW DATABASES on ClickHouse", e);
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
            log.error("Failed to switch ClickHouse database: " + dbName, e);
            throw new RuntimeException("Failed to switch database: " + dbName, e);
        }
    }

    @Override
    public List<DBObjectIdentity> listUsers() {
        return Collections.emptyList();
    }

    @Override
    public List<String> showTablesLike(String schemaName, String tableNameLike) {
        String dbName = resolveSchemaName(schemaName);
        try {
            List<String> names = jdbcOperations.query(
                    "SHOW TABLES FROM " + quoteIdentifier(dbName),
                    (rs, rowNum) -> rs.getString(1));
            if (StringUtils.isBlank(tableNameLike)) {
                return names;
            }
            return names.stream().filter(n -> n != null && n.contains(tableNameLike)).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to show tables in ClickHouse database: " + dbName, e);
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
        throw new UnsupportedOperationException("ClickHouse does not support syncing external table files");
    }

    @Override
    public List<DBObjectIdentity> listViews(String schemaName) {
        return Collections.emptyList();
    }

    @Override
    public List<DBObjectIdentity> listAllViews(String viewNameLike) {
        return Collections.emptyList();
    }

    @Override
    public List<DBObjectIdentity> listAllUserViews(String viewNameLike) {
        return Collections.emptyList();
    }

    @Override
    public List<DBObjectIdentity> listAllSystemViews(String viewNameLike) {
        return Collections.emptyList();
    }

    @Override
    public List<String> showSystemViews(String schemaName) {
        return Collections.emptyList();
    }

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
        throw new UnsupportedOperationException("ClickHouse materialized view refresh is out of scope");
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
                log.warn("Failed to list columns for ClickHouse table: " + schemaName + "." + tableName, e);
            }
        }
        return result;
    }

    @Override
    public List<DBTableColumn> listTableColumns(String schemaName, String tableName) {
        String dbName = resolveSchemaName(schemaName);
        String sql = "SELECT name, type, position, is_in_primary_key, is_in_sorting_key, comment "
                + "FROM system.columns WHERE database = '" + escapeLiteral(dbName)
                + "' AND table = '" + escapeLiteral(tableName) + "' ORDER BY position";
        try {
            return jdbcOperations.query(sql, (rs, rowNum) -> {
                DBTableColumn column = new DBTableColumn();
                column.setSchemaName(dbName);
                column.setTableName(tableName);
                column.setName(rs.getString("name"));
                String typeName = rs.getString("type");
                column.setTypeName(typeName);
                column.setFullTypeName(typeName);
                column.setOrdinalPosition(rs.getInt("position"));
                column.setComment(rs.getString("comment"));
                column.setNullable(isClickHouseNullableType(typeName));
                boolean inPk = toBool(rs.getObject("is_in_primary_key"));
                boolean inSort = toBool(rs.getObject("is_in_sorting_key"));
                if (inPk || inSort) {
                    column.setKeyType(KeyType.PRI);
                }
                return column;
            });
        } catch (Exception e) {
            log.warn("Failed to list ClickHouse columns via system.columns for {}.{}, fallback DESCRIBE",
                    dbName, tableName, e);
            return describeColumnsFallback(dbName, tableName);
        }
    }

    @Override
    public Map<String, List<DBTableColumn>> listBasicTableColumns(String schemaName) {
        Map<String, List<DBTableColumn>> result = new HashMap<>();
        for (String tableName : showTables(schemaName)) {
            try {
                List<DBTableColumn> columns = listBasicTableColumns(schemaName, tableName);
                if (!columns.isEmpty()) {
                    result.put(tableName, columns);
                }
            } catch (Exception e) {
                log.warn("Failed to list basic columns for ClickHouse table: " + schemaName + "." + tableName, e);
            }
        }
        return result;
    }

    @Override
    public List<DBTableColumn> listBasicTableColumns(String schemaName, String tableName) {
        return listTableColumns(schemaName, tableName);
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
        return listBasicTableColumns(schemaName);
    }

    @Override
    public Map<String, List<DBTableIndex>> listTableIndexes(String schemaName) {
        return Collections.emptyMap();
    }

    @Override
    public List<DBTableIndex> listTableIndexes(String schemaName, String tableName) {
        return Collections.emptyList();
    }

    @Override
    public Map<String, List<DBTableConstraint>> listTableConstraints(String schemaName) {
        Map<String, List<DBTableConstraint>> result = new HashMap<>();
        for (String tableName : showTables(schemaName)) {
            List<DBTableConstraint> constraints = listTableConstraints(schemaName, tableName);
            if (!constraints.isEmpty()) {
                result.put(tableName, constraints);
            }
        }
        return result;
    }

    @Override
    public List<DBTableConstraint> listTableConstraints(String schemaName, String tableName) {
        String dbName = resolveSchemaName(schemaName);
        List<DBTableColumn> columns = listTableColumns(dbName, tableName);
        List<String> pkColumns = columns.stream()
                .filter(c -> c.getKeyType() == KeyType.PRI)
                .map(DBTableColumn::getName)
                .collect(Collectors.toList());
        if (pkColumns.isEmpty()) {
            return Collections.emptyList();
        }
        DBTableConstraint constraint = new DBTableConstraint();
        constraint.setSchemaName(dbName);
        constraint.setTableName(tableName);
        constraint.setName("PRIMARY");
        constraint.setType(DBConstraintType.PRIMARY_KEY);
        constraint.setColumnNames(pkColumns);
        constraint.setEnabled(Boolean.TRUE);
        return Collections.singletonList(constraint);
    }

    @Override
    public Map<String, DBTableOptions> listTableOptions(String schemaName) {
        return Collections.emptyMap();
    }

    @Override
    public Map<String, DBTablePartition> listTablePartitions(@NonNull String schemaName, List<String> tableNames) {
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
        return Boolean.TRUE;
    }

    @Override
    public List<DBObjectIdentity> listPartitionTables(String partitionMethod) {
        return Collections.emptyList();
    }

    @Override
    public DBTablePartition getPartition(String schemaName, String tableName) {
        return null;
    }

    @Override
    public String getTableDDL(String schemaName, String tableName) {
        String dbName = resolveSchemaName(schemaName);
        try {
            return jdbcOperations.query(
                    "SHOW CREATE TABLE " + quoteIdentifier(dbName) + "." + quoteIdentifier(tableName),
                    rs -> {
                        if (rs.next()) {
                            int cols = rs.getMetaData().getColumnCount();
                            return rs.getString(cols);
                        }
                        return "";
                    });
        } catch (Exception e) {
            log.warn("Failed to get ClickHouse table DDL: " + dbName + "." + tableName, e);
            return "";
        }
    }

    @Override
    public DBTableOptions getTableOptions(String schemaName, String tableName) {
        return new DBTableOptions();
    }

    @Override
    public DBTableOptions getTableOptions(String schemaName, String tableName, String ddl) {
        return getTableOptions(schemaName, tableName);
    }

    @Override
    public List<DBColumnGroupElement> listTableColumnGroups(String schemaName, String tableName) {
        return Collections.emptyList();
    }

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
        Map<String, DBTable> result = new HashMap<>();
        List<String> names = tableNames;
        if (names == null || names.isEmpty()) {
            names = showTables(schemaName);
        }
        String dbName = resolveSchemaName(schemaName);
        for (String tableName : names) {
            try {
                DBTable table = new DBTable();
                table.setSchemaName(dbName);
                table.setName(tableName);
                table.setOwner(dbName);
                table.setType(DBObjectType.TABLE);
                table.setColumns(listTableColumns(dbName, tableName));
                table.setConstraints(listTableConstraints(dbName, tableName));
                table.setIndexes(Collections.emptyList());
                table.setTableOptions(getTableOptions(dbName, tableName));
                table.setDDL(getTableDDL(dbName, tableName));
                result.put(tableName, table);
            } catch (Exception e) {
                log.warn("Failed to get ClickHouse table: " + dbName + "." + tableName, e);
            }
        }
        return result;
    }

    private List<DBTableColumn> describeColumnsFallback(String dbName, String tableName) {
        try {
            String sql = "DESCRIBE TABLE " + quoteIdentifier(dbName) + "." + quoteIdentifier(tableName);
            List<DBTableColumn> columns = new ArrayList<>();
            jdbcOperations.query(sql, rs -> {
                DBTableColumn column = new DBTableColumn();
                column.setSchemaName(dbName);
                column.setTableName(tableName);
                column.setName(rs.getString(1));
                String typeName = rs.getString(2);
                column.setTypeName(typeName);
                column.setFullTypeName(typeName);
                column.setNullable(isClickHouseNullableType(typeName));
                columns.add(column);
            });
            for (int i = 0; i < columns.size(); i++) {
                columns.get(i).setOrdinalPosition(i + 1);
            }
            return columns;
        } catch (Exception e) {
            log.warn("Failed to DESCRIBE ClickHouse table: " + dbName + "." + tableName, e);
            return Collections.emptyList();
        }
    }

    private String resolveSchemaName(String schemaName) {
        return StringUtils.isNotBlank(schemaName) ? schemaName : DEFAULT_DATABASE;
    }

    private String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    /**
     * ClickHouse marks nullability in the type name (e.g. {@code Nullable(String)},
     * {@code LowCardinality(Nullable(Int32))}). Plain types like {@code String}/{@code UInt64} are
     * non-nullable.
     */
    private boolean isClickHouseNullableType(String typeName) {
        if (typeName == null) {
            return false;
        }
        return typeName.toLowerCase(Locale.ROOT).contains("nullable(");
    }

    private String escapeLiteral(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private boolean toBool(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        String s = String.valueOf(value);
        return "1".equals(s) || "true".equalsIgnoreCase(s);
    }
}
