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
package com.oceanbase.tools.dbbrowser.editor.sqlserver;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.apache.commons.collections4.CollectionUtils;

import com.oceanbase.tools.dbbrowser.editor.DBTableIndexEditor;
import com.oceanbase.tools.dbbrowser.model.DBTableIndex;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlServerSqlBuilder;
import com.oceanbase.tools.dbbrowser.util.StringUtils;

/**
 * @author yizhou.xw
 * @date 2024/12
 * @since ODC_release_4.3.4
 */
public class SqlServerIndexEditor extends DBTableIndexEditor {

    @Override
    protected SqlBuilder sqlBuilder() {
        return new SqlServerSqlBuilder();
    }

    @Override
    public boolean editable() {
        return true;
    }

    @Override
    public String generateCreateObjectDDL(@NotNull DBTableIndex index) {
        SqlBuilder sqlBuilder = sqlBuilder();
        // SQL Server 的 CREATE INDEX 不支持三部分名称，需要先使用 USE 语句切换数据库
        prependUseStatementIfNeeded(index, sqlBuilder);
        sqlBuilder.append("CREATE ");
        // SQL Server 中，NORMAL 类型不需要输出，只有 CLUSTERED、UNIQUE 等需要输出
        if (index.getType() != null && !"NORMAL".equals(index.getType().getValue())) {
            sqlBuilder.append(index.getType().getValue()).space();
        }
        sqlBuilder.append("INDEX ").identifier(index.getName()).append(" ON ")
                .append(getTableNameWithoutDatabase(index))
                .append(" (");
        List<String> columnNames = index.getColumnNames().stream()
                .map(columnName -> StringUtils.quoteSqlServerIdentifier(columnName))
                .collect(Collectors.toList());
        sqlBuilder.append(String.join(", ", columnNames)).append(")").append(";");
        return sqlBuilder.toString();
    }

    @Override
    public String generateDropObjectDDL(@NotNull DBTableIndex index) {
        SqlBuilder sqlBuilder = sqlBuilder();
        // SQL Server 的 DROP INDEX 不支持三部分名称，需要先使用 USE 语句切换数据库
        prependUseStatementIfNeeded(index, sqlBuilder);
        sqlBuilder.append("DROP INDEX ").identifier(index.getName()).append(" ON ")
                .append(getTableNameWithoutDatabase(index)).append(";");
        return sqlBuilder.toString();
    }

    @Override
    protected void appendIndexColumnModifiers(DBTableIndex index, SqlBuilder sqlBuilder) {
        // SQL Server does not have special modifiers for index columns like MySQL's length
    }

    @Override
    protected void appendIndexOptions(DBTableIndex index, SqlBuilder sqlBuilder) {
        // SQL Server index options like FILLFACTOR can be added here
    }

    @Override
    public String generateRenameObjectDDL(@NotNull DBTableIndex oldIndex, @NotNull DBTableIndex newIndex) {
        SqlBuilder sqlBuilder = sqlBuilder();
        String schemaName = oldIndex.getSchemaName();
        String table = oldIndex.getTableName();

        // 解析数据库名和 schema 名
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = dbAndSchema[0];
        String actualSchemaName = dbAndSchema[1];

        // sp_rename 只能在当前数据库中执行，需要先切换到目标数据库
        // 如果有数据库名，添加 USE [database] 语句
        if (StringUtils.isNotBlank(databaseName)) {
            sqlBuilder.append("USE ").identifier(databaseName).append(";").line();
        }

        // 构建对象名称：schema.table.index（不使用数据库名，因为已经切换到目标数据库）
        StringBuilder objectNameBuilder = new StringBuilder();
        // 使用解析后的 actualSchemaName（当只有数据库名时，parseDatabaseAndSchema 会返回 "dbo"）
        if (StringUtils.isNotBlank(actualSchemaName)) {
            objectNameBuilder.append(actualSchemaName).append(".");
        }
        if (StringUtils.isNotBlank(table)) {
            objectNameBuilder.append(table).append(".");
        }
        objectNameBuilder.append(oldIndex.getName());

        sqlBuilder.append("EXEC sp_rename ").value(objectNameBuilder.toString())
                .append(", ").value(newIndex.getName()).append(", ").value("INDEX");
        return sqlBuilder.toString();
    }

    @Override
    protected String getFullyQualifiedTableName(@NotNull DBTableIndex index) {
        SqlBuilder sqlBuilder = sqlBuilder();
        String schemaName = index.getSchemaName();

        // 解析数据库名和 schema 名
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = dbAndSchema[0];
        String actualSchemaName = dbAndSchema[1];

        // SQL Server 三部分名称：database.schema.table
        if (StringUtils.isNotBlank(databaseName)) {
            sqlBuilder.identifier(databaseName).append(".");
        }
        if (StringUtils.isNotBlank(actualSchemaName)) {
            sqlBuilder.identifier(actualSchemaName).append(".");
        }
        if (StringUtils.isNotBlank(index.getTableName())) {
            sqlBuilder.identifier(index.getTableName());
        }
        return sqlBuilder.toString();
    }

    /**
     * 获取不带数据库名的表名（用于 CREATE INDEX 和 DROP INDEX，因为它们不支持三部分名称） SQL Server 的 CREATE INDEX 和 DROP INDEX 只支持
     * schema.table 格式 如果需要跨数据库操作，需要在 SQL 前添加 USE [database] 语句
     *
     * @param index 索引对象
     * @return schema.table 格式的表名
     */
    private String getTableNameWithoutDatabase(@NotNull DBTableIndex index) {
        SqlBuilder sqlBuilder = sqlBuilder();
        String schemaName = index.getSchemaName();

        // 解析数据库名和 schema 名
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String actualSchemaName = dbAndSchema[1];

        // 只使用 schema.table 格式（不支持 database.schema.table）
        if (StringUtils.isNotBlank(actualSchemaName)) {
            sqlBuilder.identifier(actualSchemaName).append(".");
        }
        if (StringUtils.isNotBlank(index.getTableName())) {
            sqlBuilder.identifier(index.getTableName());
        }
        return sqlBuilder.toString();
    }

    /**
     * 如果需要，在 SQL 前添加 USE [database] 语句 SQL Server 的 CREATE INDEX 和 DROP INDEX
     * 不支持三部分名称（database.schema.table） 因此需要先切换到目标数据库
     *
     * @param index 索引对象
     * @param sqlBuilder SQL 构建器
     */
    private void prependUseStatementIfNeeded(@NotNull DBTableIndex index, SqlBuilder sqlBuilder) {
        String schemaName = index.getSchemaName();
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = dbAndSchema[0];

        // 如果有数据库名，添加 USE 语句
        if (StringUtils.isNotBlank(databaseName)) {
            sqlBuilder.append("USE ").identifier(databaseName).append(";").line();
        }
    }

    /**
     * 解析 schemaName，支持两种格式： 1. "database" - 只有数据库名，默认使用 dbo schema 2. "database.schema" - 数据库名和 schema
     * 名
     *
     * @param schemaName 可能是数据库名或 database.schema 格式
     * @return [数据库名, schema名] 数组
     */
    private String[] parseDatabaseAndSchema(String schemaName) {
        if (StringUtils.isBlank(schemaName)) {
            return new String[] {null, "dbo"};
        }

        if (schemaName.contains(".")) {
            String[] parts = schemaName.split("\\.", 2);
            return new String[] {parts[0], parts[1]};
        } else {
            // 只有数据库名，默认使用 dbo schema
            return new String[] {schemaName, "dbo"};
        }
    }

    /**
     * Override to compare only DDL-affecting fields, ignoring metadata fields. This prevents
     * unnecessary DROP + CREATE when only metadata (e.g., cardinality) changes, while still detecting
     * real structural changes (e.g., columnNames, type).
     */
    @Override
    public String generateUpdateObjectDDL(@NotNull DBTableIndex oldIndex, @NotNull DBTableIndex newIndex) {
        SqlBuilder sqlBuilder = sqlBuilder();

        // Only compare fields that affect the actual DDL structure
        boolean needsUpdate = false;

        // 1. Compare index type (CLUSTERED/NONCLUSTERED)
        if (!Objects.equals(oldIndex.getType(), newIndex.getType())) {
            needsUpdate = true;
        }

        // 2. Compare column names (most important - if columns change, index must be rebuilt)
        if (!Objects.equals(oldIndex.getColumnNames(), newIndex.getColumnNames())) {
            needsUpdate = true;
        }

        // 3. Compare uniqueness (affects whether UNIQUE keyword is needed)
        if (!Objects.equals(oldIndex.getUnique(), newIndex.getUnique())
                || oldIndex.isNonUnique() != newIndex.isNonUnique()) {
            needsUpdate = true;
        }

        // 4. Compare primary key attribute (affects index structure)
        if (!Objects.equals(oldIndex.getPrimary(), newIndex.getPrimary())) {
            needsUpdate = true;
        }

        // If structural changes detected, generate DROP + CREATE
        if (needsUpdate) {
            // 对于 DROP + CREATE 组合，只需要在第一个语句前添加 USE
            // 因为两个语句都在同一个数据库中执行
            String drop = generateDropObjectDDL(oldIndex);
            // 生成 CREATE 语句时，如果 DROP 已经包含了 USE，则不需要再次添加
            // 这里我们需要手动构建 CREATE 语句，避免重复添加 USE
            SqlBuilder createBuilder = sqlBuilder();
            createBuilder.append("CREATE ");
            if (newIndex.getType() != null && !"NORMAL".equals(newIndex.getType().getValue())) {
                createBuilder.append(newIndex.getType().getValue()).space();
            }
            createBuilder.append("INDEX ").identifier(newIndex.getName()).append(" ON ")
                    .append(getTableNameWithoutDatabase(newIndex))
                    .append(" (");
            List<String> columnNames = newIndex.getColumnNames().stream()
                    .map(columnName -> StringUtils.quoteSqlServerIdentifier(columnName))
                    .collect(Collectors.toList());
            createBuilder.append(String.join(", ", columnNames)).append(")").append(";");
            sqlBuilder.append(drop).space().append(createBuilder.toString());
            return sqlBuilder.toString();
        }

        // Handle index rename (if only name changed, not structure)
        if (!StringUtils.equals(oldIndex.getName(), newIndex.getName())) {
            sqlBuilder.append(generateRenameObjectDDL(oldIndex, newIndex)).append(";");
        }

        // Handle visibility changes (SQLServer doesn't support index visibility, so skip)
        // Note: SQLServer doesn't have INVISIBLE/VISIBLE index feature like MySQL/Oracle

        return sqlBuilder.toString();
    }

    /**
     * Override to use index name matching instead of ordinalPosition matching. SQLServer's
     * ordinalPosition is generated by code counter which is not stable, especially when table structure
     * changes (e.g., adding columns). Using index name matching is more reliable since index names are
     * unique within a table.
     */
    @Override
    public String generateUpdateObjectListDDL(Collection<DBTableIndex> oldIndexes,
            Collection<DBTableIndex> newIndexes) {
        SqlBuilder sqlBuilder = sqlBuilder();
        if (CollectionUtils.isEmpty(oldIndexes)) {
            if (CollectionUtils.isNotEmpty(newIndexes)) {
                newIndexes.forEach(index -> sqlBuilder.append(generateCreateObjectDDL(index)));
            }
            return sqlBuilder.toString();
        }
        if (CollectionUtils.isEmpty(newIndexes)) {
            if (CollectionUtils.isNotEmpty(oldIndexes)) {
                oldIndexes.forEach(index -> sqlBuilder.append(generateDropObjectDDL(index)));
            }
            return sqlBuilder.toString();
        }

        // Use index name for matching instead of ordinalPosition
        Map<String, DBTableIndex> name2OldIndex = new HashMap<>();
        Map<String, DBTableIndex> name2NewIndex = new HashMap<>();
        // 用于检测重命名：通过结构匹配来找到重命名的索引
        Map<String, DBTableIndex> structure2OldIndex = new HashMap<>();

        oldIndexes.forEach(oldIndex -> {
            if (StringUtils.isNotEmpty(oldIndex.getName())) {
                name2OldIndex.put(oldIndex.getName(), oldIndex);
            }
            // 创建结构键用于匹配重命名
            String structureKey = buildIndexStructureKey(oldIndex);
            if (StringUtils.isNotEmpty(structureKey)) {
                structure2OldIndex.put(structureKey, oldIndex);
            }
        });
        newIndexes.forEach(newIndex -> {
            if (StringUtils.isNotEmpty(newIndex.getName())) {
                name2NewIndex.put(newIndex.getName(), newIndex);
            }
        });

        for (DBTableIndex newIndex : newIndexes) {
            if (StringUtils.isEmpty(newIndex.getName())) {
                // If index name is empty, treat it as a new index
                String ddl = generateCreateObjectDDL(newIndex);
                if (sqlBuilder.length() > 0) {
                    sqlBuilder.space();
                }
                sqlBuilder.append(ddl);
                if (!ddl.trim().endsWith(";")) {
                    sqlBuilder.append(";");
                }
            } else if (!name2OldIndex.containsKey(newIndex.getName())) {
                // Check if this is a renamed index (same structure, different name)
                String structureKey = buildIndexStructureKey(newIndex);
                if (StringUtils.isNotEmpty(structureKey) && structure2OldIndex.containsKey(structureKey)) {
                    // This is a renamed index, use sp_rename
                    DBTableIndex oldIndex = structure2OldIndex.get(structureKey);
                    if (sqlBuilder.length() > 0) {
                        sqlBuilder.space();
                    }
                    sqlBuilder.append(generateRenameObjectDDL(oldIndex, newIndex)).append(";");
                } else {
                    // This is a new index
                    String ddl = generateCreateObjectDDL(newIndex);
                    if (sqlBuilder.length() > 0) {
                        sqlBuilder.space();
                    }
                    sqlBuilder.append(ddl);
                    if (!ddl.trim().endsWith(";")) {
                        sqlBuilder.append(";");
                    }
                }
            } else {
                // This is an existing index, check if it needs to be updated
                String ddl = generateUpdateObjectDDL(name2OldIndex.get(newIndex.getName()), newIndex);
                if (!ddl.isEmpty()) {
                    if (sqlBuilder.length() > 0) {
                        sqlBuilder.space();
                    }
                    sqlBuilder.append(ddl);
                    // 如果 DDL 不为空且不以分号结尾，添加分号
                    if (!ddl.trim().endsWith(";")) {
                        sqlBuilder.append(";");
                    }
                }
            }
        }

        for (DBTableIndex oldIndex : oldIndexes) {
            // Check if this index should be dropped
            // Skip if it was renamed (matched by structure)
            if (StringUtils.isEmpty(oldIndex.getName())
                    || (!name2NewIndex.containsKey(oldIndex.getName())
                            && !isIndexRenamed(oldIndex, newIndexes))) {
                String ddl = generateDropObjectDDL(oldIndex);
                if (sqlBuilder.length() > 0) {
                    sqlBuilder.space();
                }
                sqlBuilder.append(ddl);
                if (!ddl.trim().endsWith(";")) {
                    sqlBuilder.append(";");
                }
            }
        }

        return sqlBuilder.toString();
    }

    /**
     * 构建索引结构键，用于匹配重命名的索引 结构键包括：类型、列名、唯一性、主键属性
     */
    private String buildIndexStructureKey(DBTableIndex index) {
        if (index == null) {
            return null;
        }
        StringBuilder key = new StringBuilder();
        key.append(index.getType() != null ? index.getType().name() : "NORMAL");
        key.append("|");
        if (index.getColumnNames() != null) {
            key.append(String.join(",", index.getColumnNames()));
        }
        key.append("|");
        key.append(index.getUnique() != null ? index.getUnique() : false);
        key.append("|");
        key.append(index.getPrimary() != null ? index.getPrimary() : false);
        return key.toString();
    }

    /**
     * 检查索引是否被重命名（通过结构匹配）
     */
    private boolean isIndexRenamed(DBTableIndex oldIndex, Collection<DBTableIndex> newIndexes) {
        String oldStructureKey = buildIndexStructureKey(oldIndex);
        if (StringUtils.isEmpty(oldStructureKey)) {
            return false;
        }
        for (DBTableIndex newIndex : newIndexes) {
            String newStructureKey = buildIndexStructureKey(newIndex);
            if (oldStructureKey.equals(newStructureKey)
                    && !StringUtils.equals(oldIndex.getName(), newIndex.getName())) {
                return true;
            }
        }
        return false;
    }

}
