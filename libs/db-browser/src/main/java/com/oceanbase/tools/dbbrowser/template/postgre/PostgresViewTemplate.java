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
package com.oceanbase.tools.dbbrowser.template.postgre;

import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.Validate;

import com.oceanbase.tools.dbbrowser.model.DBView;
import com.oceanbase.tools.dbbrowser.template.BaseViewTemplate;
import com.oceanbase.tools.dbbrowser.util.PostgresSqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.StringUtils;

/**
 * PostgreSQL view template for generating CREATE VIEW statements.
 *
 * <p>
 * PostgreSQL uses double quotes for identifiers and supports CREATE OR REPLACE VIEW syntax. Unlike
 * SQL Server, PostgreSQL does not require USE statement before CREATE VIEW.
 * </p>
 *
 * <p>
 * Template output example:
 * </p>
 * 
 * <pre>
 * CREATE OR REPLACE VIEW "schema_name"."view_name" AS
 * SELECT column1, column2
 * FROM "schema_name"."table_name"
 * WHERE condition;
 * </pre>
 *
 * @author odc
 * @since ODC_release_4.3.4
 */
public class PostgresViewTemplate extends BaseViewTemplate {

    @Override
    protected String preHandle(String str) {
        return str.toLowerCase();
    }

    @Override
    protected SqlBuilder sqlBuilder() {
        return new PostgresSqlBuilder();
    }

    /**
     * Override to support schema prefix for PostgreSQL.
     *
     * <p>
     * PostgreSQL view names can be qualified with schema: "schema"."view_name"
     * </p>
     */
    @Override
    public String generateCreateObjectTemplate(@NotNull DBView dbObject) {
        Validate.notBlank(dbObject.getViewName(), "View name can not be blank");
        validOperations(dbObject);

        SqlBuilder sqlBuilder = sqlBuilder();

        // Generate CREATE OR REPLACE VIEW with optional schema prefix
        sqlBuilder.append(preHandle("create or replace view "));

        if (StringUtils.isNotBlank(dbObject.getSchemaName())) {
            sqlBuilder.identifier(dbObject.getSchemaName()).append(".");
        }

        sqlBuilder.identifier(dbObject.getViewName())
                .append(preHandle(" as"));

        // Generate query statement using base class logic
        generateQueryStatement(dbObject, sqlBuilder);

        return sqlBuilder.toString();
    }

    @Override
    protected String doGenerateCreateObjectTemplate(SqlBuilder sqlBuilder, DBView dbObject) {
        return sqlBuilder.toString();
    }

}
