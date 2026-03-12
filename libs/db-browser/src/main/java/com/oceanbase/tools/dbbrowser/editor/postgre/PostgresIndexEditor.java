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
package com.oceanbase.tools.dbbrowser.editor.postgre;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.validation.constraints.NotNull;

import org.apache.commons.collections4.CollectionUtils;

import com.oceanbase.tools.dbbrowser.editor.DBTableIndexEditor;
import com.oceanbase.tools.dbbrowser.model.DBIndexType;
import com.oceanbase.tools.dbbrowser.model.DBTableIndex;
import com.oceanbase.tools.dbbrowser.util.PostgresSqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.StringUtils;

/**
 * PostgreSQL 索引编辑器
 *
 * <p>
 * PostgreSQL 索引语法特点：
 * </p>
 * <ul>
 * <li>创建索引：CREATE [UNIQUE] INDEX "idx_name" ON "schema"."table" USING btree ("col1", "col2");</li>
 * <li>删除索引：DROP INDEX "schema"."idx_name";（索引是 Schema 级别的独立对象，不需要 ON table）</li>
 * <li>重命名索引：ALTER INDEX "schema"."old_name" RENAME TO "new_name";</li>
 * </ul>
 *
 * <p>
 * PostgreSQL 支持多种索引类型：btree（默认）、hash、gin、gist、spgist、brin
 * </p>
 *
 * @author odc
 * @since ODC_release_4.3.4
 */
public class PostgresIndexEditor extends DBTableIndexEditor {

    @Override
    protected SqlBuilder sqlBuilder() {
        return new PostgresSqlBuilder();
    }

    @Override
    public boolean editable() {
        return true;
    }

    /**
     * 生成创建索引的 DDL 语句
     *
     * <p>
     * PostgreSQL 创建索引语法： CREATE [UNIQUE] INDEX "idx_name" ON "schema"."table" USING btree ("col1",
     * "col2");
     * </p>
     *
     * @param index 索引定义
     * @return 生成的 DDL 语句字符串
     */
    @Override
    public String generateCreateObjectDDL(@NotNull DBTableIndex index) {
        SqlBuilder sqlBuilder = sqlBuilder();
        sqlBuilder.append("CREATE ");

        // UNIQUE 关键字
        if (index.getType() == DBIndexType.UNIQUE || Boolean.TRUE.equals(index.getUnique())) {
            sqlBuilder.append("UNIQUE ");
        }

        sqlBuilder.append("INDEX ").identifier(index.getName())
                .append(" ON ").append(getFullyQualifiedTableName(index));

        // 索引类型（USING 子句）
        appendIndexType(index, sqlBuilder);

        sqlBuilder.append(" (");
        appendIndexColumns(index, sqlBuilder);
        sqlBuilder.append(")");

        // 索引选项
        appendIndexOptions(index, sqlBuilder);

        sqlBuilder.append(";\n");
        return sqlBuilder.toString();
    }

    /**
     * 生成删除索引的 DDL 语句
     *
     * <p>
     * PostgreSQL 中索引是 Schema 级别的独立对象，DROP INDEX 不需要指定 ON table。 语法：DROP INDEX "schema"."index_name";
     * </p>
     *
     * @param index 索引定义
     * @return 生成的 DDL 语句字符串
     */
    @Override
    public String generateDropObjectDDL(@NotNull DBTableIndex index) {
        SqlBuilder sqlBuilder = sqlBuilder();
        sqlBuilder.append("DROP INDEX ");

        // PostgreSQL 索引是 Schema 级别对象，使用 schema.index_name 格式
        if (StringUtils.isNotEmpty(index.getSchemaName())) {
            sqlBuilder.identifier(index.getSchemaName()).append(".");
        }
        sqlBuilder.identifier(index.getName()).append(";\n");

        return sqlBuilder.toString();
    }

    /**
     * 生成重命名索引的 DDL 语句
     *
     * <p>
     * PostgreSQL 重命名索引语法：ALTER INDEX "schema"."old_name" RENAME TO "new_name";
     * </p>
     *
     * @param oldIndex 重命名前的索引定义
     * @param newIndex 重命名后的索引定义
     * @return 生成的 DDL 语句字符串
     */
    @Override
    public String generateRenameObjectDDL(@NotNull DBTableIndex oldIndex, @NotNull DBTableIndex newIndex) {
        SqlBuilder sqlBuilder = sqlBuilder();
        sqlBuilder.append("ALTER INDEX ");

        // 使用 schema.index_name 格式
        if (StringUtils.isNotEmpty(oldIndex.getSchemaName())) {
            sqlBuilder.identifier(oldIndex.getSchemaName()).append(".");
        }
        sqlBuilder.identifier(oldIndex.getName())
                .append(" RENAME TO ").identifier(newIndex.getName()).append(";");

        return sqlBuilder.toString();
    }

    /**
     * 生成更新索引的 DDL 语句
     *
     * <p>
     * PostgreSQL 的索引修改策略：
     * <ul>
     * <li>结构性变更（列、类型、唯一性等）：需要 DROP + CREATE</li>
     * <li>仅名称变更：使用 ALTER INDEX ... RENAME TO</li>
     * </ul>
     * </p>
     *
     * @param oldIndex 修改前的索引定义
     * @param newIndex 修改后的索引定义
     * @return 生成的 DDL 语句字符串
     */
    @Override
    public String generateUpdateObjectDDL(@NotNull DBTableIndex oldIndex, @NotNull DBTableIndex newIndex) {
        SqlBuilder sqlBuilder = sqlBuilder();

        // 检查是否有结构性变更
        boolean needsRebuild = hasStructuralChange(oldIndex, newIndex);

        if (needsRebuild) {
            // 结构性变更需要 DROP + CREATE
            String drop = generateDropObjectDDL(oldIndex);
            String create = generateCreateObjectDDL(newIndex);
            sqlBuilder.append(drop).append(create);
        } else if (!StringUtils.equals(oldIndex.getName(), newIndex.getName())) {
            // 仅名称变更，使用 RENAME
            sqlBuilder.append(generateRenameObjectDDL(oldIndex, newIndex)).append("\n");
        }

        return sqlBuilder.toString();
    }

    /**
     * 批量更新索引的 DDL 生成
     *
     * <p>
     * 使用索引名称进行匹配，而不是 ordinalPosition，因为名称更稳定可靠。
     * </p>
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

        // 使用名称匹配
        Map<String, DBTableIndex> name2OldIndex = new HashMap<>();
        Map<String, DBTableIndex> name2NewIndex = new HashMap<>();
        Map<String, DBTableIndex> structure2OldIndex = new HashMap<>();

        oldIndexes.forEach(oldIndex -> {
            if (StringUtils.isNotEmpty(oldIndex.getName())) {
                name2OldIndex.put(oldIndex.getName(), oldIndex);
            }
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

        // 处理新增和修改的索引
        for (DBTableIndex newIndex : newIndexes) {
            if (StringUtils.isEmpty(newIndex.getName())) {
                // 无名称视为新索引
                sqlBuilder.append(generateCreateObjectDDL(newIndex));
            } else if (!name2OldIndex.containsKey(newIndex.getName())) {
                // 检查是否为重命名
                String structureKey = buildIndexStructureKey(newIndex);
                if (StringUtils.isNotEmpty(structureKey) && structure2OldIndex.containsKey(structureKey)) {
                    // 结构相同，名称不同 -> 重命名
                    DBTableIndex oldIndex = structure2OldIndex.get(structureKey);
                    sqlBuilder.append(generateRenameObjectDDL(oldIndex, newIndex)).append(";\n");
                } else {
                    // 新索引
                    sqlBuilder.append(generateCreateObjectDDL(newIndex));
                }
            } else {
                // 已存在的索引，检查是否需要更新
                String ddl = generateUpdateObjectDDL(name2OldIndex.get(newIndex.getName()), newIndex);
                if (StringUtils.isNotEmpty(ddl)) {
                    sqlBuilder.append(ddl);
                }
            }
        }

        // 处理删除的索引
        for (DBTableIndex oldIndex : oldIndexes) {
            if (StringUtils.isEmpty(oldIndex.getName())
                    || (!name2NewIndex.containsKey(oldIndex.getName())
                            && !isIndexRenamed(oldIndex, newIndexes))) {
                sqlBuilder.append(generateDropObjectDDL(oldIndex));
            }
        }

        return sqlBuilder.toString();
    }

    /**
     * 追加索引类型（USING 子句）
     *
     * <p>
     * PostgreSQL 支持多种索引类型：btree（默认）、hash、gin、gist、spgist、brin
     * </p>
     */
    @Override
    protected void appendIndexType(DBTableIndex index, SqlBuilder sqlBuilder) {
        // 从 DBIndexType 获取 PostgreSQL 索引方法名
        String indexMethod = getIndexMethod(index);
        if (StringUtils.isNotEmpty(indexMethod)) {
            sqlBuilder.append(" USING ").append(indexMethod);
        }
    }

    /**
     * 追加索引列修饰符
     */
    @Override
    protected void appendIndexColumnModifiers(DBTableIndex index, SqlBuilder sqlBuilder) {
        // PostgreSQL 基本不需要额外的列修饰符
        // 如需支持表达式索引或排序（ASC/DESC），可在此扩展
    }

    /**
     * 追加索引选项
     */
    @Override
    protected void appendIndexOptions(DBTableIndex index, SqlBuilder sqlBuilder) {
        // PostgreSQL 索引选项如 WITH (fillfactor = 70), TABLESPACE 等
        // 目前暂不实现，可根据需要扩展
    }

    /**
     * 检查是否有结构性变更
     *
     * <p>
     * 比较影响 DDL 结构的字段：索引类型、列名、唯一性
     * </p>
     */
    private boolean hasStructuralChange(DBTableIndex oldIndex, DBTableIndex newIndex) {
        // 比较索引类型
        if (!Objects.equals(oldIndex.getType(), newIndex.getType())) {
            return true;
        }

        // 比较列名
        if (!Objects.equals(oldIndex.getColumnNames(), newIndex.getColumnNames())) {
            return true;
        }

        // 比较唯一性
        if (!Objects.equals(oldIndex.getUnique(), newIndex.getUnique())
                || oldIndex.isNonUnique() != newIndex.isNonUnique()) {
            return true;
        }

        return false;
    }

    /**
     * 获取 PostgreSQL 索引方法名
     */
    private String getIndexMethod(DBTableIndex index) {
        if (index.getType() == null) {
            return "btree"; // 默认使用 btree
        }
        switch (index.getType()) {
            case UNIQUE:
                return "btree"; // UNIQUE INDEX 默认也使用 btree
            case FULLTEXT:
                return "gin"; // 全文索引使用 gin
            case NORMAL:
            default:
                return "btree";
        }
    }

    /**
     * 构建索引结构键，用于匹配重命名的索引
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
        return key.toString();
    }

    /**
     * 检查索引是否被重命名
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
