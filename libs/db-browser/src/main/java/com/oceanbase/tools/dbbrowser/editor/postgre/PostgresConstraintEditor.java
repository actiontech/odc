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
import org.apache.commons.lang3.StringUtils;

import com.oceanbase.tools.dbbrowser.editor.DBTableConstraintEditor;
import com.oceanbase.tools.dbbrowser.model.DBConstraintType;
import com.oceanbase.tools.dbbrowser.model.DBForeignKeyModifyRule;
import com.oceanbase.tools.dbbrowser.model.DBTableConstraint;
import com.oceanbase.tools.dbbrowser.util.PostgresSqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;

/**
 * PostgreSQL 约束编辑器
 *
 * <p>
 * PostgreSQL 约束语法特点：
 * </p>
 * <ul>
 * <li>添加约束：ALTER TABLE "schema"."table" ADD CONSTRAINT "name" PRIMARY KEY/UNIQUE/CHECK/FOREIGN KEY
 * (...);</li>
 * <li>删除约束：ALTER TABLE "schema"."table" DROP CONSTRAINT "name";</li>
 * <li>重命名约束：ALTER TABLE "schema"."table" RENAME CONSTRAINT "old" TO "new";</li>
 * </ul>
 *
 * <p>
 * PostgreSQL 支持以下约束类型：
 * <ul>
 * <li>PRIMARY KEY - 主键约束</li>
 * <li>UNIQUE - 唯一约束</li>
 * <li>FOREIGN KEY - 外键约束</li>
 * <li>CHECK - 检查约束</li>
 * </ul>
 * </p>
 *
 * @author odc
 * @since ODC_release_4.3.4
 */
public class PostgresConstraintEditor extends DBTableConstraintEditor {

    @Override
    protected SqlBuilder sqlBuilder() {
        return new PostgresSqlBuilder();
    }

    /**
     * 生成添加约束的 DDL 语句
     *
     * <p>
     * PostgreSQL 添加约束语法： ALTER TABLE "schema"."table" ADD CONSTRAINT "name" PRIMARY KEY ("col1",
     * "col2");
     * </p>
     *
     * @param constraint 约束定义
     * @return 生成的 DDL 语句字符串
     */
    @Override
    public String generateCreateObjectDDL(@NotNull DBTableConstraint constraint) {
        SqlBuilder sqlBuilder = sqlBuilder();
        sqlBuilder.append("ALTER TABLE ").append(getFullyQualifiedTableName(constraint))
                .append(" ADD CONSTRAINT ").identifier(constraint.getName()).space();

        appendConstraintType(constraint, sqlBuilder);
        appendConstraintColumns(constraint, sqlBuilder);
        appendConstraintOptions(constraint, sqlBuilder);

        return sqlBuilder.toString().trim() + ";\n";
    }

    /**
     * 生成删除约束的 DDL 语句
     *
     * <p>
     * PostgreSQL 删除约束语法：ALTER TABLE "schema"."table" DROP CONSTRAINT "name";
     * </p>
     *
     * @param constraint 约束定义
     * @return 生成的 DDL 语句字符串
     */
    @Override
    public String generateDropObjectDDL(@NotNull DBTableConstraint constraint) {
        SqlBuilder sqlBuilder = sqlBuilder();
        sqlBuilder.append("ALTER TABLE ").append(getFullyQualifiedTableName(constraint))
                .append(" DROP CONSTRAINT ").identifier(constraint.getName());
        return sqlBuilder.toString().trim() + ";\n";
    }

    /**
     * 生成重命名约束的 DDL 语句
     *
     * <p>
     * PostgreSQL 重命名约束语法：ALTER TABLE "schema"."table" RENAME CONSTRAINT "old" TO "new";
     * </p>
     *
     * @param oldConstraint 重命名前的约束定义
     * @param newConstraint 重命名后的约束定义
     * @return 生成的 DDL 语句字符串
     */
    @Override
    public String generateRenameObjectDDL(@NotNull DBTableConstraint oldConstraint,
            @NotNull DBTableConstraint newConstraint) {
        SqlBuilder sqlBuilder = sqlBuilder();
        sqlBuilder.append("ALTER TABLE ").append(getFullyQualifiedTableName(oldConstraint))
                .append(" RENAME CONSTRAINT ").identifier(oldConstraint.getName())
                .append(" TO ").identifier(newConstraint.getName()).append(";");
        return sqlBuilder.toString();
    }

    /**
     * 生成更新约束的 DDL 语句
     *
     * <p>
     * PostgreSQL 约束修改策略：
     * <ul>
     * <li>结构性变更：需要 DROP + CREATE</li>
     * <li>仅名称变更：使用 ALTER TABLE ... RENAME CONSTRAINT</li>
     * </ul>
     * </p>
     *
     * @param oldConstraint 修改前的约束定义
     * @param newConstraint 修改后的约束定义
     * @return 生成的 DDL 语句字符串
     */
    @Override
    public String generateUpdateObjectDDL(@NotNull DBTableConstraint oldConstraint,
            @NotNull DBTableConstraint newConstraint) {
        SqlBuilder sqlBuilder = sqlBuilder();

        // 检查是否有结构性变更
        boolean hasStructuralChange = hasStructuralChange(oldConstraint, newConstraint);

        if (hasStructuralChange) {
            // 结构性变更需要 DROP + CREATE
            String drop = generateDropObjectDDL(oldConstraint);
            String create = generateCreateObjectDDL(newConstraint);

            String dropTrimmed = drop.trim();
            String createTrimmed = create.trim();

            sqlBuilder.append(dropTrimmed);
            if (!dropTrimmed.endsWith(";")) {
                sqlBuilder.append("; ");
            } else {
                sqlBuilder.append(" ");
            }
            sqlBuilder.append(createTrimmed);
        } else if (!StringUtils.equals(oldConstraint.getName(), newConstraint.getName())) {
            // 仅名称变更
            sqlBuilder.append(generateRenameObjectDDL(oldConstraint, newConstraint)).append("\n");
        }

        return sqlBuilder.toString();
    }

    /**
     * 批量更新约束的 DDL 生成
     *
     * <p>
     * 使用 ordinalPosition 进行匹配。
     * </p>
     */
    @Override
    public String generateUpdateObjectListDDL(Collection<DBTableConstraint> oldConstraints,
            Collection<DBTableConstraint> newConstraints) {
        SqlBuilder sqlBuilder = sqlBuilder();

        if (CollectionUtils.isEmpty(oldConstraints)) {
            if (CollectionUtils.isNotEmpty(newConstraints)) {
                newConstraints.forEach(constraint -> sqlBuilder.append(generateCreateObjectDDL(constraint)));
            }
            return sqlBuilder.toString();
        }

        if (CollectionUtils.isEmpty(newConstraints)) {
            if (CollectionUtils.isNotEmpty(oldConstraints)) {
                oldConstraints.forEach(constraint -> sqlBuilder.append(generateDropObjectDDL(constraint)));
            }
            return sqlBuilder.toString();
        }

        Map<Integer, DBTableConstraint> position2OldConstraint = new HashMap<>();
        Map<Integer, DBTableConstraint> position2NewConstraint = new HashMap<>();

        oldConstraints.forEach(
                oldConstraint -> position2OldConstraint.put(oldConstraint.getOrdinalPosition(), oldConstraint));
        newConstraints.forEach(newConstraint -> {
            if (Objects.nonNull(newConstraint.getOrdinalPosition())) {
                position2NewConstraint.put(newConstraint.getOrdinalPosition(), newConstraint);
            }
        });

        // 处理新增和修改的约束
        for (DBTableConstraint newConstraint : newConstraints) {
            if (Objects.isNull(newConstraint.getOrdinalPosition())) {
                // ordinalPosition 为空表示新约束
                sqlBuilder.append(generateCreateObjectDDL(newConstraint));
            } else if (position2OldConstraint.containsKey(newConstraint.getOrdinalPosition())) {
                // 已存在的约束，检查是否需要更新
                String ddl = generateUpdateObjectDDL(
                        position2OldConstraint.get(newConstraint.getOrdinalPosition()),
                        newConstraint);
                if (StringUtils.isNotEmpty(ddl)) {
                    sqlBuilder.append(ddl);
                }
            }
        }

        // 处理删除的约束
        for (DBTableConstraint oldConstraint : oldConstraints) {
            if (!position2NewConstraint.containsKey(oldConstraint.getOrdinalPosition())) {
                sqlBuilder.append(generateDropObjectDDL(oldConstraint));
            }
        }

        return sqlBuilder.toString();
    }

    /**
     * 追加约束选项
     *
     * <p>
     * 对于外键约束，追加 ON DELETE 和 ON UPDATE 规则。
     * </p>
     */
    @Override
    protected void appendConstraintOptions(DBTableConstraint constraint, SqlBuilder sqlBuilder) {
        // 外键约束的参照动作
        if (constraint.getType() == DBConstraintType.FOREIGN_KEY) {
            if (Objects.nonNull(constraint.getOnDeleteRule())
                    && constraint.getOnDeleteRule() != DBForeignKeyModifyRule.NO_ACTION) {
                sqlBuilder.append(" ON DELETE ").append(constraint.getOnDeleteRule().getValue());
            }
            if (Objects.nonNull(constraint.getOnUpdateRule())
                    && constraint.getOnUpdateRule() != DBForeignKeyModifyRule.NO_ACTION) {
                sqlBuilder.append(" ON UPDATE ").append(constraint.getOnUpdateRule().getValue());
            }
        }
    }

    /**
     * 检查是否有结构性变更
     *
     * <p>
     * 比较约束类型、列名、外键引用等属性。
     * </p>
     */
    private boolean hasStructuralChange(DBTableConstraint oldConstraint, DBTableConstraint newConstraint) {
        // 比较约束类型
        if (!Objects.equals(oldConstraint.getType(), newConstraint.getType())) {
            return true;
        }

        // 比较列名
        if (!Objects.equals(oldConstraint.getColumnNames(), newConstraint.getColumnNames())) {
            return true;
        }

        // 对于外键约束，比较引用表和列
        if (oldConstraint.getType() == DBConstraintType.FOREIGN_KEY) {
            if (!Objects.equals(oldConstraint.getReferenceSchemaName(), newConstraint.getReferenceSchemaName())) {
                return true;
            }
            if (!Objects.equals(oldConstraint.getReferenceTableName(), newConstraint.getReferenceTableName())) {
                return true;
            }
            if (!Objects.equals(oldConstraint.getReferenceColumnNames(), newConstraint.getReferenceColumnNames())) {
                return true;
            }
            if (!Objects.equals(oldConstraint.getOnDeleteRule(), newConstraint.getOnDeleteRule())) {
                return true;
            }
            if (!Objects.equals(oldConstraint.getOnUpdateRule(), newConstraint.getOnUpdateRule())) {
                return true;
            }
        }

        // 对于 CHECK 约束，比较检查子句
        if (oldConstraint.getType() == DBConstraintType.CHECK) {
            if (!Objects.equals(oldConstraint.getCheckClause(), newConstraint.getCheckClause())) {
                return true;
            }
        }

        return false;
    }
}
