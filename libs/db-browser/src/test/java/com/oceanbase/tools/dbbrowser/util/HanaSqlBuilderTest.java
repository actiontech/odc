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

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link HanaSqlBuilder}. Verifies HANA-specific SQL building behavior:
 * Oracle-compatible double-quote identifiers, single-quote values, two-level schema prefix
 * (schema.object), and defaultValue passthrough.
 */
public class HanaSqlBuilderTest {

    // =================== identifier tests ===================

    @Test
    public void identifier_simpleTableName_quotedWithDoubleQuotes() {
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.identifier("MY_TABLE");
        Assert.assertEquals("\"MY_TABLE\"", sb.toString());
    }

    @Test
    public void identifier_nameContainingDoubleQuote_escapedByDoubling() {
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.identifier("table\"name");
        Assert.assertEquals("\"table\"\"name\"", sb.toString());
    }

    @Test
    public void identifier_blankInput_returnsEmptyString() {
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.identifier("");
        Assert.assertEquals("", sb.toString());
    }

    @Test
    public void identifier_nullInput_returnsEmptyString() {
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.identifier(null);
        Assert.assertEquals("", sb.toString());
    }

    @Test
    public void identifier_withSchemaAndObject_dotSeparated() {
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.identifier("SCHEMA1", "TABLE1");
        Assert.assertEquals("\"SCHEMA1\".\"TABLE1\"", sb.toString());
    }

    // =================== schemaPrefixIfNotBlank tests ===================

    @Test
    public void schemaPrefixIfNotBlank_withSchemaName_appendsSchemaPrefix() {
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.schemaPrefixIfNotBlank("MY_SCHEMA");
        sb.identifier("MY_TABLE");
        Assert.assertEquals("\"MY_SCHEMA\".\"MY_TABLE\"", sb.toString());
    }

    @Test
    public void schemaPrefixIfNotBlank_withBlankSchemaName_skipsPrefix() {
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.schemaPrefixIfNotBlank("");
        sb.identifier("MY_TABLE");
        Assert.assertEquals("\"MY_TABLE\"", sb.toString());
    }

    @Test
    public void schemaPrefixIfNotBlank_withNullSchemaName_skipsPrefix() {
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.schemaPrefixIfNotBlank(null);
        sb.identifier("MY_TABLE");
        Assert.assertEquals("\"MY_TABLE\"", sb.toString());
    }

    @Test
    public void schemaPrefixIfNotBlank_twoLevelStructure_notThreeLevel() {
        // HANA uses two-level schema.object, unlike SQL Server's catalog.schema.object
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.schemaPrefixIfNotBlank("SYSTEM");
        sb.identifier("EMPLOYEES");
        // Should be "SYSTEM"."EMPLOYEES", not "catalog"."SYSTEM"."EMPLOYEES"
        Assert.assertEquals("\"SYSTEM\".\"EMPLOYEES\"", sb.toString());
    }

    // =================== value tests ===================

    @Test
    public void value_simpleString_quotedWithSingleQuotes() {
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.value("hello");
        Assert.assertEquals("'hello'", sb.toString());
    }

    @Test
    public void value_stringContainingSingleQuote_escapedByDoubling() {
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.value("it's");
        Assert.assertEquals("'it''s'", sb.toString());
    }

    @Test
    public void value_nullInput_returnsNull() {
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.value(null);
        Assert.assertEquals("null", sb.toString());
    }

    @Test
    public void value_emptyString_quotedEmpty() {
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.value("");
        Assert.assertEquals("''", sb.toString());
    }

    // =================== defaultValue tests ===================

    @Test
    public void defaultValue_passedThrough_noQuoting() {
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.defaultValue("CURRENT_TIMESTAMP");
        Assert.assertEquals("CURRENT_TIMESTAMP", sb.toString());
    }

    @Test
    public void defaultValue_numericExpression_passedThrough() {
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.defaultValue("100");
        Assert.assertEquals("100", sb.toString());
    }

    // =================== chaining tests ===================

    @Test
    public void chaining_selectStatement_correctSql() {
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.append("SELECT TABLE_NAME FROM SYS.TABLES WHERE SCHEMA_NAME=");
        sb.value("TEST_SCHEMA");
        sb.append(" AND IS_SYSTEM_TABLE=");
        sb.value("FALSE");
        String expected = "SELECT TABLE_NAME FROM SYS.TABLES WHERE SCHEMA_NAME='TEST_SCHEMA'"
                + " AND IS_SYSTEM_TABLE='FALSE'";
        Assert.assertEquals(expected, sb.toString());
    }

    @Test
    public void chaining_setSchemaStatement_correctSql() {
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.append("SET SCHEMA ");
        sb.identifier("MY_SCHEMA");
        Assert.assertEquals("SET SCHEMA \"MY_SCHEMA\"", sb.toString());
    }

    // =================== identifiers list tests ===================

    @Test
    public void identifiers_multipleColumns_commaSeparated() {
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.identifiers(Arrays.asList("COL1", "COL2", "COL3"));
        Assert.assertEquals("\"COL1\",\"COL2\",\"COL3\"", sb.toString());
    }

    // =================== values list tests ===================

    @Test
    public void values_multipleValues_commaSeparated() {
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.values(Arrays.asList("val1", "val2"));
        Assert.assertEquals("'val1','val2'", sb.toString());
    }

    // =================== space and line tests ===================

    @Test
    public void space_appendsWhiteSpace() {
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.append("A").space().append("B");
        Assert.assertEquals("A B", sb.toString());
    }

    @Test
    public void line_appendsNewLine() {
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.append("A").line().append("B");
        Assert.assertEquals("A\nB", sb.toString());
    }

    // =================== comprehensive SQL construction tests ===================

    @Test
    public void comprehensiveSelect_withWhereClause_correctSql() {
        Map<String, String> testCases = new LinkedHashMap<>();
        testCases.put(
                "SELECT TABLE_NAME FROM SYS.TABLES WHERE SCHEMA_NAME='ADMIN'"
                        + " AND IS_SYSTEM_TABLE='FALSE' ORDER BY TABLE_NAME ASC",
                buildShowTablesLikeSql("ADMIN", null));
        testCases.put(
                "SELECT SCHEMA_NAME FROM SYS.SCHEMAS WHERE HAS_PRIVILEGES='TRUE'"
                        + " ORDER BY SCHEMA_NAME",
                buildListSchemasSql());

        for (Map.Entry<String, String> entry : testCases.entrySet()) {
            Assert.assertEquals(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Helper method to simulate HanaSchemaAccessor.showTablesLike SQL generation.
     */
    private String buildShowTablesLikeSql(String schemaName, String tableNameLike) {
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.append("SELECT TABLE_NAME FROM SYS.TABLES WHERE SCHEMA_NAME=");
        sb.value(schemaName);
        sb.append(" AND IS_SYSTEM_TABLE=");
        sb.value("FALSE");
        if (tableNameLike != null && !tableNameLike.isEmpty()) {
            sb.append(" AND ").like("TABLE_NAME", tableNameLike);
        }
        sb.append(" ORDER BY TABLE_NAME ASC");
        return sb.toString();
    }

    /**
     * Helper method to simulate HanaSchemaAccessor.listSchemas SQL generation.
     */
    private String buildListSchemasSql() {
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.append("SELECT SCHEMA_NAME FROM SYS.SCHEMAS WHERE HAS_PRIVILEGES=");
        sb.value("TRUE");
        sb.append(" ORDER BY SCHEMA_NAME");
        return sb.toString();
    }
}
