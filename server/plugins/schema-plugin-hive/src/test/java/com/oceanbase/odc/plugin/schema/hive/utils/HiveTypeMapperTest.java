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

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.odc.plugin.schema.hive.utils.HiveTypeMapper.ParsedType;

/**
 * Map-case driven coverage of {@link HiveTypeMapper#parse(String)}. Every Hive language-manual
 * atomic / parameterised / complex type variant we expect to encounter from {@code DESCRIBE
 * FORMATTED} is pinned here. Test pivots: {@code typeName} normalised to UPPERCASE,
 * {@code precision} / {@code maxLength} / {@code scale} extraction, complex-type angle bracket
 * passthrough, and malformed-input graceful fallback.
 *
 * <p>
 * Covers compat-RISK R-11 (parsing resilience for varied Hive type tokens) and contributes to R-4.3
 * (read-only metadata fidelity — DDL types are preserved verbatim in {@code fullTypeName}).
 */
public class HiveTypeMapperTest {

    /**
     * One row of the parsing matrix. Each null field means "expected null"; non-null fields are
     * asserted equal.
     */
    private static final class Case {
        final String input;
        final String expectedTypeName;
        final String expectedFullTypeName;
        final Long expectedPrecision;
        final Long expectedMaxLength;
        final Integer expectedScale;

        Case(String input, String expectedTypeName, String expectedFullTypeName,
                Long expectedPrecision, Long expectedMaxLength, Integer expectedScale) {
            this.input = input;
            this.expectedTypeName = expectedTypeName;
            this.expectedFullTypeName = expectedFullTypeName;
            this.expectedPrecision = expectedPrecision;
            this.expectedMaxLength = expectedMaxLength;
            this.expectedScale = expectedScale;
        }
    }

    private static Map<String, Case> buildCases() {
        Map<String, Case> cases = new LinkedHashMap<>();
        // atomic types — no parens, no angle brackets
        cases.put("atomic_int", new Case("int", "INT", "int", null, null, null));
        cases.put("atomic_bigint", new Case("BIGINT", "BIGINT", "BIGINT", null, null, null));
        cases.put("atomic_string", new Case("string", "STRING", "string", null, null, null));
        cases.put("atomic_boolean", new Case("boolean", "BOOLEAN", "boolean", null, null, null));
        cases.put("atomic_date", new Case("date", "DATE", "date", null, null, null));
        // parameterised types
        cases.put("decimal_precision_scale",
                new Case("decimal(10,2)", "DECIMAL", "decimal(10,2)", 10L, 10L, 2));
        cases.put("varchar_length",
                new Case("varchar(255)", "VARCHAR", "varchar(255)", 255L, 255L, null));
        cases.put("char_length",
                new Case("char(8)", "CHAR", "char(8)", 8L, 8L, null));
        // complex types — angle bracket, typeName stripped to head only, fullTypeName passthrough
        cases.put("array_string",
                new Case("array<string>", "ARRAY", "array<string>", null, null, null));
        cases.put("map_string_int",
                new Case("map<string,int>", "MAP", "map<string,int>", null, null, null));
        cases.put("struct_nested",
                new Case("struct<a:int,b:string>", "STRUCT", "struct<a:int,b:string>", null, null,
                        null));
        // whitespace tolerance — input gets trimmed before parsing
        cases.put("trimmed_whitespace",
                new Case("  bigint  ", "BIGINT", "bigint", null, null, null));
        // malformed — opening paren but no closing; fall back to bare token (typeName uppercased)
        cases.put("malformed_no_close_paren",
                new Case("decimal(10,2", "DECIMAL", "decimal(10,2", null, null, null));
        // non-numeric arg — parseLong/parseInt return null gracefully (R-11 resilience)
        cases.put("decimal_non_numeric_arg",
                new Case("decimal(abc,xy)", "DECIMAL", "decimal(abc,xy)", null, null, null));
        return cases;
    }

    @Test
    public void parse_mapCaseMatrix_meetsExpectedTokens() {
        for (Map.Entry<String, Case> entry : buildCases().entrySet()) {
            String label = entry.getKey();
            Case c = entry.getValue();
            ParsedType p = HiveTypeMapper.parse(c.input);
            Assert.assertNotNull(label + ": parse should never return null", p);
            Assert.assertEquals(label + ": typeName", c.expectedTypeName, p.typeName);
            Assert.assertEquals(label + ": fullTypeName", c.expectedFullTypeName, p.fullTypeName);
            Assert.assertEquals(label + ": precision", c.expectedPrecision, p.precision);
            Assert.assertEquals(label + ": maxLength", c.expectedMaxLength, p.maxLength);
            Assert.assertEquals(label + ": scale", c.expectedScale, p.scale);
        }
    }

    @Test
    public void parse_nullInput_returnsEmptyTypeName() {
        ParsedType p = HiveTypeMapper.parse(null);
        Assert.assertNotNull(p);
        Assert.assertEquals("", p.typeName);
        Assert.assertNull(p.fullTypeName);
        Assert.assertNull(p.precision);
        Assert.assertNull(p.maxLength);
        Assert.assertNull(p.scale);
    }

    @Test
    public void parse_blankInput_returnsEmptyTypeName() {
        ParsedType p = HiveTypeMapper.parse("   ");
        Assert.assertNotNull(p);
        Assert.assertEquals("", p.typeName);
        Assert.assertEquals("", p.fullTypeName);
    }
}
