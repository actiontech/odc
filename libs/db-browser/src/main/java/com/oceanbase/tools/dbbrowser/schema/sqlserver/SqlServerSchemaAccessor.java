/*
 * Copyright (c) 2025 OceanBase.
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
package com.oceanbase.tools.dbbrowser.schema.sqlserver;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcOperations;

import com.oceanbase.tools.dbbrowser.model.DBColumnGroupElement;
import com.oceanbase.tools.dbbrowser.model.DBConstraintType;
import com.oceanbase.tools.dbbrowser.model.DBDatabase;
import com.oceanbase.tools.dbbrowser.model.DBFunction;
import com.oceanbase.tools.dbbrowser.model.DBIndexType;
import com.oceanbase.tools.dbbrowser.model.DBMViewRefreshParameter;
import com.oceanbase.tools.dbbrowser.model.DBMViewRefreshRecord;
import com.oceanbase.tools.dbbrowser.model.DBMViewRefreshRecordParam;
import com.oceanbase.tools.dbbrowser.model.DBMaterializedView;
import com.oceanbase.tools.dbbrowser.model.DBObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;
import com.oceanbase.tools.dbbrowser.model.DBPLObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBPLParam;
import com.oceanbase.tools.dbbrowser.model.DBPLParamMode;
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
import com.oceanbase.tools.dbbrowser.model.DBTablePartitionDefinition;
import com.oceanbase.tools.dbbrowser.model.DBTablePartitionOption;
import com.oceanbase.tools.dbbrowser.model.DBTableSubpartitionDefinition;
import com.oceanbase.tools.dbbrowser.model.DBTrigger;
import com.oceanbase.tools.dbbrowser.model.DBType;
import com.oceanbase.tools.dbbrowser.model.DBVariable;
import com.oceanbase.tools.dbbrowser.model.DBView;
import com.oceanbase.tools.dbbrowser.model.DBViewCheckOption;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;
import com.oceanbase.tools.dbbrowser.util.StringUtils;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SqlServerSchemaAccessor implements DBSchemaAccessor {

    protected JdbcOperations jdbcOperations;

    public SqlServerSchemaAccessor(@NonNull JdbcOperations jdbcOperations) {
        this.jdbcOperations = jdbcOperations;
    }

    @Override
    public List<String> showDatabases() {
        String sql = "SELECT name FROM sys.databases;";
        try {
            return jdbcOperations.queryForList(sql, String.class);
        } catch (BadSqlGrammarException e) {
            log.warn("Failed to query databases", e);
            return Collections.emptyList();
        }
    }

    @Override
    public DBDatabase getDatabase(String schemaName) {
        // 注意：在 SQL Server 中，schemaName 参数实际表示数据库名（database name）
        // SQL Server 的层次结构：databases -> schemas -> tables
        DBDatabase database = new DBDatabase();
        database.setId(schemaName);
        database.setName(schemaName);
        // SQL Server 中获取指定数据库的字符集和排序规则
        String sql = "SELECT "
                + "    collation_name AS collation "
                + "FROM sys.databases "
                + "WHERE name = ?";
        AtomicReference<String> collation = new AtomicReference<>();
        try {
            jdbcOperations.query(sql, new Object[] {schemaName}, rs -> {
                if (rs.next()) {
                    collation.set(rs.getString(1));
                }
            });
            database.setCollation(collation.get());
        } catch (Exception e) {
            log.warn("Failed to get database collation for database: " + schemaName, e);
        }
        return database;
    }

    @Override
    public List<DBDatabase> listDatabases() {
        // SQL Server 中返回所有数据库及其信息
        String sql = "SELECT "
                + "    name, "
                + "    collation_name AS collation "
                + "FROM sys.databases "
                + "WHERE state_desc = 'ONLINE' "
                + "ORDER BY name";
        try {
            return jdbcOperations.query(sql, (rs, rowNum) -> {
                DBDatabase database = new DBDatabase();
                database.setId(rs.getString("name"));
                database.setName(rs.getString("name"));
                database.setCollation(rs.getString("collation"));
                return database;
            });
        } catch (Exception e) {
            log.warn("Failed to list databases", e);
            return Collections.emptyList();
        }
    }

    @Override
    public void switchDatabase(String schemaName) {
        // SQL Server 使用 USE 语句切换数据库
        // 注意：参数名是 schemaName，但在 SQL Server 中实际是数据库名
        String sql = "USE [" + schemaName.replace("]", "]]") + "]";
        try {
            jdbcOperations.execute(sql);
        } catch (Exception e) {
            log.error("Failed to switch database: " + schemaName, e);
            throw new RuntimeException("Failed to switch database: " + schemaName, e);
        }
    }

    @Override
    public List<DBObjectIdentity> listUsers() {
        return Collections.emptyList();
    }

    /**
     * 解析 schemaName 参数，支持两种格式： 1. "database" - 只有数据库名，默认使用 dbo schema 2. "database.schema" - 数据库名和
     * schema 名
     *
     * @param schemaName 可能是数据库名或 database.schema 格式
     * @return [databaseName, schemaName]
     */
    private String[] parseDatabaseAndSchema(String schemaName) {
        if (schemaName == null) {
            return new String[] {"", "dbo"};
        }

        if (schemaName.contains(".")) {
            String[] parts = schemaName.split("\\.", 2);
            return new String[] {parts[0], parts[1]};
        } else {
            // 兼容旧代码：只有数据库名，默认使用 dbo
            return new String[] {schemaName, "dbo"};
        }
    }

    @Override
    public List<String> showTables(String schemaName) {
        return showTablesLike(schemaName, null);
    }

    @Override
    public List<String> showTablesLike(String schemaName, String tableNameLike) {
        // SQL Server 的层次结构：databases -> schemas -> tables
        // schemaName 参数支持两种格式：
        // 1. "database" - 只有数据库名，默认使用 dbo schema
        // 2. "database.schema" - 数据库名和 schema 名
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = dbAndSchema[0];
        String actualSchemaName = dbAndSchema[1];

        // 确保在正确的数据库中查询
        String currentDb = null;
        try {
            currentDb = jdbcOperations.queryForObject("SELECT DB_NAME()", String.class);
            if (!databaseName.equals(currentDb)) {
                switchDatabase(databaseName);
            }
        } catch (Exception e) {
            log.warn("Failed to switch to database: " + databaseName, e);
            return Collections.emptyList();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("SELECT table_name FROM information_schema.tables ");
        sb.append("WHERE table_catalog = ? ");
        sb.append("AND table_schema = ? ");
        sb.append("AND table_type = 'BASE TABLE'");

        List<Object> params = new java.util.ArrayList<>();
        params.add(databaseName);
        params.add(actualSchemaName);

        if (StringUtils.isNotBlank(tableNameLike)) {
            sb.append(" AND table_name LIKE ?");
            params.add(tableNameLike);
        }

        List<String> tableNames;
        try {
            tableNames = jdbcOperations.query(sb.toString(), params.toArray(),
                    (rs, rowNum) -> rs.getString(1));
        } catch (BadSqlGrammarException e) {
            if (StringUtils.containsIgnoreCase(e.getMessage(), "Invalid object name") ||
                    StringUtils.containsIgnoreCase(e.getMessage(), "Invalid schema")) {
                return Collections.emptyList();
            }
            throw e;
        } finally {
            // 恢复原数据库上下文
            if (currentDb != null && !currentDb.equals(databaseName)) {
                try {
                    switchDatabase(currentDb);
                } catch (Exception e) {
                    log.warn("Failed to restore database context", e);
                }
            }
        }
        return tableNames;
    }

    @Override
    public List<DBObjectIdentity> listTables(String schemaName, String tableNameLike) {
        List<String> tableNames = showTablesLike(schemaName, tableNameLike);
        return tableNames.stream().map(tableName -> {
            DBObjectIdentity identity = new DBObjectIdentity();
            identity.setSchemaName(schemaName);
            identity.setName(tableName);
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
        return false;
    }

    @Override
    public List<DBObjectIdentity> listViews(String schemaName) {
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = dbAndSchema[0];
        String actualSchemaName = dbAndSchema[1];

        // 确保在正确的数据库中查询
        String currentDb = null;
        try {
            currentDb = jdbcOperations.queryForObject("SELECT DB_NAME()", String.class);
            if (!databaseName.equals(currentDb)) {
                switchDatabase(databaseName);
            }
        } catch (Exception e) {
            log.warn("Failed to switch to database: " + databaseName, e);
            return Collections.emptyList();
        }

        String sql = "SELECT table_name FROM information_schema.views "
                + "WHERE table_catalog = ? AND table_schema = ?";
        try {
            return jdbcOperations.query(sql, new Object[] {databaseName, actualSchemaName}, (rs, rowNum) -> {
                DBObjectIdentity identity = new DBObjectIdentity();
                identity.setSchemaName(schemaName);
                identity.setName(rs.getString("table_name"));
                return identity;
            });
        } catch (BadSqlGrammarException e) {
            if (StringUtils.containsIgnoreCase(e.getMessage(), "Invalid object name") ||
                    StringUtils.containsIgnoreCase(e.getMessage(), "Invalid schema")) {
                return Collections.emptyList();
            }
            throw e;
        } finally {
            // 恢复原数据库上下文
            if (currentDb != null && !currentDb.equals(databaseName)) {
                try {
                    switchDatabase(currentDb);
                } catch (Exception e) {
                    log.warn("Failed to restore database context", e);
                }
            }
        }
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
        return null;
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
        // SQL Server 中字符集信息可以从 collation 中提取
        String sql = "SELECT DISTINCT "
                + "    SUBSTRING(collation_name, 1, CHARINDEX('_', collation_name) - 1) AS charset "
                + "FROM sys.fn_helpcollations() "
                + "WHERE SUBSTRING(collation_name, 1, CHARINDEX('_', collation_name) - 1) IS NOT NULL";
        try {
            return jdbcOperations.queryForList(sql, String.class);
        } catch (Exception e) {
            log.warn("Failed to query charset", e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<String> showCollation() {
        String sql = "SELECT name FROM sys.fn_helpcollations() ORDER BY name";
        try {
            return jdbcOperations.queryForList(sql, String.class);
        } catch (Exception e) {
            log.warn("Failed to query collation", e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<DBPLObjectIdentity> listFunctions(String schemaName) {
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = dbAndSchema[0];
        String actualSchemaName = dbAndSchema[1];

        // 确保在正确的数据库中查询
        String currentDb = null;
        try {
            currentDb = jdbcOperations.queryForObject("SELECT DB_NAME()", String.class);
            if (!databaseName.equals(currentDb)) {
                switchDatabase(databaseName);
            }
        } catch (Exception e) {
            log.warn("Failed to switch to database: " + databaseName, e);
            return Collections.emptyList();
        }

        String sql = "SELECT ROUTINE_NAME as name, ROUTINE_SCHEMA as schema_name, ROUTINE_TYPE as type "
                + "FROM information_schema.routines "
                + "WHERE ROUTINE_CATALOG = ? AND ROUTINE_SCHEMA = ? "
                + "AND ROUTINE_TYPE = 'FUNCTION' "
                + "ORDER BY ROUTINE_NAME ASC";
        try {
            return jdbcOperations.query(sql, new Object[] {databaseName, actualSchemaName}, (rs, rowNum) -> {
                DBPLObjectIdentity identity = new DBPLObjectIdentity();
                identity.setSchemaName(schemaName);
                identity.setName(rs.getString("name"));
                identity.setType(DBObjectType.valueOf(rs.getString("type")));
                return identity;
            });
        } catch (BadSqlGrammarException e) {
            if (StringUtils.containsIgnoreCase(e.getMessage(), "Invalid object name") ||
                    StringUtils.containsIgnoreCase(e.getMessage(), "Invalid schema")) {
                return Collections.emptyList();
            }
            throw e;
        } finally {
            // 恢复原数据库上下文
            if (currentDb != null && !currentDb.equals(databaseName)) {
                try {
                    switchDatabase(currentDb);
                } catch (Exception e) {
                    log.warn("Failed to restore database context", e);
                }
            }
        }
    }

    @Override
    public List<DBPLObjectIdentity> listProcedures(String schemaName) {
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = dbAndSchema[0];
        String actualSchemaName = dbAndSchema[1];

        // 确保在正确的数据库中查询
        String currentDb = null;
        try {
            currentDb = jdbcOperations.queryForObject("SELECT DB_NAME()", String.class);
            if (!databaseName.equals(currentDb)) {
                switchDatabase(databaseName);
            }
        } catch (Exception e) {
            log.warn("Failed to switch to database: " + databaseName, e);
            return Collections.emptyList();
        }

        String sql = "SELECT ROUTINE_NAME as name, ROUTINE_SCHEMA as schema_name, ROUTINE_TYPE as type "
                + "FROM information_schema.routines "
                + "WHERE ROUTINE_CATALOG = ? AND ROUTINE_SCHEMA = ? AND ROUTINE_TYPE = 'PROCEDURE' "
                + "ORDER BY ROUTINE_NAME ASC";
        try {
            return jdbcOperations.query(sql, new Object[] {databaseName, actualSchemaName}, (rs, rowNum) -> {
                DBPLObjectIdentity identity = new DBPLObjectIdentity();
                identity.setSchemaName(schemaName);
                identity.setName(rs.getString("name"));
                identity.setType(DBObjectType.valueOf(rs.getString("type")));
                return identity;
            });
        } catch (BadSqlGrammarException e) {
            if (StringUtils.containsIgnoreCase(e.getMessage(), "Invalid object name") ||
                    StringUtils.containsIgnoreCase(e.getMessage(), "Invalid schema")) {
                return Collections.emptyList();
            }
            throw e;
        } finally {
            // 恢复原数据库上下文
            if (currentDb != null && !currentDb.equals(databaseName)) {
                try {
                    switchDatabase(currentDb);
                } catch (Exception e) {
                    log.warn("Failed to restore database context", e);
                }
            }
        }
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
        return Collections.emptyMap();
    }

    @Override
    public List<DBTableColumn> listTableColumns(String schemaName, String tableName) {
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = dbAndSchema[0];
        String actualSchemaName = dbAndSchema[1];

        // 确保在正确的数据库中查询
        String currentDb = null;
        try {
            currentDb = jdbcOperations.queryForObject("SELECT DB_NAME()", String.class);
            if (!databaseName.equals(currentDb)) {
                switchDatabase(databaseName);
            }
        } catch (Exception e) {
            log.warn("Failed to switch to database: " + databaseName, e);
            return Collections.emptyList();
        }

        String sql = "SELECT "
                + "    c.column_id AS ordinal_position, "
                + "    c.name AS column_name, "
                + "    t.name AS data_type, "
                + "    CASE "
                + "        WHEN t.name IN ('varchar', 'nvarchar', 'char', 'nchar', 'binary', 'varbinary') "
                + "        THEN t.name + '(' + CASE WHEN c.max_length = -1 THEN 'MAX' ELSE CAST(c.max_length AS VARCHAR) END + ')' "
                + "        WHEN t.name IN ('decimal', 'numeric') "
                + "        THEN t.name + '(' + CAST(c.precision AS VARCHAR) + ',' + CAST(c.scale AS VARCHAR) + ')' "
                + "        WHEN t.name IN ('float', 'real') "
                + "        THEN t.name + '(' + CAST(c.precision AS VARCHAR) + ')' "
                + "        WHEN t.name IN ('datetime2', 'time', 'datetimeoffset') "
                + "        THEN t.name + '(' + CAST(c.scale AS VARCHAR) + ')' "
                + "        ELSE t.name "
                + "    END AS full_type_name, "
                + "    c.precision, "
                + "    c.scale, "
                + "    c.max_length AS character_maximum_length, "
                + "    c.is_nullable, "
                + "    ISNULL(dc.definition, '') AS column_default, "
                + "    ISNULL(ep.value, '') AS column_comment "
                + "FROM sys.columns c "
                + "INNER JOIN sys.types t ON c.user_type_id = t.user_type_id "
                + "INNER JOIN sys.tables tb ON c.object_id = tb.object_id "
                + "INNER JOIN sys.schemas s ON tb.schema_id = s.schema_id "
                + "LEFT JOIN sys.default_constraints dc ON c.default_object_id = dc.object_id "
                + "LEFT JOIN sys.extended_properties ep ON ep.major_id = c.object_id "
                + "    AND ep.minor_id = c.column_id "
                + "    AND ep.name = 'MS_Description' "
                + "WHERE DB_NAME() = ? "
                + "    AND tb.name = ? "
                + "    AND s.name = ? " // 使用参数而不是硬编码 'dbo'
                + "ORDER BY c.column_id";

        try {
            return jdbcOperations.query(sql, new Object[] {databaseName, tableName, actualSchemaName}, (rs, rowNum) -> {
                DBTableColumn column = new DBTableColumn();
                column.setSchemaName(schemaName);
                column.setTableName(tableName);
                column.setOrdinalPosition(rs.getInt("ordinal_position"));
                column.setName(rs.getString("column_name"));
                column.setTypeName(rs.getString("data_type"));
                column.setFullTypeName(rs.getString("full_type_name"));

                Object precisionObj = rs.getObject("precision");
                if (precisionObj != null) {
                    column.setPrecision(rs.getLong("precision"));
                }

                Object scaleObj = rs.getObject("scale");
                if (scaleObj != null) {
                    column.setScale(rs.getInt("scale"));
                }

                Object maxLengthObj = rs.getObject("character_maximum_length");
                if (maxLengthObj != null) {
                    int maxLength = rs.getInt("character_maximum_length");
                    if (maxLength > 0 && maxLength != -1) {
                        column.setMaxLength((long) maxLength);
                    }
                }

                column.setNullable(rs.getBoolean("is_nullable"));

                String defaultValue = rs.getString("column_default");
                if (StringUtils.isNotBlank(defaultValue)) {
                    // SQL Server 默认值可能包含括号，需要清理
                    defaultValue = defaultValue.trim();
                    if (defaultValue.startsWith("(") && defaultValue.endsWith(")")) {
                        defaultValue = defaultValue.substring(1, defaultValue.length() - 1);
                    }
                    column.fillDefaultValue(defaultValue);
                }

                String comment = rs.getString("column_comment");
                if (StringUtils.isNotBlank(comment)) {
                    column.setComment(comment);
                }

                return column;
            });
        } catch (Exception e) {
            log.warn("Failed to list table columns for table: " + schemaName + "." + tableName, e);
            return Collections.emptyList();
        } finally {
            // 恢复原数据库上下文
            if (currentDb != null && !currentDb.equals(databaseName)) {
                try {
                    switchDatabase(currentDb);
                } catch (Exception e) {
                    log.warn("Failed to restore database context", e);
                }
            }
        }
    }

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
        // SQL Server 默认情况下表名是大小写不敏感的（取决于 collation）
        // 但可以通过查询系统视图确认
        String sql = "SELECT CASE "
                + "    WHEN collation_name LIKE '%_CI%' THEN 1 "
                + "    ELSE 0 "
                + "END AS is_case_insensitive "
                + "FROM sys.databases "
                + "WHERE name = DB_NAME()";
        try {
            return jdbcOperations.query(sql, rs -> {
                if (rs.next()) {
                    return rs.getInt(1) == 1;
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("Failed to check case sensitivity", e);
            return null;
        }
    }

    @Override
    public List<DBObjectIdentity> listPartitionTables(String partitionMethod) {
        return Collections.emptyList();
    }

    @Override
    public List<DBTableConstraint> listTableConstraints(String schemaName, String tableName) {
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = dbAndSchema[0];
        String actualSchemaName = dbAndSchema[1];

        // 确保在正确的数据库中查询
        String currentDb = null;
        try {
            currentDb = jdbcOperations.queryForObject("SELECT DB_NAME()", String.class);
            if (!databaseName.equals(currentDb)) {
                switchDatabase(databaseName);
            }
        } catch (Exception e) {
            log.warn("Failed to switch to database: " + databaseName, e);
            return Collections.emptyList();
        }

        // 查询主键和唯一约束
        String pkAndUniqueSql = "SELECT "
                + "    kc.name AS constraint_name, "
                + "    kc.type_desc AS constraint_type, "
                + "    c.name AS column_name, "
                + "    kc.is_system_named "
                + "FROM sys.key_constraints kc "
                + "INNER JOIN sys.tables t ON kc.parent_object_id = t.object_id "
                + "INNER JOIN sys.schemas s ON t.schema_id = s.schema_id "
                + "INNER JOIN sys.index_columns ic ON kc.parent_object_id = ic.object_id AND kc.unique_index_id = ic.index_id "
                + "INNER JOIN sys.columns c ON ic.object_id = c.object_id AND ic.column_id = c.column_id "
                + "WHERE DB_NAME() = ? "
                + "    AND t.name = ? "
                + "    AND s.name = ? " // 使用参数
                + "ORDER BY kc.name, ic.key_ordinal";

        // 查询外键约束
        String fkSql = "SELECT "
                + "    fk.name AS constraint_name, "
                + "    c.name AS column_name, "
                + "    OBJECT_SCHEMA_NAME(fk.referenced_object_id) AS referenced_schema_name, "
                + "    OBJECT_NAME(fk.referenced_object_id) AS referenced_table_name, "
                + "    rc.name AS referenced_column_name "
                + "FROM sys.foreign_keys fk "
                + "INNER JOIN sys.tables t ON fk.parent_object_id = t.object_id "
                + "INNER JOIN sys.schemas s ON t.schema_id = s.schema_id "
                + "INNER JOIN sys.foreign_key_columns fkc ON fk.object_id = fkc.constraint_object_id "
                + "INNER JOIN sys.columns c ON fkc.parent_object_id = c.object_id AND fkc.parent_column_id = c.column_id "
                + "INNER JOIN sys.columns rc ON fkc.referenced_object_id = rc.object_id AND fkc.referenced_column_id = rc.column_id "
                + "WHERE DB_NAME() = ? "
                + "    AND t.name = ? "
                + "    AND s.name = ? " // 使用参数
                + "ORDER BY fk.name, fkc.constraint_column_id";

        // 查询检查约束
        String checkSql = "SELECT "
                + "    cc.name AS constraint_name, "
                + "    cc.definition AS check_definition "
                + "FROM sys.check_constraints cc "
                + "INNER JOIN sys.tables t ON cc.parent_object_id = t.object_id "
                + "INNER JOIN sys.schemas s ON t.schema_id = s.schema_id "
                + "WHERE DB_NAME() = ? "
                + "    AND t.name = ? "
                + "    AND s.name = ?"; // 使用参数

        try {
            Map<String, DBTableConstraint> constraintMap = new java.util.LinkedHashMap<>();

            // 处理主键和唯一约束
            jdbcOperations.query(pkAndUniqueSql, new Object[] {databaseName, tableName, actualSchemaName},
                    (rs, rowNum) -> {
                        String constraintName = rs.getString("constraint_name");
                        String constraintType = rs.getString("constraint_type");
                        String columnName = rs.getString("column_name");

                        DBTableConstraint constraint = constraintMap.get(constraintName);
                        if (constraint == null) {
                            constraint = new DBTableConstraint();
                            constraint.setName(constraintName);
                            constraint.setSchemaName(schemaName);
                            constraint.setTableName(tableName);
                            constraint.setOwner(schemaName);

                            if ("PRIMARY_KEY_CONSTRAINT".equalsIgnoreCase(constraintType)) {
                                constraint.setType(DBConstraintType.PRIMARY_KEY);
                            } else if ("UNIQUE_CONSTRAINT".equalsIgnoreCase(constraintType)) {
                                constraint.setType(DBConstraintType.UNIQUE);
                            }

                            constraint.setColumnNames(new java.util.ArrayList<>());
                            constraintMap.put(constraintName, constraint);
                        }

                        constraint.getColumnNames().add(columnName);
                        return null;
                    });

            // 处理外键约束
            jdbcOperations.query(fkSql, new Object[] {databaseName, tableName, actualSchemaName}, (rs, rowNum) -> {
                String constraintName = rs.getString("constraint_name");
                String columnName = rs.getString("column_name");
                String referencedSchemaName = rs.getString("referenced_schema_name");
                String referencedTableName = rs.getString("referenced_table_name");
                String referencedColumnName = rs.getString("referenced_column_name");

                DBTableConstraint constraint = constraintMap.get(constraintName);
                if (constraint == null) {
                    constraint = new DBTableConstraint();
                    constraint.setName(constraintName);
                    constraint.setSchemaName(schemaName);
                    constraint.setTableName(tableName);
                    constraint.setOwner(schemaName);
                    constraint.setType(DBConstraintType.FOREIGN_KEY);
                    constraint.setReferenceSchemaName(referencedSchemaName);
                    constraint.setReferenceTableName(referencedTableName);
                    constraint.setColumnNames(new java.util.ArrayList<>());
                    constraint.setReferenceColumnNames(new java.util.ArrayList<>());
                    constraintMap.put(constraintName, constraint);
                }

                constraint.getColumnNames().add(columnName);
                constraint.getReferenceColumnNames().add(referencedColumnName);
                return null;
            });

            // 处理检查约束
            jdbcOperations.query(checkSql, new Object[] {databaseName, tableName, actualSchemaName}, (rs, rowNum) -> {
                String constraintName = rs.getString("constraint_name");
                String checkDefinition = rs.getString("check_definition");

                DBTableConstraint constraint = new DBTableConstraint();
                constraint.setName(constraintName);
                constraint.setSchemaName(schemaName);
                constraint.setTableName(tableName);
                constraint.setOwner(schemaName);
                constraint.setType(DBConstraintType.CHECK);
                // constraint.setCheckExpression(checkDefinition);
                constraint.setColumnNames(new java.util.ArrayList<>());
                constraintMap.put(constraintName, constraint);
                return null;
            });

            return new java.util.ArrayList<>(constraintMap.values());
        } catch (Exception e) {
            log.warn("Failed to list table constraints for table: " + schemaName + "." + tableName, e);
            return Collections.emptyList();
        } finally {
            // 恢复原数据库上下文
            if (currentDb != null && !currentDb.equals(databaseName)) {
                try {
                    switchDatabase(currentDb);
                } catch (Exception e) {
                    log.warn("Failed to restore database context", e);
                }
            }
        }
    }

    @Override
    public DBTablePartition getPartition(String schemaName, String tableName) {
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = dbAndSchema[0];
        String actualSchemaName = dbAndSchema[1];

        // 确保在正确的数据库中查询
        String currentDb = null;
        try {
            currentDb = jdbcOperations.queryForObject("SELECT DB_NAME()", String.class);
            if (!databaseName.equals(currentDb)) {
                switchDatabase(databaseName);
            }
        } catch (Exception e) {
            log.warn("Failed to switch to database: " + databaseName, e);
            return null;
        }

        // SQL Server 支持表分区，查询分区信息
        String sql = "SELECT "
                + "    ps.name AS partition_scheme_name, "
                + "    pf.name AS partition_function_name, "
                + "    pf.type_desc AS partition_function_type, "
                + "    p.partition_number, "
                + "    p.rows AS partition_rows "
                + "FROM sys.tables t "
                + "INNER JOIN sys.schemas s ON t.schema_id = s.schema_id "
                + "INNER JOIN sys.indexes i ON t.object_id = i.object_id AND i.type IN (0, 1) "
                + "LEFT JOIN sys.partition_schemes ps ON i.data_space_id = ps.data_space_id "
                + "LEFT JOIN sys.partition_functions pf ON ps.function_id = pf.function_id "
                + "LEFT JOIN sys.partitions p ON t.object_id = p.object_id AND i.index_id = p.index_id "
                + "WHERE DB_NAME() = ? "
                + "    AND t.name = ? "
                + "    AND s.name = ? " // 使用参数
                + "ORDER BY p.partition_number";

        try {
            DBTablePartition partition = new DBTablePartition();
            AtomicReference<String> partitionSchemeName = new AtomicReference<>();
            AtomicReference<String> partitionFunctionName = new AtomicReference<>();
            AtomicReference<String> partitionFunctionType = new AtomicReference<>();
            java.util.List<DBTablePartitionDefinition> definitions = new java.util.ArrayList<>();

            jdbcOperations.query(sql, new Object[] {databaseName, tableName, actualSchemaName}, rs -> {
                if (partitionSchemeName.get() == null) {
                    partitionSchemeName.set(rs.getString("partition_scheme_name"));
                    partitionFunctionName.set(rs.getString("partition_function_name"));
                    partitionFunctionType.set(rs.getString("partition_function_type"));

                    if (partitionSchemeName.get() != null) {
                        DBTablePartitionOption option = new DBTablePartitionOption();
                        // option.setMethod(partitionFunctionType.get());
                        option.setExpression(partitionFunctionName.get());
                        partition.setPartitionOption(option);
                    }
                }

                if (partitionSchemeName.get() != null) {
                    DBTablePartitionDefinition definition = new DBTablePartitionDefinition();
                    definition.setName("Partition_" + rs.getInt("partition_number"));
                    definition.setOrdinalPosition(rs.getInt("partition_number"));
                    // definition.setRowCount(rs.getLong("partition_rows"));
                    definitions.add(definition);
                }
            });

            if (!definitions.isEmpty()) {
                partition.setPartitionDefinitions(definitions);
            } else {
                return null; // 表未分区
            }

            return partition;
        } catch (Exception e) {
            log.warn("Failed to get partition for table: " + schemaName + "." + tableName, e);
            return null;
        } finally {
            // 恢复原数据库上下文
            if (currentDb != null && !currentDb.equals(databaseName)) {
                try {
                    switchDatabase(currentDb);
                } catch (Exception e) {
                    log.warn("Failed to restore database context", e);
                }
            }
        }
    }

    @Override
    public List<DBTableIndex> listTableIndexes(String schemaName, String tableName) {
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = dbAndSchema[0];
        String actualSchemaName = dbAndSchema[1];

        // 确保在正确的数据库中查询
        String currentDb = null;
        try {
            currentDb = jdbcOperations.queryForObject("SELECT DB_NAME()", String.class);
            if (!databaseName.equals(currentDb)) {
                switchDatabase(databaseName);
            }
        } catch (Exception e) {
            log.warn("Failed to switch to database: " + databaseName, e);
            return Collections.emptyList();
        }

        String sql = "SELECT "
                + "    i.name AS index_name, "
                + "    i.type_desc AS index_type, "
                + "    i.is_unique, "
                + "    i.is_primary_key, "
                + "    ic.key_ordinal AS ordinal_position, "
                + "    c.name AS column_name, "
                + "    ic.is_descending_key, "
                + "    i.is_disabled, "
                + "    i.filter_definition "
                + "FROM sys.indexes i "
                + "INNER JOIN sys.tables t ON i.object_id = t.object_id "
                + "INNER JOIN sys.schemas s ON t.schema_id = s.schema_id "
                + "INNER JOIN sys.index_columns ic ON i.object_id = ic.object_id AND i.index_id = ic.index_id "
                + "INNER JOIN sys.columns c ON ic.object_id = c.object_id AND ic.column_id = c.column_id "
                + "WHERE DB_NAME() = ? "
                + "    AND t.name = ? "
                + "    AND s.name = ? " // 使用参数而不是硬编码 'dbo'
                + "    AND i.type > 0 " // 排除堆（heap）
                + "ORDER BY i.name, ic.key_ordinal";

        try {
            Map<String, DBTableIndex> indexMap = new java.util.LinkedHashMap<>();
            jdbcOperations.query(sql, new Object[] {databaseName, tableName, actualSchemaName}, (rs, rowNum) -> {
                String indexName = rs.getString("index_name");
                DBTableIndex index = indexMap.get(indexName);

                if (index == null) {
                    index = new DBTableIndex();
                    index.setSchemaName(schemaName);
                    index.setTableName(tableName);
                    index.setName(indexName);
                    index.setOrdinalPosition(rs.getInt("ordinal_position"));
                    index.setPrimary(rs.getBoolean("is_primary_key"));
                    index.setNonUnique(!rs.getBoolean("is_unique"));
                    // index.setDisabled(rs.getBoolean("is_disabled"));

                    String indexType = rs.getString("index_type");
                    if ("CLUSTERED".equalsIgnoreCase(indexType)) {
                        index.setType(DBIndexType.CLUSTERED);
                    } else if ("NONCLUSTERED".equalsIgnoreCase(indexType)) {
                        if (index.isNonUnique()) {
                            index.setType(DBIndexType.NORMAL);
                        } else {
                            index.setType(DBIndexType.UNIQUE);
                        }
                    } else {
                        index.setType(DBIndexType.NORMAL);
                    }

                    String filterDefinition = rs.getString("filter_definition");
                    if (StringUtils.isNotBlank(filterDefinition)) {
                        index.setAdditionalInfo("Filter: " + filterDefinition);
                    }

                    index.setColumnNames(new java.util.ArrayList<>());
                    indexMap.put(indexName, index);
                }

                String columnName = rs.getString("column_name");
                boolean isDescending = rs.getBoolean("is_descending_key");
                if (isDescending) {
                    columnName = columnName + " DESC";
                }
                index.getColumnNames().add(columnName);

                return null;
            });

            return new java.util.ArrayList<>(indexMap.values());
        } catch (Exception e) {
            log.warn("Failed to list table indexes for table: " + schemaName + "." + tableName, e);
            return Collections.emptyList();
        } finally {
            // 恢复原数据库上下文
            if (currentDb != null && !currentDb.equals(databaseName)) {
                try {
                    switchDatabase(currentDb);
                } catch (Exception e) {
                    log.warn("Failed to restore database context", e);
                }
            }
        }
    }

    @Override
    public String getTableDDL(String schemaName, String tableName) {
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = dbAndSchema[0];
        String actualSchemaName = dbAndSchema[1];

        // SQL Server 可以使用 OBJECT_DEFINITION 或者查询系统视图来生成 DDL
        // 但更准确的方法是使用系统存储过程 sp_helptext 或者查询 sys.sql_modules
        // 对于表，我们需要手动构建 DDL，因为 SQL Server 没有直接提供表的 DDL 函数
        // 这里我们使用一个简化的方法，通过查询系统视图来生成基本的 CREATE TABLE 语句

        try {
            // 获取列信息（listTableColumns 内部会处理数据库切换）
            List<DBTableColumn> columns = listTableColumns(schemaName, tableName);
            if (columns.isEmpty()) {
                return "";
            }

            StringBuilder ddl = new StringBuilder();
            ddl.append("CREATE TABLE [").append(tableName).append("] (\n");

            // 添加列定义
            for (int i = 0; i < columns.size(); i++) {
                DBTableColumn column = columns.get(i);
                if (i > 0) {
                    ddl.append(",\n");
                }
                ddl.append("    [").append(column.getName()).append("] ");
                ddl.append(column.getFullTypeName());

                if (!column.getNullable()) {
                    ddl.append(" NOT NULL");
                }

                if (column.getDefaultValue() != null && StringUtils.isNotBlank(column.getDefaultValue().toString())) {
                    ddl.append(" DEFAULT ").append(column.getDefaultValue());
                }
            }

            // 添加主键约束
            List<DBTableConstraint> constraints = listTableConstraints(schemaName, tableName);
            for (DBTableConstraint constraint : constraints) {
                if (constraint.getType() == DBConstraintType.PRIMARY_KEY) {
                    ddl.append(",\n    CONSTRAINT [").append(constraint.getName()).append("] PRIMARY KEY (");
                    for (int i = 0; i < constraint.getColumnNames().size(); i++) {
                        if (i > 0) {
                            ddl.append(", ");
                        }
                        ddl.append("[").append(constraint.getColumnNames().get(i)).append("]");
                    }
                    ddl.append(")");
                }
            }

            ddl.append("\n);");

            return ddl.toString();
        } catch (Exception e) {
            log.warn("Failed to get table DDL for table: " + schemaName + "." + tableName, e);
            return "";
        }
    }

    @Override
    public DBTableOptions getTableOptions(String schemaName, String tableName) {
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = dbAndSchema[0];
        String actualSchemaName = dbAndSchema[1];

        // 确保在正确的数据库中查询
        String currentDb = null;
        try {
            currentDb = jdbcOperations.queryForObject("SELECT DB_NAME()", String.class);
            if (!databaseName.equals(currentDb)) {
                switchDatabase(databaseName);
            }
        } catch (Exception e) {
            log.warn("Failed to switch to database: " + databaseName, e);
            return new DBTableOptions();
        }

        String sql = "SELECT "
                + "    t.create_date AS create_time, "
                + "    t.modify_date AS update_time, "
                + "    t.name AS table_name, "
                + "    ISNULL(ep.value, '') AS table_comment "
                + "FROM sys.tables t "
                + "INNER JOIN sys.schemas s ON t.schema_id = s.schema_id "
                + "LEFT JOIN sys.extended_properties ep ON ep.major_id = t.object_id "
                + "    AND ep.minor_id = 0 "
                + "    AND ep.name = 'MS_Description' "
                + "WHERE DB_NAME() = ? "
                + "    AND t.name = ? "
                + "    AND s.name = ?"; // 使用参数

        try {
            DBTableOptions options = new DBTableOptions();
            jdbcOperations.query(sql, new Object[] {databaseName, tableName, actualSchemaName}, rs -> {
                if (rs.next()) {
                    java.sql.Timestamp createTime = rs.getTimestamp("create_time");
                    if (createTime != null) {
                        options.setCreateTime(createTime);
                    }

                    java.sql.Timestamp updateTime = rs.getTimestamp("update_time");
                    if (updateTime != null) {
                        options.setUpdateTime(updateTime);
                    }

                    String comment = rs.getString("table_comment");
                    if (StringUtils.isNotBlank(comment)) {
                        options.setComment(comment);
                    }
                }
            });
            return options;
        } catch (Exception e) {
            log.warn("Failed to get table options for table: " + schemaName + "." + tableName, e);
            return new DBTableOptions();
        } finally {
            // 恢复原数据库上下文
            if (currentDb != null && !currentDb.equals(databaseName)) {
                try {
                    switchDatabase(currentDb);
                } catch (Exception e) {
                    log.warn("Failed to restore database context", e);
                }
            }
        }
    }

    @Override
    public DBTableOptions getTableOptions(String schemaName, String tableName, String ddl) {
        return null;
    }

    @Override
    public List<DBColumnGroupElement> listTableColumnGroups(String schemaName, String tableName) {
        return Collections.emptyList();
    }

    @Override
    public DBView getView(String schemaName, String viewName) {
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = dbAndSchema[0];
        String actualSchemaName = dbAndSchema[1];

        // 确保在正确的数据库中查询
        String currentDb = null;
        try {
            currentDb = jdbcOperations.queryForObject("SELECT DB_NAME()", String.class);
            if (!databaseName.equals(currentDb)) {
                switchDatabase(databaseName);
            }
        } catch (Exception e) {
            log.warn("Failed to switch to database: " + databaseName, e);
            return null;
        }

        DBView view = new DBView();
        view.setViewName(viewName);
        view.setSchemaName(schemaName);

        try {
            // 查询视图基本信息
            String infoSql = "SELECT "
                    + "    TABLE_SCHEMA, "
                    + "    CHECK_OPTION, "
                    + "    IS_UPDATABLE "
                    + "FROM information_schema.views "
                    + "WHERE TABLE_CATALOG = ? "
                    + "    AND TABLE_SCHEMA = ? "
                    + "    AND TABLE_NAME = ?";

            jdbcOperations.query(infoSql, new Object[] {databaseName, actualSchemaName, viewName}, rs -> {
                view.setDefiner(rs.getString("TABLE_SCHEMA"));
                String checkOption = rs.getString("CHECK_OPTION");
                if (StringUtils.isNotBlank(checkOption)) {
                    if ("CASCADE".equalsIgnoreCase(checkOption)) {
                        // SQL Server 不支持 CASCADE，但可以设置为 NONE
                        view.setCheckOption(DBViewCheckOption.NONE.name());
                    } else {
                        view.setCheckOption(DBViewCheckOption.NONE.name());
                    }
                } else {
                    view.setCheckOption(DBViewCheckOption.NONE.name());
                }
                String isUpdatable = rs.getString("IS_UPDATABLE");
                view.setUpdatable("YES".equalsIgnoreCase(isUpdatable));
            });

            // 获取视图的 DDL - 使用 OBJECT_DEFINITION 函数
            // 由于已经切换到正确的数据库上下文，可以直接使用 schema.object 格式
            // OBJECT_ID 函数需要字符串参数，格式为 'schema.object'，不需要方括号
            String objectName = actualSchemaName + "." + viewName;
            String ddlSql = "SELECT OBJECT_DEFINITION(OBJECT_ID('" + objectName + "')) AS view_definition";
            AtomicReference<String> viewDefinition = new AtomicReference<>();
            jdbcOperations.query(ddlSql, rs -> {
                String definition = rs.getString("view_definition");
                if (StringUtils.isNotBlank(definition)) {
                    viewDefinition.set(definition);
                }
            });

            // 如果 OBJECT_DEFINITION 返回空，尝试使用 sys.sql_modules
            if (StringUtils.isBlank(viewDefinition.get())) {
                String moduleSql = "SELECT m.definition "
                        + "FROM sys.sql_modules m "
                        + "INNER JOIN sys.views v ON m.object_id = v.object_id "
                        + "INNER JOIN sys.schemas s ON v.schema_id = s.schema_id "
                        + "WHERE DB_NAME() = ? "
                        + "    AND v.name = ? "
                        + "    AND s.name = ?";
                jdbcOperations.query(moduleSql, new Object[] {databaseName, viewName, actualSchemaName}, rs -> {
                    viewDefinition.set(rs.getString("definition"));
                });
            }

            // 构建 DDL
            if (StringUtils.isNotBlank(viewDefinition.get())) {
                StringBuilder ddl = new StringBuilder();
                ddl.append("CREATE VIEW ");
                if (StringUtils.isNotEmpty(actualSchemaName)) {
                    ddl.append("[").append(actualSchemaName).append("].");
                }
                ddl.append("[").append(viewName).append("]");
                if (view.getCheckOption() != null && view.getCheckOption() != DBViewCheckOption.NONE) {
                    ddl.append(" WITH ").append(view.getCheckOption().name());
                }
                ddl.append(" AS ").append(viewDefinition.get());
                view.setDdl(ddl.toString());
            }

            // 获取视图的列信息
            String columnSql = "SELECT "
                    + "    c.column_id AS ordinal_position, "
                    + "    c.name AS column_name, "
                    + "    t.name AS data_type, "
                    + "    CASE "
                    + "        WHEN t.name IN ('varchar', 'nvarchar', 'char', 'nchar', 'binary', 'varbinary') "
                    + "        THEN t.name + '(' + CASE WHEN c.max_length = -1 THEN 'MAX' ELSE CAST(c.max_length AS VARCHAR) END + ')' "
                    + "        WHEN t.name IN ('decimal', 'numeric') "
                    + "        THEN t.name + '(' + CAST(c.precision AS VARCHAR) + ',' + CAST(c.scale AS VARCHAR) + ')' "
                    + "        WHEN t.name IN ('float', 'real') "
                    + "        THEN t.name + '(' + CAST(c.precision AS VARCHAR) + ')' "
                    + "        WHEN t.name IN ('datetime2', 'time', 'datetimeoffset') "
                    + "        THEN t.name + '(' + CAST(c.scale AS VARCHAR) + ')' "
                    + "        ELSE t.name "
                    + "    END AS full_type_name, "
                    + "    c.is_nullable, "
                    + "    ISNULL(ep.value, '') AS column_comment "
                    + "FROM sys.columns c "
                    + "INNER JOIN sys.types t ON c.user_type_id = t.user_type_id "
                    + "INNER JOIN sys.views v ON c.object_id = v.object_id "
                    + "INNER JOIN sys.schemas s ON v.schema_id = s.schema_id "
                    + "LEFT JOIN sys.extended_properties ep ON ep.major_id = c.object_id "
                    + "    AND ep.minor_id = c.column_id "
                    + "    AND ep.name = 'MS_Description' "
                    + "WHERE DB_NAME() = ? "
                    + "    AND v.name = ? "
                    + "    AND s.name = ? "
                    + "ORDER BY c.column_id";

            List<DBTableColumn> columns = jdbcOperations.query(columnSql,
                    new Object[] {databaseName, viewName, actualSchemaName}, (rs, rowNum) -> {
                        DBTableColumn column = new DBTableColumn();
                        column.setOrdinalPosition(rs.getInt("ordinal_position"));
                        column.setName(rs.getString("column_name"));
                        column.setTypeName(rs.getString("data_type"));
                        column.setFullTypeName(rs.getString("full_type_name"));
                        column.setNullable(rs.getBoolean("is_nullable"));
                        column.setComment(rs.getString("column_comment"));
                        column.setSchemaName(schemaName);
                        column.setTableName(viewName);
                        return column;
                    });
            view.setColumns(columns);

            return view;
        } catch (BadSqlGrammarException e) {
            if (StringUtils.containsIgnoreCase(e.getMessage(), "Invalid object name") ||
                    StringUtils.containsIgnoreCase(e.getMessage(), "Invalid schema")) {
                log.warn("View not found: " + schemaName + "." + viewName);
                return null;
            }
            throw e;
        } catch (Exception e) {
            log.warn("Failed to get view: " + schemaName + "." + viewName, e);
            return null;
        } finally {
            // 恢复原数据库上下文
            if (currentDb != null && !currentDb.equals(databaseName)) {
                try {
                    switchDatabase(currentDb);
                } catch (Exception e) {
                    log.warn("Failed to restore database context", e);
                }
            }
        }
    }

    @Override
    public DBFunction getFunction(String schemaName, String functionName) {
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = dbAndSchema[0];
        String actualSchemaName = dbAndSchema[1];

        // 确保在正确的数据库中查询
        String currentDb = null;
        try {
            currentDb = jdbcOperations.queryForObject("SELECT DB_NAME()", String.class);
            if (!databaseName.equals(currentDb)) {
                switchDatabase(databaseName);
            }
        } catch (Exception e) {
            log.warn("Failed to switch to database: " + databaseName, e);
            return null;
        }

        DBFunction function = new DBFunction();
        function.setFunName(functionName);
        // function.setSchemaName(schemaName);

        try {
            // 查询函数基本信息
            String infoSql = "SELECT "
                    + "    ROUTINE_SCHEMA, "
                    + "    CREATED, "
                    + "    LAST_ALTERED, "
                    + "    ROUTINE_DEFINITION "
                    + "FROM information_schema.routines "
                    + "WHERE ROUTINE_CATALOG = ? "
                    + "    AND ROUTINE_SCHEMA = ? "
                    + "    AND ROUTINE_NAME = ?";

            AtomicReference<String> routineDefinition = new AtomicReference<>();
            jdbcOperations.query(infoSql, new Object[] {databaseName, actualSchemaName, functionName}, rs -> {
                function.setDefiner(rs.getString("ROUTINE_SCHEMA"));
                Timestamp created = rs.getTimestamp("CREATED");
                if (created != null) {
                    function.setCreateTime(created);
                }
                Timestamp lastAltered = rs.getTimestamp("LAST_ALTERED");
                if (lastAltered != null) {
                    function.setModifyTime(lastAltered);
                }
                routineDefinition.set(rs.getString("ROUTINE_DEFINITION"));
            });

            // 查询函数参数和返回类型
            String paramSql = "SELECT "
                    + "    PARAMETER_MODE, "
                    + "    PARAMETER_NAME, "
                    + "    DATA_TYPE, "
                    + "    CHARACTER_MAXIMUM_LENGTH, "
                    + "    NUMERIC_PRECISION, "
                    + "    NUMERIC_SCALE, "
                    + "    ORDINAL_POSITION "
                    + "FROM information_schema.parameters "
                    + "WHERE SPECIFIC_CATALOG = ? "
                    + "    AND SPECIFIC_SCHEMA = ? "
                    + "    AND SPECIFIC_NAME = ? "
                    + "ORDER BY ORDINAL_POSITION";

            List<DBPLParam> params = new ArrayList<>();
            AtomicReference<String> returnType = new AtomicReference<>();

            jdbcOperations.query(paramSql, new Object[] {databaseName, actualSchemaName, functionName}, rs -> {
                String paramMode = rs.getString("PARAMETER_MODE");
                String paramName = rs.getString("PARAMETER_NAME");
                String dataType = rs.getString("DATA_TYPE");
                Integer maxLength = rs.getObject("CHARACTER_MAXIMUM_LENGTH", Integer.class);
                Integer precision = rs.getObject("NUMERIC_PRECISION", Integer.class);
                Integer scale = rs.getObject("NUMERIC_SCALE", Integer.class);
                int ordinalPosition = rs.getInt("ORDINAL_POSITION");

                // 构建完整的数据类型字符串
                StringBuilder fullDataType = new StringBuilder(dataType);
                if (maxLength != null && maxLength > 0) {
                    if (maxLength == -1) {
                        fullDataType.append("(MAX)");
                    } else {
                        fullDataType.append("(").append(maxLength).append(")");
                    }
                } else if (precision != null && scale != null) {
                    fullDataType.append("(").append(precision).append(",").append(scale).append(")");
                } else if (precision != null) {
                    fullDataType.append("(").append(precision).append(")");
                }

                // 如果 PARAMETER_MODE 为 NULL，表示这是返回类型
                if (paramMode == null || "NULL".equalsIgnoreCase(paramMode)) {
                    returnType.set(fullDataType.toString());
                } else {
                    // 这是输入参数
                    DBPLParam param = new DBPLParam();
                    param.setParamName(paramName);
                    param.setSeqNum(ordinalPosition);
                    param.setDataType(fullDataType.toString());
                    // SQL Server 函数参数通常是 IN 类型
                    param.setParamMode(DBPLParamMode.IN);
                    params.add(param);
                }
            });

            function.setReturnType(returnType.get());
            function.setParams(params);

            // 构建 DDL
            StringBuilder ddl = new StringBuilder();
            ddl.append("CREATE FUNCTION ");
            if (StringUtils.isNotEmpty(actualSchemaName)) {
                ddl.append("[").append(actualSchemaName).append("].");
            }
            ddl.append("[").append(functionName).append("]");
            ddl.append("(");

            // 添加参数列表
            if (!params.isEmpty()) {
                for (int i = 0; i < params.size(); i++) {
                    DBPLParam param = params.get(i);
                    if (i > 0) {
                        ddl.append(", ");
                    }
                    ddl.append("@").append(param.getParamName()).append(" ").append(param.getDataType());
                }
            }
            ddl.append(")");
            ddl.append(" RETURNS ").append(returnType.get());
            ddl.append(" AS BEGIN ");
            if (StringUtils.isNotBlank(routineDefinition.get())) {
                ddl.append(routineDefinition.get());
            }
            ddl.append(" END");

            function.setDdl(ddl.toString());

            return function;
        } catch (BadSqlGrammarException e) {
            if (StringUtils.containsIgnoreCase(e.getMessage(), "Invalid object name") ||
                    StringUtils.containsIgnoreCase(e.getMessage(), "Invalid schema")) {
                log.warn("Function not found: " + schemaName + "." + functionName);
                return null;
            }
            throw e;
        } catch (Exception e) {
            log.warn("Failed to get function: " + schemaName + "." + functionName, e);
            return null;
        } finally {
            // 恢复原数据库上下文
            if (currentDb != null && !currentDb.equals(databaseName)) {
                try {
                    switchDatabase(currentDb);
                } catch (Exception e) {
                    log.warn("Failed to restore database context", e);
                }
            }
        }
    }

    @Override
    public DBProcedure getProcedure(String schemaName, String procedureName) {
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = dbAndSchema[0];
        String actualSchemaName = dbAndSchema[1];

        // 确保在正确的数据库中查询
        String currentDb = null;
        try {
            currentDb = jdbcOperations.queryForObject("SELECT DB_NAME()", String.class);
            if (!databaseName.equals(currentDb)) {
                switchDatabase(databaseName);
            }
        } catch (Exception e) {
            log.warn("Failed to switch to database: " + databaseName, e);
            return null;
        }

        DBProcedure procedure = new DBProcedure();
        procedure.setProName(procedureName);

        try {
            // 查询存储过程基本信息
            String infoSql = "SELECT "
                    + "    ROUTINE_SCHEMA, "
                    + "    CREATED, "
                    + "    LAST_ALTERED, "
                    + "    ROUTINE_DEFINITION "
                    + "FROM information_schema.routines "
                    + "WHERE ROUTINE_CATALOG = ? "
                    + "    AND ROUTINE_SCHEMA = ? "
                    + "    AND ROUTINE_TYPE = 'PROCEDURE' "
                    + "    AND ROUTINE_NAME = ?";

            AtomicReference<String> routineDefinition = new AtomicReference<>();
            jdbcOperations.query(infoSql, new Object[] {databaseName, actualSchemaName, procedureName}, rs -> {
                procedure.setDefiner(rs.getString("ROUTINE_SCHEMA"));
                Timestamp created = rs.getTimestamp("CREATED");
                if (created != null) {
                    procedure.setCreateTime(created);
                }
                Timestamp lastAltered = rs.getTimestamp("LAST_ALTERED");
                if (lastAltered != null) {
                    procedure.setModifyTime(lastAltered);
                }
                routineDefinition.set(rs.getString("ROUTINE_DEFINITION"));
            });

            // 查询存储过程参数
            String paramSql = "SELECT "
                    + "    PARAMETER_MODE, "
                    + "    PARAMETER_NAME, "
                    + "    DATA_TYPE, "
                    + "    CHARACTER_MAXIMUM_LENGTH, "
                    + "    NUMERIC_PRECISION, "
                    + "    NUMERIC_SCALE, "
                    + "    ORDINAL_POSITION "
                    + "FROM information_schema.parameters "
                    + "WHERE SPECIFIC_CATALOG = ? "
                    + "    AND SPECIFIC_SCHEMA = ? "
                    + "    AND SPECIFIC_NAME = ? "
                    + "ORDER BY ORDINAL_POSITION";

            List<DBPLParam> params = new ArrayList<>();

            jdbcOperations.query(paramSql, new Object[] {databaseName, actualSchemaName, procedureName}, rs -> {
                String paramMode = rs.getString("PARAMETER_MODE");
                String paramName = rs.getString("PARAMETER_NAME");
                String dataType = rs.getString("DATA_TYPE");
                Integer maxLength = rs.getObject("CHARACTER_MAXIMUM_LENGTH", Integer.class);
                Integer precision = rs.getObject("NUMERIC_PRECISION", Integer.class);
                Integer scale = rs.getObject("NUMERIC_SCALE", Integer.class);
                int ordinalPosition = rs.getInt("ORDINAL_POSITION");

                // 构建完整的数据类型字符串
                StringBuilder fullDataType = new StringBuilder(dataType);
                if (maxLength != null && maxLength > 0) {
                    if (maxLength == -1) {
                        fullDataType.append("(MAX)");
                    } else {
                        fullDataType.append("(").append(maxLength).append(")");
                    }
                } else if (precision != null && scale != null) {
                    fullDataType.append("(").append(precision).append(",").append(scale).append(")");
                } else if (precision != null) {
                    fullDataType.append("(").append(precision).append(")");
                }

                // 存储过程参数处理
                DBPLParam param = new DBPLParam();
                param.setParamName(paramName);
                param.setSeqNum(ordinalPosition);
                param.setDataType(fullDataType.toString());

                // SQL Server 存储过程参数模式映射
                // IN - 输入参数（默认）
                // OUT - 输出参数（SQL Server 使用 OUTPUT 关键字）
                // INOUT - 输入输出参数
                if (paramMode == null || StringUtils.isBlank(paramMode)) {
                    // 默认为输入参数
                    param.setParamMode(DBPLParamMode.IN);
                } else if ("IN".equalsIgnoreCase(paramMode)) {
                    param.setParamMode(DBPLParamMode.IN);
                } else if ("OUT".equalsIgnoreCase(paramMode)) {
                    param.setParamMode(DBPLParamMode.OUT);
                } else if ("INOUT".equalsIgnoreCase(paramMode)) {
                    param.setParamMode(DBPLParamMode.INOUT);
                } else {
                    param.setParamMode(DBPLParamMode.UNKNOWN);
                }

                params.add(param);
            });

            procedure.setParams(params);

            // 构建 DDL
            StringBuilder ddl = new StringBuilder();
            ddl.append("CREATE PROCEDURE ");
            if (StringUtils.isNotEmpty(actualSchemaName)) {
                ddl.append("[").append(actualSchemaName).append("].");
            }
            ddl.append("[").append(procedureName).append("]");
            ddl.append("(");

            // 添加参数列表
            if (!params.isEmpty()) {
                for (int i = 0; i < params.size(); i++) {
                    DBPLParam param = params.get(i);
                    if (i > 0) {
                        ddl.append(", ");
                    }
                    ddl.append("@").append(param.getParamName()).append(" ").append(param.getDataType());
                    // SQL Server 存储过程输出参数使用 OUTPUT 关键字
                    if (param.getParamMode() == DBPLParamMode.OUT || param.getParamMode() == DBPLParamMode.INOUT) {
                        ddl.append(" OUTPUT");
                    }
                }
            }
            ddl.append(")");
            ddl.append(" AS BEGIN ");
            if (StringUtils.isNotBlank(routineDefinition.get())) {
                ddl.append(routineDefinition.get());
            }
            ddl.append(" END");

            procedure.setDdl(ddl.toString());

            return procedure;
        } catch (BadSqlGrammarException e) {
            if (StringUtils.containsIgnoreCase(e.getMessage(), "Invalid object name") ||
                    StringUtils.containsIgnoreCase(e.getMessage(), "Invalid schema")) {
                log.warn("Procedure not found: " + schemaName + "." + procedureName);
                return null;
            }
            throw e;
        } catch (Exception e) {
            log.warn("Failed to get procedure: " + schemaName + "." + procedureName, e);
            return null;
        } finally {
            // 恢复原数据库上下文
            if (currentDb != null && !currentDb.equals(databaseName)) {
                try {
                    switchDatabase(currentDb);
                } catch (Exception e) {
                    log.warn("Failed to restore database context", e);
                }
            }
        }
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
        return Collections.emptyMap();
    }
}
