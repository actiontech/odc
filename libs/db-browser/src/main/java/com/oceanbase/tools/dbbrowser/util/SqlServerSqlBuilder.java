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

public class SqlServerSqlBuilder extends SqlBuilder {

    public SqlServerSqlBuilder() {
        super();
    }

    @Override
    public SqlBuilder identifier(String identifier) {
        return append(StringUtils.quoteSqlServerIdentifier(identifier));
    }

    @Override
    public SqlBuilder value(String value) {
        return append(StringUtils.quoteSqlServerValue(value));
    }

    @Override
    public SqlBuilder defaultValue(String value) {
        // SQL Server default value handling similar to Oracle
        return append(value);
    }

    @Override
    public SqlBuilder like(String fieldKey, String fieldLikeValue) {
        // SQL Server LIKE clause uses backslash for escaping (similar to MySQL)
        return super.like(fieldKey, fieldLikeValue);
    }
}
