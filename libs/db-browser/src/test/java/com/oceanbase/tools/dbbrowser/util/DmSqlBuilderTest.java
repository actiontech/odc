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
 * Unit tests for {@link DmSqlBuilder}. Verifies DM-specific SQL building behavior:
 * Oracle-compatible double-quote identifiers, single-quote values, LIKE with ESCAPE clause, and
 * defaultValue passthrough.
 */
public class DmSqlBuilderTest {

    // =================== identifier tests ===================

    @Test
    public void identifier_simpleTableName_quotedWithDoubleQuotes() {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.identifier("MY_TABLE");
        Assert.assertEquals("\"MY_TABLE\"", sb.toString());
    }

    @Test
    public void identifier_nameContainingDoubleQuote_escapedByDoubling() {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.identifier("table\"name");
        Assert.assertEquals("\"table\"\"name\"", sb.toString());
    }

    @Test
    public void identifier_nullInput_returnsNull() {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.identifier(null);
        Assert.assertEquals("null", sb.toString());
    }

    @Test
    public void identifier_emptyString_quotedEmpty() {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.identifier("");
        Assert.assertEquals("\"\"", sb.toString());
    }

    @Test
    public void identifier_withSchemaAndObject_dotSeparated() {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.identifier("SCHEMA1", "TABLE1");
        Assert.assertEquals("\"SCHEMA1\".\"TABLE1\"", sb.toString());
    }

    // =================== value tests ===================

    @Test
    public void value_simpleString_quotedWithSingleQuotes() {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.value("hello");
        Assert.assertEquals("'hello'", sb.toString());
    }

    @Test
    public void value_stringContainingSingleQuote_escapedByDoubling() {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.value("it's");
        Assert.assertEquals("'it''s'", sb.toString());
    }

    @Test
    public void value_nullInput_returnsNull() {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.value(null);
        Assert.assertEquals("null", sb.toString());
    }

    @Test
    public void value_emptyString_quotedEmpty() {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.value("");
        Assert.assertEquals("''", sb.toString());
    }

    // =================== defaultValue tests ===================

    @Test
    public void defaultValue_passedThrough_noQuoting() {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.defaultValue("SYSDATE");
        Assert.assertEquals("SYSDATE", sb.toString());
    }

    @Test
    public void defaultValue_numericExpression_passedThrough() {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.defaultValue("100");
        Assert.assertEquals("100", sb.toString());
    }

    // =================== like tests ===================

    @Test
    public void like_withFieldKeyAndValue_generatesLikeWithEscape() {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.like("TABLE_NAME", "test");
        Assert.assertEquals("TABLE_NAME LIKE '%test%' ESCAPE '\\'", sb.toString());
    }

    @Test
    public void like_valueContainingPercent_escaped() {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.like("COL", "a%b");
        Assert.assertEquals("COL LIKE '%a\\%b%' ESCAPE '\\'", sb.toString());
    }

    @Test
    public void like_valueContainingUnderscore_escaped() {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.like("COL", "a_b");
        Assert.assertEquals("COL LIKE '%a\\_b%' ESCAPE '\\'", sb.toString());
    }

    // =================== chaining tests ===================

    @Test
    public void chaining_selectStatement_correctSql() {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.append("SELECT * FROM SYS.ALL_TABLES WHERE OWNER=");
        sb.value("TEST_SCHEMA");
        sb.append(" AND ").like("TABLE_NAME", "my_table");
        String expected = "SELECT * FROM SYS.ALL_TABLES WHERE OWNER='TEST_SCHEMA'"
                + " AND TABLE_NAME LIKE '%my\\_table%' ESCAPE '\\'";
        Assert.assertEquals(expected, sb.toString());
    }

    @Test
    public void chaining_setSchemaStatement_correctSql() {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.append("SET SCHEMA ");
        sb.identifier("MY_SCHEMA");
        Assert.assertEquals("SET SCHEMA \"MY_SCHEMA\"", sb.toString());
    }

    // =================== identifiers list tests ===================

    @Test
    public void identifiers_multipleColumns_commaSeparated() {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.identifiers(Arrays.asList("COL1", "COL2", "COL3"));
        Assert.assertEquals("\"COL1\",\"COL2\",\"COL3\"", sb.toString());
    }

    // =================== values list tests ===================

    @Test
    public void values_multipleValues_commaSeparated() {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.values(Arrays.asList("val1", "val2"));
        Assert.assertEquals("'val1','val2'", sb.toString());
    }

    // =================== space and line tests ===================

    @Test
    public void space_appendsWhiteSpace() {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.append("A").space().append("B");
        Assert.assertEquals("A B", sb.toString());
    }

    @Test
    public void line_appendsNewLine() {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.append("A").line().append("B");
        Assert.assertEquals("A\nB", sb.toString());
    }

    // =================== schemaPrefixIfNotBlank tests ===================

    @Test
    public void schemaPrefixIfNotBlank_withSchemaName_appendsSchemaPrefix() {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.schemaPrefixIfNotBlank("MY_SCHEMA");
        sb.identifier("MY_TABLE");
        Assert.assertEquals("\"MY_SCHEMA\".\"MY_TABLE\"", sb.toString());
    }

    @Test
    public void schemaPrefixIfNotBlank_withBlankSchemaName_skipsPrefix() {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.schemaPrefixIfNotBlank("");
        sb.identifier("MY_TABLE");
        Assert.assertEquals("\"MY_TABLE\"", sb.toString());
    }

    @Test
    public void schemaPrefixIfNotBlank_withNullSchemaName_skipsPrefix() {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.schemaPrefixIfNotBlank(null);
        sb.identifier("MY_TABLE");
        Assert.assertEquals("\"MY_TABLE\"", sb.toString());
    }

    // =================== comprehensive SQL construction tests ===================

    @Test
    public void comprehensiveSelect_withWhereClause_correctSql() {
        Map<String, String> testCases = new LinkedHashMap<>();
        testCases.put(
                "SELECT TABLE_NAME FROM SYS.ALL_TABLES WHERE OWNER='ADMIN' ORDER BY TABLE_NAME ASC",
                buildShowTablesLikeSql("ADMIN", null));
        testCases.put(
                "SELECT TABLE_NAME FROM SYS.ALL_TABLES WHERE OWNER='ADMIN'"
                        + " AND TABLE_NAME LIKE '%test%' ESCAPE '\\' ORDER BY TABLE_NAME ASC",
                buildShowTablesLikeSql("ADMIN", "test"));

        for (Map.Entry<String, String> entry : testCases.entrySet()) {
            Assert.assertEquals(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Helper method to simulate DmSchemaAccessor.showTablesLike SQL generation.
     */
    private String buildShowTablesLikeSql(String schemaName, String tableNameLike) {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.append("SELECT TABLE_NAME FROM SYS.ALL_TABLES WHERE OWNER=");
        sb.value(schemaName);
        if (tableNameLike != null && !tableNameLike.isEmpty()) {
            sb.append(" AND ").like("TABLE_NAME", tableNameLike);
        }
        sb.append(" ORDER BY TABLE_NAME ASC");
        return sb.toString();
    }
}
