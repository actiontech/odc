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
 * SQL builder for SAP HANA database. HANA uses double quotes for identifier quoting (same as
 * Oracle). HANA is case-insensitive by default and stores unquoted identifiers in upper case. HANA
 * uses a two-level naming structure: schema.object (not catalog.schema.object like SQL Server).
 */
public class HanaSqlBuilder extends SqlBuilder {

    public HanaSqlBuilder() {
        super();
    }

    @Override
    public SqlBuilder identifier(String identifier) {
        if (StringUtils.isBlank(identifier)) {
            return this;
        }
        return append(StringUtils.quoteOracleIdentifier(identifier));
    }

    @Override
    public SqlBuilder schemaPrefixIfNotBlank(String schemaName) {
        if (StringUtils.isBlank(schemaName)) {
            return this;
        }
        // HANA uses two-level structure: schema.object (not catalog.schema.object)
        return this.identifier(schemaName).append(".");
    }

    @Override
    public SqlBuilder value(String value) {
        return append(StringUtils.quoteOracleValue(value));
    }

    @Override
    public SqlBuilder defaultValue(String value) {
        return append(value);
    }
}
