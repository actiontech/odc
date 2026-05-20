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
package com.oceanbase.odc.plugin.schema.hive.utils;

/**
 * Splits a Hive column type string returned by {@code DESCRIBE FORMATTED} into:
 * <ul>
 * <li>{@code typeName} - bare type, e.g. {@code "VARCHAR"} / {@code "DECIMAL"} /
 * {@code "ARRAY"}</li>
 * <li>{@code precision} - first numeric argument of {@code "TYPE(a,b)"}, or null</li>
 * <li>{@code scale} - second numeric argument of {@code "TYPE(a,b)"}, or null</li>
 * <li>{@code maxLength} - alias of precision for VARCHAR / CHAR / STRING families</li>
 * </ul>
 *
 * <p>
 * Per Hive language manual the recognized atomic types are: TINYINT, SMALLINT, INT, BIGINT, FLOAT,
 * DOUBLE, DECIMAL, NUMERIC, TIMESTAMP, DATE, INTERVAL, STRING, VARCHAR, CHAR, BOOLEAN, BINARY.
 * Complex types (ARRAY, MAP, STRUCT, UNIONTYPE) are kept as-is in {@code typeName}; their arguments
 * stay in the original full type string for downstream rendering.
 *
 * <p>
 * This mapper does <b>not</b> attempt to translate Hive types to JDBC SQL types — DDL fidelity is
 * preserved by emitting the original token in {@code fullTypeName}.
 *
 * @since ODC_release_4.3.4
 */
public final class HiveTypeMapper {

    private HiveTypeMapper() {}

    /**
     * Parse a Hive type token. The returned object always has a non-null {@code typeName}; the
     * remaining fields may be null when not applicable.
     *
     * @param raw raw type token; e.g. {@code "decimal(10,2)"} / {@code "varchar(255)"} /
     *        {@code "array<string>"}; null or blank yields an empty parse with typeName equal to
     *        {@code raw}.
     */
    public static ParsedType parse(String raw) {
        ParsedType p = new ParsedType();
        if (raw == null) {
            p.typeName = "";
            return p;
        }
        String trimmed = raw.trim();
        p.fullTypeName = trimmed;
        if (trimmed.isEmpty()) {
            p.typeName = "";
            return p;
        }
        // complex types use angle brackets, e.g. "array<string>" / "map<string,int>"
        int lt = trimmed.indexOf('<');
        int lp = trimmed.indexOf('(');
        if (lt > 0 && (lp < 0 || lt < lp)) {
            p.typeName = trimmed.substring(0, lt).toUpperCase();
            return p;
        }
        if (lp <= 0) {
            p.typeName = trimmed.toUpperCase();
            return p;
        }
        int rp = trimmed.lastIndexOf(')');
        if (rp <= lp) {
            // malformed; fall back to bare token
            p.typeName = trimmed.substring(0, lp).toUpperCase();
            return p;
        }
        p.typeName = trimmed.substring(0, lp).toUpperCase();
        String args = trimmed.substring(lp + 1, rp);
        String[] parts = args.split(",");
        if (parts.length >= 1) {
            p.precision = parseLong(parts[0].trim());
            p.maxLength = p.precision;
        }
        if (parts.length >= 2) {
            p.scale = parseInt(parts[1].trim());
        }
        return p;
    }

    private static Long parseLong(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseInt(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parsed type tokens. Public fields are deliberately mutable so callers may patch values (e.g.
     * override charset semantics) without introducing setters/getters.
     */
    public static final class ParsedType {
        public String typeName;
        public String fullTypeName;
        public Long precision;
        public Long maxLength;
        public Integer scale;
    }
}
