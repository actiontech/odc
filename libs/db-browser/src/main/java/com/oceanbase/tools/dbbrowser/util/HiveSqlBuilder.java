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
package com.oceanbase.tools.dbbrowser.util;

/**
 * SQL builder for Apache Hive. Inherits MySQL-compatible backtick identifier quoting from
 * {@link MySQLSqlBuilder} and overrides schema prefix to use Hive's two-level
 * {@code database.table} naming (no catalog level).
 */
public class HiveSqlBuilder extends MySQLSqlBuilder {

    @Override
    public SqlBuilder schemaPrefixIfNotBlank(String schemaName) {
        if (StringUtils.isNotBlank(schemaName)) {
            return this.identifier(schemaName).append(".");
        }
        return this;
    }
}
