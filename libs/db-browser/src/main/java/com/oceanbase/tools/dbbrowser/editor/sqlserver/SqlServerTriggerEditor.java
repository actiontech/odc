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

import javax.validation.constraints.NotNull;

import com.oceanbase.tools.dbbrowser.editor.DBObjectEditor;
import com.oceanbase.tools.dbbrowser.model.DBTrigger;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlServerSqlBuilder;
import com.oceanbase.tools.dbbrowser.util.StringUtils;

/**
 * @author yizhou.xw
 * @date 2024/12
 * @since ODC_release_4.3.4
 */
public class SqlServerTriggerEditor implements DBObjectEditor<DBTrigger> {

    @Override
    public boolean editable() {
        return true;
    }

    @Override
    public String generateCreateObjectDDL(@NotNull DBTrigger dbObject) {
        return dbObject.getDdl();
    }

    @Override
    public String generateCreateDefinitionDDL(@NotNull DBTrigger dbObject) {
        return generateCreateObjectDDL(dbObject);
    }

    @Override
    public String generateUpdateObjectDDL(@NotNull DBTrigger oldObject, @NotNull DBTrigger newObject) {
        // SQL Server uses CREATE OR ALTER TRIGGER
        return newObject.getDdl();
    }

    @Override
    public String generateUpdateObjectListDDL(Collection<DBTrigger> oldObjects, Collection<DBTrigger> newObjects) {
        return "";
    }

    @Override
    public String generateRenameObjectDDL(@NotNull DBTrigger oldObject, @NotNull DBTrigger newObject) {
        SqlBuilder sqlBuilder = new SqlServerSqlBuilder();
        String schemaName = oldObject.getSchemaName();

        // 解析数据库名和 schema 名
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = dbAndSchema[0];
        String actualSchemaName = dbAndSchema[1];

        // sp_rename 只能在当前数据库中执行，需要先切换到目标数据库
        // 如果有数据库名，添加 USE [database] 语句
        if (StringUtils.isNotBlank(databaseName)) {
            sqlBuilder.append("USE ").identifier(databaseName).append(";").line();
        }

        // sp_rename 只需要 schema.trigger_name，不需要数据库名（因为已经切换到目标数据库）
        String objectName = StringUtils.isNotBlank(actualSchemaName)
                ? actualSchemaName + "." + oldObject.getTriggerName()
                : oldObject.getTriggerName();
        sqlBuilder.append("EXEC sp_rename ").value(objectName).append(", ").value(newObject.getTriggerName());
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
            return new String[] {null, "dbo"};
        }

        if (schemaName.contains(".")) {
            String[] parts = schemaName.split("\\.", 2);
            if (parts.length == 2 && StringUtils.isNotBlank(parts[0]) && StringUtils.isNotBlank(parts[1])) {
                return new String[] {parts[0], parts[1]};
            }
            // 如果格式不正确，返回默认值
            return new String[] {schemaName, "dbo"};
        } else {
            // 只有数据库名，默认使用 dbo schema
            return new String[] {schemaName, "dbo"};
        }
    }

    @Override
    public String generateDropObjectDDL(@NotNull DBTrigger dbObject) {
        return "DROP TRIGGER "
                + new SqlServerSqlBuilder().identifier(dbObject.getSchemaName(), dbObject.getTriggerName()).toString();
    }

}
