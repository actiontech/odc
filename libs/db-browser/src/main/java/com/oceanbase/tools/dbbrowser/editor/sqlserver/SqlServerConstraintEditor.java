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
import java.util.Map;
import java.util.Objects;

import javax.validation.constraints.NotNull;

import org.apache.commons.collections4.CollectionUtils;

import com.oceanbase.tools.dbbrowser.editor.DBTableConstraintEditor;
import com.oceanbase.tools.dbbrowser.model.DBTableConstraint;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlServerSqlBuilder;
import com.oceanbase.tools.dbbrowser.util.StringUtils;

/**
 * @author yizhou.xw
 * @date 2024/12
 * @since ODC_release_4.3.4
 */
public class SqlServerConstraintEditor extends DBTableConstraintEditor {

    @Override
    protected SqlBuilder sqlBuilder() {
        return new SqlServerSqlBuilder();
    }

    @Override
    public String generateCreateObjectDDL(@NotNull DBTableConstraint constraint) {
        SqlBuilder sqlBuilder = sqlBuilder();
        sqlBuilder.append("ALTER TABLE ").append(getFullyQualifiedTableName(constraint))
                .append(" ADD CONSTRAINT ").identifier(constraint.getName()).space()
                .append(constraint.getType().getValue()).append(" (");
        sqlBuilder.append(String.join(", ", constraint.getColumnNames().stream()
                .map(StringUtils::quoteSqlServerIdentifier).toArray(String[]::new))).append(")");
        return sqlBuilder.toString().trim() + ";";
    }

    @Override
    public String generateDropObjectDDL(@NotNull DBTableConstraint constraint) {
        SqlBuilder sqlBuilder = sqlBuilder();
        sqlBuilder.append("ALTER TABLE ").append(getFullyQualifiedTableName(constraint))
                .append(" DROP CONSTRAINT ").identifier(constraint.getName());
        return sqlBuilder.toString().trim() + ";";
    }

    @Override
    public String generateRenameObjectDDL(@NotNull DBTableConstraint oldConstraint,
            @NotNull DBTableConstraint newConstraint) {
        SqlBuilder sqlBuilder = sqlBuilder();
        String schemaName = oldConstraint.getSchemaName();

        // 解析数据库名和 schema 名
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = dbAndSchema[0];
        String actualSchemaName = dbAndSchema[1];

        // sp_rename 只能在当前数据库中执行，需要先切换到目标数据库
        // 如果有数据库名，添加 USE [database] 语句
        if (StringUtils.isNotBlank(databaseName)) {
            sqlBuilder.append("USE ").identifier(databaseName).append(";").line();
        }

        // sp_rename 只需要 schema.constraint_name，不需要数据库名（因为已经切换到目标数据库）
        String objectName = StringUtils.isNotBlank(actualSchemaName)
                ? actualSchemaName + "." + oldConstraint.getName()
                : oldConstraint.getName();
        sqlBuilder.append("EXEC sp_rename ").value(objectName).append(", ").value(newConstraint.getName())
                .append(", ").value("OBJECT").append(";");
        return sqlBuilder.toString();
    }

    @Override
    protected String getFullyQualifiedTableName(@NotNull DBTableConstraint constraint) {
        SqlBuilder sqlBuilder = sqlBuilder();
        String schemaName = constraint.getSchemaName();

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
        if (StringUtils.isNotBlank(constraint.getTableName())) {
            sqlBuilder.identifier(constraint.getTableName());
        }
        return sqlBuilder.toString();
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
            return new String[] {null, null};
        }

        if (schemaName.contains(".")) {
            String[] parts = schemaName.split("\\.", 2);
            return new String[] {parts[0], parts[1]};
        } else {
            // 只有数据库名，默认使用 dbo schema
            return new String[] {schemaName, "dbo"};
        }
    }

    @Override
    public String generateUpdateObjectListDDL(Collection<DBTableConstraint> oldConstraints,
            Collection<DBTableConstraint> newConstraints) {
        SqlBuilder sqlBuilder = sqlBuilder();
        if (CollectionUtils.isEmpty(oldConstraints)) {
            if (CollectionUtils.isNotEmpty(newConstraints)) {
                for (DBTableConstraint constraint : newConstraints) {
                    if (sqlBuilder.length() > 0) {
                        sqlBuilder.space();
                    }
                    sqlBuilder.append(generateCreateObjectDDL(constraint));
                }
            }
            return sqlBuilder.toString();
        }
        if (CollectionUtils.isEmpty(newConstraints)) {
            if (CollectionUtils.isNotEmpty(oldConstraints)) {
                for (DBTableConstraint constraint : oldConstraints) {
                    if (sqlBuilder.length() > 0) {
                        sqlBuilder.space();
                    }
                    sqlBuilder.append(generateDropObjectDDL(constraint));
                }
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
        for (DBTableConstraint newConstraint : newConstraints) {
            // ordinaryPosition is NULL means this is a new constraint
            if (Objects.isNull(newConstraint.getOrdinalPosition())) {
                if (sqlBuilder.length() > 0) {
                    sqlBuilder.space();
                }
                sqlBuilder.append(generateCreateObjectDDL(newConstraint));
            } else if (position2OldConstraint.containsKey(newConstraint.getOrdinalPosition())) {
                // this is an existing constraint
                String ddl = generateUpdateObjectDDL(position2OldConstraint.get(newConstraint.getOrdinalPosition()),
                        newConstraint);
                if (!ddl.isEmpty()) {
                    if (sqlBuilder.length() > 0) {
                        sqlBuilder.space();
                    }
                    sqlBuilder.append(ddl);
                }
            }
        }
        for (DBTableConstraint oldConstraint : oldConstraints) {
            // means this constraint should be dropped
            if (!position2NewConstraint.containsKey(oldConstraint.getOrdinalPosition())) {
                if (sqlBuilder.length() > 0) {
                    sqlBuilder.space();
                }
                sqlBuilder.append(generateDropObjectDDL(oldConstraint));
            }
        }
        return sqlBuilder.toString();
    }

}
