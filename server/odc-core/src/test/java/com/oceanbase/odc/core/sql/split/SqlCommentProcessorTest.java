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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.collections4.IteratorUtils;
import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.odc.common.util.YamlUtils;
import com.oceanbase.odc.core.shared.constant.DialectType;

public class SqlCommentProcessorTest {

    @Test
    public void testOffsetString_Mysql() throws IOException {
        String line = getSqlFromFile("sql/split/comment-processor-mysql-test.sql");
        SqlCommentProcessor processor = new SqlCommentProcessor(DialectType.OB_MYSQL, false, false, false);
        StringBuffer builder = new StringBuffer();
        List<OffsetString> actual = processor.split(builder, line);
        List<OffsetString> expected = getSqls("sql/split/comment-processor-mysql-verify.yml");
        Assert.assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            Assert.assertEquals(expected.get(i), actual.get(i));
        }
    }

    @Test
    public void testIterator_MysqlMode() throws Exception {
        List<OffsetString> actual;
        try (InputStream in =
                this.getClass().getClassLoader().getResourceAsStream("sql/split/comment-processor-mysql-test.sql")) {
            SqlStatementIterator iterator = SqlCommentProcessor.iterator(in, StandardCharsets.UTF_8,
                    new SqlCommentProcessor(DialectType.OB_MYSQL, false, false, false));
            actual = IteratorUtils.toList(iterator);
        }
        List<OffsetString> expected = getSqls("sql/split/comment-processor-mysql-verify.yml");
        Assert.assertEquals(expected.size(), actual.size());
        for (int i = 0; i < actual.size(); i++) {
            Assert.assertEquals(expected.get(i), actual.get(i));
        }
    }

    @Test
    public void testOffsetString_Oracle() throws IOException {
        String line = getSqlFromFile("sql/split/comment-processor-oracle-test.sql");
        SqlCommentProcessor processor = new SqlCommentProcessor(DialectType.OB_ORACLE, false, false, false);
        StringBuffer builder = new StringBuffer();
        List<OffsetString> actual = processor.split(builder, line);
        List<OffsetString> expected = getSqls("sql/split/comment-processor-oracle-verify.yml");
        Assert.assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            Assert.assertEquals(expected.get(i), actual.get(i));
        }
    }

    @Test
    public void testIterator_OracleMode() throws Exception {
        List<OffsetString> actual;
        try (InputStream in =
                this.getClass().getClassLoader().getResourceAsStream("sql/split/comment-processor-oracle-test.sql")) {
            SqlStatementIterator iterator = SqlCommentProcessor.iterator(in, StandardCharsets.UTF_8,
                    new SqlCommentProcessor(DialectType.OB_ORACLE, false, false, false));
            actual = IteratorUtils.toList(iterator);
        }
        List<OffsetString> expected = getSqls("sql/split/comment-processor-oracle-verify.yml");
        Assert.assertEquals(expected.size(), actual.size());
        for (int i = 0; i < actual.size(); i++) {
            Assert.assertEquals(expected.get(i), actual.get(i));
        }
    }

    @Test
    public void split_splitBySlashAndContainsMultiComment_splitSucceed() {
        List<String> sqls = new ArrayList<>();
        sqls.add("CREATE\n"
                + "OR REPLACE PACKAGE BODY BASE_ACCESS_CONTROL_PKG IS\n"
                + "/* $Header:  BASE_ACCESS_CONTROL_PKG.bdy 3.1.0 2016/10/1 18:48:48 Midea ship $ */\n"
                + "BEGIN\n"
                + "    DBMS_OUTPUT.PUT_LINE('a');\n"
                + "END;\n");
        sqls.add("select 1+3 from dual");
        SqlCommentProcessor processor = new SqlCommentProcessor(DialectType.OB_ORACLE, true, true);
        processor.setDelimiter("/");
        StringBuffer buffer = new StringBuffer();
        List<String> actual =
                processor.split(buffer, String.join("/", sqls) + "/").stream().map(OffsetString::getStr).collect(
                        Collectors.toList());
        Assert.assertEquals(sqls, actual);
    }

    @Test
    public void split_splitBySlashAndContainsMultiHint_splitSucceed() {
        List<String> sqls = new ArrayList<>();
        sqls.add("create or replace package body afp.pm_fd_notify_dr_change_pub is \n"
                + "g_user_id number := base_api_pkg.get_user_id;\n"
                + "g_package_name varchar2(30) := 'PM_FD_NOTIFY_DR_CHANGE_PUB';\n"
                + "procedure delete_row(\n"
                + "  p_batch_id in number\n"
                + ") is cursor cur_delete is\n"
                + "select\n"
                + "  distinct dnl.notify_header_id,\n"
                + "  dnl.notify_line_id\n"
                + "from\n"
                + "  PM_FD_NOTIFY_DR_CHANGE_HIS h,\n"
                + "  pm_fd_del_notify_lines dnl\n"
                + "where\n"
                + "  h.batch_id = p_batch_id\n"
                + "  and h.notify_header_id = dnl.notify_header_id\n"
                + "  and not exists (\n"
                + "    select\n"
                + "      /*+driving_site(dnl2)*/\n"
                + "      'x'\n"
                + "    from\n"
                + "      pm_fd_del_notify_lines_syn dnl2 --erp 表\n"
                + "    where\n"
                + "      dnl2.notify_line_id = dnl.notify_line_id\n"
                + "  )\n"
                + "  and not exists (\n"
                + "    select\n"
                + "      'x'\n"
                + "    from\n"
                + "      PM_FD_NOTIFY_DR_CHANGE_HIS h2\n"
                + "    where\n"
                + "      h2.notify_line_id = dnl.notify_line_id\n"
                + "      and h2.notify_header_id = dnl.notify_header_id\n"
                + "      and h2.dml_type = 'DELETE' --删除类型\n"
                + "  );\n"
                + "v_api_name varchar2(30) := 'insert_row';\n"
                + "end pm_fd_notify_dr_change_pub;\n");
        sqls.add("select 1+3 from dual");
        SqlCommentProcessor processor = new SqlCommentProcessor(DialectType.OB_ORACLE, true, true);
        processor.setDelimiter("/");
        StringBuffer buffer = new StringBuffer();
        List<String> actual =
                processor.split(buffer, String.join("/", sqls) + "/").stream().map(OffsetString::getStr).collect(
                        Collectors.toList());
        Assert.assertEquals(sqls, actual);
    }

    @Test
    public void splitGaussDBSql_ReturnsList() {
        // P4 regression: SqlCommentProcessor.split must support PG family (POSTGRESQL + GAUSSDB),
        // otherwise SQL Console on GaussDB / openGauss is blocked with "dialect type is illegal".
        // See episodic task_005_006_odc_sqlcommentprocessor_p4_pg_family.
        SqlCommentProcessor processor = new SqlCommentProcessor(DialectType.GAUSSDB, false, false);
        StringBuffer buffer = new StringBuffer();
        String script = "SELECT version();\nSELECT 1;";
        List<OffsetString> sqls = processor.split(buffer, script);
        Assert.assertEquals(2, sqls.size());
        Assert.assertEquals("SELECT version()", sqls.get(0).getStr());
        Assert.assertEquals("SELECT 1", sqls.get(1).getStr());
    }

    @Test
    public void splitGBase8aSql_ReturnsList() {
        // S3 / AC-3: GBASE_8A must reuse MySQL-family split (';';type is illegal" / Oracle PL path).
        SqlCommentProcessor processor = new SqlCommentProcessor(DialectType.GBASE_8A, false, false);
        StringBuffer buffer = new StringBuffer();
        String script = "SELECT 1;\nSELECT 2;";
        List<OffsetString> sqls = processor.split(buffer, script);
        Assert.assertEquals(2, sqls.size());
        Assert.assertEquals("SELECT 1", sqls.get(0).getStr());
        Assert.assertEquals("SELECT 2", sqls.get(1).getStr());
    }

    @Test
    public void splitPostgreSqlSql_ReturnsList() {
        // P4 regression: same as splitGaussDBSql_ReturnsList but for the POSTGRESQL dialect.
        SqlCommentProcessor processor = new SqlCommentProcessor(DialectType.POSTGRESQL, false, false);
        StringBuffer buffer = new StringBuffer();
        String script = "SELECT NOW();\nSELECT * FROM pg_stat_activity LIMIT 1;";
        List<OffsetString> sqls = processor.split(buffer, script);
        Assert.assertEquals(2, sqls.size());
        Assert.assertEquals("SELECT NOW()", sqls.get(0).getStr());
        Assert.assertEquals("SELECT * FROM pg_stat_activity LIMIT 1", sqls.get(1).getStr());
    }

    @Test
    public void splitOpenGaussSql_SingleStatement_ReturnsOneItem() {
        // openGauss reports dialect=GAUSSDB at the ODC layer; verify single-statement script
        // still splits cleanly without DELIMITER syntax.
        SqlCommentProcessor processor = new SqlCommentProcessor(DialectType.GAUSSDB, false, false);
        StringBuffer buffer = new StringBuffer();
        String script = "INSERT INTO test_odc_opengauss.t_complex (jsonb_col) VALUES ('{\"x\":1}'::jsonb);";
        List<OffsetString> sqls = processor.split(buffer, script);
        Assert.assertEquals(1, sqls.size());
        Assert.assertEquals(
                "INSERT INTO test_odc_opengauss.t_complex (jsonb_col) VALUES ('{\"x\":1}'::jsonb)",
                sqls.get(0).getStr());
    }

    private String getSqlFromFile(String fileName) throws IOException {
        InputStream input = this.getClass().getClassLoader().getResourceAsStream(fileName);
        assert input != null;
        BufferedReader reader = new BufferedReader(new InputStreamReader((input)));
        StringWriter writer = new StringWriter();
        char[] buffer = new char[1024];
        int len = reader.read(buffer);
        while (len != -1) {
            writer.write(buffer, 0, len);
            len = reader.read(buffer, 0, len);
        }
        reader.close();
        writer.close();
        return writer.getBuffer().toString();
    }

    private List<OffsetString> getSqls(String fileName) {
        return YamlUtils.fromYamlList(fileName, OffsetString.class);
    }

    /**
     * B-23: DB2 SQL splitting should match the MySQL path (line comments {@code --} and block comments
     * share semantics with MySQL, and {@code ;} acts as the statement separator). Mirrors
     * {@code addLineMysql} dispatch.
     */
    @Test
    public void split_db2Dialect_reusesMysqlPath() {
        SqlCommentProcessor processor = new SqlCommentProcessor(DialectType.DB2, false, false, false);
        StringBuffer buffer = new StringBuffer();
        List<OffsetString> actual = processor.split(buffer,
                "-- leading line comment\nSELECT 1 FROM SYSIBM.SYSDUMMY1;\nSELECT 2 FROM SYSIBM.SYSDUMMY1;\n");
        Assert.assertEquals(2, actual.size());
        Assert.assertEquals("SELECT 1 FROM SYSIBM.SYSDUMMY1", actual.get(0).getStr().trim()
                .replaceAll(";\\s*$", "").trim());
        Assert.assertEquals("SELECT 2 FROM SYSIBM.SYSDUMMY1", actual.get(1).getStr().trim()
                .replaceAll(";\\s*$", "").trim());
    }

    @Test
    public void split_db2DialectWithBlockComment_reusesMysqlPath() {
        SqlCommentProcessor processor = new SqlCommentProcessor(DialectType.DB2, false, false, false);
        StringBuffer buffer = new StringBuffer();
        List<OffsetString> actual = processor.split(buffer,
                "/* header */\nSELECT 'a' FROM SYSIBM.SYSDUMMY1;\n");
        Assert.assertEquals(1, actual.size());
        Assert.assertEquals("SELECT 'a' FROM SYSIBM.SYSDUMMY1", actual.get(0).getStr().trim()
                .replaceAll(";\\s*$", "").trim());
    }

}
