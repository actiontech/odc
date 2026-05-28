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
package com.oceanbase.tools.dbbrowser.editor.hive;

import java.util.List;

import javax.validation.constraints.NotNull;

import com.oceanbase.tools.dbbrowser.editor.DBTablePartitionEditor;
import com.oceanbase.tools.dbbrowser.model.DBTablePartition;
import com.oceanbase.tools.dbbrowser.model.DBTablePartitionDefinition;
import com.oceanbase.tools.dbbrowser.model.DBTablePartitionOption;
import com.oceanbase.tools.dbbrowser.util.HiveSqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;

import lombok.NonNull;

/**
 * Partition editor for Apache Hive.
 * <p>
 * Supports ADD PARTITION and DROP PARTITION operations. Hive partition specifications use the format
 * {@code (key='value', key2='value2')}.
 * </p>
 * <p>
 * Most partition DDL operations (e.g., modifying partition type, creating partition definitions
 * inline) are not supported through this editor.
 * </p>
 */
public class HivePartitionEditor extends DBTablePartitionEditor {

    private static final String UNSUPPORTED_MSG = "Hive does not support this partition operation";

    @Override
    public boolean editable() {
        return true;
    }

    @Override
    public String generateCreateObjectDDL(@NotNull DBTablePartition partition) {
        SqlBuilder sqlBuilder = sqlBuilder();
        String fullyQualifiedTableName = getFullyQualifiedTableName(partition);
        if (partition.getPartitionDefinitions() != null) {
            for (DBTablePartitionDefinition definition : partition.getPartitionDefinitions()) {
                sqlBuilder.append("ALTER TABLE ").append(fullyQualifiedTableName)
                        .append(" ADD PARTITION (").append(definition.getName()).append(");\n");
            }
        }
        return sqlBuilder.toString();
    }

    @Override
    public String generateDropObjectDDL(@NotNull DBTablePartition partition) {
        SqlBuilder sqlBuilder = sqlBuilder();
        String fullyQualifiedTableName = getFullyQualifiedTableName(partition);
        if (partition.getPartitionDefinitions() != null) {
            for (DBTablePartitionDefinition definition : partition.getPartitionDefinitions()) {
                sqlBuilder.append("ALTER TABLE ").append(fullyQualifiedTableName)
                        .append(" DROP PARTITION (").append(definition.getName()).append(");\n");
            }
        }
        return sqlBuilder.toString();
    }

    @Override
    public String generateDropPartitionDefinitionDDL(@NotNull DBTablePartitionDefinition definition,
            String fullyQualifiedTableName) {
        SqlBuilder sqlBuilder = sqlBuilder();
        sqlBuilder.append("ALTER TABLE ").append(fullyQualifiedTableName)
                .append(" DROP PARTITION (").append(definition.getName()).append(");\n");
        return sqlBuilder.toString();
    }

    @Override
    public String generateAddPartitionDefinitionDDL(@NotNull DBTablePartitionDefinition definition,
            @NotNull DBTablePartitionOption option, String fullyQualifiedTableName) {
        SqlBuilder sqlBuilder = sqlBuilder();
        sqlBuilder.append("ALTER TABLE ").append(fullyQualifiedTableName)
                .append(" ADD PARTITION (").append(definition.getName()).append(");\n");
        return sqlBuilder.toString();
    }

    @Override
    public String generateAddPartitionDefinitionDDL(String schemaName, @NonNull String tableName,
            @NonNull DBTablePartitionOption option, List<DBTablePartitionDefinition> definitions) {
        SqlBuilder sqlBuilder = sqlBuilder();
        String fullyQualifiedTableName = buildFullyQualifiedTableName(schemaName, tableName);
        for (DBTablePartitionDefinition definition : definitions) {
            sqlBuilder.append("ALTER TABLE ").append(fullyQualifiedTableName)
                    .append(" ADD PARTITION (").append(definition.getName()).append(");\n");
        }
        return sqlBuilder.toString();
    }

    @Override
    protected void appendDefinitions(DBTablePartition partition, SqlBuilder sqlBuilder) {
        // Hive partitions are not defined inline in CREATE TABLE with value ranges.
        // Hive uses PARTITIONED BY (col type) in CREATE TABLE, and actual partition values are
        // added later with ALTER TABLE ADD PARTITION.
    }

    @Override
    protected void appendDefinition(DBTablePartitionOption option,
            DBTablePartitionDefinition definition, SqlBuilder sqlBuilder) {
        // Not applicable for Hive -- see appendDefinitions()
    }

    @Override
    protected String modifyPartitionType(@NotNull DBTablePartition oldPartition,
            @NotNull DBTablePartition newPartition) {
        throw new UnsupportedOperationException(UNSUPPORTED_MSG);
    }

    @Override
    protected SqlBuilder sqlBuilder() {
        return new HiveSqlBuilder();
    }

    private String buildFullyQualifiedTableName(String schemaName, String tableName) {
        SqlBuilder sqlBuilder = sqlBuilder();
        if (org.apache.commons.lang3.StringUtils.isNotEmpty(schemaName)) {
            sqlBuilder.identifier(schemaName).append(".");
        }
        sqlBuilder.identifier(tableName);
        return sqlBuilder.toString();
    }

}
