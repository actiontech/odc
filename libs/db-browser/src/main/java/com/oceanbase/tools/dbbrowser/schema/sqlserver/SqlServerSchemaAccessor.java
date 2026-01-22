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
package com.oceanbase.tools.dbbrowser.schema.sqlserver;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcOperations;

import com.oceanbase.tools.dbbrowser.model.DBColumnGroupElement;
import com.oceanbase.tools.dbbrowser.model.DBConstraintType;
import com.oceanbase.tools.dbbrowser.model.DBDatabase;
import com.oceanbase.tools.dbbrowser.model.DBForeignKeyModifyRule;
import com.oceanbase.tools.dbbrowser.model.DBFunction;
import com.oceanbase.tools.dbbrowser.model.DBIndexType;
import com.oceanbase.tools.dbbrowser.model.DBMViewRefreshParameter;
import com.oceanbase.tools.dbbrowser.model.DBMViewRefreshRecord;
import com.oceanbase.tools.dbbrowser.model.DBMViewRefreshRecordParam;
import com.oceanbase.tools.dbbrowser.model.DBMaterializedView;
import com.oceanbase.tools.dbbrowser.model.DBMaterializedViewRefreshMethod;
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

    /**
     * 返回所有 schema 列表
     * <p>
     * <b>SQL Server 特殊处理：</b> 为了统一抽象层并与 MySQL 保持一致，SQL Server 将 database.schema 组合作为 schema 返回。 例如：返回
     * "MyDB.dbo" 而不是分别返回数据库和 schema。 这样前后端可以统一处理 schema.table 格式，无需为 SQL Server 做特殊适配。
     * </p>
     *
     * @return 所有 database.schema 组合的列表，已过滤系统 schema
     */
    @Override
    public List<String> showDatabases() {
        // 对于 SQL Server，返回所有 database.schema 组合，以兼容 database.schema 格式的 schemaName
        List<String> results = new ArrayList<>();
        // 过滤系统数据库：master, model, tempdb, msdb
        String sql = "SELECT name FROM sys.databases "
                + "WHERE state_desc = 'ONLINE' "
                + "AND name NOT IN ('master', 'model', 'tempdb', 'msdb')";
        List<String> databases;
        try {
            databases = jdbcOperations.queryForList(sql, String.class);
        } catch (BadSqlGrammarException e) {
            log.warn("Failed to query databases", e);
            return Collections.emptyList();
        }

        String currentDb = null;
        try {
            currentDb = jdbcOperations.queryForObject("SELECT DB_NAME()", String.class);
        } catch (Exception e) {
            log.warn("Failed to get current database", e);
        }

        for (String databaseName : databases) {
            try {
                if (currentDb == null || !databaseName.equals(currentDb)) {
                    switchDatabase(databaseName);
                }

                // 查询该数据库下的所有用户 schema，排除系统 schema
                String schemaSql = "SELECT name FROM sys.schemas "
                        + "WHERE name NOT IN ('sys', 'INFORMATION_SCHEMA', 'guest', "
                        + "'db_accessadmin', 'db_backupoperator', 'db_datareader', "
                        + "'db_datawriter', 'db_ddladmin', 'db_denydatareader', "
                        + "'db_denydatawriter', 'db_owner', 'db_securityadmin') "
                        + "ORDER BY name";
                List<String> schemas = jdbcOperations.queryForList(schemaSql, String.class);

                for (String schemaName : schemas) {
                    results.add(SqlServerSchemaUtil.buildFullSchemaName(databaseName, schemaName));
                }
            } catch (Exception e) {
                log.warn("Failed to list schemas from database: " + databaseName, e);
            }
        }

        // 恢复原数据库上下文
        if (currentDb != null && !databases.isEmpty() && !currentDb.equals(databases.get(0))) {
            try {
                switchDatabase(currentDb);
            } catch (Exception e) {
                log.warn("Failed to restore database context", e);
            }
        }

        return results;
    }

    @Override
    public DBDatabase getDatabase(String schemaName) {
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
        // SQL Server 中返回所有数据库及其信息，过滤系统数据库
        String sql = "SELECT "
                + "    name, "
                + "    collation_name AS collation "
                + "FROM sys.databases "
                + "WHERE state_desc = 'ONLINE' "
                + "AND name NOT IN ('master', 'model', 'tempdb', 'msdb') "
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
        // 使用统一的工具类方法进行解析
        // 当 schemaName 为空时，会尝试获取当前数据库名
        return SqlServerSchemaUtil.parseDatabaseAndSchema(schemaName, jdbcOperations);
    }

    /**
     * 显示指定 schema 下的所有表
     * <p>
     * <b>SQL Server 特殊处理：</b> schemaName 参数支持两种格式： 1. "database" - 只有数据库名，默认使用 dbo schema（兼容旧代码） 2.
     * "database.schema" - 完整的 database.schema 格式（从 showDatabases() 返回的格式） 为了统一抽象层，SQL Server 将
     * database.schema 作为 schema 处理，与 MySQL 保持一致。
     * </p>
     *
     * @param schemaName 数据库名或 database.schema 格式
     * @return 表名列表
     */
    @Override
    public List<String> showTables(String schemaName) {
        return showTablesLike(schemaName, null);
    }

    /**
     * 显示指定 schema 下匹配条件的表
     * <p>
     * <b>SQL Server 特殊处理：</b> schemaName 参数支持两种格式： 1. "database" - 只有数据库名，默认使用 dbo schema（兼容旧代码） 2.
     * "database.schema" - 完整的 database.schema 格式（从 showDatabases() 返回的格式） 为了统一抽象层，SQL Server 将
     * database.schema 作为 schema 处理，与 MySQL 保持一致。
     * </p>
     *
     * @param schemaName 数据库名或 database.schema 格式
     * @param tableNameLike 表名匹配模式（可选）
     * @return 匹配的表名列表
     */
    @Override
    public List<String> showTablesLike(String schemaName, String tableNameLike) {
        // SQL Server 的层次结构：databases -> schemas -> tables
        // schemaName 参数支持两种格式：
        // 1. "database.schema" - 数据库名和 schema 名
        // 2. "schema" - 只有 schema 名，使用当前数据库
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = SqlServerSchemaUtil.getDatabaseName(dbAndSchema[0], jdbcOperations);
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

    /**
     * 列出指定 schema 下的表
     * <p>
     * <b>SQL Server 特殊处理：</b> schemaName 参数支持两种格式： 1. "database" - 只有数据库名，默认使用 dbo schema（兼容旧代码） 2.
     * "database.schema" - 完整的 database.schema 格式（从 showDatabases() 返回的格式） 为 null 时返回所有数据库下的表。
     * 为了统一抽象层，SQL Server 将 database.schema 作为 schema 处理，与 MySQL 保持一致。
     * </p>
     *
     * @param schemaName 数据库名或 database.schema 格式，为 null 时返回所有表
     * @param tableNameLike 表名匹配模式（可选）
     * @return 表对象列表
     */
    @Override
    public List<DBObjectIdentity> listTables(String schemaName, String tableNameLike) {
        // 当 schemaName 为 null 时，遍历所有数据库（类似 listAllUserViews 的逻辑）
        if (StringUtils.isBlank(schemaName)) {
            return listAllTables(tableNameLike);
        }

        // 解析 database.schema 格式，构建完整的 schemaName
        // 如果 schemaName 为 null，parseDatabaseAndSchema 会尝试获取当前数据库
        String[] dbAndSchema;
        try {
            dbAndSchema = parseDatabaseAndSchema(schemaName);
        } catch (IllegalArgumentException e) {
            // 如果无法解析 schemaName（包括 null 且无法获取当前数据库的情况），返回空列表
            log.warn("Failed to parse schemaName: " + schemaName, e);
            return Collections.emptyList();
        }
        String databaseName = SqlServerSchemaUtil.getDatabaseName(dbAndSchema[0], jdbcOperations);
        String actualSchemaName = dbAndSchema[1];

        // 构建完整的 schemaName（database.schema 格式）
        String fullSchemaName = SqlServerSchemaUtil.buildFullSchemaName(databaseName, actualSchemaName);

        List<String> tableNames = showTablesLike(schemaName, tableNameLike);
        return tableNames.stream().map(tableName -> {
            DBObjectIdentity identity = new DBObjectIdentity();
            identity.setSchemaName(fullSchemaName);
            identity.setName(tableName);
            identity.setType(DBObjectType.TABLE);
            return identity;
        }).collect(Collectors.toList());
    }

    private List<DBObjectIdentity> listAllTables(String tableNameLike) {
        List<DBObjectIdentity> results = new ArrayList<>();
        List<String> databasesAndSchemas = showDatabases();
        // 提取去重后的数据库名，避免同一数据库被多次切库查询
        List<String> databaseNames = databasesAndSchemas.stream()
                .map(item -> SqlServerSchemaUtil.parseDatabaseAndSchema(item)[0])
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        String currentDb = null;
        try {
            currentDb = jdbcOperations.queryForObject("SELECT DB_NAME()", String.class);
        } catch (Exception e) {
            log.warn("Failed to get current database", e);
        }

        for (String databaseName : databaseNames) {
            try {
                if (currentDb == null || !databaseName.equals(currentDb)) {
                    switchDatabase(databaseName);
                }

                // 查询所有 schema 下的表
                StringBuilder sql = new StringBuilder();
                sql.append("SELECT DB_NAME() AS database_name, s.name AS schema_name, t.name AS table_name ")
                        .append("FROM sys.tables t ")
                        .append("INNER JOIN sys.schemas s ON t.schema_id = s.schema_id ")
                        .append("WHERE t.is_ms_shipped = 0"); // 排除系统表

                List<Object> params = new ArrayList<>();
                if (StringUtils.isNotBlank(tableNameLike)) {
                    sql.append(
                            " AND (t.name LIKE ? ESCAPE '\\' OR s.name LIKE ? ESCAPE '\\' OR DB_NAME() LIKE ? ESCAPE '\\')");
                    String likePattern = StringUtils.escapeLike(tableNameLike);
                    params.add(likePattern);
                    params.add(likePattern);
                    params.add(likePattern);
                }
                sql.append(" ORDER BY s.name, t.name");

                List<DBObjectIdentity> tables = jdbcOperations.query(sql.toString(), params.toArray(), (rs, rowNum) -> {
                    DBObjectIdentity identity = new DBObjectIdentity();
                    // SQL Server 使用 database.schema 格式作为 schemaName
                    String dbName = rs.getString("database_name");
                    String schemaName = rs.getString("schema_name");
                    identity.setSchemaName(SqlServerSchemaUtil.buildFullSchemaName(dbName, schemaName));
                    identity.setName(rs.getString("table_name"));
                    identity.setType(DBObjectType.TABLE);
                    return identity;
                });
                results.addAll(tables);
            } catch (Exception e) {
                log.warn("Failed to list tables from database: " + databaseName, e);
            }
        }

        // 恢复原数据库上下文
        if (currentDb != null && !databaseNames.isEmpty()
                && !currentDb.equals(databaseNames.get(databaseNames.size() - 1))) {
            try {
                switchDatabase(currentDb);
            } catch (Exception e) {
                log.warn("Failed to restore database context", e);
            }
        }

        return results;
    }

    @Override
    public List<String> showExternalTablesLike(String schemaName, String tableNameLike) {
        // SQL Server does not support external tables, return empty list
        return Collections.emptyList();
    }

    @Override
    public List<DBObjectIdentity> listExternalTables(String schemaName, String tableNameLike) {
        // SQL Server does not support external tables, return empty list
        return Collections.emptyList();
    }

    @Override
    public boolean isExternalTable(String schemaName, String tableName) {
        // SQL Server does not support external tables, always return false
        return false;
    }

    @Override
    public boolean syncExternalTableFiles(String schemaName, String tableName) {
        // SQL Server does not support external tables
        throw new UnsupportedOperationException("SQL Server does not support external tables");
    }

    /**
     * 列出指定 schema 下的视图
     * <p>
     * <b>SQL Server 特殊处理：</b> schemaName 参数支持两种格式： 1. "database" - 只有数据库名，默认使用 dbo schema（兼容旧代码） 2.
     * "database.schema" - 完整的 database.schema 格式（从 showDatabases() 返回的格式） 为 null 或空时返回所有数据库下的视图。
     * 为了统一抽象层，SQL Server 将 database.schema 作为 schema 处理，与 MySQL 保持一致。
     * </p>
     *
     * @param schemaName 数据库名或 database.schema 格式，为 null 或空时返回所有视图
     * @return 视图对象列表
     */
    @Override
    public List<DBObjectIdentity> listViews(String schemaName) {
        if (StringUtils.isBlank(schemaName)) {
            List<DBObjectIdentity> results = new ArrayList<>();
            results.addAll(listAllUserViews(null));
            results.addAll(listAllSystemViews(null));
            return results;
        }
        // 解析 database.schema 格式
        String[] dbAndSchema;
        try {
            dbAndSchema = parseDatabaseAndSchema(schemaName);
        } catch (IllegalArgumentException e) {
            // 如果无法解析 schemaName（包括 null 且无法获取当前数据库的情况），返回空列表
            log.warn("Failed to parse schemaName: " + schemaName, e);
            return Collections.emptyList();
        }
        String databaseName = SqlServerSchemaUtil.getDatabaseName(dbAndSchema[0], jdbcOperations);
        String actualSchemaName = dbAndSchema[1];

        // 构建完整的 schemaName（database.schema 格式）
        String fullSchemaName = SqlServerSchemaUtil.buildFullSchemaName(databaseName, actualSchemaName);

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
                identity.setSchemaName(fullSchemaName);
                identity.setName(rs.getString("table_name"));
                identity.setType(DBObjectType.VIEW);
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
        List<DBObjectIdentity> results = new ArrayList<>();
        List<String> databasesAndSchemas = showDatabases();
        // 提取去重后的数据库名
        List<String> databaseNames = databasesAndSchemas.stream()
                .map(item -> SqlServerSchemaUtil.parseDatabaseAndSchema(item)[0])
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        String currentDb = null;
        try {
            currentDb = jdbcOperations.queryForObject("SELECT DB_NAME()", String.class);
        } catch (Exception e) {
            log.warn("Failed to get current database", e);
        }

        for (String databaseName : databaseNames) {
            try {
                if (currentDb == null || !databaseName.equals(currentDb)) {
                    switchDatabase(databaseName);
                }

                String sql =
                        "SELECT DB_NAME() AS database_name, table_schema, table_name FROM information_schema.views";
                if (StringUtils.isNotBlank(viewNameLike)) {
                    sql += " WHERE table_name LIKE ? ESCAPE '\\'";
                }
                sql += " ORDER BY table_schema, table_name";

                List<DBObjectIdentity> views;
                if (StringUtils.isNotBlank(viewNameLike)) {
                    String likePattern = StringUtils.escapeLike(viewNameLike);
                    views = jdbcOperations.query(sql, new Object[] {likePattern}, (rs, rowNum) -> {
                        DBObjectIdentity identity = new DBObjectIdentity();
                        // SQL Server 使用 database.schema 格式作为 schemaName
                        String dbName = rs.getString("database_name");
                        String schemaName = rs.getString("table_schema");
                        identity.setSchemaName(SqlServerSchemaUtil.buildFullSchemaName(dbName, schemaName));
                        identity.setName(rs.getString("table_name"));
                        identity.setType(DBObjectType.VIEW);
                        return identity;
                    });
                } else {
                    views = jdbcOperations.query(sql, (rs, rowNum) -> {
                        DBObjectIdentity identity = new DBObjectIdentity();
                        // SQL Server 使用 database.schema 格式作为 schemaName
                        String dbName = rs.getString("database_name");
                        String schemaName = rs.getString("table_schema");
                        identity.setSchemaName(SqlServerSchemaUtil.buildFullSchemaName(dbName, schemaName));
                        identity.setName(rs.getString("table_name"));
                        identity.setType(DBObjectType.VIEW);
                        return identity;
                    });
                }
                results.addAll(views);
            } catch (Exception e) {
                log.warn("Failed to list views from database: " + databaseName, e);
            }
        }

        // 恢复原数据库上下文
        if (currentDb != null && !databaseNames.isEmpty() && !currentDb.equals(databaseNames.get(0))) {
            try {
                switchDatabase(currentDb);
            } catch (Exception e) {
                log.warn("Failed to restore database context", e);
            }
        }

        return results;
    }

    @Override
    public List<DBObjectIdentity> listAllUserViews(String viewNameLike) {
        List<DBObjectIdentity> results = new ArrayList<>();
        List<String> databasesAndSchemas = showDatabases();
        // 提取去重后的数据库名
        List<String> databaseNames = databasesAndSchemas.stream()
                .map(item -> SqlServerSchemaUtil.parseDatabaseAndSchema(item)[0])
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        String currentDb = null;
        try {
            currentDb = jdbcOperations.queryForObject("SELECT DB_NAME()", String.class);
        } catch (Exception e) {
            log.warn("Failed to get current database", e);
        }

        for (String databaseName : databaseNames) {
            try {
                if (currentDb == null || !databaseName.equals(currentDb)) {
                    switchDatabase(databaseName);
                }

                // 查询用户视图（非系统视图）
                String sql = "SELECT DB_NAME() AS database_name, s.name AS schema_name, v.name AS view_name "
                        + "FROM sys.views v "
                        + "INNER JOIN sys.schemas s ON v.schema_id = s.schema_id "
                        + "WHERE v.is_ms_shipped = 0";
                List<Object> params = new ArrayList<>();
                if (StringUtils.isNotBlank(viewNameLike)) {
                    sql += " AND (v.name LIKE ? ESCAPE '\\' OR s.name LIKE ? ESCAPE '\\' OR DB_NAME() LIKE ? ESCAPE '\\')";
                    String likePattern = StringUtils.escapeLike(viewNameLike);
                    params.add(likePattern);
                    params.add(likePattern);
                    params.add(likePattern);
                }
                sql += " ORDER BY s.name, v.name";

                List<DBObjectIdentity> views = jdbcOperations.query(sql, params.toArray(), (rs, rowNum) -> {
                    DBObjectIdentity identity = new DBObjectIdentity();
                    String dbName = rs.getString("database_name");
                    String schemaName = rs.getString("schema_name");
                    identity.setSchemaName(SqlServerSchemaUtil.buildFullSchemaName(dbName, schemaName));
                    identity.setName(rs.getString("view_name"));
                    identity.setType(DBObjectType.VIEW);
                    return identity;
                });
                results.addAll(views);
            } catch (Exception e) {
                log.warn("Failed to list user views from database: " + databaseName, e);
            }
        }

        // 恢复原数据库上下文
        if (currentDb != null && !databaseNames.isEmpty() && !currentDb.equals(databaseNames.get(0))) {
            try {
                switchDatabase(currentDb);
            } catch (Exception e) {
                log.warn("Failed to restore database context", e);
            }
        }

        return results;
    }

    @Override
    public List<DBObjectIdentity> listAllSystemViews(String viewNameLike) {
        List<DBObjectIdentity> results = new ArrayList<>();
        List<String> databasesAndSchemas = showDatabases();
        // 提取去重后的数据库名
        List<String> databaseNames = databasesAndSchemas.stream()
                .map(item -> SqlServerSchemaUtil.parseDatabaseAndSchema(item)[0])
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        String currentDb = null;
        try {
            currentDb = jdbcOperations.queryForObject("SELECT DB_NAME()", String.class);
        } catch (Exception e) {
            log.warn("Failed to get current database", e);
        }

        for (String databaseName : databaseNames) {
            try {
                if (currentDb == null || !databaseName.equals(currentDb)) {
                    switchDatabase(databaseName);
                }

                // 查询系统视图（is_ms_shipped = 1）
                String sql = "SELECT DB_NAME() AS database_name, s.name AS schema_name, v.name AS view_name "
                        + "FROM sys.views v "
                        + "INNER JOIN sys.schemas s ON v.schema_id = s.schema_id "
                        + "WHERE v.is_ms_shipped = 1";
                List<Object> params = new ArrayList<>();
                if (StringUtils.isNotBlank(viewNameLike)) {
                    sql += " AND (v.name LIKE ? ESCAPE '\\' OR s.name LIKE ? ESCAPE '\\' OR DB_NAME() LIKE ? ESCAPE '\\')";
                    String likePattern = StringUtils.escapeLike(viewNameLike);
                    params.add(likePattern);
                    params.add(likePattern);
                    params.add(likePattern);
                }
                sql += " ORDER BY s.name, v.name";

                List<DBObjectIdentity> views = jdbcOperations.query(sql, params.toArray(), (rs, rowNum) -> {
                    DBObjectIdentity identity = new DBObjectIdentity();
                    String dbName = rs.getString("database_name");
                    String schemaName = rs.getString("schema_name");
                    identity.setSchemaName(SqlServerSchemaUtil.buildFullSchemaName(dbName, schemaName));
                    identity.setName(rs.getString("view_name"));
                    identity.setType(DBObjectType.VIEW);
                    return identity;
                });
                results.addAll(views);
            } catch (Exception e) {
                log.warn("Failed to list system views from database: " + databaseName, e);
            }
        }

        // 恢复原数据库上下文
        if (currentDb != null && !databaseNames.isEmpty() && !currentDb.equals(databaseNames.get(0))) {
            try {
                switchDatabase(currentDb);
            } catch (Exception e) {
                log.warn("Failed to restore database context", e);
            }
        }

        return results;
    }

    /**
     * 显示指定 schema 下的系统视图
     * <p>
     * <b>SQL Server 特殊处理：</b> schemaName 参数支持两种格式： 1. "database" - 只有数据库名，默认使用 dbo schema（兼容旧代码） 2.
     * "database.schema" - 完整的 database.schema 格式（从 showDatabases() 返回的格式） 为了统一抽象层，SQL Server 将
     * database.schema 作为 schema 处理，与 MySQL 保持一致。
     * </p>
     *
     * @param schemaName 数据库名或 database.schema 格式
     * @return 系统视图名列表
     */
    @Override
    public List<String> showSystemViews(String schemaName) {
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = SqlServerSchemaUtil.getDatabaseName(dbAndSchema[0], jdbcOperations);
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

        String sql = "SELECT v.name AS view_name "
                + "FROM sys.views v "
                + "INNER JOIN sys.schemas s ON v.schema_id = s.schema_id "
                + "WHERE v.is_ms_shipped = 1 AND s.name = ? "
                + "ORDER BY v.name";

        try {
            return jdbcOperations.query(sql, new Object[] {actualSchemaName},
                    (rs, rowNum) -> rs.getString("view_name"));
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

    /**
     * 列出指定 schema 下的物化视图（索引视图）
     * <p>
     * <b>SQL Server 特殊处理：</b> schemaName 参数支持两种格式： 1. "database" - 只有数据库名，默认使用 dbo schema（兼容旧代码） 2.
     * "database.schema" - 完整的 database.schema 格式（从 showDatabases() 返回的格式） 为 null 或空时返回所有数据库下的物化视图。
     * 为了统一抽象层，SQL Server 将 database.schema 作为 schema 处理，与 MySQL 保持一致。
     * </p>
     *
     * @param schemaName 数据库名或 database.schema 格式，为 null 或空时返回所有物化视图
     * @return 物化视图对象列表
     */
    @Override
    public List<DBObjectIdentity> listMViews(String schemaName) {
        if (StringUtils.isBlank(schemaName)) {
            return listAllMViewsLike(null);
        }
        // SQL Server 使用索引视图（Indexed Views）而不是传统物化视图
        // 索引视图是通过在视图上创建唯一聚集索引来实现的
        // 解析 database.schema 格式
        String[] dbAndSchema;
        try {
            dbAndSchema = parseDatabaseAndSchema(schemaName);
        } catch (IllegalArgumentException e) {
            // 如果无法解析 schemaName（包括 null 且无法获取当前数据库的情况），返回空列表
            log.warn("Failed to parse schemaName: " + schemaName, e);
            return Collections.emptyList();
        }
        String databaseName = SqlServerSchemaUtil.getDatabaseName(dbAndSchema[0], jdbcOperations);
        String actualSchemaName = dbAndSchema[1];

        // 构建完整的 schemaName（database.schema 格式）
        String fullSchemaName = SqlServerSchemaUtil.buildFullSchemaName(databaseName, actualSchemaName);

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

        // 查询有聚集索引的视图（索引视图）
        // 索引视图必须有唯一聚集索引（type = 1）
        String sql = "SELECT DISTINCT "
                + "    v.name AS view_name "
                + "FROM sys.views v "
                + "INNER JOIN sys.schemas s ON v.schema_id = s.schema_id "
                + "INNER JOIN sys.indexes i ON v.object_id = i.object_id "
                + "WHERE DB_NAME() = ? "
                + "    AND s.name = ? "
                + "    AND i.type = 1 " // CLUSTERED index
                + "    AND i.is_unique = 1 " // UNIQUE clustered index (required for indexed views)
                + "ORDER BY v.name";

        try {
            return jdbcOperations.query(sql, new Object[] {databaseName, actualSchemaName}, (rs, rowNum) -> {
                DBObjectIdentity identity = new DBObjectIdentity();
                identity.setSchemaName(fullSchemaName);
                identity.setName(rs.getString("view_name"));
                identity.setType(DBObjectType.MATERIALIZED_VIEW);
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
    public List<DBObjectIdentity> listAllMViewsLike(String mViewNameLike) {
        // SQL Server 使用索引视图（Indexed Views）
        // 在所有数据库中查询有聚集索引的视图
        List<DBObjectIdentity> results = new ArrayList<>();
        List<String> databasesAndSchemas = showDatabases();
        // 提取去重后的数据库名
        List<String> databaseNames = databasesAndSchemas.stream()
                .map(item -> SqlServerSchemaUtil.parseDatabaseAndSchema(item)[0])
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        String currentDb = null;
        try {
            currentDb = jdbcOperations.queryForObject("SELECT DB_NAME()", String.class);
        } catch (Exception e) {
            log.warn("Failed to get current database", e);
        }

        for (String databaseName : databaseNames) {
            try {
                if (currentDb == null || !databaseName.equals(currentDb)) {
                    switchDatabase(databaseName);
                }

                // 查询有聚集索引的视图（索引视图）
                StringBuilder sql = new StringBuilder();
                sql.append("SELECT DISTINCT ")
                        .append("    DB_NAME() AS database_name, ")
                        .append("    s.name AS schema_name, ")
                        .append("    v.name AS view_name ")
                        .append("FROM sys.views v ")
                        .append("INNER JOIN sys.schemas s ON v.schema_id = s.schema_id ")
                        .append("INNER JOIN sys.indexes i ON v.object_id = i.object_id ")
                        .append("WHERE i.type = 1 ") // CLUSTERED index
                        .append("    AND i.is_unique = 1 "); // UNIQUE clustered index

                List<Object> params = new ArrayList<>();
                if (StringUtils.isNotBlank(mViewNameLike)) {
                    sql.append(
                            " AND (v.name LIKE ? ESCAPE '\\' OR s.name LIKE ? ESCAPE '\\' OR DB_NAME() LIKE ? ESCAPE '\\')");
                    String likePattern = StringUtils.escapeLike(mViewNameLike);
                    params.add(likePattern);
                    params.add(likePattern);
                    params.add(likePattern);
                }
                sql.append(" ORDER BY s.name, v.name");

                List<DBObjectIdentity> views = jdbcOperations.query(sql.toString(), params.toArray(), (rs, rowNum) -> {
                    DBObjectIdentity identity = new DBObjectIdentity();
                    String dbName = rs.getString("database_name");
                    String schemaName = rs.getString("schema_name");
                    identity.setSchemaName(SqlServerSchemaUtil.buildFullSchemaName(dbName, schemaName));
                    identity.setName(rs.getString("view_name"));
                    identity.setType(DBObjectType.MATERIALIZED_VIEW);
                    return identity;
                });
                results.addAll(views);
            } catch (Exception e) {
                log.warn("Failed to list indexed views from database: " + databaseName, e);
            }
        }

        // 恢复原数据库上下文
        if (currentDb != null && !databaseNames.isEmpty() && !currentDb.equals(databaseNames.get(0))) {
            try {
                switchDatabase(currentDb);
            } catch (Exception e) {
                log.warn("Failed to restore database context", e);
            }
        }

        return results;
    }

    @Override
    public Boolean refreshMVData(DBMViewRefreshParameter parameter) {
        // SQL Server 的索引视图是自动维护的，不需要手动刷新
        // 当基础表的数据发生变化时，索引视图会自动更新
        // 因此这个方法在 SQL Server 中不需要执行任何操作
        log.info("SQL Server indexed views are automatically maintained, no manual refresh needed for: " +
                (parameter != null ? parameter.getMvName() : "unknown"));
        return Boolean.TRUE;
    }

    /**
     * 获取物化视图（索引视图）信息
     * <p>
     * <b>SQL Server 特殊处理：</b> schemaName 参数支持两种格式： 1. "database" - 只有数据库名，默认使用 dbo schema（兼容旧代码） 2.
     * "database.schema" - 完整的 database.schema 格式（从 showDatabases() 返回的格式） 为了统一抽象层，SQL Server 将
     * database.schema 作为 schema 处理，与 MySQL 保持一致。
     * </p>
     *
     * @param schemaName 数据库名或 database.schema 格式
     * @param mViewName 物化视图名
     * @return 物化视图信息
     */
    @Override
    public DBMaterializedView getMView(String schemaName, String mViewName) {
        // SQL Server 使用索引视图（Indexed Views）
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = SqlServerSchemaUtil.getDatabaseName(dbAndSchema[0], jdbcOperations);
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

        // 首先检查视图是否存在且有聚集索引（索引视图）
        String checkSql = "SELECT COUNT(*) "
                + "FROM sys.views v "
                + "INNER JOIN sys.schemas s ON v.schema_id = s.schema_id "
                + "INNER JOIN sys.indexes i ON v.object_id = i.object_id "
                + "WHERE DB_NAME() = ? "
                + "    AND s.name = ? "
                + "    AND v.name = ? "
                + "    AND i.type = 1 " // CLUSTERED index
                + "    AND i.is_unique = 1"; // UNIQUE clustered index

        Integer count = jdbcOperations.queryForObject(checkSql,
                new Object[] {databaseName, actualSchemaName, mViewName}, Integer.class);
        if (count == null || count == 0) {
            log.warn("Indexed view not found: " + schemaName + "." + mViewName);
            return null;
        }

        DBMaterializedView mView = new DBMaterializedView();
        mView.setName(mViewName);
        mView.setSchemaName(schemaName);

        try {
            // 获取视图的 DDL
            // 使用参数化查询避免SQL注入，OBJECT_ID需要对象名称字符串
            // 转义方括号以防止SQL注入：将 ] 替换为 ]]
            String escapedSchemaName = actualSchemaName.replace("]", "]]");
            String escapedViewName = mViewName.replace("]", "]]");
            String objectName = "[" + escapedSchemaName + "].[" + escapedViewName + "]";
            String ddlSql = "SELECT OBJECT_DEFINITION(OBJECT_ID(?, 'V')) AS view_definition";
            AtomicReference<String> viewDefinition = new AtomicReference<>();
            jdbcOperations.query(ddlSql, new Object[] {objectName}, rs -> {
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
                jdbcOperations.query(moduleSql, new Object[] {databaseName, mViewName, actualSchemaName}, rs -> {
                    viewDefinition.set(rs.getString("definition"));
                });
            }

            if (StringUtils.isNotBlank(viewDefinition.get())) {
                StringBuilder ddl = new StringBuilder();
                ddl.append("CREATE VIEW ");
                if (StringUtils.isNotEmpty(actualSchemaName)) {
                    ddl.append("[").append(actualSchemaName).append("].");
                }
                ddl.append("[").append(mViewName).append("] AS ").append(viewDefinition.get());
                mView.setDdl(ddl.toString());
            }

            // SQL Server 索引视图是自动维护的，没有刷新方法的概念
            // 但为了兼容性，设置一些默认值
            mView.setRefreshMethod(DBMaterializedViewRefreshMethod.REFRESH_FAST);
            mView.setEnableQueryRewrite(true); // 索引视图支持查询重写
            mView.setEnableQueryComputation(false);

            // 获取索引视图的列信息
            List<DBTableColumn> columns = listBasicViewColumns(schemaName, mViewName);
            mView.setColumns(columns);

            // 获取索引视图的索引信息
            List<DBTableIndex> indexes = listMViewIndexes(schemaName, mViewName);
            mView.setIndexes(indexes);

            // 获取索引视图的约束信息
            List<DBTableConstraint> constraints = listMViewConstraints(schemaName, mViewName);
            mView.setConstraints(constraints);

        } catch (Exception e) {
            log.warn("Failed to get indexed view details: " + schemaName + "." + mViewName, e);
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

        return mView;
    }

    @Override
    public List<DBTableConstraint> listMViewConstraints(String schemaName, String mViewName) {
        // SQL Server 索引视图可以有关键约束（主键、唯一约束）
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = SqlServerSchemaUtil.getDatabaseName(dbAndSchema[0], jdbcOperations);
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
                + "INNER JOIN sys.views v ON kc.parent_object_id = v.object_id "
                + "INNER JOIN sys.schemas s ON v.schema_id = s.schema_id "
                + "INNER JOIN sys.index_columns ic ON kc.parent_object_id = ic.object_id AND kc.unique_index_id = ic.index_id "
                + "INNER JOIN sys.columns c ON ic.object_id = c.object_id AND ic.column_id = c.column_id "
                + "WHERE DB_NAME() = ? "
                + "    AND s.name = ? "
                + "    AND v.name = ? "
                + "ORDER BY kc.name, ic.key_ordinal";

        try {
            Map<String, DBTableConstraint> constraintMap = new HashMap<>();
            AtomicInteger constraintCounter = new AtomicInteger(1);

            jdbcOperations.query(pkAndUniqueSql, new Object[] {databaseName, actualSchemaName, mViewName},
                    (rs, rowNum) -> {
                        String constraintName = rs.getString("constraint_name");
                        String constraintType = rs.getString("constraint_type");
                        String columnName = rs.getString("column_name");

                        DBTableConstraint constraint = constraintMap.get(constraintName);
                        if (constraint == null) {
                            constraint = new DBTableConstraint();
                            constraint.setName(constraintName);
                            constraint.setSchemaName(schemaName);
                            constraint.setTableName(mViewName);
                            constraint.setOwner(schemaName);
                            constraint.setOrdinalPosition(constraintCounter.getAndIncrement());

                            if ("PRIMARY_KEY_CONSTRAINT".equalsIgnoreCase(constraintType)) {
                                constraint.setType(DBConstraintType.PRIMARY_KEY);
                            } else if ("UNIQUE_CONSTRAINT".equalsIgnoreCase(constraintType)) {
                                constraint.setType(DBConstraintType.UNIQUE);
                            }

                            constraint.setColumnNames(new ArrayList<>());
                            constraintMap.put(constraintName, constraint);
                        }

                        constraint.getColumnNames().add(columnName);
                        return null;
                    });

            return new ArrayList<>(constraintMap.values());
        } catch (Exception e) {
            log.warn("Failed to list indexed view constraints for: " + schemaName + "." + mViewName, e);
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
    public List<DBMViewRefreshRecord> listMViewRefreshRecords(DBMViewRefreshRecordParam param) {
        // SQL Server 索引视图是自动维护的，没有刷新记录
        // 当基础表的数据发生变化时，索引视图会自动更新，不需要手动刷新
        // 因此返回空列表
        log.debug("SQL Server indexed views are automatically maintained, no refresh records available");
        return Collections.emptyList();
    }

    @Override
    public List<DBTableIndex> listMViewIndexes(String schemaName, String mViewName) {
        // SQL Server 索引视图可以有多个索引（聚集索引和非聚集索引）
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = SqlServerSchemaUtil.getDatabaseName(dbAndSchema[0], jdbcOperations);
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

        // 查询视图上的所有索引
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
                + "INNER JOIN sys.views v ON i.object_id = v.object_id "
                + "INNER JOIN sys.schemas s ON v.schema_id = s.schema_id "
                + "INNER JOIN sys.index_columns ic ON i.object_id = ic.object_id AND i.index_id = ic.index_id "
                + "INNER JOIN sys.columns c ON ic.object_id = c.object_id AND ic.column_id = c.column_id "
                + "WHERE DB_NAME() = ? "
                + "    AND s.name = ? "
                + "    AND v.name = ? "
                + "    AND i.type > 0 " // 排除堆（heap）
                + "ORDER BY i.name, ic.key_ordinal";

        try {
            Map<String, DBTableIndex> indexMap = new HashMap<>();
            AtomicInteger indexCounter = new AtomicInteger(1);
            jdbcOperations.query(sql, new Object[] {databaseName, actualSchemaName, mViewName}, (rs, rowNum) -> {
                String indexName = rs.getString("index_name");
                DBTableIndex index = indexMap.get(indexName);

                if (index == null) {
                    index = new DBTableIndex();
                    index.setSchemaName(schemaName);
                    index.setTableName(mViewName);
                    index.setName(indexName);
                    // ordinal_position应该是索引在视图中的序号，而不是列在索引中的序号
                    index.setOrdinalPosition(indexCounter.getAndIncrement());
                    index.setPrimary(rs.getBoolean("is_primary_key"));
                    index.setNonUnique(!rs.getBoolean("is_unique"));

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

                    index.setColumnNames(new ArrayList<>());
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

            return new ArrayList<>(indexMap.values());
        } catch (Exception e) {
            log.warn("Failed to list indexed view indexes for: " + schemaName + "." + mViewName, e);
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
    public List<DBVariable> showVariables() {
        // 返回所有变量：服务器配置 + 会话变量
        List<DBVariable> variables = new ArrayList<>();

        try {
            // 1. 获取服务器配置（全局变量）
            String configSql = "SELECT name, CAST(value AS VARCHAR(MAX)) AS value FROM sys.configurations";
            jdbcOperations.query(configSql, (rs, rowNum) -> {
                DBVariable variable = new DBVariable();
                variable.setName(rs.getString("name"));
                variable.setValue(rs.getString("value"));
                variables.add(variable);
                return null;
            });

            // 2. 获取会话变量（系统函数）
            String sessionSql = "SELECT "
                    + "    '@@VERSION' AS name, CAST(@@VERSION AS VARCHAR(MAX)) AS value "
                    + "UNION ALL SELECT "
                    + "    '@@SERVERNAME', CAST(@@SERVERNAME AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    '@@SERVICENAME', CAST(@@SERVICENAME AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    '@@LANGUAGE', CAST(@@LANGUAGE AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    '@@SPID', CAST(@@SPID AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    '@@TRANCOUNT', CAST(@@TRANCOUNT AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    '@@NESTLEVEL', CAST(@@NESTLEVEL AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    '@@OPTIONS', CAST(@@OPTIONS AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    '@@MAX_CONNECTIONS', CAST(@@MAX_CONNECTIONS AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    '@@MAX_PRECISION', CAST(@@MAX_PRECISION AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    '@@TEXTSIZE', CAST(@@TEXTSIZE AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    '@@DBTS', CAST(@@DBTS AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    '@@LANGID', CAST(@@LANGID AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    '@@LOCK_TIMEOUT', CAST(@@LOCK_TIMEOUT AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    '@@DATEFIRST', CAST(@@DATEFIRST AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    '@@DATEFORMAT', CAST(@@DATEFORMAT AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    'DB_NAME()', CAST(DB_NAME() AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    'USER_NAME()', CAST(USER_NAME() AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    'SYSTEM_USER', CAST(SYSTEM_USER AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    'CURRENT_USER', CAST(CURRENT_USER AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    'SESSION_USER', CAST(SESSION_USER AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    'USER', CAST(USER AS VARCHAR(MAX))";

            jdbcOperations.query(sessionSql, (rs, rowNum) -> {
                DBVariable variable = new DBVariable();
                variable.setName(rs.getString("name"));
                variable.setValue(rs.getString("value"));
                variables.add(variable);
                return null;
            });
        } catch (Exception e) {
            log.warn("Failed to show variables", e);
        }

        return variables;
    }

    @Override
    public List<DBVariable> showSessionVariables() {
        // 返回会话级别的变量（系统函数）
        List<DBVariable> variables = new ArrayList<>();

        try {
            String sql = "SELECT "
                    + "    '@@VERSION' AS name, CAST(@@VERSION AS VARCHAR(MAX)) AS value "
                    + "UNION ALL SELECT "
                    + "    '@@SERVERNAME', CAST(@@SERVERNAME AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    '@@SERVICENAME', CAST(@@SERVICENAME AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    '@@LANGUAGE', CAST(@@LANGUAGE AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    '@@SPID', CAST(@@SPID AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    '@@TRANCOUNT', CAST(@@TRANCOUNT AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    '@@NESTLEVEL', CAST(@@NESTLEVEL AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    '@@OPTIONS', CAST(@@OPTIONS AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    '@@MAX_CONNECTIONS', CAST(@@MAX_CONNECTIONS AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    '@@MAX_PRECISION', CAST(@@MAX_PRECISION AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    '@@TEXTSIZE', CAST(@@TEXTSIZE AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    '@@DBTS', CAST(@@DBTS AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    '@@LANGID', CAST(@@LANGID AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    '@@LOCK_TIMEOUT', CAST(@@LOCK_TIMEOUT AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    '@@DATEFIRST', CAST(@@DATEFIRST AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    '@@DATEFORMAT', CAST(@@DATEFORMAT AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    'DB_NAME()', CAST(DB_NAME() AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    'USER_NAME()', CAST(USER_NAME() AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    'SYSTEM_USER', CAST(SYSTEM_USER AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    'CURRENT_USER', CAST(CURRENT_USER AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    'SESSION_USER', CAST(SESSION_USER AS VARCHAR(MAX)) "
                    + "UNION ALL SELECT "
                    + "    'USER', CAST(USER AS VARCHAR(MAX))";

            jdbcOperations.query(sql, (rs, rowNum) -> {
                DBVariable variable = new DBVariable();
                variable.setName(rs.getString("name"));
                variable.setValue(rs.getString("value"));
                variables.add(variable);
                return null;
            });
        } catch (Exception e) {
            log.warn("Failed to show session variables", e);
        }

        return variables;
    }

    @Override
    public List<DBVariable> showGlobalVariables() {
        // 返回服务器级别的配置（全局变量）
        List<DBVariable> variables = new ArrayList<>();

        try {
            String sql = "SELECT name, CAST(value AS VARCHAR(MAX)) AS value FROM sys.configurations ORDER BY name";
            jdbcOperations.query(sql, (rs, rowNum) -> {
                DBVariable variable = new DBVariable();
                variable.setName(rs.getString("name"));
                variable.setValue(rs.getString("value"));
                variables.add(variable);
                return null;
            });
        } catch (Exception e) {
            log.warn("Failed to show global variables", e);
        }

        return variables;
    }

    @Override
    public List<String> showCharset() {
        // SQL Server 中字符集信息可以从 collation 中提取
        String sql = "SELECT DISTINCT "
                + "    SUBSTRING(name, 1, CHARINDEX('_', name) - 1) AS charset "
                + "FROM sys.fn_helpcollations() "
                + "WHERE SUBSTRING(name, 1, CHARINDEX('_', name) - 1) IS NOT NULL";
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

    /**
     * 列出指定 schema 下的函数
     * <p>
     * <b>SQL Server 特殊处理：</b> schemaName 参数支持两种格式： 1. "database" - 只有数据库名，默认使用 dbo schema（兼容旧代码） 2.
     * "database.schema" - 完整的 database.schema 格式（从 showDatabases() 返回的格式） 为了统一抽象层，SQL Server 将
     * database.schema 作为 schema 处理，与 MySQL 保持一致。
     * </p>
     *
     * @param schemaName 数据库名或 database.schema 格式
     * @return 函数对象列表
     */
    @Override
    public List<DBPLObjectIdentity> listFunctions(String schemaName) {
        // 解析 database.schema 格式
        String[] dbAndSchema;
        try {
            dbAndSchema = parseDatabaseAndSchema(schemaName);
        } catch (IllegalArgumentException e) {
            // 如果无法解析 schemaName（包括 null 且无法获取当前数据库的情况），返回空列表
            log.warn("Failed to parse schemaName: " + schemaName, e);
            return Collections.emptyList();
        }
        String databaseName = SqlServerSchemaUtil.getDatabaseName(dbAndSchema[0], jdbcOperations);
        String actualSchemaName = dbAndSchema[1];

        // 构建完整的 schemaName（database.schema 格式）
        String fullSchemaName = SqlServerSchemaUtil.buildFullSchemaName(databaseName, actualSchemaName);

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
                identity.setSchemaName(fullSchemaName);
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
        String[] dbAndSchema;
        try {
            dbAndSchema = parseDatabaseAndSchema(schemaName);
        } catch (IllegalArgumentException e) {
            // 如果无法解析 schemaName（包括 null 且无法获取当前数据库的情况），返回空列表
            log.warn("Failed to parse schemaName: " + schemaName, e);
            return Collections.emptyList();
        }
        String databaseName = SqlServerSchemaUtil.getDatabaseName(dbAndSchema[0], jdbcOperations);
        String actualSchemaName = dbAndSchema[1];

        // 构建完整的 schemaName（database.schema 格式）
        String fullSchemaName = SqlServerSchemaUtil.buildFullSchemaName(databaseName, actualSchemaName);

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
                identity.setSchemaName(fullSchemaName);
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
        // SQL Server 不支持 Oracle 风格的包（Package）概念
        // Oracle 的包是用于组织存储过程、函数、变量等的容器
        // SQL Server 中没有对应的概念，只能通过 schema 和命名约定来组织对象
        return Collections.emptyList();
    }

    @Override
    public List<DBPLObjectIdentity> listPackageBodies(String schemaName) {
        // SQL Server 不支持 Oracle 风格的包体（Package Body）概念
        // 包体是 Oracle 包中实现部分，SQL Server 中没有对应的概念
        return Collections.emptyList();
    }

    /**
     * 列出指定 schema 下的触发器
     * <p>
     * <b>SQL Server 特殊处理：</b> schemaName 参数支持两种格式： 1. "database" - 只有数据库名，默认使用 dbo schema（兼容旧代码） 2.
     * "database.schema" - 完整的 database.schema 格式（从 showDatabases() 返回的格式） 为了统一抽象层，SQL Server 将
     * database.schema 作为 schema 处理，与 MySQL 保持一致。
     * </p>
     *
     * @param schemaName 数据库名或 database.schema 格式
     * @return 触发器对象列表
     */
    @Override
    public List<DBPLObjectIdentity> listTriggers(String schemaName) {
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = SqlServerSchemaUtil.getDatabaseName(dbAndSchema[0], jdbcOperations);
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

        // SQL Server 支持触发器，包括 DML 触发器（表/视图）和 DDL 触发器
        // 这里查询 DML 触发器（与表或视图关联的触发器）
        String sql = "SELECT "
                + "    t.name AS trigger_name, "
                + "    s.name AS schema_name, "
                + "    t.is_disabled, "
                + "    t.is_instead_of_trigger, "
                + "    OBJECT_NAME(t.parent_id) AS parent_object_name "
                + "FROM sys.triggers t "
                + "INNER JOIN sys.objects o ON t.parent_id = o.object_id "
                + "INNER JOIN sys.schemas s ON o.schema_id = s.schema_id "
                + "WHERE DB_NAME() = ? "
                + "    AND s.name = ? "
                + "    AND t.parent_class = 1 " // 1 = DML triggers on tables/views
                + "ORDER BY t.name ASC";

        try {
            return jdbcOperations.query(sql, new Object[] {databaseName, actualSchemaName}, (rs, rowNum) -> {
                DBPLObjectIdentity trigger = new DBPLObjectIdentity();
                trigger.setSchemaName(schemaName);
                trigger.setName(rs.getString("trigger_name"));
                trigger.setType(DBObjectType.TRIGGER);
                // SQL Server 中 is_disabled = 0 表示启用，1 表示禁用
                trigger.setEnable(!rs.getBoolean("is_disabled"));
                // 设置状态信息
                String status = rs.getBoolean("is_disabled") ? "DISABLED" : "ENABLED";
                trigger.setStatus(status);
                return trigger;
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

    /**
     * 列出指定 schema 下的类型
     * <p>
     * <b>SQL Server 特殊处理：</b> schemaName 参数支持两种格式： 1. "database" - 只有数据库名，默认使用 dbo schema（兼容旧代码） 2.
     * "database.schema" - 完整的 database.schema 格式（从 showDatabases() 返回的格式） 为了统一抽象层，SQL Server 将
     * database.schema 作为 schema 处理，与 MySQL 保持一致。
     * </p>
     *
     * @param schemaName 数据库名或 database.schema 格式
     * @return 类型对象列表
     */
    @Override
    public List<DBPLObjectIdentity> listTypes(String schemaName) {
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = SqlServerSchemaUtil.getDatabaseName(dbAndSchema[0], jdbcOperations);
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

        // SQL Server 支持用户定义类型（UDT），包括标量类型和表类型
        // 通过 sys.types 查询，过滤掉系统类型（is_user_defined = 1）
        String sql = "SELECT "
                + "    t.name AS type_name, "
                + "    s.name AS schema_name, "
                + "    t.is_table_type, "
                + "    CASE WHEN t.is_table_type = 1 THEN 'TABLE_TYPE' ELSE 'SCALAR_TYPE' END AS type_kind "
                + "FROM sys.types t "
                + "INNER JOIN sys.schemas s ON t.schema_id = s.schema_id "
                + "WHERE DB_NAME() = ? "
                + "    AND s.name = ? "
                + "    AND t.is_user_defined = 1 " // 只查询用户定义类型
                + "    AND t.system_type_id != t.user_type_id " // 排除系统类型的别名
                + "ORDER BY t.name ASC";

        try {
            return jdbcOperations.query(sql, new Object[] {databaseName, actualSchemaName}, (rs, rowNum) -> {
                DBPLObjectIdentity type = new DBPLObjectIdentity();
                type.setSchemaName(schemaName);
                type.setName(rs.getString("type_name"));
                type.setType(DBObjectType.TYPE);
                // 设置状态为 VALID（SQL Server 中类型没有无效状态的概念）
                type.setStatus("VALID");
                return type;
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

    /**
     * 列出指定 schema 下的序列
     * <p>
     * <b>SQL Server 特殊处理：</b> schemaName 参数支持两种格式： 1. "database" - 只有数据库名，默认使用 dbo schema（兼容旧代码） 2.
     * "database.schema" - 完整的 database.schema 格式（从 showDatabases() 返回的格式） 为 null 或空时返回所有数据库下的序列。
     * 为了统一抽象层，SQL Server 将 database.schema 作为 schema 处理，与 MySQL 保持一致。
     * </p>
     *
     * @param schemaName 数据库名或 database.schema 格式，为 null 或空时返回所有序列
     * @return 序列对象列表
     */
    @Override
    public List<DBObjectIdentity> listSequences(String schemaName) {
        if (StringUtils.isBlank(schemaName)) {
            return listAllSequences(null);
        }
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = SqlServerSchemaUtil.getDatabaseName(dbAndSchema[0], jdbcOperations);
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

        // SQL Server 从 2012 版本开始支持序列（SEQUENCE）对象
        // 使用 sys.sequences 系统视图查询
        String sql = "SELECT "
                + "    seq.name AS sequence_name, "
                + "    s.name AS schema_name "
                + "FROM sys.sequences seq "
                + "INNER JOIN sys.schemas s ON seq.schema_id = s.schema_id "
                + "WHERE DB_NAME() = ? "
                + "    AND s.name = ? "
                + "ORDER BY seq.name ASC";

        try {
            return jdbcOperations.query(sql, new Object[] {databaseName, actualSchemaName}, (rs, rowNum) -> {
                DBObjectIdentity sequence = new DBObjectIdentity();
                sequence.setSchemaName(schemaName);
                sequence.setName(rs.getString("sequence_name"));
                sequence.setType(DBObjectType.SEQUENCE);
                return sequence;
            });
        } catch (BadSqlGrammarException e) {
            // 如果 sys.sequences 不存在（SQL Server 2008 及更早版本），返回空列表
            if (StringUtils.containsIgnoreCase(e.getMessage(), "Invalid object name") ||
                    StringUtils.containsIgnoreCase(e.getMessage(), "Invalid schema") ||
                    StringUtils.containsIgnoreCase(e.getMessage(), "sequence")) {
                log.debug("Sequences not supported in this SQL Server version or schema not found");
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

    private List<DBObjectIdentity> listAllSequences(String sequenceNameLike) {
        List<DBObjectIdentity> results = new ArrayList<>();
        List<String> databasesAndSchemas = showDatabases();
        // 提取去重后的数据库名
        List<String> databaseNames = databasesAndSchemas.stream()
                .map(item -> SqlServerSchemaUtil.parseDatabaseAndSchema(item)[0])
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        String currentDb = null;
        try {
            currentDb = jdbcOperations.queryForObject("SELECT DB_NAME()", String.class);
        } catch (Exception e) {
            log.warn("Failed to get current database", e);
        }

        for (String databaseName : databaseNames) {
            try {
                if (currentDb == null || !databaseName.equals(currentDb)) {
                    switchDatabase(databaseName);
                }

                StringBuilder sql = new StringBuilder();
                sql.append("SELECT DB_NAME() AS database_name, s.name AS schema_name, seq.name AS sequence_name ")
                        .append("FROM sys.sequences seq ")
                        .append("INNER JOIN sys.schemas s ON seq.schema_id = s.schema_id ");

                List<Object> params = new ArrayList<>();
                if (StringUtils.isNotBlank(sequenceNameLike)) {
                    sql.append(
                            " WHERE (seq.name LIKE ? ESCAPE '\\' OR s.name LIKE ? ESCAPE '\\' OR DB_NAME() LIKE ? ESCAPE '\\')");
                    String likePattern = StringUtils.escapeLike(sequenceNameLike);
                    params.add(likePattern);
                    params.add(likePattern);
                    params.add(likePattern);
                }
                sql.append(" ORDER BY s.name, seq.name");

                List<DBObjectIdentity> sequences =
                        jdbcOperations.query(sql.toString(), params.toArray(), (rs, rowNum) -> {
                            DBObjectIdentity identity = new DBObjectIdentity();
                            String dbName = rs.getString("database_name");
                            String schemaName = rs.getString("schema_name");
                            identity.setSchemaName(SqlServerSchemaUtil.buildFullSchemaName(dbName, schemaName));
                            identity.setName(rs.getString("sequence_name"));
                            identity.setType(DBObjectType.SEQUENCE);
                            return identity;
                        });
                results.addAll(sequences);
            } catch (Exception e) {
                // 如果 sys.sequences 不存在，说明该版本不支持，跳过
                log.debug("Failed to list sequences from database: " + databaseName);
            }
        }

        // 恢复原数据库上下文
        if (currentDb != null && !databaseNames.isEmpty()
                && !currentDb.equals(databaseNames.get(databaseNames.size() - 1))) {
            try {
                switchDatabase(currentDb);
            } catch (Exception e) {
                log.warn("Failed to restore database context", e);
            }
        }

        return results;
    }

    /**
     * 列出指定 schema 下的同义词
     * <p>
     * <b>SQL Server 特殊处理：</b> schemaName 参数支持两种格式： 1. "database" - 只有数据库名，默认使用 dbo schema（兼容旧代码） 2.
     * "database.schema" - 完整的 database.schema 格式（从 showDatabases() 返回的格式） 为了统一抽象层，SQL Server 将
     * database.schema 作为 schema 处理，与 MySQL 保持一致。
     * </p>
     *
     * @param schemaName 数据库名或 database.schema 格式
     * @param synonymType 同义词类型
     * @return 同义词对象列表
     */
    @Override
    public List<DBObjectIdentity> listSynonyms(String schemaName, DBSynonymType synonymType) {
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = SqlServerSchemaUtil.getDatabaseName(dbAndSchema[0], jdbcOperations);
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

        // SQL Server 支持同义词（Synonym），可以通过 sys.synonyms 查询
        // SQL Server 中的同义词都是 schema 级别的，没有 PUBLIC 同义词的概念
        // 但为了兼容接口，我们根据 synonymType 参数进行过滤
        String sql;
        if (DBSynonymType.PUBLIC.equals(synonymType)) {
            // SQL Server 没有 PUBLIC 同义词，返回空列表
            return Collections.emptyList();
        } else if (DBSynonymType.COMMON.equals(synonymType)) {
            sql = "SELECT "
                    + "    syn.name AS synonym_name, "
                    + "    s.name AS schema_name "
                    + "FROM sys.synonyms syn "
                    + "INNER JOIN sys.schemas s ON syn.schema_id = s.schema_id "
                    + "WHERE DB_NAME() = ? "
                    + "    AND s.name = ? "
                    + "ORDER BY syn.name ASC";
        } else {
            throw new UnsupportedOperationException("Not supported Synonym type: " + synonymType);
        }

        try {
            return jdbcOperations.query(sql, new Object[] {databaseName, actualSchemaName}, (rs, rowNum) -> {
                DBObjectIdentity synonym = new DBObjectIdentity();
                synonym.setSchemaName(schemaName);
                synonym.setName(rs.getString("synonym_name"));
                synonym.setType(DBObjectType.SYNONYM);
                return synonym;
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
    public Map<String, List<DBTableColumn>> listTableColumns(String schemaName, List<String> tableNames) {
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = SqlServerSchemaUtil.getDatabaseName(dbAndSchema[0], jdbcOperations);
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
            return Collections.emptyMap();
        }

        if (tableNames == null || tableNames.isEmpty()) {
            return Collections.emptyMap();
        }

        // 构建 IN 子句
        StringBuilder inClause = new StringBuilder();
        List<Object> params = new ArrayList<>();
        params.add(databaseName);
        params.add(actualSchemaName);
        for (int i = 0; i < tableNames.size(); i++) {
            if (i > 0) {
                inClause.append(",");
            }
            inClause.append("?");
            params.add(tableNames.get(i));
        }

        String sql = "SELECT "
                + "    tb.name AS table_name, "
                + "    c.column_id AS ordinal_position, "
                + "    c.name AS column_name, "
                + "    t.name AS data_type, "
                + "    CASE "
                + "        WHEN t.name IN ('nvarchar', 'nchar') "
                + "        THEN t.name + '(' + CASE WHEN c.max_length = -1 THEN 'MAX' ELSE CAST(c.max_length / 2 AS VARCHAR) END + ')' "
                + "        WHEN t.name IN ('varchar', 'char', 'binary', 'varbinary') "
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
                + "    t.name AS base_type_name, "
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
                + "    AND s.name = ? "
                + "    AND tb.name IN (" + inClause.toString() + ") "
                + "ORDER BY tb.name, c.column_id";

        try {
            List<DBTableColumn> columns = jdbcOperations.query(sql, params.toArray(), (rs, rowNum) -> {
                DBTableColumn column = new DBTableColumn();
                column.setSchemaName(schemaName);
                String tableName = rs.getString("table_name");
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
                    String baseTypeName = rs.getString("base_type_name");
                    // 对于nvarchar/nchar，max_length存储的是字节数（字符数*2），需要转换为字符数
                    if (maxLength > 0 && maxLength != -1) {
                        if ("nvarchar".equalsIgnoreCase(baseTypeName) || "nchar".equalsIgnoreCase(baseTypeName)) {
                            // Unicode类型：字节数除以2得到字符数
                            column.setMaxLength((long) (maxLength / 2));
                        } else {
                            column.setMaxLength((long) maxLength);
                        }
                    }
                }

                column.setNullable(rs.getBoolean("is_nullable"));

                String defaultValue = rs.getString("column_default");
                if (StringUtils.isNotBlank(defaultValue)) {
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
            return columns.stream()
                    .filter(col -> col.getTableName() != null)
                    .collect(Collectors.groupingBy(DBTableColumn::getTableName));
        } catch (Exception e) {
            log.warn("Failed to list table columns for schema: " + schemaName, e);
            return Collections.emptyMap();
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

    /**
     * 列出指定表的列信息
     * <p>
     * <b>SQL Server 特殊处理：</b> schemaName 参数支持两种格式： 1. "database" - 只有数据库名，默认使用 dbo schema（兼容旧代码） 2.
     * "database.schema" - 完整的 database.schema 格式（从 showDatabases() 返回的格式） 为了统一抽象层，SQL Server 将
     * database.schema 作为 schema 处理，与 MySQL 保持一致。
     * </p>
     *
     * @param schemaName 数据库名或 database.schema 格式
     * @param tableName 表名
     * @return 列信息列表
     */
    @Override
    public List<DBTableColumn> listTableColumns(String schemaName, String tableName) {
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = SqlServerSchemaUtil.getDatabaseName(dbAndSchema[0], jdbcOperations);
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
                + "        WHEN t.name IN ('nvarchar', 'nchar') "
                + "        THEN t.name + '(' + CASE WHEN c.max_length = -1 THEN 'MAX' ELSE CAST(c.max_length / 2 AS VARCHAR) END + ')' "
                + "        WHEN t.name IN ('varchar', 'char', 'binary', 'varbinary') "
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
                + "    t.name AS base_type_name, "
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
                    String baseTypeName = rs.getString("base_type_name");
                    // 对于nvarchar/nchar，max_length存储的是字节数（字符数*2），需要转换为字符数
                    if (maxLength > 0 && maxLength != -1) {
                        if ("nvarchar".equalsIgnoreCase(baseTypeName) || "nchar".equalsIgnoreCase(baseTypeName)) {
                            // Unicode类型：字节数除以2得到字符数
                            column.setMaxLength((long) (maxLength / 2));
                        } else {
                            column.setMaxLength((long) maxLength);
                        }
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

    /**
     * 列出指定 schema 下所有表的基本列信息
     * <p>
     * <b>SQL Server 特殊处理：</b> schemaName 参数支持两种格式： 1. "database" - 只有数据库名，默认使用 dbo schema（兼容旧代码） 2.
     * "database.schema" - 完整的 database.schema 格式（从 showDatabases() 返回的格式） 为了统一抽象层，SQL Server 将
     * database.schema 作为 schema 处理，与 MySQL 保持一致。
     * </p>
     *
     * @param schemaName 数据库名或 database.schema 格式
     * @return 表名到列列表的映射
     */
    @Override
    public Map<String, List<DBTableColumn>> listBasicTableColumns(String schemaName) {
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = SqlServerSchemaUtil.getDatabaseName(dbAndSchema[0], jdbcOperations);
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
            return Collections.emptyMap();
        }

        String sql = "SELECT "
                + "    tb.name AS table_name, "
                + "    c.column_id AS ordinal_position, "
                + "    c.name AS column_name, "
                + "    t.name AS data_type, "
                + "    ISNULL(ep.value, '') AS column_comment "
                + "FROM sys.columns c "
                + "INNER JOIN sys.types t ON c.user_type_id = t.user_type_id "
                + "INNER JOIN sys.tables tb ON c.object_id = tb.object_id "
                + "INNER JOIN sys.schemas s ON tb.schema_id = s.schema_id "
                + "LEFT JOIN sys.extended_properties ep ON ep.major_id = c.object_id "
                + "    AND ep.minor_id = c.column_id "
                + "    AND ep.name = 'MS_Description' "
                + "WHERE DB_NAME() = ? "
                + "    AND s.name = ? "
                + "ORDER BY tb.name, c.column_id";

        try {
            List<DBTableColumn> columns = jdbcOperations.query(sql, new Object[] {databaseName, actualSchemaName},
                    (rs, rowNum) -> {
                        DBTableColumn column = new DBTableColumn();
                        column.setSchemaName(schemaName);
                        column.setTableName(rs.getString("table_name"));
                        column.setName(rs.getString("column_name"));
                        column.setTypeName(rs.getString("data_type"));
                        String comment = rs.getString("column_comment");
                        if (StringUtils.isNotBlank(comment)) {
                            column.setComment(comment);
                        }
                        return column;
                    });
            return columns.stream()
                    .filter(col -> col.getTableName() != null)
                    .collect(Collectors.groupingBy(DBTableColumn::getTableName));
        } catch (Exception e) {
            log.warn("Failed to list basic table columns for schema: " + schemaName, e);
            return Collections.emptyMap();
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
    public List<DBTableColumn> listBasicTableColumns(String schemaName, String tableName) {
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = SqlServerSchemaUtil.getDatabaseName(dbAndSchema[0], jdbcOperations);
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
                + "    ISNULL(ep.value, '') AS column_comment "
                + "FROM sys.columns c "
                + "INNER JOIN sys.types t ON c.user_type_id = t.user_type_id "
                + "INNER JOIN sys.tables tb ON c.object_id = tb.object_id "
                + "INNER JOIN sys.schemas s ON tb.schema_id = s.schema_id "
                + "LEFT JOIN sys.extended_properties ep ON ep.major_id = c.object_id "
                + "    AND ep.minor_id = c.column_id "
                + "    AND ep.name = 'MS_Description' "
                + "WHERE DB_NAME() = ? "
                + "    AND tb.name = ? "
                + "    AND s.name = ? "
                + "ORDER BY c.column_id";

        try {
            return jdbcOperations.query(sql, new Object[] {databaseName, tableName, actualSchemaName},
                    (rs, rowNum) -> {
                        DBTableColumn column = new DBTableColumn();
                        column.setSchemaName(schemaName);
                        column.setTableName(tableName);
                        column.setName(rs.getString("column_name"));
                        column.setTypeName(rs.getString("data_type"));
                        String comment = rs.getString("column_comment");
                        if (StringUtils.isNotBlank(comment)) {
                            column.setComment(comment);
                        }
                        return column;
                    });
        } catch (Exception e) {
            log.warn("Failed to list basic table columns for table: " + schemaName + "." + tableName, e);
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
    public Map<String, List<DBTableColumn>> listBasicViewColumns(String schemaName) {
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = SqlServerSchemaUtil.getDatabaseName(dbAndSchema[0], jdbcOperations);
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
            return Collections.emptyMap();
        }

        String sql = "SELECT "
                + "    v.name AS view_name, "
                + "    c.column_id AS ordinal_position, "
                + "    c.name AS column_name, "
                + "    t.name AS data_type, "
                + "    ISNULL(ep.value, '') AS column_comment "
                + "FROM sys.columns c "
                + "INNER JOIN sys.types t ON c.user_type_id = t.user_type_id "
                + "INNER JOIN sys.views v ON c.object_id = v.object_id "
                + "INNER JOIN sys.schemas s ON v.schema_id = s.schema_id "
                + "LEFT JOIN sys.extended_properties ep ON ep.major_id = c.object_id "
                + "    AND ep.minor_id = c.column_id "
                + "    AND ep.name = 'MS_Description' "
                + "WHERE DB_NAME() = ? "
                + "    AND s.name = ? "
                + "ORDER BY v.name, c.column_id";

        try {
            List<DBTableColumn> columns = jdbcOperations.query(sql, new Object[] {databaseName, actualSchemaName},
                    (rs, rowNum) -> {
                        DBTableColumn column = new DBTableColumn();
                        column.setSchemaName(schemaName);
                        column.setTableName(rs.getString("view_name"));
                        column.setName(rs.getString("column_name"));
                        column.setTypeName(rs.getString("data_type"));
                        String comment = rs.getString("column_comment");
                        if (StringUtils.isNotBlank(comment)) {
                            column.setComment(comment);
                        }
                        return column;
                    });
            return columns.stream()
                    .filter(col -> col.getTableName() != null)
                    .collect(Collectors.groupingBy(DBTableColumn::getTableName));
        } catch (Exception e) {
            log.warn("Failed to list basic view columns for schema: " + schemaName, e);
            return Collections.emptyMap();
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

    /**
     * 列出指定视图的基本列信息
     * <p>
     * <b>SQL Server 特殊处理：</b> schemaName 参数支持两种格式： 1. "database" - 只有数据库名，默认使用 dbo schema（兼容旧代码） 2.
     * "database.schema" - 完整的 database.schema 格式（从 showDatabases() 返回的格式） 为了统一抽象层，SQL Server 将
     * database.schema 作为 schema 处理，与 MySQL 保持一致。
     * </p>
     *
     * @param schemaName 数据库名或 database.schema 格式
     * @param viewName 视图名
     * @return 列信息列表
     */
    @Override
    public List<DBTableColumn> listBasicViewColumns(String schemaName, String viewName) {
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = SqlServerSchemaUtil.getDatabaseName(dbAndSchema[0], jdbcOperations);
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

        try {
            return jdbcOperations.query(sql, new Object[] {databaseName, viewName, actualSchemaName},
                    (rs, rowNum) -> {
                        DBTableColumn column = new DBTableColumn();
                        column.setSchemaName(schemaName);
                        column.setTableName(viewName);
                        column.setName(rs.getString("column_name"));
                        column.setTypeName(rs.getString("data_type"));
                        String comment = rs.getString("column_comment");
                        if (StringUtils.isNotBlank(comment)) {
                            column.setComment(comment);
                        }
                        return column;
                    });
        } catch (Exception e) {
            log.warn("Failed to list basic view columns for view: " + schemaName + "." + viewName, e);
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

    /**
     * SQL Server 不支持标准的外部表（External Table）功能。 虽然 SQL Server 2016+ 通过 PolyBase 提供了外部表支持，但需要： 1. 安装和配置
     * PolyBase 服务 2. 创建外部数据源（External Data Source） 3. 配置安全凭据 这不是 SQL Server 的标准功能，且 Azure SQL Database
     * 通常不支持。 因此，本方法返回空集合，与 {@link #listExternalTables(String, String)} 和
     * {@link #isExternalTable(String, String)} 的实现保持一致。
     */
    @Override
    public Map<String, List<DBTableColumn>> listBasicExternalTableColumns(String schemaName) {
        return Collections.emptyMap();
    }

    /**
     * SQL Server 不支持标准的外部表（External Table）功能。 虽然 SQL Server 2016+ 通过 PolyBase 提供了外部表支持，但需要： 1. 安装和配置
     * PolyBase 服务 2. 创建外部数据源（External Data Source） 3. 配置安全凭据 这不是 SQL Server 的标准功能，且 Azure SQL Database
     * 通常不支持。 因此，本方法返回空集合，与 {@link #listExternalTables(String, String)} 和
     * {@link #isExternalTable(String, String)} 的实现保持一致。
     */
    @Override
    public List<DBTableColumn> listBasicExternalTableColumns(String schemaName, String externalTableName) {
        return Collections.emptyList();
    }

    /**
     * SQL Server 不支持标准的物化视图（Materialized View）功能。 SQL Server 使用"索引视图"（Indexed Views）来实现类似功能，但索引视图： 1.
     * 不是独立的数据库对象类型，而是带有唯一聚集索引的视图 2. 在系统目录中仍然被当作普通视图（VIEW）处理 3. 通过 sys.views 和 information_schema.views
     * 查询，而不是独立的物化视图表 因此，本方法返回空集合，与 {@link #listMViews(String)} 和 {@link #getMView(String, String)}
     * 的实现保持一致。 如果需要查询索引视图的列信息，应使用 {@link #listBasicViewColumns(String)} 方法。
     */
    @Override
    public Map<String, List<DBTableColumn>> listBasicMViewColumns(String schemaName) {
        return Collections.emptyMap();
    }

    /**
     * SQL Server 不支持标准的物化视图（Materialized View）功能。 SQL Server 使用"索引视图"（Indexed Views）来实现类似功能，但索引视图： 1.
     * 不是独立的数据库对象类型，而是带有唯一聚集索引的视图 2. 在系统目录中仍然被当作普通视图（VIEW）处理 3. 通过 sys.views 和 information_schema.views
     * 查询，而不是独立的物化视图表 因此，本方法返回空集合，与 {@link #listMViews(String)} 和 {@link #getMView(String, String)}
     * 的实现保持一致。 如果需要查询索引视图的列信息，应使用 {@link #listBasicViewColumns(String, String)} 方法。
     */
    @Override
    public List<DBTableColumn> listBasicMViewColumns(String schemaName, String externalTableName) {
        return Collections.emptyList();
    }

    @Override
    public Map<String, List<DBTableColumn>> listBasicColumnsInfo(String schemaName) {
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = SqlServerSchemaUtil.getDatabaseName(dbAndSchema[0], jdbcOperations);
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
            return Collections.emptyMap();
        }

        // 查询表和视图的列信息（只包含基本字段：schema, table, column name）
        String sql = "SELECT "
                + "    tb.name AS table_name, "
                + "    c.name AS column_name "
                + "FROM sys.columns c "
                + "INNER JOIN sys.tables tb ON c.object_id = tb.object_id "
                + "INNER JOIN sys.schemas s ON tb.schema_id = s.schema_id "
                + "WHERE DB_NAME() = ? "
                + "    AND s.name = ? "
                + "UNION ALL "
                + "SELECT "
                + "    v.name AS table_name, "
                + "    c.name AS column_name "
                + "FROM sys.columns c "
                + "INNER JOIN sys.views v ON c.object_id = v.object_id "
                + "INNER JOIN sys.schemas s ON v.schema_id = s.schema_id "
                + "WHERE DB_NAME() = ? "
                + "    AND s.name = ? "
                + "ORDER BY table_name, column_name";

        try {
            List<DBTableColumn> columns = jdbcOperations.query(sql,
                    new Object[] {databaseName, actualSchemaName, databaseName, actualSchemaName},
                    (rs, rowNum) -> {
                        DBTableColumn column = new DBTableColumn();
                        column.setSchemaName(schemaName);
                        column.setTableName(rs.getString("table_name"));
                        column.setName(rs.getString("column_name"));
                        return column;
                    });
            return columns.stream()
                    .filter(col -> col.getTableName() != null)
                    .collect(Collectors.groupingBy(DBTableColumn::getTableName));
        } catch (Exception e) {
            log.warn("Failed to list basic columns info for schema: " + schemaName, e);
            return Collections.emptyMap();
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

    /**
     * 列出指定 schema 下所有表的索引信息
     * <p>
     * <b>SQL Server 特殊处理：</b> schemaName 参数支持两种格式： 1. "database" - 只有数据库名，默认使用 dbo schema（兼容旧代码） 2.
     * "database.schema" - 完整的 database.schema 格式（从 showDatabases() 返回的格式） 为了统一抽象层，SQL Server 将
     * database.schema 作为 schema 处理，与 MySQL 保持一致。
     * </p>
     *
     * @param schemaName 数据库名或 database.schema 格式
     * @return 表名到索引列表的映射
     */
    @Override
    public Map<String, List<DBTableIndex>> listTableIndexes(String schemaName) {
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = SqlServerSchemaUtil.getDatabaseName(dbAndSchema[0], jdbcOperations);
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
            return Collections.emptyMap();
        }

        String sql = "SELECT "
                + "    t.name AS table_name, "
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
                + "    AND s.name = ? "
                + "    AND i.type > 0 " // 排除堆（heap）
                + "ORDER BY t.name, i.name, ic.key_ordinal";

        try {
            Map<String, Map<String, DBTableIndex>> tableIndexMap = new java.util.LinkedHashMap<>();
            Map<String, AtomicInteger> tableIndexCounterMap = new java.util.HashMap<>();
            jdbcOperations.query(sql, new Object[] {databaseName, actualSchemaName}, (rs, rowNum) -> {
                String tableName = rs.getString("table_name");
                String indexName = rs.getString("index_name");

                Map<String, DBTableIndex> indexMap = tableIndexMap.computeIfAbsent(tableName,
                        k -> new java.util.LinkedHashMap<>());
                DBTableIndex index = indexMap.get(indexName);

                if (index == null) {
                    index = new DBTableIndex();
                    index.setSchemaName(schemaName);
                    index.setTableName(tableName);
                    index.setName(indexName);
                    // ordinal_position应该是索引在表中的序号，为每个表单独计数
                    AtomicInteger indexCounter = tableIndexCounterMap.computeIfAbsent(tableName,
                            k -> new AtomicInteger(1));
                    index.setOrdinalPosition(indexCounter.getAndIncrement());
                    index.setPrimary(rs.getBoolean("is_primary_key"));
                    index.setNonUnique(!rs.getBoolean("is_unique"));

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

            // 转换为 Map<String, List<DBTableIndex>>
            Map<String, List<DBTableIndex>> result = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, Map<String, DBTableIndex>> entry : tableIndexMap.entrySet()) {
                result.put(entry.getKey(), new java.util.ArrayList<>(entry.getValue().values()));
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to list table indexes for schema: " + schemaName, e);
            return Collections.emptyMap();
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

    /**
     * 列出指定 schema 下所有表的约束信息
     * <p>
     * <b>SQL Server 特殊处理：</b> schemaName 参数支持两种格式： 1. "database" - 只有数据库名，默认使用 dbo schema（兼容旧代码） 2.
     * "database.schema" - 完整的 database.schema 格式（从 showDatabases() 返回的格式） 为了统一抽象层，SQL Server 将
     * database.schema 作为 schema 处理，与 MySQL 保持一致。
     * </p>
     *
     * @param schemaName 数据库名或 database.schema 格式
     * @return 表名到约束列表的映射
     */
    @Override
    public Map<String, List<DBTableConstraint>> listTableConstraints(String schemaName) {
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = SqlServerSchemaUtil.getDatabaseName(dbAndSchema[0], jdbcOperations);
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
            return Collections.emptyMap();
        }

        // 查询主键和唯一约束
        String pkAndUniqueSql = "SELECT "
                + "    t.name AS table_name, "
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
                + "    AND s.name = ? "
                + "ORDER BY t.name, kc.name, ic.key_ordinal";

        // 查询外键约束
        // 注意：对于跨数据库的外键，OBJECT_SCHEMA_NAME和OBJECT_NAME可能返回NULL
        String fkSql = "SELECT "
                + "    t.name AS table_name, "
                + "    fk.name AS constraint_name, "
                + "    c.name AS column_name, "
                + "    OBJECT_SCHEMA_NAME(fk.referenced_object_id, DB_ID()) AS referenced_schema_name, "
                + "    OBJECT_NAME(fk.referenced_object_id, DB_ID()) AS referenced_table_name, "
                + "    rc.name AS referenced_column_name, "
                + "    fk.delete_referential_action, "
                + "    fk.update_referential_action "
                + "FROM sys.foreign_keys fk "
                + "INNER JOIN sys.tables t ON fk.parent_object_id = t.object_id "
                + "INNER JOIN sys.schemas s ON t.schema_id = s.schema_id "
                + "INNER JOIN sys.foreign_key_columns fkc ON fk.object_id = fkc.constraint_object_id "
                + "INNER JOIN sys.columns c ON fkc.parent_object_id = c.object_id AND fkc.parent_column_id = c.column_id "
                + "LEFT JOIN sys.columns rc ON fkc.referenced_object_id = rc.object_id AND fkc.referenced_column_id = rc.column_id "
                + "WHERE DB_NAME() = ? "
                + "    AND s.name = ? "
                + "ORDER BY t.name, fk.name, fkc.constraint_column_id";

        // 查询检查约束
        String checkSql = "SELECT "
                + "    t.name AS table_name, "
                + "    cc.name AS constraint_name, "
                + "    cc.definition AS check_definition "
                + "FROM sys.check_constraints cc "
                + "INNER JOIN sys.tables t ON cc.parent_object_id = t.object_id "
                + "INNER JOIN sys.schemas s ON t.schema_id = s.schema_id "
                + "WHERE DB_NAME() = ? "
                + "    AND s.name = ?";

        try {
            Map<String, Map<String, DBTableConstraint>> tableConstraintMap = new java.util.LinkedHashMap<>();
            AtomicInteger constraintCounter = new AtomicInteger(1);

            // 处理主键和唯一约束
            jdbcOperations.query(pkAndUniqueSql, new Object[] {databaseName, actualSchemaName}, (rs, rowNum) -> {
                String tableName = rs.getString("table_name");
                String constraintName = rs.getString("constraint_name");
                String constraintType = rs.getString("constraint_type");
                String columnName = rs.getString("column_name");

                Map<String, DBTableConstraint> constraintMap = tableConstraintMap.computeIfAbsent(tableName,
                        k -> new java.util.LinkedHashMap<>());
                DBTableConstraint constraint = constraintMap.get(constraintName);

                if (constraint == null) {
                    constraint = new DBTableConstraint();
                    constraint.setName(constraintName);
                    constraint.setSchemaName(schemaName);
                    constraint.setTableName(tableName);
                    constraint.setOwner(schemaName);
                    constraint.setOrdinalPosition(constraintCounter.getAndIncrement());

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
            jdbcOperations.query(fkSql, new Object[] {databaseName, actualSchemaName}, (rs, rowNum) -> {
                String tableName = rs.getString("table_name");
                String constraintName = rs.getString("constraint_name");
                String columnName = rs.getString("column_name");
                String referencedSchemaName = rs.getString("referenced_schema_name");
                String referencedTableName = rs.getString("referenced_table_name");
                String referencedColumnName = rs.getString("referenced_column_name");
                Integer deleteAction = rs.getInt("delete_referential_action");
                Integer updateAction = rs.getInt("update_referential_action");
                // 对于跨数据库外键，referenced_schema_name和referenced_table_name可能为NULL

                Map<String, DBTableConstraint> constraintMap = tableConstraintMap.computeIfAbsent(tableName,
                        k -> new java.util.LinkedHashMap<>());
                DBTableConstraint constraint = constraintMap.get(constraintName);

                if (constraint == null) {
                    constraint = new DBTableConstraint();
                    constraint.setName(constraintName);
                    constraint.setSchemaName(schemaName);
                    constraint.setTableName(tableName);
                    constraint.setOwner(schemaName);
                    constraint.setType(DBConstraintType.FOREIGN_KEY);
                    // 处理跨数据库引用：如果referenced_schema_name或referenced_table_name为NULL，
                    // 说明是跨数据库引用，此时无法获取完整的引用信息
                    constraint.setReferenceSchemaName(
                            StringUtils.isNotBlank(referencedSchemaName) ? referencedSchemaName : null);
                    constraint.setReferenceTableName(
                            StringUtils.isNotBlank(referencedTableName) ? referencedTableName : null);
                    constraint.setColumnNames(new java.util.ArrayList<>());
                    constraint.setReferenceColumnNames(new java.util.ArrayList<>());
                    constraint.setOrdinalPosition(constraintCounter.getAndIncrement());

                    // 设置ON DELETE规则
                    if (deleteAction != null) {
                        switch (deleteAction) {
                            case 1:
                                constraint.setOnDeleteRule(DBForeignKeyModifyRule.CASCADE);
                                break;
                            case 2:
                                constraint.setOnDeleteRule(DBForeignKeyModifyRule.SET_NULL);
                                break;
                            case 3:
                                constraint.setOnDeleteRule(DBForeignKeyModifyRule.SET_DEFAULT);
                                break;
                            case 0:
                            default:
                                constraint.setOnDeleteRule(DBForeignKeyModifyRule.NO_ACTION);
                                break;
                        }
                    }

                    // 设置ON UPDATE规则
                    if (updateAction != null) {
                        switch (updateAction) {
                            case 1:
                                constraint.setOnUpdateRule(DBForeignKeyModifyRule.CASCADE);
                                break;
                            case 2:
                                constraint.setOnUpdateRule(DBForeignKeyModifyRule.SET_NULL);
                                break;
                            case 3:
                                constraint.setOnUpdateRule(DBForeignKeyModifyRule.SET_DEFAULT);
                                break;
                            case 0:
                            default:
                                constraint.setOnUpdateRule(DBForeignKeyModifyRule.NO_ACTION);
                                break;
                        }
                    }

                    constraintMap.put(constraintName, constraint);
                }

                constraint.getColumnNames().add(columnName);
                // 对于跨数据库外键，referencedColumnName可能为NULL
                if (StringUtils.isNotBlank(referencedColumnName)) {
                    constraint.getReferenceColumnNames().add(referencedColumnName);
                } else {
                    // 如果无法获取引用列名，添加空字符串占位，保持列数一致
                    constraint.getReferenceColumnNames().add("");
                }
                return null;
            });

            // 处理检查约束
            jdbcOperations.query(checkSql, new Object[] {databaseName, actualSchemaName}, (rs, rowNum) -> {
                String tableName = rs.getString("table_name");
                String constraintName = rs.getString("constraint_name");
                String checkDefinition = rs.getString("check_definition");

                Map<String, DBTableConstraint> constraintMap = tableConstraintMap.computeIfAbsent(tableName,
                        k -> new java.util.LinkedHashMap<>());
                DBTableConstraint constraint = new DBTableConstraint();
                constraint.setName(constraintName);
                constraint.setSchemaName(schemaName);
                constraint.setTableName(tableName);
                constraint.setOwner(schemaName);
                constraint.setType(DBConstraintType.CHECK);
                if (StringUtils.isNotBlank(checkDefinition)) {
                    constraint.setCheckClause(checkDefinition);
                }
                constraint.setColumnNames(new java.util.ArrayList<>());
                constraint.setOrdinalPosition(constraintCounter.getAndIncrement());
                constraintMap.put(constraintName, constraint);
                return null;
            });

            // 转换为 Map<String, List<DBTableConstraint>> 并过滤约束列名
            Map<String, List<DBTableConstraint>> result = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, Map<String, DBTableConstraint>> entry : tableConstraintMap.entrySet()) {
                List<DBTableConstraint> constraints = new java.util.ArrayList<>(entry.getValue().values());
                filterConstraintColumns(constraints);
                result.put(entry.getKey(), constraints);
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to list table constraints for schema: " + schemaName, e);
            return Collections.emptyMap();
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

    /**
     * 列出指定 schema 下所有表的选项信息
     * <p>
     * <b>SQL Server 特殊处理：</b> schemaName 参数支持两种格式： 1. "database" - 只有数据库名，默认使用 dbo schema（兼容旧代码） 2.
     * "database.schema" - 完整的 database.schema 格式（从 showDatabases() 返回的格式） 为了统一抽象层，SQL Server 将
     * database.schema 作为 schema 处理，与 MySQL 保持一致。
     * </p>
     *
     * @param schemaName 数据库名或 database.schema 格式
     * @return 表名到表选项的映射
     */
    @Override
    public Map<String, DBTableOptions> listTableOptions(String schemaName) {
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = SqlServerSchemaUtil.getDatabaseName(dbAndSchema[0], jdbcOperations);
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
            return Collections.emptyMap();
        }

        String sql = "SELECT "
                + "    t.name AS table_name, "
                + "    t.create_date AS create_time, "
                + "    t.modify_date AS update_time, "
                + "    ISNULL(ep.value, '') AS table_comment "
                + "FROM sys.tables t "
                + "INNER JOIN sys.schemas s ON t.schema_id = s.schema_id "
                + "LEFT JOIN sys.extended_properties ep ON ep.major_id = t.object_id "
                + "    AND ep.minor_id = 0 "
                + "    AND ep.name = 'MS_Description' "
                + "WHERE DB_NAME() = ? "
                + "    AND s.name = ?";

        try {
            Map<String, DBTableOptions> result = new java.util.LinkedHashMap<>();
            jdbcOperations.query(sql, new Object[] {databaseName, actualSchemaName}, (rs, rowNum) -> {
                String tableName = rs.getString("table_name");
                DBTableOptions options = new DBTableOptions();

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

                result.put(tableName, options);
                return null;
            });
            return result;
        } catch (Exception e) {
            log.warn("Failed to list table options for schema: " + schemaName, e);
            return Collections.emptyMap();
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

    /**
     * 列出指定 schema 下多个表的分区信息
     * <p>
     * <b>SQL Server 特殊处理：</b> schemaName 参数支持两种格式： 1. "database" - 只有数据库名，默认使用 dbo schema（兼容旧代码） 2.
     * "database.schema" - 完整的 database.schema 格式（从 showDatabases() 返回的格式） 为了统一抽象层，SQL Server 将
     * database.schema 作为 schema 处理，与 MySQL 保持一致。
     * </p>
     *
     * @param schemaName 数据库名或 database.schema 格式
     * @param tableNames 表名列表
     * @return 表名到分区信息的映射
     */
    @Override
    public Map<String, DBTablePartition> listTablePartitions(@NonNull String schemaName, List<String> tableNames) {
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = SqlServerSchemaUtil.getDatabaseName(dbAndSchema[0], jdbcOperations);
        String actualSchemaName = dbAndSchema[1];

        if (tableNames == null || tableNames.isEmpty()) {
            return Collections.emptyMap();
        }

        // 确保在正确的数据库中查询
        String currentDb = null;
        try {
            currentDb = jdbcOperations.queryForObject("SELECT DB_NAME()", String.class);
            if (!databaseName.equals(currentDb)) {
                switchDatabase(databaseName);
            }
        } catch (Exception e) {
            log.warn("Failed to switch to database: " + databaseName, e);
            return Collections.emptyMap();
        }

        // 构建 IN 子句
        StringBuilder inClause = new StringBuilder();
        List<Object> params = new java.util.ArrayList<>();
        params.add(databaseName);
        params.add(actualSchemaName);
        for (int i = 0; i < tableNames.size(); i++) {
            if (i > 0) {
                inClause.append(",");
            }
            inClause.append("?");
            params.add(tableNames.get(i));
        }

        // 查询分区信息
        String sql = "SELECT "
                + "    t.name AS table_name, "
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
                + "    AND s.name = ? "
                + "    AND t.name IN (" + inClause.toString() + ") "
                + "ORDER BY t.name, p.partition_number";

        try {
            Map<String, DBTablePartition> partitionMap = new java.util.LinkedHashMap<>();
            Map<String, AtomicReference<String>> tablePartitionFunctionMap = new java.util.HashMap<>();

            // 先收集所有分区信息
            jdbcOperations.query(sql, params.toArray(), rs -> {
                String tableName = rs.getString("table_name");
                String partitionSchemeName = rs.getString("partition_scheme_name");
                String partitionFunctionName = rs.getString("partition_function_name");

                if (partitionSchemeName != null && !partitionMap.containsKey(tableName)) {
                    DBTablePartition partition = new DBTablePartition();
                    partitionMap.put(tableName, partition);
                    tablePartitionFunctionMap.put(tableName, new AtomicReference<>(partitionFunctionName));

                    if (partitionFunctionName != null) {
                        DBTablePartitionOption option = new DBTablePartitionOption();
                        option.setExpression(partitionFunctionName);
                        partition.setPartitionOption(option);
                    }
                }

                DBTablePartition partition = partitionMap.get(tableName);
                if (partition != null && partitionSchemeName != null) {
                    List<DBTablePartitionDefinition> definitions = partition.getPartitionDefinitions();
                    if (definitions == null) {
                        definitions = new java.util.ArrayList<>();
                        partition.setPartitionDefinitions(definitions);
                    }

                    DBTablePartitionDefinition definition = new DBTablePartitionDefinition();
                    definition.setName("Partition_" + rs.getInt("partition_number"));
                    definition.setOrdinalPosition(rs.getInt("partition_number"));
                    definitions.add(definition);
                }
            });

            // 获取分区函数的边界值
            for (Map.Entry<String, AtomicReference<String>> entry : tablePartitionFunctionMap.entrySet()) {
                String tableName = entry.getKey();
                String partitionFunctionName = entry.getValue().get();
                if (partitionFunctionName != null) {
                    String boundarySql = "SELECT value FROM sys.partition_range_values "
                            + "WHERE function_id = (SELECT function_id FROM sys.partition_functions WHERE name = ?) "
                            + "ORDER BY boundary_id";
                    List<String> boundaries = jdbcOperations.query(boundarySql,
                            new Object[] {partitionFunctionName}, (rs, rowNum) -> rs.getString(1));

                    DBTablePartition partition = partitionMap.get(tableName);
                    if (partition != null && partition.getPartitionDefinitions() != null) {
                        List<DBTablePartitionDefinition> definitions = partition.getPartitionDefinitions();
                        for (int i = 0; i < boundaries.size() && i < definitions.size(); i++) {
                            definitions.get(i).setMaxValues(Collections.singletonList(boundaries.get(i)));
                        }
                    }
                }
            }

            // 移除没有分区的表（partition_scheme_name 为 null）
            partitionMap.entrySet().removeIf(entry -> entry.getValue().getPartitionDefinitions() == null
                    || entry.getValue().getPartitionDefinitions().isEmpty());

            return partitionMap;
        } catch (Exception e) {
            log.warn("Failed to list table partitions for schema: " + schemaName, e);
            return Collections.emptyMap();
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

    /**
     * 不实现此方法的原因： 1. 该方法已被标记为 @Deprecated，不再推荐使用 2. SQL Server 没有 "tenant"（租户）概念，这是 OceanBase 数据库特有的概念
     * 3. 其他数据库实现（Oracle、PostgreSQL、MySQL、Doris）也都未实现此方法 4. 如需获取分区信息，请使用 getPartition(String schemaName,
     * String tableName) 方法
     */
    @Override
    public List<DBTablePartition> listTableRangePartitionInfo(String tenantName) {
        return Collections.emptyList();
    }

    /**
     * 不实现此方法的原因： SQL Server 不支持子分区（Subpartitioning/Composite Partitioning）功能。 SQL Server
     * 仅支持单级范围分区（Range Partitioning），不支持将分区进一步划分为子分区。 这是 SQL Server 与 Oracle、MySQL 等数据库的一个重要区别。
     * 如需获取分区信息，请使用 getPartition(String schemaName, String tableName) 方法。
     */
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

    /**
     * 列出指定分区方法的分区表 SQL Server 支持表分区功能，通过分区方案（Partition Scheme）和分区函数（Partition Function）实现
     * 
     * @param partitionMethod 分区方法，如 "RANGE", "LIST", "HASH" 等
     * @return 分区表列表
     */
    @Override
    public List<DBObjectIdentity> listPartitionTables(String partitionMethod) {
        if (StringUtils.isBlank(partitionMethod)) {
            return Collections.emptyList();
        }

        // SQL Server 的分区函数类型：RANGE LEFT, RANGE RIGHT
        // 需要将 partitionMethod 映射到 SQL Server 的 type_desc
        // 使用白名单验证避免SQL注入
        String partitionTypeFilter;
        List<Object> params = new ArrayList<>();
        String upperPartitionMethod = partitionMethod.toUpperCase();

        if ("RANGE".equalsIgnoreCase(partitionMethod)) {
            // RANGE 包括 RANGE LEFT 和 RANGE RIGHT
            partitionTypeFilter = "pf.type_desc LIKE ?";
            params.add("RANGE%");
        } else if ("LIST".equalsIgnoreCase(partitionMethod)) {
            partitionTypeFilter = "pf.type_desc = ?";
            params.add("LIST");
        } else if ("HASH".equalsIgnoreCase(partitionMethod)) {
            partitionTypeFilter = "pf.type_desc = ?";
            params.add("HASH");
        } else {
            // 验证其他类型是否为有效的SQL Server分区类型
            // SQL Server支持的分区类型：RANGE LEFT, RANGE RIGHT, LIST, HASH
            if (!upperPartitionMethod.matches("^[A-Z_]+$")) {
                // 包含非法字符，拒绝请求
                log.warn("Invalid partition method contains illegal characters: " + partitionMethod);
                return Collections.emptyList();
            }
            // 对于其他类型，使用参数化查询
            partitionTypeFilter = "pf.type_desc = ?";
            params.add(upperPartitionMethod);
        }

        // 查询当前数据库中的分区表
        // 注意：SQL Server 的 listPartitionTables 方法没有 schemaName 参数，
        // 因此只查询当前连接的数据库，与其他数据库的实现保持一致
        String currentDb = null;
        try {
            currentDb = jdbcOperations.queryForObject("SELECT DB_NAME()", String.class);
        } catch (Exception e) {
            log.warn("Failed to get current database", e);
            return Collections.emptyList();
        }

        try {
            String sql = "SELECT DISTINCT "
                    + "    DB_NAME() AS database_name, "
                    + "    s.name AS schema_name, "
                    + "    t.name AS table_name, "
                    + "    pf.type_desc AS partition_function_type "
                    + "FROM sys.tables t "
                    + "INNER JOIN sys.schemas s ON t.schema_id = s.schema_id "
                    + "INNER JOIN sys.indexes i ON t.object_id = i.object_id AND i.type IN (0, 1) "
                    + "INNER JOIN sys.partition_schemes ps ON i.data_space_id = ps.data_space_id "
                    + "INNER JOIN sys.partition_functions pf ON ps.function_id = pf.function_id "
                    + "WHERE " + partitionTypeFilter;

            return jdbcOperations.query(sql, params.toArray(), (rs, rowNum) -> {
                DBObjectIdentity identity = new DBObjectIdentity();
                String dbName = rs.getString("database_name");
                String schemaName = rs.getString("schema_name");
                identity.setSchemaName(SqlServerSchemaUtil.buildFullSchemaName(dbName, schemaName));
                identity.setName(rs.getString("table_name"));
                identity.setType(DBObjectType.TABLE);
                return identity;
            });
        } catch (Exception e) {
            log.warn("Failed to list partition tables in current database", e);
            return Collections.emptyList();
        }
    }

    /**
     * 列出指定表的约束信息
     * <p>
     * <b>SQL Server 特殊处理：</b> schemaName 参数支持两种格式： 1. "database" - 只有数据库名，默认使用 dbo schema（兼容旧代码） 2.
     * "database.schema" - 完整的 database.schema 格式（从 showDatabases() 返回的格式） 为了统一抽象层，SQL Server 将
     * database.schema 作为 schema 处理，与 MySQL 保持一致。
     * </p>
     *
     * @param schemaName 数据库名或 database.schema 格式
     * @param tableName 表名
     * @return 约束信息列表
     */
    @Override
    public List<DBTableConstraint> listTableConstraints(String schemaName, String tableName) {
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = SqlServerSchemaUtil.getDatabaseName(dbAndSchema[0], jdbcOperations);
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
        // 注意：对于跨数据库的外键，OBJECT_SCHEMA_NAME和OBJECT_NAME可能返回NULL
        // 需要使用LEFT JOIN来处理跨数据库引用的情况
        String fkSql = "SELECT "
                + "    fk.name AS constraint_name, "
                + "    c.name AS column_name, "
                + "    OBJECT_SCHEMA_NAME(fk.referenced_object_id, DB_ID()) AS referenced_schema_name, "
                + "    OBJECT_NAME(fk.referenced_object_id, DB_ID()) AS referenced_table_name, "
                + "    rc.name AS referenced_column_name, "
                + "    fk.delete_referential_action, "
                + "    fk.update_referential_action, "
                + "    fk.referenced_object_id "
                + "FROM sys.foreign_keys fk "
                + "INNER JOIN sys.tables t ON fk.parent_object_id = t.object_id "
                + "INNER JOIN sys.schemas s ON t.schema_id = s.schema_id "
                + "INNER JOIN sys.foreign_key_columns fkc ON fk.object_id = fkc.constraint_object_id "
                + "INNER JOIN sys.columns c ON fkc.parent_object_id = c.object_id AND fkc.parent_column_id = c.column_id "
                + "LEFT JOIN sys.columns rc ON fkc.referenced_object_id = rc.object_id AND fkc.referenced_column_id = rc.column_id "
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
            // 使用计数器为约束分配ordinalPosition
            AtomicInteger constraintCounter = new AtomicInteger(1);

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
                            // 设置ordinalPosition
                            constraint.setOrdinalPosition(constraintCounter.getAndIncrement());

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
                Integer deleteAction = rs.getInt("delete_referential_action");
                Integer updateAction = rs.getInt("update_referential_action");
                // 对于跨数据库外键，referenced_schema_name和referenced_table_name可能为NULL
                // 此时referenced_object_id不为0，但无法通过OBJECT_NAME获取名称

                DBTableConstraint constraint = constraintMap.get(constraintName);
                if (constraint == null) {
                    constraint = new DBTableConstraint();
                    constraint.setName(constraintName);
                    constraint.setSchemaName(schemaName);
                    constraint.setTableName(tableName);
                    constraint.setOwner(schemaName);
                    constraint.setType(DBConstraintType.FOREIGN_KEY);
                    // 处理跨数据库引用：如果referenced_schema_name或referenced_table_name为NULL，
                    // 说明是跨数据库引用，此时无法获取完整的引用信息
                    constraint.setReferenceSchemaName(
                            StringUtils.isNotBlank(referencedSchemaName) ? referencedSchemaName : null);
                    constraint.setReferenceTableName(
                            StringUtils.isNotBlank(referencedTableName) ? referencedTableName : null);
                    constraint.setColumnNames(new java.util.ArrayList<>());
                    constraint.setReferenceColumnNames(new java.util.ArrayList<>());
                    // 设置ordinalPosition
                    constraint.setOrdinalPosition(constraintCounter.getAndIncrement());

                    // 设置ON DELETE规则
                    // SQLServer的delete_referential_action: 0=NO_ACTION, 1=CASCADE, 2=SET_NULL, 3=SET_DEFAULT
                    if (deleteAction != null) {
                        switch (deleteAction) {
                            case 1:
                                constraint.setOnDeleteRule(DBForeignKeyModifyRule.CASCADE);
                                break;
                            case 2:
                                constraint.setOnDeleteRule(DBForeignKeyModifyRule.SET_NULL);
                                break;
                            case 3:
                                constraint.setOnDeleteRule(DBForeignKeyModifyRule.SET_DEFAULT);
                                break;
                            case 0:
                            default:
                                constraint.setOnDeleteRule(DBForeignKeyModifyRule.NO_ACTION);
                                break;
                        }
                    }

                    // 设置ON UPDATE规则
                    // SQLServer的update_referential_action: 0=NO_ACTION, 1=CASCADE, 2=SET_NULL, 3=SET_DEFAULT
                    if (updateAction != null) {
                        switch (updateAction) {
                            case 1:
                                constraint.setOnUpdateRule(DBForeignKeyModifyRule.CASCADE);
                                break;
                            case 2:
                                constraint.setOnUpdateRule(DBForeignKeyModifyRule.SET_NULL);
                                break;
                            case 3:
                                constraint.setOnUpdateRule(DBForeignKeyModifyRule.SET_DEFAULT);
                                break;
                            case 0:
                            default:
                                constraint.setOnUpdateRule(DBForeignKeyModifyRule.NO_ACTION);
                                break;
                        }
                    }

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
                // 设置检查约束的表达式
                if (StringUtils.isNotBlank(checkDefinition)) {
                    constraint.setCheckClause(checkDefinition);
                }
                constraint.setColumnNames(new java.util.ArrayList<>());
                // 设置ordinalPosition
                constraint.setOrdinalPosition(constraintCounter.getAndIncrement());
                constraintMap.put(constraintName, constraint);
                return null;
            });

            List<DBTableConstraint> constraints = new java.util.ArrayList<>(constraintMap.values());
            // 过滤约束列名中的NULL值和重复值
            filterConstraintColumns(constraints);
            return constraints;
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

    /**
     * 过滤约束列名中的NULL值和重复值 三表联查的结果 ColumnNames 和 RefColumnNames 可能存在 NULL 或者重复值，这里做一个过滤
     */
    protected void filterConstraintColumns(List<DBTableConstraint> constraints) {
        for (DBTableConstraint constraint : constraints) {
            if (Objects.nonNull(constraint.getReferenceColumnNames())) {
                constraint.setReferenceColumnNames(constraint.getReferenceColumnNames().stream()
                        .filter(Objects::nonNull).distinct().collect(Collectors.toList()));
            }
            if (Objects.nonNull(constraint.getColumnNames())) {
                constraint.setColumnNames(constraint.getColumnNames().stream().filter(Objects::nonNull).distinct()
                        .collect(Collectors.toList()));
            }
        }
    }

    /**
     * 获取表的分区信息
     * <p>
     * <b>SQL Server 特殊处理：</b> schemaName 参数支持两种格式： 1. "database" - 只有数据库名，默认使用 dbo schema（兼容旧代码） 2.
     * "database.schema" - 完整的 database.schema 格式（从 showDatabases() 返回的格式） 为了统一抽象层，SQL Server 将
     * database.schema 作为 schema 处理，与 MySQL 保持一致。
     * </p>
     *
     * @param schemaName 数据库名或 database.schema 格式
     * @param tableName 表名
     * @return 分区信息
     */
    @Override
    public DBTablePartition getPartition(String schemaName, String tableName) {
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = SqlServerSchemaUtil.getDatabaseName(dbAndSchema[0], jdbcOperations);
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
                        option.setExpression(partitionFunctionName.get());
                        partition.setPartitionOption(option);
                    }
                }

                if (partitionSchemeName.get() != null) {
                    DBTablePartitionDefinition definition = new DBTablePartitionDefinition();
                    definition.setName("Partition_" + rs.getInt("partition_number"));
                    definition.setOrdinalPosition(rs.getInt("partition_number"));
                    // Attempt to get boundary values if available
                    definitions.add(definition);
                }
            });

            // 获取分区函数的边界值
            if (partitionFunctionName.get() != null) {
                String boundarySql = "SELECT value FROM sys.partition_range_values "
                        + "WHERE function_id = (SELECT function_id FROM sys.partition_functions WHERE name = ?) "
                        + "ORDER BY boundary_id";
                List<String> boundaries = jdbcOperations.query(boundarySql,
                        new Object[] {partitionFunctionName.get()}, (rs, rowNum) -> rs.getString(1));

                for (int i = 0; i < boundaries.size() && i < definitions.size(); i++) {
                    definitions.get(i).setMaxValues(Collections.singletonList(boundaries.get(i)));
                }
            }

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

    /**
     * 列出指定表的索引信息
     * <p>
     * <b>SQL Server 特殊处理：</b> schemaName 参数支持两种格式： 1. "database" - 只有数据库名，默认使用 dbo schema（兼容旧代码） 2.
     * "database.schema" - 完整的 database.schema 格式（从 showDatabases() 返回的格式） 为了统一抽象层，SQL Server 将
     * database.schema 作为 schema 处理，与 MySQL 保持一致。
     * </p>
     *
     * @param schemaName 数据库名或 database.schema 格式
     * @param tableName 表名
     * @return 索引信息列表
     */
    @Override
    public List<DBTableIndex> listTableIndexes(String schemaName, String tableName) {
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = SqlServerSchemaUtil.getDatabaseName(dbAndSchema[0], jdbcOperations);
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
            AtomicInteger indexCounter = new AtomicInteger(1);
            jdbcOperations.query(sql, new Object[] {databaseName, tableName, actualSchemaName}, (rs, rowNum) -> {
                String indexName = rs.getString("index_name");
                DBTableIndex index = indexMap.get(indexName);

                if (index == null) {
                    index = new DBTableIndex();
                    index.setSchemaName(schemaName);
                    index.setTableName(tableName);
                    index.setName(indexName);
                    // ordinal_position应该是索引在表中的序号，而不是列在索引中的序号
                    index.setOrdinalPosition(indexCounter.getAndIncrement());
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

    /**
     * 获取表的 DDL 语句
     * <p>
     * <b>SQL Server 特殊处理：</b> schemaName 参数支持两种格式： 1. "database" - 只有数据库名，默认使用 dbo schema（兼容旧代码） 2.
     * "database.schema" - 完整的 database.schema 格式（从 showDatabases() 返回的格式） 为了统一抽象层，SQL Server 将
     * database.schema 作为 schema 处理，与 MySQL 保持一致。
     * </p>
     *
     * @param schemaName 数据库名或 database.schema 格式
     * @param tableName 表名
     * @return DDL 语句
     */
    @Override
    public String getTableDDL(String schemaName, String tableName) {
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = SqlServerSchemaUtil.getDatabaseName(dbAndSchema[0], jdbcOperations);
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

    /**
     * 获取表的选项信息
     * <p>
     * <b>SQL Server 特殊处理：</b> schemaName 参数支持两种格式： 1. "database" - 只有数据库名，默认使用 dbo schema（兼容旧代码） 2.
     * "database.schema" - 完整的 database.schema 格式（从 showDatabases() 返回的格式） 为了统一抽象层，SQL Server 将
     * database.schema 作为 schema 处理，与 MySQL 保持一致。
     * </p>
     *
     * @param schemaName 数据库名或 database.schema 格式
     * @param tableName 表名
     * @return 表选项信息
     */
    @Override
    public DBTableOptions getTableOptions(String schemaName, String tableName) {
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = SqlServerSchemaUtil.getDatabaseName(dbAndSchema[0], jdbcOperations);
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

    /**
     * 获取表选项（带 DDL 参数的重载方法） 注意：SQL Server 不支持从 DDL 解析表选项，因此直接调用无 DDL 参数的版本
     * 
     * @param schemaName schema 名称（格式：database.schema）
     * @param tableName 表名
     * @param ddl 表的 DDL 语句（此参数在 SQL Server 中不使用）
     * @return 表选项
     */
    @Override
    public DBTableOptions getTableOptions(String schemaName, String tableName, String ddl) {
        // SQL Server 不支持从 DDL 解析表选项，直接调用无 DDL 参数的版本
        return getTableOptions(schemaName, tableName);
    }

    /**
     * 列出表的列组
     * 
     * 不实现此方法的原因： 列组（Column Group）是 OceanBase 数据库特有的功能，用于列式存储优化。 SQL Server 不支持列组功能，因此返回空列表。
     * 
     * @param schemaName schema 名称（格式：database.schema）
     * @param tableName 表名
     * @return 空列表（SQL Server 不支持列组）
     */
    @Override
    public List<DBColumnGroupElement> listTableColumnGroups(String schemaName, String tableName) {
        // SQL Server 不支持列组功能，返回空列表
        return Collections.emptyList();
    }

    /**
     * 获取视图信息
     * <p>
     * <b>SQL Server 特殊处理：</b> schemaName 参数支持两种格式： 1. "database" - 只有数据库名，默认使用 dbo schema（兼容旧代码） 2.
     * "database.schema" - 完整的 database.schema 格式（从 showDatabases() 返回的格式） 为了统一抽象层，SQL Server 将
     * database.schema 作为 schema 处理，与 MySQL 保持一致。
     * </p>
     *
     * @param schemaName 数据库名或 database.schema 格式
     * @param viewName 视图名
     * @return 视图信息
     */
    @Override
    public DBView getView(String schemaName, String viewName) {
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = SqlServerSchemaUtil.getDatabaseName(dbAndSchema[0], jdbcOperations);
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
                // SQL Server的CHECK_OPTION可能的值：'NONE' 或 'CASCADE'
                // 但DBViewCheckOption枚举只有NONE和READ_ONLY，SQL Server不支持READ_ONLY语义
                // 所以统一映射为NONE
                if (StringUtils.isNotBlank(checkOption) && !"NONE".equalsIgnoreCase(checkOption)) {
                    // SQL Server中如果有CHECK_OPTION，通常显示为'CASCADE'，但实际语义不同
                    // 这里统一设置为NONE，因为SQL Server的CHECK_OPTION语义与标准不同
                    view.setCheckOption(DBViewCheckOption.NONE.name());
                } else {
                    view.setCheckOption(DBViewCheckOption.NONE.name());
                }
                String isUpdatable = rs.getString("IS_UPDATABLE");
                view.setUpdatable("YES".equalsIgnoreCase(isUpdatable));
            });

            // 获取视图的 DDL - 使用 OBJECT_DEFINITION 函数
            // 使用参数化查询避免SQL注入，OBJECT_ID需要对象名称字符串
            // 转义方括号以防止SQL注入：将 ] 替换为 ]]
            String escapedSchemaName = actualSchemaName.replace("]", "]]");
            String escapedViewName = viewName.replace("]", "]]");
            String objectName = "[" + escapedSchemaName + "].[" + escapedViewName + "]";
            String ddlSql = "SELECT OBJECT_DEFINITION(OBJECT_ID(?, 'V')) AS view_definition";
            AtomicReference<String> viewDefinition = new AtomicReference<>();
            jdbcOperations.query(ddlSql, new Object[] {objectName}, rs -> {
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
                    + "        WHEN t.name IN ('nvarchar', 'nchar') "
                    + "        THEN t.name + '(' + CASE WHEN c.max_length = -1 THEN 'MAX' ELSE CAST(c.max_length / 2 AS VARCHAR) END + ')' "
                    + "        WHEN t.name IN ('varchar', 'char', 'binary', 'varbinary') "
                    + "        THEN t.name + '(' + CASE WHEN c.max_length = -1 THEN 'MAX' ELSE CAST(c.max_length AS VARCHAR) END + ')' "
                    + "        WHEN t.name IN ('decimal', 'numeric') "
                    + "        THEN t.name + '(' + CAST(c.precision AS VARCHAR) + ',' + CAST(c.scale AS VARCHAR) + ')' "
                    + "        WHEN t.name IN ('float', 'real') "
                    + "        THEN t.name + '(' + CAST(c.precision AS VARCHAR) + ')' "
                    + "        WHEN t.name IN ('datetime2', 'time', 'datetimeoffset') "
                    + "        THEN t.name + '(' + CAST(c.scale AS VARCHAR) + ')' "
                    + "        ELSE t.name "
                    + "    END AS full_type_name, "
                    + "    t.name AS base_type_name, "
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

    /**
     * 获取函数信息
     * <p>
     * <b>SQL Server 特殊处理：</b> schemaName 参数支持两种格式： 1. "database" - 只有数据库名，默认使用 dbo schema（兼容旧代码） 2.
     * "database.schema" - 完整的 database.schema 格式（从 showDatabases() 返回的格式） 为了统一抽象层，SQL Server 将
     * database.schema 作为 schema 处理，与 MySQL 保持一致。
     * </p>
     *
     * @param schemaName 数据库名或 database.schema 格式
     * @param functionName 函数名
     * @return 函数信息
     */
    @Override
    public DBFunction getFunction(String schemaName, String functionName) {
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = SqlServerSchemaUtil.getDatabaseName(dbAndSchema[0], jdbcOperations);
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

    /**
     * 获取存储过程信息
     * <p>
     * <b>SQL Server 特殊处理：</b> schemaName 参数支持两种格式： 1. "database" - 只有数据库名，默认使用 dbo schema（兼容旧代码） 2.
     * "database.schema" - 完整的 database.schema 格式（从 showDatabases() 返回的格式） 为了统一抽象层，SQL Server 将
     * database.schema 作为 schema 处理，与 MySQL 保持一致。
     * </p>
     *
     * @param schemaName 数据库名或 database.schema 格式
     * @param procedureName 存储过程名
     * @return 存储过程信息
     */
    @Override
    public DBProcedure getProcedure(String schemaName, String procedureName) {
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = SqlServerSchemaUtil.getDatabaseName(dbAndSchema[0], jdbcOperations);
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
        // SQL Server 不支持 Oracle 风格的包（Package）概念
        // Oracle 的包是用于组织存储过程、函数、变量等的容器，包含包规范和包体两部分
        // SQL Server 中没有对应的概念，只能通过 schema 和命名约定来组织对象
        // 因此该方法始终返回 null
        return null;
    }

    @Override
    public DBTrigger getTrigger(String schemaName, String packageName) {
        // 注意：接口参数名为 packageName，但实际表示的是触发器名称（triggerName）
        // 这是接口设计的历史遗留问题，为了保持接口一致性，这里使用 packageName 作为参数名
        String triggerName = packageName;

        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = SqlServerSchemaUtil.getDatabaseName(dbAndSchema[0], jdbcOperations);
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

        DBTrigger trigger = new DBTrigger();
        trigger.setTriggerName(triggerName);

        try {
            // 查询触发器基本信息
            String sql = "SELECT "
                    + "    t.name AS trigger_name, "
                    + "    s.name AS schema_name, "
                    + "    t.is_disabled, "
                    + "    t.is_instead_of_trigger, "
                    + "    t.is_not_for_replication, "
                    + "    OBJECT_NAME(t.parent_id) AS parent_object_name, "
                    + "    OBJECT_SCHEMA_NAME(t.parent_id) AS parent_object_schema, "
                    + "    o.type_desc AS parent_object_type, "
                    + "    o.create_date, "
                    + "    o.modify_date "
                    + "FROM sys.triggers t "
                    + "INNER JOIN sys.objects o ON t.parent_id = o.object_id "
                    + "INNER JOIN sys.schemas s ON o.schema_id = s.schema_id "
                    + "WHERE DB_NAME() = ? "
                    + "    AND s.name = ? "
                    + "    AND t.name = ? "
                    + "    AND t.parent_class = 1"; // 1 = DML triggers on tables/views

            AtomicReference<String> parentObjectName = new AtomicReference<>();
            AtomicReference<String> parentObjectSchema = new AtomicReference<>();
            AtomicReference<String> parentObjectType = new AtomicReference<>();
            AtomicInteger rowCount = new AtomicInteger(0);

            jdbcOperations.query(sql, new Object[] {databaseName, actualSchemaName, triggerName}, rs -> {
                rowCount.incrementAndGet();
                trigger.setOwner(rs.getString("schema_name"));
                trigger.setSchemaMode(rs.getString("parent_object_schema"));
                trigger.setSchemaName(rs.getString("parent_object_name"));
                trigger.setEnable(!rs.getBoolean("is_disabled"));
                parentObjectName.set(rs.getString("parent_object_name"));
                parentObjectSchema.set(rs.getString("parent_object_schema"));
                parentObjectType.set(rs.getString("parent_object_type"));

                // 设置基础对象类型
                if ("USER_TABLE".equalsIgnoreCase(parentObjectType.get())) {
                    trigger.setBaseObjectType("TABLE");
                } else if ("VIEW".equalsIgnoreCase(parentObjectType.get())) {
                    trigger.setBaseObjectType("VIEW");
                } else {
                    trigger.setBaseObjectType(parentObjectType.get());
                }

                // 设置状态
                String status = rs.getBoolean("is_disabled") ? "DISABLED" : "ENABLED";
                trigger.setStatus(status);

                Timestamp createDate = rs.getTimestamp("create_date");
                Timestamp modifyDate = rs.getTimestamp("modify_date");
                // 注意：DBTrigger 模型中没有 createTime 和 modifyTime 字段，如果需要可以扩展
            });

            // 如果没有查询到结果，返回 null
            if (rowCount.get() == 0) {
                log.warn("Trigger not found: " + schemaName + "." + triggerName);
                return null;
            }

            // 查询触发器定义（DDL）
            // 使用参数化查询避免SQL注入，转义方括号以防止SQL注入
            String escapedSchemaName = actualSchemaName.replace("]", "]]");
            String escapedTriggerName = triggerName.replace("]", "]]");
            String triggerObjectName = "[" + escapedSchemaName + "].[" + escapedTriggerName + "]";
            AtomicReference<String> triggerDefinition = new AtomicReference<>();

            try {
                // 使用 OBJECT_DEFINITION 函数获取触发器定义
                // OBJECT_ID 需要对象名称字符串，使用参数化查询
                String objectIdSql = "SELECT OBJECT_DEFINITION(OBJECT_ID(?, 'TR')) AS trigger_definition";
                jdbcOperations.query(objectIdSql, new Object[] {triggerObjectName}, rs -> {
                    if (rs.next()) {
                        triggerDefinition.set(rs.getString("trigger_definition"));
                    }
                });
            } catch (Exception e) {
                log.warn("Failed to get trigger definition using OBJECT_DEFINITION, trying sys.sql_modules", e);
                // 如果 OBJECT_DEFINITION 失败，尝试使用 sys.sql_modules
                String moduleSql = "SELECT m.definition "
                        + "FROM sys.sql_modules m "
                        + "INNER JOIN sys.objects o ON m.object_id = o.object_id "
                        + "INNER JOIN sys.schemas s ON o.schema_id = s.schema_id "
                        + "WHERE DB_NAME() = ? "
                        + "    AND s.name = ? "
                        + "    AND o.name = ? "
                        + "    AND o.type = 'TR'";

                jdbcOperations.query(moduleSql, new Object[] {databaseName, actualSchemaName, triggerName}, rs -> {
                    if (rs.next()) {
                        triggerDefinition.set(rs.getString("definition"));
                    }
                });
            }

            // 构建 DDL
            if (StringUtils.isNotBlank(triggerDefinition.get())) {
                trigger.setDdl(triggerDefinition.get());
            } else {
                // 如果无法获取定义，构建一个基本的 DDL 模板
                StringBuilder ddl = new StringBuilder();
                ddl.append("CREATE TRIGGER ");
                if (StringUtils.isNotEmpty(actualSchemaName)) {
                    ddl.append("[").append(actualSchemaName).append("].");
                }
                ddl.append("[").append(triggerName).append("] ");
                ddl.append("ON ");
                if (StringUtils.isNotEmpty(parentObjectSchema.get())) {
                    ddl.append("[").append(parentObjectSchema.get()).append("].");
                }
                ddl.append("[").append(parentObjectName.get()).append("] ");
                if (trigger.isEnable()) {
                    ddl.append("ENABLE");
                } else {
                    ddl.append("DISABLE");
                }
                trigger.setDdl(ddl.toString());
            }

            return trigger;
        } catch (BadSqlGrammarException e) {
            if (StringUtils.containsIgnoreCase(e.getMessage(), "Invalid object name") ||
                    StringUtils.containsIgnoreCase(e.getMessage(), "Invalid schema")) {
                log.warn("Trigger not found: " + schemaName + "." + triggerName);
                return null;
            }
            throw e;
        } catch (Exception e) {
            log.warn("Failed to get trigger: " + schemaName + "." + triggerName, e);
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

    /**
     * 获取类型信息
     * <p>
     * <b>SQL Server 特殊处理：</b> schemaName 参数支持两种格式： 1. "database" - 只有数据库名，默认使用 dbo schema（兼容旧代码） 2.
     * "database.schema" - 完整的 database.schema 格式（从 showDatabases() 返回的格式） 为了统一抽象层，SQL Server 将
     * database.schema 作为 schema 处理，与 MySQL 保持一致。
     * </p>
     *
     * @param schemaName 数据库名或 database.schema 格式
     * @param typeName 类型名
     * @return 类型信息
     */
    @Override
    public DBType getType(String schemaName, String typeName) {
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = SqlServerSchemaUtil.getDatabaseName(dbAndSchema[0], jdbcOperations);
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

        DBType type = new DBType();
        type.setTypeName(typeName);
        type.setOwner(actualSchemaName);

        try {
            // 查询用户定义类型信息
            String sql = "SELECT "
                    + "    t.name AS type_name, "
                    + "    s.name AS schema_name, "
                    + "    t.is_table_type, "
                    + "    t.is_user_defined, "
                    + "    st.name AS system_type_name, "
                    + "    t.max_length, "
                    + "    t.precision, "
                    + "    t.scale, "
                    + "    o.create_date, "
                    + "    o.modify_date "
                    + "FROM sys.types t "
                    + "INNER JOIN sys.schemas s ON t.schema_id = s.schema_id "
                    + "INNER JOIN sys.types st ON t.system_type_id = st.user_type_id "
                    + "LEFT JOIN sys.objects o ON t.user_type_id = o.object_id "
                    + "WHERE DB_NAME() = ? "
                    + "    AND s.name = ? "
                    + "    AND t.name = ? "
                    + "    AND t.is_user_defined = 1 "
                    + "    AND t.system_type_id != t.user_type_id";

            AtomicReference<String> systemTypeName = new AtomicReference<>();
            AtomicReference<Boolean> isTableType = new AtomicReference<>(false);
            AtomicInteger rowCount = new AtomicInteger(0);

            jdbcOperations.query(sql, new Object[] {databaseName, actualSchemaName, typeName}, rs -> {
                rowCount.incrementAndGet();
                type.setOwner(rs.getString("schema_name"));
                isTableType.set(rs.getBoolean("is_table_type"));
                systemTypeName.set(rs.getString("system_type_name"));

                // 设置类型代码
                if (isTableType.get()) {
                    type.setType("TABLE_TYPE");
                } else {
                    type.setType("SCALAR_TYPE");
                }

                Timestamp createDate = rs.getTimestamp("create_date");
                Timestamp modifyDate = rs.getTimestamp("modify_date");
                if (createDate != null) {
                    type.setCreateTime(createDate);
                }
                if (modifyDate != null) {
                    type.setLastDdlTime(modifyDate);
                }
            });

            // 如果没有查询到结果，返回 null
            if (rowCount.get() == 0) {
                log.warn("Type not found: " + schemaName + "." + typeName);
                return null;
            }

            // 查询类型定义（DDL）
            // 对于表类型，需要查询表类型的列定义
            // 对于标量类型，使用 OBJECT_DEFINITION 或查询 sys.sql_modules
            AtomicReference<String> typeDefinition = new AtomicReference<>();

            if (isTableType.get()) {
                // 表类型：查询表类型的列定义
                String tableTypeSql = "SELECT "
                        + "    c.name AS column_name, "
                        + "    ty.name AS type_name, "
                        + "    c.max_length, "
                        + "    c.precision, "
                        + "    c.scale, "
                        + "    c.is_nullable "
                        + "FROM sys.table_types tt "
                        + "INNER JOIN sys.schemas s ON tt.schema_id = s.schema_id "
                        + "INNER JOIN sys.columns c ON tt.type_table_object_id = c.object_id "
                        + "INNER JOIN sys.types ty ON c.user_type_id = ty.user_type_id "
                        + "WHERE DB_NAME() = ? "
                        + "    AND s.name = ? "
                        + "    AND tt.name = ? "
                        + "ORDER BY c.column_id";

                List<String> columns = new ArrayList<>();
                jdbcOperations.query(tableTypeSql, new Object[] {databaseName, actualSchemaName, typeName}, rs -> {
                    StringBuilder colDef = new StringBuilder();
                    colDef.append("[").append(rs.getString("column_name")).append("] ");
                    colDef.append(rs.getString("type_name"));

                    Integer maxLength = rs.getObject("max_length", Integer.class);
                    Integer precision = rs.getObject("precision", Integer.class);
                    Integer scale = rs.getObject("scale", Integer.class);

                    if (maxLength != null && maxLength > 0 && maxLength != -1) {
                        if (maxLength == -1) {
                            colDef.append("(MAX)");
                        } else {
                            colDef.append("(").append(maxLength).append(")");
                        }
                    } else if (precision != null && scale != null) {
                        colDef.append("(").append(precision).append(",").append(scale).append(")");
                    } else if (precision != null) {
                        colDef.append("(").append(precision).append(")");
                    }

                    if (!rs.getBoolean("is_nullable")) {
                        colDef.append(" NOT NULL");
                    }

                    columns.add(colDef.toString());
                });

                // 构建表类型的 DDL
                StringBuilder ddl = new StringBuilder();
                ddl.append("CREATE TYPE ");
                if (StringUtils.isNotEmpty(actualSchemaName)) {
                    ddl.append("[").append(actualSchemaName).append("].");
                }
                ddl.append("[").append(typeName).append("] AS TABLE (");
                ddl.append(String.join(", ", columns));
                ddl.append(")");
                typeDefinition.set(ddl.toString());
            } else {
                // 标量类型：尝试使用 OBJECT_DEFINITION
                try {
                    String objectIdSql = "SELECT OBJECT_DEFINITION(OBJECT_ID(?, 'TT')) AS type_definition";
                    jdbcOperations.query(objectIdSql, new Object[] {
                            actualSchemaName + "." + typeName}, rs -> {
                                if (rs.next()) {
                                    typeDefinition.set(rs.getString("type_definition"));
                                }
                            });
                } catch (Exception e) {
                    log.debug("Failed to get type definition using OBJECT_DEFINITION", e);
                }

                // 如果无法获取定义，构建一个基本的 DDL
                if (StringUtils.isBlank(typeDefinition.get())) {
                    StringBuilder ddl = new StringBuilder();
                    ddl.append("CREATE TYPE ");
                    if (StringUtils.isNotEmpty(actualSchemaName)) {
                        ddl.append("[").append(actualSchemaName).append("].");
                    }
                    ddl.append("[").append(typeName).append("] FROM ");
                    ddl.append(systemTypeName.get());
                    typeDefinition.set(ddl.toString());
                }
            }

            type.setDdl(typeDefinition.get());
            type.setStatus("VALID"); // SQL Server 中类型没有无效状态的概念

            return type;
        } catch (BadSqlGrammarException e) {
            if (StringUtils.containsIgnoreCase(e.getMessage(), "Invalid object name") ||
                    StringUtils.containsIgnoreCase(e.getMessage(), "Invalid schema")) {
                log.warn("Type not found: " + schemaName + "." + typeName);
                return null;
            }
            throw e;
        } catch (Exception e) {
            log.warn("Failed to get type: " + schemaName + "." + typeName, e);
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

    /**
     * 获取序列信息
     * <p>
     * <b>SQL Server 特殊处理：</b> schemaName 参数支持两种格式： 1. "database" - 只有数据库名，默认使用 dbo schema（兼容旧代码） 2.
     * "database.schema" - 完整的 database.schema 格式（从 showDatabases() 返回的格式） 为了统一抽象层，SQL Server 将
     * database.schema 作为 schema 处理，与 MySQL 保持一致。
     * </p>
     *
     * @param schemaName 数据库名或 database.schema 格式
     * @param sequenceName 序列名
     * @return 序列信息
     */
    @Override
    public DBSequence getSequence(String schemaName, String sequenceName) {
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = SqlServerSchemaUtil.getDatabaseName(dbAndSchema[0], jdbcOperations);
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

        DBSequence sequence = new DBSequence();
        sequence.setName(sequenceName);
        sequence.setUser(actualSchemaName);

        try {
            // SQL Server 从 2012 版本开始支持序列（SEQUENCE）对象
            // 查询序列详细信息
            String sql = "SELECT "
                    + "    seq.name AS sequence_name, "
                    + "    s.name AS schema_name, "
                    + "    seq.start_value, "
                    + "    seq.increment, "
                    + "    seq.minimum_value, "
                    + "    seq.maximum_value, "
                    + "    seq.current_value, "
                    + "    seq.is_cycling, "
                    + "    seq.cache_size, "
                    + "    o.create_date, "
                    + "    o.modify_date "
                    + "FROM sys.sequences seq "
                    + "INNER JOIN sys.schemas s ON seq.schema_id = s.schema_id "
                    + "LEFT JOIN sys.objects o ON seq.object_id = o.object_id "
                    + "WHERE DB_NAME() = ? "
                    + "    AND s.name = ? "
                    + "    AND seq.name = ?";

            AtomicInteger rowCount = new AtomicInteger(0);
            jdbcOperations.query(sql, new Object[] {databaseName, actualSchemaName, sequenceName}, rs -> {
                rowCount.incrementAndGet();
                sequence.setStartValue(rs.getBigDecimal("start_value").toString());
                sequence.setIncreament(rs.getBigDecimal("increment").longValue());
                sequence.setMinValue(rs.getBigDecimal("minimum_value").toString());
                sequence.setMaxValue(rs.getBigDecimal("maximum_value").toString());
                sequence.setNextCacheValue(rs.getBigDecimal("current_value").toString());
                sequence.setCycled(rs.getBoolean("is_cycling"));

                Long cacheSize = rs.getBigDecimal("cache_size").longValue();
                if (cacheSize > 1) {
                    sequence.setCacheSize(cacheSize);
                    sequence.setCached(true);
                } else {
                    sequence.setCached(false);
                }

                // SQL Server 序列默认是有序的
                sequence.setOrderd(true);
            });

            // 如果没有查询到结果，返回 null
            if (rowCount.get() == 0) {
                log.warn("Sequence not found: " + schemaName + "." + sequenceName);
                return null;
            }

            // 构建序列的 DDL
            StringBuilder ddl = new StringBuilder();
            ddl.append("CREATE SEQUENCE ");
            if (StringUtils.isNotEmpty(actualSchemaName)) {
                ddl.append("[").append(actualSchemaName).append("].");
            }
            ddl.append("[").append(sequenceName).append("] ");
            ddl.append("AS ").append(sequence.getMinValue().contains(".") ? "DECIMAL" : "BIGINT").append(" ");
            ddl.append("START WITH ").append(sequence.getStartValue()).append(" ");
            ddl.append("INCREMENT BY ").append(sequence.getIncreament()).append(" ");
            ddl.append("MINVALUE ").append(sequence.getMinValue()).append(" ");
            ddl.append("MAXVALUE ").append(sequence.getMaxValue()).append(" ");

            if (sequence.getCycled()) {
                ddl.append("CYCLE ");
            } else {
                ddl.append("NO CYCLE ");
            }

            if (sequence.getCached() && sequence.getCacheSize() != null) {
                ddl.append("CACHE ").append(sequence.getCacheSize()).append(" ");
            } else {
                ddl.append("NO CACHE ");
            }

            sequence.setDdl(ddl.toString().trim());

            return sequence;
        } catch (BadSqlGrammarException e) {
            // 如果 sys.sequences 不存在（SQL Server 2008 及更早版本），返回 null
            if (StringUtils.containsIgnoreCase(e.getMessage(), "Invalid object name") ||
                    StringUtils.containsIgnoreCase(e.getMessage(), "Invalid schema") ||
                    StringUtils.containsIgnoreCase(e.getMessage(), "sequence")) {
                log.warn("Sequence not found or not supported: " + schemaName + "." + sequenceName);
                return null;
            }
            throw e;
        } catch (Exception e) {
            log.warn("Failed to get sequence: " + schemaName + "." + sequenceName, e);
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

    /**
     * 获取同义词信息
     * <p>
     * <b>SQL Server 特殊处理：</b> schemaName 参数支持两种格式： 1. "database" - 只有数据库名，默认使用 dbo schema（兼容旧代码） 2.
     * "database.schema" - 完整的 database.schema 格式（从 showDatabases() 返回的格式） 为了统一抽象层，SQL Server 将
     * database.schema 作为 schema 处理，与 MySQL 保持一致。
     * </p>
     *
     * @param schemaName 数据库名或 database.schema 格式
     * @param synonymName 同义词名
     * @param synonymType 同义词类型
     * @return 同义词信息
     */
    @Override
    public DBSynonym getSynonym(String schemaName, String synonymName, DBSynonymType synonymType) {
        // 解析 database.schema 格式
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = SqlServerSchemaUtil.getDatabaseName(dbAndSchema[0], jdbcOperations);
        String actualSchemaName = dbAndSchema[1];

        // SQL Server 中的同义词都是 schema 级别的，没有 PUBLIC 同义词的概念
        if (DBSynonymType.PUBLIC.equals(synonymType)) {
            log.warn("SQL Server does not support PUBLIC synonyms: " + synonymName);
            return null;
        }

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

        DBSynonym synonym = new DBSynonym();
        synonym.setSynonymName(synonymName);
        synonym.setSynonymType(synonymType);

        try {
            // 查询同义词信息
            String sql = "SELECT "
                    + "    syn.name AS synonym_name, "
                    + "    s.name AS schema_name, "
                    + "    syn.base_object_name, "
                    + "    o.create_date, "
                    + "    o.modify_date "
                    + "FROM sys.synonyms syn "
                    + "INNER JOIN sys.schemas s ON syn.schema_id = s.schema_id "
                    + "LEFT JOIN sys.objects o ON syn.object_id = o.object_id "
                    + "WHERE DB_NAME() = ? "
                    + "    AND s.name = ? "
                    + "    AND syn.name = ?";

            AtomicReference<String> baseObjectName = new AtomicReference<>();
            AtomicInteger rowCount = new AtomicInteger(0);

            jdbcOperations.query(sql, new Object[] {databaseName, actualSchemaName, synonymName}, rs -> {
                rowCount.incrementAndGet();
                synonym.setOwner(rs.getString("schema_name"));
                baseObjectName.set(rs.getString("base_object_name"));

                Timestamp createDate = rs.getTimestamp("create_date");
                Timestamp modifyDate = rs.getTimestamp("modify_date");
                if (createDate != null) {
                    synonym.setCreated(createDate);
                }
                if (modifyDate != null) {
                    synonym.setLastDdlTime(modifyDate);
                }
            });

            // 如果没有查询到结果，返回 null
            if (rowCount.get() == 0) {
                log.warn("Synonym not found: " + schemaName + "." + synonymName);
                return null;
            }

            // 解析 base_object_name
            // SQL Server 同义词的 base_object_name 格式可能是：
            // - [server].[database].[schema].[object]
            // - [database].[schema].[object]
            // - [schema].[object]
            // - [object]
            if (StringUtils.isNotBlank(baseObjectName.get())) {
                String[] parts = baseObjectName.get().replace("[", "").replace("]", "").split("\\.");
                if (parts.length >= 2) {
                    // 假设最后两部分是 schema 和 object
                    synonym.setTableOwner(parts[parts.length - 2]);
                    synonym.setTableName(parts[parts.length - 1]);
                } else if (parts.length == 1) {
                    // 只有对象名，可能是当前 schema
                    synonym.setTableOwner(actualSchemaName);
                    synonym.setTableName(parts[0]);
                }
            }

            synonym.setStatus("VALID"); // SQL Server 中同义词没有无效状态的概念

            // 构建同义词的 DDL
            StringBuilder ddl = new StringBuilder();
            ddl.append("CREATE SYNONYM ");
            if (StringUtils.isNotEmpty(actualSchemaName)) {
                ddl.append("[").append(actualSchemaName).append("].");
            }
            ddl.append("[").append(synonymName).append("] ");
            ddl.append("FOR ");
            if (StringUtils.isNotBlank(baseObjectName.get())) {
                ddl.append(baseObjectName.get());
            } else {
                ddl.append("[");
                if (StringUtils.isNotBlank(synonym.getTableOwner())) {
                    ddl.append(synonym.getTableOwner()).append("].");
                }
                ddl.append("[").append(synonym.getTableName()).append("]");
            }

            synonym.setDdl(ddl.toString());

            return synonym;
        } catch (BadSqlGrammarException e) {
            if (StringUtils.containsIgnoreCase(e.getMessage(), "Invalid object name") ||
                    StringUtils.containsIgnoreCase(e.getMessage(), "Invalid schema")) {
                log.warn("Synonym not found: " + schemaName + "." + synonymName);
                return null;
            }
            throw e;
        } catch (Exception e) {
            log.warn("Failed to get synonym: " + schemaName + "." + synonymName, e);
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

    /**
     * 获取指定 schema 下多个表的详细信息
     * <p>
     * <b>SQL Server 特殊处理：</b> schemaName 参数支持两种格式： 1. "database" - 只有数据库名，默认使用 dbo schema（兼容旧代码） 2.
     * "database.schema" - 完整的 database.schema 格式（从 showDatabases() 返回的格式） 为了统一抽象层，SQL Server 将
     * database.schema 作为 schema 处理，与 MySQL 保持一致。
     * </p>
     *
     * @param schemaName 数据库名或 database.schema 格式
     * @param tableNames 表名列表，为 null 或空时返回所有表
     * @return 表名到表对象的映射
     */
    @Override
    public Map<String, DBTable> getTables(@NonNull String schemaName, List<String> tableNames) {
        Map<String, DBTable> returnVal = new HashMap<>();

        // 如果 tableNames 为空，获取所有表
        if (tableNames == null || tableNames.isEmpty()) {
            tableNames = showTables(schemaName);
        }

        if (tableNames.isEmpty()) {
            return returnVal;
        }

        // 批量获取 DDL
        Map<String, String> tableName2Ddl = new HashMap<>();
        for (String tableName : tableNames) {
            try {
                String ddl = getTableDDL(schemaName, tableName);
                if (StringUtils.isNotBlank(ddl)) {
                    tableName2Ddl.put(tableName, ddl);
                }
            } catch (Exception e) {
                log.warn("Failed to get DDL for table: " + schemaName + "." + tableName, e);
            }
        }

        // 批量获取列、索引、约束、选项等信息
        Map<String, List<DBTableColumn>> tableName2Columns = listTableColumns(schemaName, tableNames);
        Map<String, List<DBTableIndex>> tableName2Indexes = listTableIndexes(schemaName);
        Map<String, List<DBTableConstraint>> tableName2Constraints = listTableConstraints(schemaName);
        Map<String, DBTableOptions> tableName2Options = listTableOptions(schemaName);

        // 组装 DBTable 对象
        for (String tableName : tableNames) {
            if (!tableName2Columns.containsKey(tableName)) {
                continue;
            }

            DBTable table = new DBTable();
            table.setSchemaName(schemaName);
            table.setOwner(schemaName);
            table.setName(tableName);
            table.setColumns(tableName2Columns.getOrDefault(tableName, new ArrayList<>()));
            table.setIndexes(tableName2Indexes.getOrDefault(tableName, new ArrayList<>()));
            table.setConstraints(tableName2Constraints.getOrDefault(tableName, new ArrayList<>()));
            table.setTableOptions(tableName2Options.getOrDefault(tableName, new DBTableOptions()));

            // 设置分区信息
            try {
                table.setPartition(getPartition(schemaName, tableName));
            } catch (Exception e) {
                log.warn("Failed to set table partition for table: " + schemaName + "." + tableName, e);
            }

            // 设置 DDL
            table.setDDL(tableName2Ddl.get(tableName));

            returnVal.put(tableName, table);
        }

        return returnVal;
    }
}
