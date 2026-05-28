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
package com.oceanbase.tools.dbbrowser.template.hive;

import com.oceanbase.tools.dbbrowser.template.BaseViewTemplate;
import com.oceanbase.tools.dbbrowser.util.HiveSqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;

/**
 * View template for Apache Hive.
 * <p>
 * Generates CREATE VIEW template DDL using Hive two-level naming ({@code database.view_name}).
 * </p>
 * <p>
 * Example output:
 *
 * <pre>
 * CREATE OR REPLACE VIEW `database`.`view_name` AS
 * SELECT
 *     *
 * FROM
 *     `database`.`table_name`
 * </pre>
 */
public class HiveViewTemplate extends BaseViewTemplate {

    @Override
    protected String preHandle(String str) {
        return str.toUpperCase();
    }

    @Override
    protected SqlBuilder sqlBuilder() {
        return new HiveSqlBuilder();
    }

    @Override
    protected String doGenerateCreateObjectTemplate(SqlBuilder sqlBuilder, com.oceanbase.tools.dbbrowser.model.DBView dbObject) {
        return sqlBuilder.toString();
    }

}
