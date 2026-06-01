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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link PostgresSqlBuilder}.
 *
 * <p>
 * Test coverage:
 * <ul>
 * <li>Identifier quoting with double quotes</li>
 * <li>Value quoting with single quotes</li>
 * <li>Escape handling for embedded quotes</li>
 * <li>Schema prefix handling</li>
 * <li>LIKE clause handling</li>
 * <li>NULL value handling</li>
 * </ul>
 * </p>
 */
public class PostgresSqlBuilderTest {

    private PostgresSqlBuilder builder;

    @Before
    public void setUp() {
        builder = new PostgresSqlBuilder();
    }

    // ========== Identifier Tests ==========

    /**
     * Test basic identifier quoting. PostgreSQL uses double quotes for identifiers.
     */
    @Test
    public void testIdentifier_Basic() {
        String result = builder.identifier("table_name").toString();
        Assert.assertEquals("\"table_name\"", result);
    }

    /**
     * Test identifier with embedded double quote. Double quotes are escaped by doubling.
     */
    @Test
    public void testIdentifier_EscapeDoubleQuote() {
        String result = builder.identifier("col\"umn").toString();
        Assert.assertEquals("\"col\"\"umn\"", result);
    }

    /**
     * Test blank identifier returns empty.
     */
    @Test
    public void testIdentifier_Blank() {
        String result = builder.identifier("").toString();
        Assert.assertEquals("", result);
    }

    /**
     * Test null identifier returns empty.
     */
    @Test
    public void testIdentifier_Null() {
        String result = builder.identifier(null).toString();
        Assert.assertEquals("", result);
    }

    /**
     * Test multiple identifiers in sequence.
     */
    @Test
    public void testIdentifier_Multiple() {
        String result = builder.identifier("schema").append(".").identifier("table").toString();
        Assert.assertEquals("\"schema\".\"table\"", result);
    }

    // ========== Value Tests ==========

    /**
     * Test basic value quoting. PostgreSQL uses single quotes for values.
     */
    @Test
    public void testValue_Basic() {
        String result = builder.value("hello").toString();
        Assert.assertEquals("'hello'", result);
    }

    /**
     * Test value with embedded single quote. Single quotes are escaped by doubling.
     */
    @Test
    public void testValue_EscapeSingleQuote() {
        String result = builder.value("it's").toString();
        Assert.assertEquals("'it''s'", result);
    }

    /**
     * Test value with multiple embedded single quotes.
     */
    @Test
    public void testValue_MultipleSingleQuotes() {
        String result = builder.value("it's a test, isn't it?").toString();
        Assert.assertEquals("'it''s a test, isn''t it?'", result);
    }

    /**
     * Test null value returns NULL (SQL keyword).
     */
    @Test
    public void testValue_Null() {
        String result = builder.value(null).toString();
        Assert.assertEquals("NULL", result);
    }

    /**
     * Test empty value.
     */
    @Test
    public void testValue_Empty() {
        String result = builder.value("").toString();
        Assert.assertEquals("''", result);
    }

    // ========== DefaultValue Tests ==========

    /**
     * Test default value is appended as-is.
     */
    @Test
    public void testDefaultValue_Function() {
        String result = builder.defaultValue("now()").toString();
        Assert.assertEquals("now()", result);
    }

    /**
     * Test default value with literal.
     */
    @Test
    public void testDefaultValue_Literal() {
        String result = builder.defaultValue("'default'").toString();
        Assert.assertEquals("'default'", result);
    }

    // ========== SchemaPrefix Tests ==========

    /**
     * Test schema prefix adds identifier with dot.
     */
    @Test
    public void testSchemaPrefixIfNotBlank_Basic() {
        String result = builder.schemaPrefixIfNotBlank("myschema").identifier("mytable").toString();
        Assert.assertEquals("\"myschema\".\"mytable\"", result);
    }

    /**
     * Test blank schema prefix is skipped.
     */
    @Test
    public void testSchemaPrefixIfNotBlank_Blank() {
        String result = builder.schemaPrefixIfNotBlank("").identifier("mytable").toString();
        Assert.assertEquals("\"mytable\"", result);
    }

    /**
     * Test null schema prefix is skipped.
     */
    @Test
    public void testSchemaPrefixIfNotBlank_Null() {
        String result = builder.schemaPrefixIfNotBlank(null).identifier("mytable").toString();
        Assert.assertEquals("\"mytable\"", result);
    }

    // ========== identifier(String, String) Tests ==========

    /**
     * Test two-argument identifier method.
     */
    @Test
    public void testIdentifier_TwoArgs() {
        String result = builder.identifier("schema", "table").toString();
        Assert.assertEquals("\"schema\".\"table\"", result);
    }

    /**
     * Test two-argument identifier with null schema.
     */
    @Test
    public void testIdentifier_TwoArgs_NullSchema() {
        String result = builder.identifier(null, "table").toString();
        Assert.assertEquals("\"table\"", result);
    }

    // ========== LIKE Tests ==========

    /**
     * Test LIKE clause without explicit ESCAPE. PostgreSQL's LIKE behavior differs from Oracle which
     * adds "ESCAPE '\'".
     */
    @Test
    public void testLike_Basic() {
        String result = builder.like("name", "test").toString();
        Assert.assertEquals("name LIKE '%test%'", result);
    }

    /**
     * Test LIKE clause with special characters.
     */
    @Test
    public void testLike_SpecialChars() {
        String result = builder.like("name", "%test").toString();
        // % should be escaped in the like pattern
        Assert.assertTrue(result.contains("\\%"));
    }

    // ========== List Tests ==========

    /**
     * Test identifiers list.
     */
    @Test
    public void testIdentifiers_List() {
        String result = builder.identifiers(Arrays.asList("col1", "col2", "col3")).toString();
        Assert.assertEquals("\"col1\",\"col2\",\"col3\"", result);
    }

    /**
     * Test values list.
     */
    @Test
    public void testValues_List() {
        String result = builder.values(Arrays.asList("val1", "val2")).toString();
        Assert.assertEquals("'val1','val2'", result);
    }

    // ========== Complex SQL Construction Tests ==========

    /**
     * Test building a simple SELECT statement.
     */
    @Test
    public void testBuildSelectStatement() {
        String result = builder.append("SELECT ")
                .identifiers(Arrays.asList("id", "name"))
                .append(" FROM ")
                .identifier("public", "users")
                .append(" WHERE ")
                .identifier("status")
                .append(" = ")
                .value("active")
                .toString();
        Assert.assertEquals("SELECT \"id\",\"name\" FROM \"public\".\"users\" WHERE \"status\" = 'active'", result);
    }

    /**
     * Test building an INSERT statement.
     */
    @Test
    public void testBuildInsertStatement() {
        String result = builder.append("INSERT INTO ")
                .identifier("public", "users")
                .append(" (")
                .identifiers(Arrays.asList("id", "name"))
                .append(") VALUES (")
                .values(Arrays.asList("1", "John's Data"))
                .append(")")
                .toString();
        Assert.assertEquals(
                "INSERT INTO \"public\".\"users\" (\"id\",\"name\") VALUES ('1','John''s Data')",
                result);
    }

    /**
     * Test building a CREATE TABLE statement with reserved keywords.
     */
    @Test
    public void testBuildCreateTableWithReservedKeywords() {
        String result = builder.append("CREATE TABLE ")
                .identifier("public", "order")
                .append(" (")
                .identifier("id").append(" SERIAL PRIMARY KEY, ")
                .identifier("user").append(" VARCHAR(100), ")
                .identifier("table").append(" VARCHAR(100)")
                .append(")")
                .toString();
        Assert.assertEquals(
                "CREATE TABLE \"public\".\"order\" (\"id\" SERIAL PRIMARY KEY, \"user\" VARCHAR(100), \"table\" VARCHAR(100))",
                result);
    }

    // ========== Comparison with Oracle Behavior ==========

    /**
     * Verify that PostgreSQL identifier quoting is same as Oracle (both use double quotes).
     */
    @Test
    public void testIdentifier_SameAsOracle() {
        PostgresSqlBuilder pgBuilder = new PostgresSqlBuilder();
        OracleSqlBuilder oracleBuilder = new OracleSqlBuilder();

        String pgResult = pgBuilder.identifier("table_name").toString();
        String oracleResult = oracleBuilder.identifier("table_name").toString();

        Assert.assertEquals("PostgreSQL and Oracle should have same identifier quoting", oracleResult, pgResult);
    }

    /**
     * Verify that PostgreSQL value quoting is same as Oracle (both use single quotes).
     */
    @Test
    public void testValue_SameAsOracle() {
        PostgresSqlBuilder pgBuilder = new PostgresSqlBuilder();
        OracleSqlBuilder oracleBuilder = new OracleSqlBuilder();

        String pgResult = pgBuilder.value("test's value").toString();
        String oracleResult = oracleBuilder.value("test's value").toString();

        Assert.assertEquals("PostgreSQL and Oracle should have same value quoting", oracleResult, pgResult);
    }

    /**
     * Verify LIKE clause differs from Oracle. Oracle appends "ESCAPE '\'" after LIKE clause.
     */
    @Test
    public void testLike_DifferentFromOracle() {
        PostgresSqlBuilder pgBuilder = new PostgresSqlBuilder();
        OracleSqlBuilder oracleBuilder = new OracleSqlBuilder();

        String pgResult = pgBuilder.like("name", "test").toString();
        String oracleResult = oracleBuilder.like("name", "test").toString();

        // Oracle adds ESCAPE '\' at the end
        Assert.assertFalse("PostgreSQL should not have ESCAPE clause", pgResult.contains("ESCAPE"));
        Assert.assertTrue("Oracle should have ESCAPE clause", oracleResult.contains("ESCAPE"));
    }
}
