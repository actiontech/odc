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
package com.oceanbase.tools.dbbrowser.template.sqlserver;

import javax.validation.constraints.NotNull;

import com.oceanbase.tools.dbbrowser.model.DBProcedure;
import com.oceanbase.tools.dbbrowser.schema.sqlserver.SqlServerSchemaUtil;
import com.oceanbase.tools.dbbrowser.template.DBObjectTemplate;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlServerSqlBuilder;
import com.oceanbase.tools.dbbrowser.util.StringUtils;

/**
 * @author yizhou.xw
 * @date 2024/12
 * @since ODC_release_4.3.4
 */
public class SqlServerProcedureTemplate implements DBObjectTemplate<DBProcedure> {

    @Override
    public String generateCreateObjectTemplate(@NotNull DBProcedure dbObject) {
        SqlBuilder sqlBuilder = new SqlServerSqlBuilder();

        // 解析 schema 名称（格式：database.schema）
        // 对于 Procedure，如果没有直接的 schemaName 字段，使用默认值
        // 注意：这里假设 schemaName 格式为 database.schema（如 wenshu_test.dbo）
        String schemaName = null;
        String[] dbAndSchema = SqlServerSchemaUtil.parseDatabaseAndSchema(schemaName);
        String databaseName = dbAndSchema[0] != null ? dbAndSchema[0] : "请填写数据库名";
        String actualSchemaName = dbAndSchema[1] != null ? dbAndSchema[1] : "dbo";

        // 1. 生成 USE 语句
        sqlBuilder.append("USE ").identifier(databaseName).append(";\n");
        sqlBuilder.append("GO\n");

        // 2. 生成 CREATE PROCEDURE [schema].[procedureName]
        sqlBuilder.append("CREATE PROCEDURE ");
        if (StringUtils.isNotBlank(actualSchemaName)) {
            sqlBuilder.identifier(actualSchemaName).append(".");
        }
        sqlBuilder.identifier(dbObject.getProName()).append(" (")
                .line().append("    @param1 INT").line().append(")").line()
                .append("AS").line()
                .append("BEGIN").line()
                .append("    SET NOCOUNT ON;").line()
                .append("    SELECT @param1;").line()
                .append("END");
        return sqlBuilder.toString();
    }

}
