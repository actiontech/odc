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
 * PostgreSQL SQL builder.
 *
 * <p>
 * PostgreSQL uses double quotes for identifiers and single quotes for string values. The escaping
 * rules are:
 * <ul>
 * <li>Identifiers: use double quotes, internal double quotes are escaped by doubling: "column"</li>
 * <li>Values: use single quotes, internal single quotes are escaped by doubling: 'value'</li>
 * </ul>
 * </p>
 *
 * <p>
 * Note: PostgreSQL identifier quoting is similar to Oracle (both use double quotes), but the LIKE
 * clause escaping behavior differs. Oracle requires explicit ESCAPE clause, while PostgreSQL treats
 * backslash as a literal character by default in LIKE patterns (unless standard_conforming_strings
 * is off).
 * </p>
 *
 * @author odc
 */
public class PostgresSqlBuilder extends SqlBuilder {

    public PostgresSqlBuilder() {
        super();
    }

    /**
     * Append identifier with PostgreSQL quoting rules.
     *
     * <p>
     * PostgreSQL uses double quotes for identifiers. Internal double quotes are escaped by doubling
     * them.
     * </p>
     *
     * @param identifier the identifier to quote
     * @return this SqlBuilder
     */
    @Override
    public SqlBuilder identifier(String identifier) {
        if (StringUtils.isBlank(identifier)) {
            return this;
        }
        // PostgreSQL uses double quotes for identifiers, same as Oracle
        return append(StringUtils.quoteOracleIdentifier(identifier));
    }

    /**
     * Append value with PostgreSQL quoting rules.
     *
     * <p>
     * PostgreSQL uses single quotes for string values. Internal single quotes are escaped by doubling
     * them.
     * </p>
     *
     * @param value the value to quote
     * @return this SqlBuilder
     */
    @Override
    public SqlBuilder value(String value) {
        if (value == null) {
            return append("NULL");
        }
        // PostgreSQL uses single quotes for values, same as Oracle
        return append(StringUtils.quoteOracleValue(value));
    }

    /**
     * Append default value.
     *
     * <p>
     * Default values in PostgreSQL are typically function calls or literals, so they are appended
     * as-is.
     * </p>
     *
     * @param value the default value
     * @return this SqlBuilder
     */
    @Override
    public SqlBuilder defaultValue(String value) {
        return append(value);
    }

    /**
     * Append LIKE clause.
     *
     * <p>
     * PostgreSQL LIKE clause uses backslash for escaping by default (when standard_conforming_strings
     * is off) or can use the ESCAPE clause explicitly. For simplicity, we use the base implementation
     * without explicit ESCAPE clause, which differs from Oracle that always appends ESCAPE '\'.
     * </p>
     *
     * @param fieldKey the field name
     * @param fieldLikeValue the like pattern value
     * @return this SqlBuilder
     */
    @Override
    public SqlBuilder like(String fieldKey, String fieldLikeValue) {
        // Use base implementation without explicit ESCAPE clause
        // PostgreSQL's LIKE behavior depends on standard_conforming_strings setting
        return super.like(fieldKey, fieldLikeValue);
    }
}
