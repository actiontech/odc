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
package com.oceanbase.odc.core.sql.split;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link PostgreSqlSplitter}.
 *
 * <p>
 * Test categories:
 * <ul>
 * <li>Boundary conditions (3 tests)</li>
 * <li>Basic splitting (3 tests)</li>
 * <li>Dollar-quoting (8 tests)</li>
 * <li>E-string (3 tests)</li>
 * <li>Comment handling (4 tests)</li>
 * <li>String/Identifier handling (4 tests)</li>
 * <li>Complex scenarios (5 tests)</li>
 * </ul>
 * </p>
 */
public class PostgreSqlSplitterTest {

    // ==================== 边界条件测试 (3 tests) ====================

    @Test
    public void split_Blank_Empty() {
        String sql = " ";
        PostgreSqlSplitter splitter = new PostgreSqlSplitter();

        List<String> stmts = splitter.split(sql).stream()
                .map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertTrue(stmts.isEmpty());
    }

    @Test
    public void split_Null_Empty() {
        String sql = null;
        PostgreSqlSplitter splitter = new PostgreSqlSplitter();

        List<String> stmts = splitter.split(sql).stream()
                .map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertTrue(stmts.isEmpty());
    }

    @Test
    public void split_EmptyString_Empty() {
        String sql = "";
        PostgreSqlSplitter splitter = new PostgreSqlSplitter();

        List<String> stmts = splitter.split(sql).stream()
                .map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertTrue(stmts.isEmpty());
    }

    // ==================== 基本切分测试 (3 tests) ====================

    @Test
    public void split_SingleStatement() {
        String sql = "SELECT 1;";
        PostgreSqlSplitter splitter = new PostgreSqlSplitter();

        List<String> stmts = splitter.split(sql).stream()
                .map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(1, stmts.size());
        Assert.assertEquals("SELECT 1;", stmts.get(0));
    }

    @Test
    public void split_MultipleStatements() {
        String sql = "SELECT 1;\nSELECT 2;";
        PostgreSqlSplitter splitter = new PostgreSqlSplitter();

        List<String> stmts = splitter.split(sql).stream()
                .map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(2, stmts.size());
        Assert.assertEquals("SELECT 1;", stmts.get(0));
        Assert.assertEquals("\nSELECT 2;", stmts.get(1));
    }

    @Test
    public void split_NoTrailingDelimiter() {
        String sql = "SELECT 1";
        PostgreSqlSplitter splitter = new PostgreSqlSplitter();

        List<String> stmts = splitter.split(sql).stream()
                .map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(1, stmts.size());
        Assert.assertEquals("SELECT 1", stmts.get(0));
    }

    // ==================== Dollar-quoting 测试 (8 tests) ====================

    @Test
    public void split_DollarQuote_NoSplitInside() {
        // Simple $$...$$ without tag
        String sql = "SELECT $$hello;world$$;";
        PostgreSqlSplitter splitter = new PostgreSqlSplitter();

        List<String> stmts = splitter.split(sql).stream()
                .map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(1, stmts.size());
        Assert.assertEquals("SELECT $$hello;world$$;", stmts.get(0));
    }

    @Test
    public void split_DollarQuoteWithTag_NoSplitInside() {
        // $tag$...$tag$ with custom tag
        String sql = "SELECT $body$hello;world$body$;";
        PostgreSqlSplitter splitter = new PostgreSqlSplitter();

        List<String> stmts = splitter.split(sql).stream()
                .map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(1, stmts.size());
        Assert.assertEquals("SELECT $body$hello;world$body$;", stmts.get(0));
    }

    @Test
    public void split_DollarQuoteInFunction_NoSplitInside() {
        // Realistic PG function with multiple semicolons inside $$
        String sql = "CREATE FUNCTION test() RETURNS void AS $$\n" +
                "BEGIN\n" +
                "  SELECT 1;\n" +
                "  SELECT 2;\n" +
                "END;\n" +
                "$$ LANGUAGE plpgsql;";
        PostgreSqlSplitter splitter = new PostgreSqlSplitter();

        List<String> stmts = splitter.split(sql).stream()
                .map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(1, stmts.size());
        Assert.assertTrue(stmts.get(0).contains("$$"));
    }

    @Test
    public void split_DollarQuoteWithUnderscoreTag() {
        // Tag with underscore
        String sql = "SELECT $my_tag$content;here$my_tag$;";
        PostgreSqlSplitter splitter = new PostgreSqlSplitter();

        List<String> stmts = splitter.split(sql).stream()
                .map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(1, stmts.size());
        Assert.assertEquals("SELECT $my_tag$content;here$my_tag$;", stmts.get(0));
    }

    @Test
    public void split_DollarQuoteWithNumericInTag() {
        // Tag with numbers (not at the beginning)
        String sql = "SELECT $tag123$content;here$tag123$;";
        PostgreSqlSplitter splitter = new PostgreSqlSplitter();

        List<String> stmts = splitter.split(sql).stream()
                .map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(1, stmts.size());
        Assert.assertEquals("SELECT $tag123$content;here$tag123$;", stmts.get(0));
    }

    @Test
    public void split_DollarQuoteEmptyContent() {
        // Empty content between $$
        String sql = "SELECT $$$$;";
        PostgreSqlSplitter splitter = new PostgreSqlSplitter();

        List<String> stmts = splitter.split(sql).stream()
                .map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(1, stmts.size());
        Assert.assertEquals("SELECT $$$$;", stmts.get(0));
    }

    @Test
    public void split_DollarQuoteNewlines() {
        // Dollar-quoted string with newlines
        String sql = "SELECT $$line1\nline2\n;line3$$;";
        PostgreSqlSplitter splitter = new PostgreSqlSplitter();

        List<String> stmts = splitter.split(sql).stream()
                .map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(1, stmts.size());
        Assert.assertTrue(stmts.get(0).contains("line1"));
        Assert.assertTrue(stmts.get(0).contains("line3"));
    }

    @Test
    public void split_MultipleDollarQuotesInOneStatement() {
        // Multiple dollar-quoted strings in one statement (different tags)
        String sql = "SELECT $a$test1$a$, $b$test2;b$b$;";
        PostgreSqlSplitter splitter = new PostgreSqlSplitter();

        List<String> stmts = splitter.split(sql).stream()
                .map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(1, stmts.size());
        Assert.assertTrue(stmts.get(0).contains("$a$"));
        Assert.assertTrue(stmts.get(0).contains("$b$"));
    }

    // ==================== E-string 测试 (3 tests) ====================

    @Test
    public void split_EString_NoSplitInside() {
        // E-string with semicolon
        String sql = "SELECT E'hello;world';";
        PostgreSqlSplitter splitter = new PostgreSqlSplitter();

        List<String> stmts = splitter.split(sql).stream()
                .map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(1, stmts.size());
        Assert.assertEquals("SELECT E'hello;world';", stmts.get(0));
    }

    @Test
    public void split_EStringWithBackslashEscape() {
        // E-string with backslash escapes
        String sql = "SELECT E'line1\\nline2\\ttab';";
        PostgreSqlSplitter splitter = new PostgreSqlSplitter();

        List<String> stmts = splitter.split(sql).stream()
                .map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(1, stmts.size());
        Assert.assertEquals("SELECT E'line1\\nline2\\ttab';", stmts.get(0));
    }

    @Test
    public void split_EStringWithBackslashQuote() {
        // E-string with escaped quote via backslash
        String sql = "SELECT E'I\\'m here';";
        PostgreSqlSplitter splitter = new PostgreSqlSplitter();

        List<String> stmts = splitter.split(sql).stream()
                .map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(1, stmts.size());
        Assert.assertTrue(stmts.get(0).contains("E'I\\'m here'"));
    }

    // ==================== 注释处理测试 (4 tests) ====================

    @Test
    public void split_LineComment_SemicolonIgnored() {
        // Semicolon in line comment should not split
        // The semicolon BEFORE the comment triggers split, but semicolon IN comment does not
        String sql = "SELECT 1 -- comment; here\n;SELECT 2;";
        PostgreSqlSplitter splitter = new PostgreSqlSplitter();

        List<String> stmts = splitter.split(sql).stream()
                .map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(2, stmts.size());
        Assert.assertEquals("SELECT 1 -- comment; here\n;", stmts.get(0));
        Assert.assertEquals("SELECT 2;", stmts.get(1));
    }

    @Test
    public void split_BlockComment_SemicolonIgnored() {
        // Semicolon in block comment should not split
        String sql = "SELECT 1 /* comment; here */;SELECT 2;";
        PostgreSqlSplitter splitter = new PostgreSqlSplitter();

        List<String> stmts = splitter.split(sql).stream()
                .map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(2, stmts.size());
        Assert.assertTrue(stmts.get(0).contains("/* comment; here */"));
        Assert.assertEquals("SELECT 2;", stmts.get(1));
    }

    @Test
    public void split_NestedBlockComment_NoSplitInside() {
        // PostgreSQL supports nested block comments
        String sql = "SELECT 1 /* outer /* inner; nested */ back */;SELECT 2;";
        PostgreSqlSplitter splitter = new PostgreSqlSplitter();

        List<String> stmts = splitter.split(sql).stream()
                .map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(2, stmts.size());
        Assert.assertTrue(stmts.get(0).contains("/* outer /* inner; nested */ back */"));
        Assert.assertEquals("SELECT 2;", stmts.get(1));
    }

    @Test
    public void split_DeeplyNestedBlockComment() {
        // Deeply nested block comments
        String sql = "SELECT 1 /* level1 /* level2 /* level3; */ */ */;SELECT 2;";
        PostgreSqlSplitter splitter = new PostgreSqlSplitter();

        List<String> stmts = splitter.split(sql).stream()
                .map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(2, stmts.size());
        Assert.assertTrue(stmts.get(0).contains("level3;"));
        Assert.assertEquals("SELECT 2;", stmts.get(1));
    }

    // ==================== 字符串/标识符测试 (4 tests) ====================

    @Test
    public void split_SingleQuoteString_NoSplitInside() {
        // Semicolon in single-quoted string
        String sql = "SELECT 'hello;world';";
        PostgreSqlSplitter splitter = new PostgreSqlSplitter();

        List<String> stmts = splitter.split(sql).stream()
                .map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(1, stmts.size());
        Assert.assertEquals("SELECT 'hello;world';", stmts.get(0));
    }

    @Test
    public void split_EscapedSingleQuote() {
        // Doubled single quote escape
        String sql = "SELECT 'it''s;ok';";
        PostgreSqlSplitter splitter = new PostgreSqlSplitter();

        List<String> stmts = splitter.split(sql).stream()
                .map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(1, stmts.size());
        Assert.assertEquals("SELECT 'it''s;ok';", stmts.get(0));
    }

    @Test
    public void split_DoubleQuoteIdentifier_NoSplitInside() {
        // Semicolon in double-quoted identifier
        String sql = "SELECT \"column;name\" FROM t;";
        PostgreSqlSplitter splitter = new PostgreSqlSplitter();

        List<String> stmts = splitter.split(sql).stream()
                .map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(1, stmts.size());
        Assert.assertEquals("SELECT \"column;name\" FROM t;", stmts.get(0));
    }

    @Test
    public void split_EscapedDoubleQuoteIdentifier() {
        // Doubled double quote escape in identifier
        String sql = "SELECT \"column\"\"name\" FROM t;";
        PostgreSqlSplitter splitter = new PostgreSqlSplitter();

        List<String> stmts = splitter.split(sql).stream()
                .map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(1, stmts.size());
        Assert.assertEquals("SELECT \"column\"\"name\" FROM t;", stmts.get(0));
    }

    // ==================== 综合场景测试 (5 tests) ====================

    @Test
    public void split_ComplexFunction() {
        // Complete PostgreSQL function with various constructs
        String sql = "CREATE OR REPLACE FUNCTION test_func(p_id INTEGER)\n" +
                "RETURNS INTEGER AS $$\n" +
                "DECLARE\n" +
                "  v_result INTEGER;\n" +
                "BEGIN\n" +
                "  SELECT col INTO v_result FROM table WHERE id = p_id;\n" +
                "  IF v_result > 0 THEN\n" +
                "    RETURN v_result;\n" +
                "  END IF;\n" +
                "  RETURN 0;\n" +
                "END;\n" +
                "$$ LANGUAGE plpgsql;\n" +
                "SELECT test_func(1);";
        PostgreSqlSplitter splitter = new PostgreSqlSplitter();

        List<String> stmts = splitter.split(sql).stream()
                .map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(2, stmts.size());
        Assert.assertTrue(stmts.get(0).contains("CREATE OR REPLACE FUNCTION"));
        Assert.assertTrue(stmts.get(0).contains("$$"));
        Assert.assertTrue(stmts.get(1).contains("test_func(1)"));
    }

    @Test
    public void split_MixedConstructs() {
        // Mix of comments, strings, identifiers, dollar quotes
        String sql = "SELECT \"id\", 'value;1';\n" +
                "/* block; comment */\n" +
                "SELECT $$dollar;$$, E'e\\';string';";
        PostgreSqlSplitter splitter = new PostgreSqlSplitter();

        List<String> stmts = splitter.split(sql).stream()
                .map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(2, stmts.size());
    }

    @Test
    public void split_OffsetCorrect() {
        // Verify offset tracking
        String sql = "SELECT 1;\nSELECT 2;";
        PostgreSqlSplitter splitter = new PostgreSqlSplitter();

        List<OffsetString> stmts = splitter.split(sql);

        Assert.assertEquals(2, stmts.size());
        Assert.assertEquals(0, stmts.get(0).getOffset());
        Assert.assertEquals("SELECT 1;", stmts.get(0).getStr());
        Assert.assertEquals(9, stmts.get(1).getOffset());
        Assert.assertEquals("\nSELECT 2;", stmts.get(1).getStr());
    }

    @Test
    public void split_ProcedureWithNamedDollarTag() {
        // PostgreSQL 11+ procedure with named dollar tag
        String sql = "CREATE OR REPLACE PROCEDURE my_proc()\n" +
                "LANGUAGE plpgsql\n" +
                "AS $procedure$\n" +
                "BEGIN\n" +
                "  INSERT INTO log VALUES ('test');\n" +
                "  COMMIT;\n" +
                "END;\n" +
                "$procedure$;\n" +
                "CALL my_proc();";
        PostgreSqlSplitter splitter = new PostgreSqlSplitter();

        List<String> stmts = splitter.split(sql).stream()
                .map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(2, stmts.size());
        Assert.assertTrue(stmts.get(0).contains("CREATE OR REPLACE PROCEDURE"));
        Assert.assertTrue(stmts.get(0).contains("$procedure$"));
        Assert.assertTrue(stmts.get(1).contains("CALL my_proc()"));
    }

    @Test
    public void split_MultipleSimilarTags() {
        // Ensure different tags don't interfere
        String sql = "SELECT $a$content$a$, $a$more$a$;\nSELECT $b$other$b$;";
        PostgreSqlSplitter splitter = new PostgreSqlSplitter();

        List<String> stmts = splitter.split(sql).stream()
                .map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(2, stmts.size());
    }

    // ==================== Iterator 测试 ====================

    @Test
    public void iterator_Basic() {
        String sql = "SELECT 1;\nSELECT 2;";
        SqlStatementIterator iterator = PostgreSqlSplitter.iterator(
                new ByteArrayInputStream(sql.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8, ";");

        List<String> stmts = new ArrayList<>();
        while (iterator.hasNext()) {
            stmts.add(iterator.next().getStr());
        }

        Assert.assertEquals(2, stmts.size());
        Assert.assertEquals("SELECT 1;", stmts.get(0));
        Assert.assertEquals("\nSELECT 2;", stmts.get(1));
    }

    @Test
    public void iterator_IteratedBytes() {
        String sql = "SELECT 1;\nSELECT 2;";
        SqlStatementIterator iterator = PostgreSqlSplitter.iterator(
                new ByteArrayInputStream(sql.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8, ";");

        long bytes = 0;
        while (iterator.hasNext()) {
            iterator.next();
            bytes = iterator.iteratedBytes();
        }

        Assert.assertEquals(sql.length(), bytes);
    }

    // ==================== 额外边界测试 ====================

    @Test
    public void split_EStringLowercase() {
        // Lowercase e prefix for E-string
        String sql = "SELECT e'hello;world';";
        PostgreSqlSplitter splitter = new PostgreSqlSplitter();

        List<String> stmts = splitter.split(sql).stream()
                .map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(1, stmts.size());
        Assert.assertEquals("SELECT e'hello;world';", stmts.get(0));
    }

    @Test
    public void split_StandaloneDollarNotTreatedAsQuote() {
        // Standalone $ not at word boundary should not start dollar-quoting
        // (invalid dollar tag - can't start with digit)
        String sql = "SELECT $1, $2 FROM table;";
        PostgreSqlSplitter splitter = new PostgreSqlSplitter();

        List<String> stmts = splitter.split(sql).stream()
                .map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(1, stmts.size());
        Assert.assertTrue(stmts.get(0).contains("$1"));
        Assert.assertTrue(stmts.get(0).contains("$2"));
    }

    @Test
    public void split_EStringWithDoubleQuoteEscape() {
        // E-string also supports '' escape
        String sql = "SELECT E'it''s ok';";
        PostgreSqlSplitter splitter = new PostgreSqlSplitter();

        List<String> stmts = splitter.split(sql).stream()
                .map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(1, stmts.size());
        Assert.assertEquals("SELECT E'it''s ok';", stmts.get(0));
    }

    @Test
    public void split_DollarQuoteContainsQuotes() {
        // Dollar-quoted string containing quotes
        String sql = "SELECT $$it's \"quoted\"$$;";
        PostgreSqlSplitter splitter = new PostgreSqlSplitter();

        List<String> stmts = splitter.split(sql).stream()
                .map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(1, stmts.size());
        Assert.assertTrue(stmts.get(0).contains("it's"));
        Assert.assertTrue(stmts.get(0).contains("\"quoted\""));
    }

    @Test
    public void split_CustomDelimiter() {
        // Using custom delimiter (not dollar sign to avoid conflict with dollar-quoting)
        String sql = "SELECT 1@\nSELECT 2@";
        PostgreSqlSplitter splitter = new PostgreSqlSplitter("@");

        List<String> stmts = splitter.split(sql).stream()
                .map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(2, stmts.size());
        Assert.assertEquals("SELECT 1@", stmts.get(0));
        Assert.assertEquals("\nSELECT 2@", stmts.get(1));
    }
}
