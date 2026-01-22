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

import java.util.Objects;

import javax.validation.constraints.NotNull;

import com.oceanbase.tools.dbbrowser.editor.DBObjectEditor;
import com.oceanbase.tools.dbbrowser.editor.DBTableEditor;
import com.oceanbase.tools.dbbrowser.model.DBTable;
import com.oceanbase.tools.dbbrowser.model.DBTableColumn;
import com.oceanbase.tools.dbbrowser.model.DBTableConstraint;
import com.oceanbase.tools.dbbrowser.model.DBTableIndex;
import com.oceanbase.tools.dbbrowser.model.DBTablePartition;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlServerSqlBuilder;
import com.oceanbase.tools.dbbrowser.util.StringUtils;

/**
 * @author yizhou.xw
 * @date 2024/12
 * @since ODC_release_4.3.4
 */
public class SqlServerTableEditor extends DBTableEditor {

    public SqlServerTableEditor(DBObjectEditor<DBTableIndex> indexEditor,
            DBObjectEditor<DBTableColumn> columnEditor,
            DBObjectEditor<DBTableConstraint> constraintEditor,
            DBObjectEditor<DBTablePartition> partitionEditor) {
        super(indexEditor, columnEditor, constraintEditor, partitionEditor);
    }

    @Override
    protected void appendColumnComment(DBTable table, SqlBuilder sqlBuilder) {
        if (Objects.isNull(table.getColumns())) {
            return;
        }
        for (DBTableColumn column : table.getColumns()) {
            column.setSchemaName(table.getSchemaName());
            column.setTableName(table.getName());
            ((SqlServerColumnEditor) columnEditor).generateColumnComment(column, sqlBuilder);
        }
    }

    @Override
    protected void appendTableComment(DBTable table, SqlBuilder sqlBuilder) {
        if (Objects.isNull(table.getTableOptions()) || StringUtils.isBlank(table.getTableOptions().getComment())) {
            return;
        }
        String schemaName = table.getSchemaName();
        // 解析数据库名和 schema 名
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String actualSchemaName = dbAndSchema[1];
        if (StringUtils.isBlank(actualSchemaName)) {
            actualSchemaName = "dbo";
        }
        String name = table.getName();
        String comment = table.getTableOptions().getComment();

        sqlBuilder.append("IF EXISTS (SELECT 1 FROM sys.extended_properties WHERE name = N'MS_Description' "
                + "AND major_id = OBJECT_ID(N'").append(actualSchemaName).append(".").append(name)
                .append("') AND minor_id = 0)")
                .line()
                .append("  EXEC sp_updateextendedproperty @name=N'MS_Description', @value=")
                .value(comment)
                .append(", @level0type=N'SCHEMA', @level0name=").value(actualSchemaName)
                .append(", @level1type=N'TABLE', @level1name=").value(name).line()
                .append("ELSE").line()
                .append("  EXEC sp_addextendedproperty @name=N'MS_Description', @value=")
                .value(comment)
                .append(", @level0type=N'SCHEMA', @level0name=").value(actualSchemaName)
                .append(", @level1type=N'TABLE', @level1name=").value(name).append(";\n");
    }

    @Override
    protected boolean createIndexWhenCreatingTable() {
        return false;
    }

    @Override
    protected void appendTableOptions(DBTable table, SqlBuilder sqlBuilder) {
        // SQL Server has fewer table options in DDL than MySQL.
    }

    @Override
    public void generateUpdateTableOptionDDL(DBTable oldTable, DBTable newTable, SqlBuilder sqlBuilder) {
        String oldComment =
                Objects.nonNull(oldTable.getTableOptions()) ? oldTable.getTableOptions().getComment() : null;
        String newComment =
                Objects.nonNull(newTable.getTableOptions()) ? newTable.getTableOptions().getComment() : null;
        if (!Objects.equals(oldComment, newComment)) {
            appendTableComment(newTable, sqlBuilder);
        }
    }

    @Override
    public String generateRenameObjectDDL(@NotNull DBTable oldTable, @NotNull DBTable newTable) {
        SqlBuilder sqlBuilder = sqlBuilder();
        String schemaName = oldTable.getSchemaName();

        // 解析数据库名和 schema 名
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = dbAndSchema[0];
        String actualSchemaName = dbAndSchema[1];

        // sp_rename 只能在当前数据库中执行，需要先切换到目标数据库
        // 如果有数据库名，添加 USE [database] 语句
        if (StringUtils.isNotBlank(databaseName)) {
            sqlBuilder.append("USE ").identifier(databaseName).append(";").line();
        }

        // sp_rename 只需要 schema.table_name，不需要数据库名（因为已经切换到目标数据库）
        String objectName = StringUtils.isNotBlank(actualSchemaName)
                ? actualSchemaName + "." + oldTable.getName()
                : oldTable.getName();
        sqlBuilder.append("EXEC sp_rename ").value(objectName).append(", ").value(newTable.getName());
        return sqlBuilder.toString();
    }

    /**
     * 解析 schemaName，支持两种格式： 1. "schema" - 只有 schema 名（在当前数据库中） 2. "database.schema" - 数据库名和 schema 名
     *
     * @param schemaName 可能是 schema 名或 database.schema 格式
     * @return [数据库名, schema名] 数组
     */
    private String[] parseDatabaseAndSchema(String schemaName) {
        if (StringUtils.isBlank(schemaName)) {
            return new String[] {null, "dbo"};
        }

        if (schemaName.contains(".")) {
            String[] parts = schemaName.split("\\.", 2);
            if (parts.length == 2 && StringUtils.isNotBlank(parts[0]) && StringUtils.isNotBlank(parts[1])) {
                return new String[] {parts[0], parts[1]};
            }
            // 如果格式不正确，假设是 schema 名
            return new String[] {null, schemaName};
        } else {
            // 不包含 "."，假设是 schema 名（在当前数据库中）
            return new String[] {null, schemaName};
        }
    }

    @Override
    protected SqlBuilder sqlBuilder() {
        return new SqlServerSqlBuilder();
    }

    @Override
    protected String getFullyQualifiedTableName(@NotNull DBTable table) {
        SqlBuilder sqlBuilder = sqlBuilder();
        String schemaName = table.getSchemaName();

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
        if (StringUtils.isNotBlank(table.getName())) {
            sqlBuilder.identifier(table.getName());
        }
        return sqlBuilder.toString();
    }

}
