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

import java.util.List;

import javax.validation.constraints.NotNull;

import com.oceanbase.tools.dbbrowser.editor.DBTablePartitionEditor;
import com.oceanbase.tools.dbbrowser.model.DBTablePartition;
import com.oceanbase.tools.dbbrowser.model.DBTablePartitionDefinition;
import com.oceanbase.tools.dbbrowser.model.DBTablePartitionOption;
import com.oceanbase.tools.dbbrowser.util.PostgresSqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.StringUtils;

/**
 * PostgreSQL 分区编辑器
 *
 * <p>
 * PostgreSQL 声明式分区语法特点：
 * </p>
 * <ul>
 * <li>创建分区表：CREATE TABLE ... PARTITION BY RANGE/LIST/HASH (column);</li>
 * <li>添加分区：ALTER TABLE ... ATTACH PARTITION ...;</li>
 * <li>删除分区：ALTER TABLE ... DETACH PARTITION ...;</li>
 * </ul>
 *
 * <p>
 * 注意：PostgreSQL 分区表需要先创建父表，再创建子分区表。本期仅提供基本的分区识别能力， 分区的可视化编辑不在本期范围。
 * </p>
 *
 * @author odc
 * @since ODC_release_4.3.4
 */
public class PostgresPartitionEditor extends DBTablePartitionEditor {

    @Override
    protected SqlBuilder sqlBuilder() {
        return new PostgresSqlBuilder();
    }

    @Override
    public String generateCreateObjectDDL(@NotNull DBTablePartition partition) {
        // PostgreSQL 分区需要创建父表后再创建子分区表
        // 这里仅返回空字符串，因为完整 DDL 在 createDefinitionDDL 中处理
        return "";
    }

    @Override
    public String generateCreateDefinitionDDL(@NotNull DBTablePartition partition) {
        if (partition.getPartitionOption() == null
                || partition.getPartitionOption().getType() == null) {
            return "";
        }

        SqlBuilder sqlBuilder = sqlBuilder();
        sqlBuilder.append(" PARTITION BY ");

        switch (partition.getPartitionOption().getType()) {
            case RANGE:
                sqlBuilder.append("RANGE");
                break;
            case LIST:
                sqlBuilder.append("LIST");
                break;
            case HASH:
                sqlBuilder.append("HASH");
                break;
            default:
                return "";
        }

        sqlBuilder.append("(");
        List<String> columnNames = partition.getPartitionOption().getColumnNames();
        if (columnNames != null && !columnNames.isEmpty()) {
            for (int i = 0; i < columnNames.size(); i++) {
                if (i > 0) {
                    sqlBuilder.append(", ");
                }
                sqlBuilder.identifier(columnNames.get(i));
            }
        }
        sqlBuilder.append(")");

        return sqlBuilder.toString();
    }

    @Override
    protected void appendDefinitions(DBTablePartition partition, SqlBuilder sqlBuilder) {
        // PostgreSQL 分区定义在子表中，不在父表 DDL 中
    }

    @Override
    protected void appendDefinition(DBTablePartitionOption option, DBTablePartitionDefinition definition,
            SqlBuilder sqlBuilder) {
        // PostgreSQL 分区定义在子表中
    }

    @Override
    protected String modifyPartitionType(@NotNull DBTablePartition oldPartition,
            @NotNull DBTablePartition newPartition) {
        // PostgreSQL 不支持直接修改分区类型，需要重建表
        return "-- PostgreSQL does not support direct PARTITION BY modification\n"
                + "-- Please recreate the table with the new partition type\n";
    }

    @Override
    public String generateAddPartitionDefinitionDDL(@NotNull DBTablePartitionDefinition definition,
            @NotNull DBTablePartitionOption option, String fullyQualifiedTableName) {
        if (definition == null || StringUtils.isBlank(fullyQualifiedTableName)) {
            return "";
        }

        SqlBuilder sqlBuilder = sqlBuilder();
        sqlBuilder.append("-- PostgreSQL requires creating a separate partition table:\n");
        sqlBuilder.append("-- CREATE TABLE ").append(fullyQualifiedTableName).append("_")
                .append(definition.getName());

        if (option.getType() != null) {
            switch (option.getType()) {
                case RANGE:
                    sqlBuilder.append(" PARTITION OF ").append(fullyQualifiedTableName)
                            .append(" FOR VALUES FROM (")
                            .append(definition.getMaxValues() != null && !definition.getMaxValues().isEmpty()
                                    ? definition.getMaxValues().get(0)
                                    : "MINVALUE")
                            .append(") TO (")
                            .append(definition.getMaxValues() != null && definition.getMaxValues().size() > 1
                                    ? definition.getMaxValues().get(1)
                                    : "MAXVALUE")
                            .append(")");
                    break;
                case LIST:
                    sqlBuilder.append(" PARTITION OF ").append(fullyQualifiedTableName)
                            .append(" FOR VALUES IN (")
                            .append(definition.getMaxValues() != null
                                    ? String.join(", ", definition.getMaxValues())
                                    : "")
                            .append(")");
                    break;
                case HASH:
                    sqlBuilder.append(" PARTITION OF ").append(fullyQualifiedTableName)
                            .append(" FOR VALUES WITH (MODULUS ")
                            .append(option.getPartitionsNum() != null ? option.getPartitionsNum() : 1)
                            .append(", REMAINDER ")
                            .append(definition.getOrdinalPosition() != null ? definition.getOrdinalPosition() : 0)
                            .append(")");
                    break;
                default:
                    break;
            }
        }
        sqlBuilder.append(";\n");

        return sqlBuilder.toString();
    }

    @Override
    public String generateAddPartitionDefinitionDDL(String schemaName, @NotNull String tableName,
            @NotNull DBTablePartitionOption option,
            List<DBTablePartitionDefinition> definitions) {
        if (definitions == null || definitions.isEmpty()) {
            return "";
        }

        SqlBuilder sqlBuilder = sqlBuilder();
        for (DBTablePartitionDefinition definition : definitions) {
            sqlBuilder.append(generateAddPartitionDefinitionDDL(definition, option,
                    getFullyQualifiedTableName(schemaName, tableName)));
        }
        return sqlBuilder.toString();
    }

    @Override
    public String generateDropObjectDDL(@NotNull DBTablePartition partition) {
        // PostgreSQL 删除分区需要 DETACH 分区表
        return "-- Use ALTER TABLE ... DETACH PARTITION to remove a partition\n";
    }

    private String getFullyQualifiedTableName(String schemaName, String tableName) {
        SqlBuilder sqlBuilder = sqlBuilder();
        if (StringUtils.isNotBlank(schemaName)) {
            sqlBuilder.identifier(schemaName).append(".");
        }
        sqlBuilder.identifier(tableName);
        return sqlBuilder.toString();
    }
}
