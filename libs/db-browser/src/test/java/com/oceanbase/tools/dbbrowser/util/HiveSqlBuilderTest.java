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
 * Unit tests for {@link HiveSqlBuilder}. Verifies Hive-specific SQL building behavior: backtick
 * identifier quoting (inherited from MySQLSqlBuilder) and two-level schema prefix
 * ({@code database.table}).
 */
public class HiveSqlBuilderTest {

    // =================== identifier tests (design.md 5.2.1) ===================

    @Test
    public void identifier_simpleTableName_quotedWithBackticks() {
        HiveSqlBuilder sb = new HiveSqlBuilder();
        sb.identifier("my_table");
        Assert.assertEquals("`my_table`", sb.toString());
    }

    @Test
    public void identifier_nameContainingBacktick_escapedByDoubling() {
        HiveSqlBuilder sb = new HiveSqlBuilder();
        sb.identifier("my`table");
        Assert.assertEquals("`my``table`", sb.toString());
    }

    @Test
    public void identifier_nullInput_returnsNull() {
        HiveSqlBuilder sb = new HiveSqlBuilder();
        sb.identifier(null);
        Assert.assertEquals("null", sb.toString());
    }

    @Test
    public void identifier_emptyString_quotedEmpty() {
        HiveSqlBuilder sb = new HiveSqlBuilder();
        sb.identifier("");
        Assert.assertEquals("``", sb.toString());
    }

    @Test
    public void identifier_withSchemaAndObject_dotSeparated() {
        HiveSqlBuilder sb = new HiveSqlBuilder();
        sb.identifier("mydb", "my_table");
        Assert.assertEquals("`mydb`.`my_table`", sb.toString());
    }

    // =================== schemaPrefixIfNotBlank tests (design.md 5.2.1) ===================

    @Test
    public void schemaPrefixIfNotBlank_withSchemaName_appendsDatabasePrefix() {
        HiveSqlBuilder sb = new HiveSqlBuilder();
        sb.schemaPrefixIfNotBlank("mydb");
        Assert.assertEquals("`mydb`.", sb.toString());
    }

    @Test
    public void schemaPrefixIfNotBlank_withSchemaAndTable_twoLevelNaming() {
        HiveSqlBuilder sb = new HiveSqlBuilder();
        sb.schemaPrefixIfNotBlank("mydb");
        sb.identifier("my_table");
        Assert.assertEquals("`mydb`.`my_table`", sb.toString());
    }

    @Test
    public void schemaPrefixIfNotBlank_emptyString_skipsPrefix() {
        HiveSqlBuilder sb = new HiveSqlBuilder();
        sb.schemaPrefixIfNotBlank("");
        Assert.assertEquals("", sb.toString());
    }

    @Test
    public void schemaPrefixIfNotBlank_nullSchemaName_skipsPrefix() {
        HiveSqlBuilder sb = new HiveSqlBuilder();
        sb.schemaPrefixIfNotBlank(null);
        Assert.assertEquals("", sb.toString());
    }

    @Test
    public void schemaPrefixIfNotBlank_blankSpacesOnly_skipsPrefix() {
        HiveSqlBuilder sb = new HiveSqlBuilder();
        sb.schemaPrefixIfNotBlank("   ");
        Assert.assertEquals("", sb.toString());
    }

    @Test
    public void schemaPrefixIfNotBlank_schemaWithBacktick_escapedCorrectly() {
        HiveSqlBuilder sb = new HiveSqlBuilder();
        sb.schemaPrefixIfNotBlank("my`db");
        sb.identifier("my_table");
        Assert.assertEquals("`my``db`.`my_table`", sb.toString());
    }

    /**
     * Hive uses two-level naming (database.table), NOT three-level like SQL Server
     * (catalog.schema.table). Schema names containing dots should NOT be split.
     */
    @Test
    public void schemaPrefixIfNotBlank_schemaWithDot_notSplitLikeSqlServer() {
        HiveSqlBuilder sb = new HiveSqlBuilder();
        sb.schemaPrefixIfNotBlank("my.db");
        sb.identifier("my_table");
        // The dot is inside the identifier, treated as a literal character
        Assert.assertEquals("`my.db`.`my_table`", sb.toString());
    }

    // =================== value tests ===================

    @Test
    public void value_simpleString_quotedWithSingleQuotes() {
        HiveSqlBuilder sb = new HiveSqlBuilder();
        sb.value("hello");
        Assert.assertEquals("'hello'", sb.toString());
    }

    @Test
    public void value_stringContainingSingleQuote_escapedByDoubling() {
        HiveSqlBuilder sb = new HiveSqlBuilder();
        sb.value("it's");
        Assert.assertEquals("'it''s'", sb.toString());
    }

    // =================== chaining tests ===================

    @Test
    public void chaining_selectFromDatabaseTable_correctSql() {
        HiveSqlBuilder sb = new HiveSqlBuilder();
        sb.append("SELECT * FROM ");
        sb.schemaPrefixIfNotBlank("mydb");
        sb.identifier("my_table");
        Assert.assertEquals("SELECT * FROM `mydb`.`my_table`", sb.toString());
    }

    @Test
    public void chaining_selectWithoutSchema_correctSql() {
        HiveSqlBuilder sb = new HiveSqlBuilder();
        sb.append("SELECT * FROM ");
        sb.schemaPrefixIfNotBlank("");
        sb.identifier("my_table");
        Assert.assertEquals("SELECT * FROM `my_table`", sb.toString());
    }

    // =================== identifiers list tests ===================

    @Test
    public void identifiers_multipleColumns_commaSeparated() {
        HiveSqlBuilder sb = new HiveSqlBuilder();
        sb.identifiers(Arrays.asList("col1", "col2", "col3"));
        Assert.assertEquals("`col1`,`col2`,`col3`", sb.toString());
    }

    // =================== map case comprehensive tests ===================

    @Test
    public void mapCase_identifierQuoting_allCases() {
        Map<String, String> cases = new LinkedHashMap<>();
        cases.put("`my_table`", "my_table");
        cases.put("`my``table`", "my`table");
        cases.put("`123_start`", "123_start");
        cases.put("`select`", "select");
        cases.put("`with spaces`", "with spaces");
        cases.put("`UPPER_CASE`", "UPPER_CASE");

        for (Map.Entry<String, String> entry : cases.entrySet()) {
            HiveSqlBuilder sb = new HiveSqlBuilder();
            sb.identifier(entry.getValue());
            Assert.assertEquals("identifier(" + entry.getValue() + ")",
                    entry.getKey(), sb.toString());
        }
    }

    @Test
    public void mapCase_schemaPrefixAndTable_allCases() {
        Map<String, String[]> cases = new LinkedHashMap<>();
        // key = expected output, value = [schemaName, tableName]
        cases.put("`mydb`.`my_table`", new String[] {"mydb", "my_table"});
        cases.put("`default`.`test`", new String[] {"default", "test"});
        cases.put("`my_table`", new String[] {"", "my_table"});
        cases.put("`my_table`", new String[] {null, "my_table"});

        int i = 0;
        for (Map.Entry<String, String[]> entry : cases.entrySet()) {
            HiveSqlBuilder sb = new HiveSqlBuilder();
            sb.schemaPrefixIfNotBlank(entry.getValue()[0]);
            sb.identifier(entry.getValue()[1]);
            Assert.assertEquals("case " + i + ": schema=" + entry.getValue()[0]
                    + ", table=" + entry.getValue()[1],
                    entry.getKey(), sb.toString());
            i++;
        }
    }
}
