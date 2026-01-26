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
 * Unit tests for {@link SqlServerSqlSplitter}.
 */
public class SqlServerSqlSplitterTest {

    @Test
    public void split_Blank_Empty() {
        String sql = " ";
        SqlServerSqlSplitter splitter = new SqlServerSqlSplitter(";");

        List<String> stmts = splitter.split(sql).stream().map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertTrue(stmts.isEmpty());
    }

    @Test
    public void split_Null_Empty() {
        String sql = null;
        SqlServerSqlSplitter splitter = new SqlServerSqlSplitter(";");

        List<String> stmts = splitter.split(sql).stream().map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertTrue(stmts.isEmpty());
    }

    @Test
    public void split_EmptyString_Empty() {
        String sql = "";
        SqlServerSqlSplitter splitter = new SqlServerSqlSplitter(";");

        List<String> stmts = splitter.split(sql).stream().map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertTrue(stmts.isEmpty());
    }

    @Test
    public void split_SingleStatement() {
        String sql = "SELECT 1 FROM dual;";
        SqlServerSqlSplitter splitter = new SqlServerSqlSplitter(";");

        List<String> stmts = splitter.split(sql).stream().map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(1, stmts.size());
        Assert.assertEquals("SELECT 1 FROM dual;", stmts.get(0));
    }

    @Test
    public void split_MultipleStatements() {
        String sql = "SELECT 1 FROM dual;\nSELECT 2 FROM dual;";
        SqlServerSqlSplitter splitter = new SqlServerSqlSplitter(";");

        List<String> stmts = splitter.split(sql).stream().map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(2, stmts.size());
        Assert.assertEquals("SELECT 1 FROM dual;", stmts.get(0));
        Assert.assertEquals("\nSELECT 2 FROM dual;", stmts.get(1));
    }

    @Test
    public void split_CustomDelimiter() {
        String sql = "SELECT 1 FROM dual$\nSELECT 2 FROM dual$";
        SqlServerSqlSplitter splitter = new SqlServerSqlSplitter("$");

        List<String> stmts = splitter.split(sql).stream().map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(2, stmts.size());
        Assert.assertEquals("SELECT 1 FROM dual$", stmts.get(0));
        Assert.assertEquals("\nSELECT 2 FROM dual$", stmts.get(1));
    }

    @Test
    public void split_DefaultDelimiterWhenBlank() {
        String sql = "SELECT 1 FROM dual;";
        SqlServerSqlSplitter splitter = new SqlServerSqlSplitter("");

        List<String> stmts = splitter.split(sql).stream().map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(1, stmts.size());
        Assert.assertEquals("SELECT 1 FROM dual;", stmts.get(0));
    }

    @Test
    public void split_GoStatement() {
        String sql = "SELECT 1 FROM dual\nGO\nSELECT 2 FROM dual;";
        SqlServerSqlSplitter splitter = new SqlServerSqlSplitter(";");

        List<String> stmts = splitter.split(sql).stream().map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(2, stmts.size());
        Assert.assertEquals("SELECT 1 FROM dual\n", stmts.get(0));
        Assert.assertEquals("\nSELECT 2 FROM dual;", stmts.get(1));
    }

    @Test
    public void split_GoStatementWithWhitespace() {
        String sql = "SELECT 1 FROM dual\n  GO  \nSELECT 2 FROM dual;";
        SqlServerSqlSplitter splitter = new SqlServerSqlSplitter(";");

        List<String> stmts = splitter.split(sql).stream().map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(2, stmts.size());
        Assert.assertEquals("SELECT 1 FROM dual\n", stmts.get(0));
        Assert.assertEquals("\nSELECT 2 FROM dual;", stmts.get(1));
    }

    @Test
    public void split_GoStatementWithNumber() {
        String sql = "SELECT 1 FROM dual\nGO 5\nSELECT 2 FROM dual;";
        SqlServerSqlSplitter splitter = new SqlServerSqlSplitter(";");

        List<String> stmts = splitter.split(sql).stream().map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(2, stmts.size());
        Assert.assertEquals("SELECT 1 FROM dual\n", stmts.get(0));
        Assert.assertEquals("\nSELECT 2 FROM dual;", stmts.get(1));
    }

    @Test
    public void split_GoStatementWithComment() {
        String sql = "SELECT 1 FROM dual\nGO -- comment\nSELECT 2 FROM dual;";
        SqlServerSqlSplitter splitter = new SqlServerSqlSplitter(";");

        List<String> stmts = splitter.split(sql).stream().map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(2, stmts.size());
        Assert.assertEquals("SELECT 1 FROM dual\n", stmts.get(0));
        Assert.assertEquals("\nSELECT 2 FROM dual;", stmts.get(1));
    }

    @Test
    public void split_GoStatementWithBlockComment() {
        String sql = "SELECT 1 FROM dual\nGO /* comment */\nSELECT 2 FROM dual;";
        SqlServerSqlSplitter splitter = new SqlServerSqlSplitter(";");

        List<String> stmts = splitter.split(sql).stream().map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(2, stmts.size());
        Assert.assertEquals("SELECT 1 FROM dual\n", stmts.get(0));
        Assert.assertEquals("\nSELECT 2 FROM dual;", stmts.get(1));
    }

    @Test
    public void split_GoStatementCaseInsensitive() {
        String sql = "SELECT 1 FROM dual\ngo\nSELECT 2 FROM dual;";
        SqlServerSqlSplitter splitter = new SqlServerSqlSplitter(";");

        List<String> stmts = splitter.split(sql).stream().map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(2, stmts.size());
        Assert.assertEquals("SELECT 1 FROM dual\n", stmts.get(0));
        Assert.assertEquals("\nSELECT 2 FROM dual;", stmts.get(1));
    }

    @Test
    public void split_GoStatementNotAtLineStart_Ignored() {
        String sql = "SELECT GO FROM dual;";
        SqlServerSqlSplitter splitter = new SqlServerSqlSplitter(";");

        List<String> stmts = splitter.split(sql).stream().map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(1, stmts.size());
        Assert.assertEquals("SELECT GO FROM dual;", stmts.get(0));
    }

    @Test
    public void split_BeginEndBlock_NoSplitInside() {
        String sql = "BEGIN\n  SELECT 1;\n  SELECT 2;\nEND;";
        SqlServerSqlSplitter splitter = new SqlServerSqlSplitter(";");

        List<String> stmts = splitter.split(sql).stream().map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(1, stmts.size());
        Assert.assertEquals("BEGIN\n  SELECT 1;\n  SELECT 2;\nEND;", stmts.get(0));
    }

    @Test
    public void split_NestedBeginEndBlock_NoSplitInside() {
        String sql = "BEGIN\n  BEGIN\n    SELECT 1;\n  END;\n  SELECT 2;\nEND;";
        SqlServerSqlSplitter splitter = new SqlServerSqlSplitter(";");

        List<String> stmts = splitter.split(sql).stream().map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(1, stmts.size());
        Assert.assertEquals("BEGIN\n  BEGIN\n    SELECT 1;\n  END;\n  SELECT 2;\nEND;", stmts.get(0));
    }

    @Test
    public void split_BeginTran_NotTreatedAsBlock() {
        String sql = "BEGIN TRAN;\nSELECT 1;";
        SqlServerSqlSplitter splitter = new SqlServerSqlSplitter(";");

        List<String> stmts = splitter.split(sql).stream().map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(2, stmts.size());
        Assert.assertEquals("BEGIN TRAN;", stmts.get(0));
        Assert.assertEquals("\nSELECT 1;", stmts.get(1));
    }

    @Test
    public void split_BeginTransaction_NotTreatedAsBlock() {
        String sql = "BEGIN TRANSACTION;\nSELECT 1;";
        SqlServerSqlSplitter splitter = new SqlServerSqlSplitter(";");

        List<String> stmts = splitter.split(sql).stream().map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(2, stmts.size());
        Assert.assertEquals("BEGIN TRANSACTION;", stmts.get(0));
        Assert.assertEquals("\nSELECT 1;", stmts.get(1));
    }

    @Test
    public void split_BeginDistributed_NotTreatedAsBlock() {
        String sql = "BEGIN DISTRIBUTED;\nSELECT 1;";
        SqlServerSqlSplitter splitter = new SqlServerSqlSplitter(";");

        List<String> stmts = splitter.split(sql).stream().map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(2, stmts.size());
        Assert.assertEquals("BEGIN DISTRIBUTED;", stmts.get(0));
        Assert.assertEquals("\nSELECT 1;", stmts.get(1));
    }

    @Test
    public void split_CaseEndBlock_NoSplitInside() {
        String sql = "CASE\n  WHEN 1 THEN SELECT 1;\n  WHEN 2 THEN SELECT 2;\nEND;";
        SqlServerSqlSplitter splitter = new SqlServerSqlSplitter(";");

        List<String> stmts = splitter.split(sql).stream().map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(1, stmts.size());
        Assert.assertEquals("CASE\n  WHEN 1 THEN SELECT 1;\n  WHEN 2 THEN SELECT 2;\nEND;", stmts.get(0));
    }

    @Test
    public void split_NestedCaseEndBlock_NoSplitInside() {
        String sql = "CASE\n  WHEN 1 THEN CASE WHEN 2 THEN SELECT 1; END;\nEND;";
        SqlServerSqlSplitter splitter = new SqlServerSqlSplitter(";");

        List<String> stmts = splitter.split(sql).stream().map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(1, stmts.size());
        Assert.assertEquals("CASE\n  WHEN 1 THEN CASE WHEN 2 THEN SELECT 1; END;\nEND;", stmts.get(0));
    }

    @Test
    public void split_MixedBeginCaseEnd_NoSplitInside() {
        String sql = "BEGIN\n  CASE\n    WHEN 1 THEN SELECT 1;\n  END;\nEND;";
        SqlServerSqlSplitter splitter = new SqlServerSqlSplitter(";");

        List<String> stmts = splitter.split(sql).stream().map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(1, stmts.size());
        Assert.assertEquals("BEGIN\n  CASE\n    WHEN 1 THEN SELECT 1;\n  END;\nEND;", stmts.get(0));
    }

    @Test
    public void split_LineComment_Ignored() {
        String sql = "SELECT 1 FROM dual; -- comment\nSELECT 2 FROM dual;";
        SqlServerSqlSplitter splitter = new SqlServerSqlSplitter(";");

        List<String> stmts = splitter.split(sql).stream().map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(2, stmts.size());
        Assert.assertEquals("SELECT 1 FROM dual; -- comment\n", stmts.get(0));
        Assert.assertEquals("SELECT 2 FROM dual;", stmts.get(1));
    }

    @Test
    public void split_BlockComment_Ignored() {
        String sql = "SELECT 1 FROM dual; /* comment */\nSELECT 2 FROM dual;";
        SqlServerSqlSplitter splitter = new SqlServerSqlSplitter(";");

        List<String> stmts = splitter.split(sql).stream().map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(2, stmts.size());
        Assert.assertEquals("SELECT 1 FROM dual; /* comment */\n", stmts.get(0));
        Assert.assertEquals("SELECT 2 FROM dual;", stmts.get(1));
    }

    @Test
    public void split_MultilineBlockComment_Ignored() {
        String sql = "SELECT 1 FROM dual; /* comment\n   more comment */\nSELECT 2 FROM dual;";
        SqlServerSqlSplitter splitter = new SqlServerSqlSplitter(";");

        List<String> stmts = splitter.split(sql).stream().map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(2, stmts.size());
        Assert.assertEquals("SELECT 1 FROM dual; /* comment\n   more comment */\n", stmts.get(0));
        Assert.assertEquals("SELECT 2 FROM dual;", stmts.get(1));
    }

    @Test
    public void split_SingleQuoteString_NoSplitInside() {
        String sql = "SELECT 'hello;world' FROM dual;";
        SqlServerSqlSplitter splitter = new SqlServerSqlSplitter(";");

        List<String> stmts = splitter.split(sql).stream().map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(1, stmts.size());
        Assert.assertEquals("SELECT 'hello;world' FROM dual;", stmts.get(0));
    }

    @Test
    public void split_EscapedSingleQuote_Handled() {
        String sql = "SELECT 'hello''world' FROM dual;";
        SqlServerSqlSplitter splitter = new SqlServerSqlSplitter(";");

        List<String> stmts = splitter.split(sql).stream().map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(1, stmts.size());
        Assert.assertEquals("SELECT 'hello''world' FROM dual;", stmts.get(0));
    }

    @Test
    public void split_DoubleQuoteString_NoSplitInside() {
        String sql = "SELECT \"hello;world\" FROM dual;";
        SqlServerSqlSplitter splitter = new SqlServerSqlSplitter(";");

        List<String> stmts = splitter.split(sql).stream().map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(1, stmts.size());
        Assert.assertEquals("SELECT \"hello;world\" FROM dual;", stmts.get(0));
    }

    @Test
    public void split_BracketIdentifier_NoSplitInside() {
        String sql = "SELECT [column;name] FROM dual;";
        SqlServerSqlSplitter splitter = new SqlServerSqlSplitter(";");

        List<String> stmts = splitter.split(sql).stream().map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(1, stmts.size());
        Assert.assertEquals("SELECT [column;name] FROM dual;", stmts.get(0));
    }

    @Test
    public void split_EscapedBracketIdentifier_Handled() {
        String sql = "SELECT [column]]name] FROM dual;";
        SqlServerSqlSplitter splitter = new SqlServerSqlSplitter(";");

        List<String> stmts = splitter.split(sql).stream().map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(1, stmts.size());
        Assert.assertEquals("SELECT [column]]name] FROM dual;", stmts.get(0));
    }

    @Test
    public void split_ComplexScenario() {
        String sql = "SELECT 1 FROM dual;\n" +
                "BEGIN\n" +
                "  SELECT 'test;value' FROM table;\n" +
                "  CASE WHEN 1 THEN SELECT 2; END;\n" +
                "END;\n" +
                "GO\n" +
                "SELECT 3 FROM dual;";
        SqlServerSqlSplitter splitter = new SqlServerSqlSplitter(";");

        List<String> stmts = splitter.split(sql).stream().map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(2, stmts.size());
        Assert.assertTrue(stmts.get(0).contains("SELECT 1"));
        Assert.assertTrue(stmts.get(0).contains("BEGIN"));
        Assert.assertTrue(stmts.get(0).contains("END"));
        Assert.assertTrue(stmts.get(1).contains("SELECT 3"));
    }

    @Test
    public void split_GoInsideBeginEnd_NotTreatedAsSeparator() {
        String sql = "BEGIN\n  SELECT GO FROM table;\nEND;";
        SqlServerSqlSplitter splitter = new SqlServerSqlSplitter(";");

        List<String> stmts = splitter.split(sql).stream().map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(1, stmts.size());
        Assert.assertEquals("BEGIN\n  SELECT GO FROM table;\nEND;", stmts.get(0));
    }

    @Test
    public void iterator_Basic() {
        String sql = "SELECT 1 FROM dual;\nSELECT 2 FROM dual;";
        SqlStatementIterator iterator = SqlServerSqlSplitter.iterator(
                new ByteArrayInputStream(sql.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8, ";");

        List<String> stmts = new ArrayList<>();
        while (iterator.hasNext()) {
            stmts.add(iterator.next().getStr());
        }

        Assert.assertEquals(2, stmts.size());
        Assert.assertEquals("SELECT 1 FROM dual;", stmts.get(0));
        Assert.assertEquals("\nSELECT 2 FROM dual;", stmts.get(1));
    }

    @Test
    public void iterator_WithGo() {
        String sql = "SELECT 1 FROM dual\nGO\nSELECT 2 FROM dual;";
        SqlStatementIterator iterator = SqlServerSqlSplitter.iterator(
                new ByteArrayInputStream(sql.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8, ";");

        List<String> stmts = new ArrayList<>();
        while (iterator.hasNext()) {
            stmts.add(iterator.next().getStr());
        }

        Assert.assertEquals(2, stmts.size());
        Assert.assertEquals("SELECT 1 FROM dual\n", stmts.get(0));
        Assert.assertEquals("\nSELECT 2 FROM dual;", stmts.get(1));
    }

    @Test
    public void iterator_IteratedBytes() {
        String sql = "SELECT 1 FROM dual;\nSELECT 2 FROM dual;";
        SqlStatementIterator iterator = SqlServerSqlSplitter.iterator(
                new ByteArrayInputStream(sql.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8, ";");

        long bytes = 0;
        while (iterator.hasNext()) {
            OffsetString stmt = iterator.next();
            bytes = iterator.iteratedBytes();
        }

        Assert.assertEquals(sql.length(), bytes);
    }

    @Test
    public void split_OffsetCorrect() {
        String sql = "SELECT 1;\nSELECT 2;";
        SqlServerSqlSplitter splitter = new SqlServerSqlSplitter(";");

        List<OffsetString> stmts = splitter.split(sql);

        Assert.assertEquals(2, stmts.size());
        Assert.assertEquals(0, stmts.get(0).getOffset());
        Assert.assertTrue(stmts.get(1).getOffset() > 0);
    }

    @Test
    public void split_NoTrailingDelimiter() {
        String sql = "SELECT 1 FROM dual";
        SqlServerSqlSplitter splitter = new SqlServerSqlSplitter(";");

        List<String> stmts = splitter.split(sql).stream().map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(1, stmts.size());
        Assert.assertEquals("SELECT 1 FROM dual", stmts.get(0));
    }

    @Test
    public void split_MultipleGoStatements() {
        String sql = "SELECT 1\nGO\nSELECT 2\nGO\nSELECT 3;";
        SqlServerSqlSplitter splitter = new SqlServerSqlSplitter(";");

        List<String> stmts = splitter.split(sql).stream().map(OffsetString::getStr)
                .collect(Collectors.toList());

        Assert.assertEquals(3, stmts.size());
        Assert.assertTrue(stmts.get(0).contains("SELECT 1"));
        Assert.assertTrue(stmts.get(1).contains("SELECT 2"));
        Assert.assertTrue(stmts.get(2).contains("SELECT 3"));
    }
}
