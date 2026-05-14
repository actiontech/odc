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
 * SqlBuilder for IBM Db2 LUW. Db2 uses double quotes {@code "} as the standard SQL quoted
 * identifier (folded to upper case when unquoted) and single quotes {@code '} for string values
 * (doubled to escape). See design.md §3.5.2 / §3.5.3.
 */
public class DB2SqlBuilder extends SqlBuilder {

    public DB2SqlBuilder() {
        super();
    }

    @Override
    public SqlBuilder identifier(String identifier) {
        if (StringUtils.isBlank(identifier)) {
            return this;
        }
        // DB2 quoted identifier: "name". Internal " is escaped by doubling.
        String escaped = identifier.replace("\"", "\"\"");
        return append("\"").append(escaped).append("\"");
    }

    @Override
    public SqlBuilder schemaPrefixIfNotBlank(String schemaName) {
        if (StringUtils.isBlank(schemaName)) {
            return this;
        }
        return this.identifier(schemaName).append(".");
    }

    @Override
    public SqlBuilder value(String value) {
        if (value == null) {
            return append("NULL");
        }
        // DB2 string literal: 'value' with internal ' doubled.
        String escaped = value.replace("'", "''");
        return append("'").append(escaped).append("'");
    }

    @Override
    public SqlBuilder defaultValue(String value) {
        // DB2 DEFAULT accepts literal expression as-is (e.g. CURRENT TIMESTAMP, 0, 'foo').
        return append(value);
    }
}
