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

import javax.validation.constraints.NotNull;

import com.oceanbase.tools.dbbrowser.editor.DBTablePartitionEditor;
import com.oceanbase.tools.dbbrowser.model.DBTablePartition;
import com.oceanbase.tools.dbbrowser.model.DBTablePartitionDefinition;
import com.oceanbase.tools.dbbrowser.model.DBTablePartitionOption;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlServerSqlBuilder;
import com.oceanbase.tools.dbbrowser.util.StringUtils;

/**
 * @author yizhou.xw
 * @date 2024/12
 * @since ODC_release_4.3.4
 */
public class SqlServerPartitionEditor extends DBTablePartitionEditor {

    @Override
    protected SqlBuilder sqlBuilder() {
        return new SqlServerSqlBuilder();
    }

    @Override
    public String generateCreateObjectDDL(@NotNull DBTablePartition partition) {
        return "";
    }

    @Override
    public String generateCreateDefinitionDDL(@NotNull DBTablePartition partition) {
        DBTablePartitionOption option = partition.getPartitionOption();
        if (option == null || StringUtils.isBlank(option.getExpression())) {
            return "";
        }
        // For SQL Server, partitioning is applied via 'ON PartitionSchemeName(ColumnName)'
        // Currently, we use 'expression' to store the partition scheme or function name
        // This is a simplified implementation.
        SqlBuilder sqlBuilder = sqlBuilder();
        sqlBuilder.append(" ON ").append(option.getExpression());
        if (option.getColumnNames() != null && !option.getColumnNames().isEmpty()) {
            sqlBuilder.append("(");
            for (int i = 0; i < option.getColumnNames().size(); i++) {
                if (i > 0) {
                    sqlBuilder.append(", ");
                }
                sqlBuilder.identifier(option.getColumnNames().get(i));
            }
            sqlBuilder.append(")");
        }
        return sqlBuilder.toString();
    }

    @Override
    protected void appendDefinitions(DBTablePartition partition, SqlBuilder sqlBuilder) {
        // SQL Server partition definitions are part of PARTITION FUNCTION, not directly in CREATE TABLE
    }

    @Override
    protected void appendDefinition(DBTablePartitionOption option, DBTablePartitionDefinition definition,
            SqlBuilder sqlBuilder) {
        // Not used for SQL Server in this model
    }

    @Override
    protected String modifyPartitionType(@NotNull DBTablePartition oldPartition,
            @NotNull DBTablePartition newPartition) {
        return "-- SQL Server does not support direct PARTITION BY modification in ALTER TABLE\n";
    }

    @Override
    public String generateAddPartitionDefinitionDDL(@NotNull DBTablePartitionDefinition definition,
            @NotNull DBTablePartitionOption option, String fullyQualifiedTableName) {
        if (StringUtils.isBlank(option.getExpression())) {
            return "";
        }
        SqlBuilder sqlBuilder = sqlBuilder();
        sqlBuilder.append("-- ALTER PARTITION FUNCTION ").append(option.getExpression())
                .append("() SPLIT RANGE (").append(definition.getMaxValues().get(0)).append(");")
                .line();
        return sqlBuilder.toString();
    }

    @Override
    public String generateAddPartitionDefinitionDDL(String schemaName, @NotNull String tableName,
            @NotNull DBTablePartitionOption option,
            java.util.List<com.oceanbase.tools.dbbrowser.model.DBTablePartitionDefinition> definitions) {
        if (StringUtils.isBlank(option.getExpression())) {
            return "";
        }
        SqlBuilder sqlBuilder = sqlBuilder();
        for (com.oceanbase.tools.dbbrowser.model.DBTablePartitionDefinition definition : definitions) {
            sqlBuilder.append("-- ALTER PARTITION FUNCTION ").append(option.getExpression())
                    .append("() SPLIT RANGE (").append(definition.getMaxValues().get(0)).append(");")
                    .line();
        }
        return sqlBuilder.toString();
    }

    @Override
    public String generateDropObjectDDL(@NotNull DBTablePartition partition) {
        // Drop partitioning in SQL Server is usually done by moving to a filegroup or rebuilding index
        return "";
    }

    @Override
    public String generateUpdateObjectDDL(DBTablePartition oldPartition, DBTablePartition newPartition) {
        if (newPartition == null || newPartition.getPartitionOption() == null
                || StringUtils.isBlank(newPartition.getPartitionOption().getExpression())) {
            return "";
        }
        SqlBuilder sqlBuilder = sqlBuilder();
        String functionName = newPartition.getPartitionOption().getExpression();

        // Basic implementation for SPLIT/MERGE
        // Since we don't have full metadata about which range to split/merge, we provide the template
        // structure
        if (newPartition.getPartitionDefinitions().size() > oldPartition.getPartitionDefinitions().size()) {
            sqlBuilder.append("-- ALTER PARTITION FUNCTION ").append(functionName).append("() SPLIT RANGE (new_value);")
                    .line();
        } else if (newPartition.getPartitionDefinitions().size() < oldPartition.getPartitionDefinitions().size()) {
            sqlBuilder.append("-- ALTER PARTITION FUNCTION ").append(functionName)
                    .append("() MERGE RANGE (existing_value);")
                    .line();
        }
        return sqlBuilder.toString();
    }

}
