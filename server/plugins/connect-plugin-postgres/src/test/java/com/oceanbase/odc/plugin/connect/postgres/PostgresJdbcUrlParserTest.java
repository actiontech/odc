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
package com.oceanbase.odc.plugin.connect.postgres;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.oceanbase.odc.plugin.connect.api.HostAddress;
import com.oceanbase.odc.plugin.connect.api.JdbcUrlParser;

/**
 * {@link PostgresJdbcUrlParser} 单元测试
 *
 * @author ODC Team
 * @date 2025-03
 * @since ODC_release_4.3.5
 */
public class PostgresJdbcUrlParserTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    // ==================== 构造函数异常测试 ====================

    /**
     * 测试无效的 JDBC URL 前缀
     */
    @Test
    public void createParser_invalidPrefix_exceptionThrown() throws SQLException {
        String jdbcUrl = "jdbc:mysql://localhost:3306/testdb";

        thrown.expect(SQLException.class);
        thrown.expectMessage("must start with 'jdbc:postgresql://'");
        new PostgresJdbcUrlParser(jdbcUrl);
    }

    /**
     * 测试缺少数据库名的 URL
     */
    @Test
    public void createParser_missingDatabase_exceptionThrown() throws SQLException {
        String jdbcUrl = "jdbc:postgresql://localhost:5432";

        thrown.expect(SQLException.class);
        thrown.expectMessage("missing database name");
        new PostgresJdbcUrlParser(jdbcUrl);
    }

    /**
     * 测试空的 URL
     */
    @Test(expected = NullPointerException.class)
    public void createParser_nullUrl_exceptionThrown() throws SQLException {
        new PostgresJdbcUrlParser(null);
    }

    // ==================== 标准 URL 测试 ====================

    /**
     * 测试标准 URL 解析
     */
    @Test
    public void parse_standardUrl_success() throws SQLException {
        String jdbcUrl = "jdbc:postgresql://192.168.1.100:5432/mydb";
        JdbcUrlParser parser = new PostgresJdbcUrlParser(jdbcUrl);

        List<HostAddress> addresses = parser.getHostAddresses();
        Assert.assertEquals(1, addresses.size());
        Assert.assertEquals("192.168.1.100", addresses.get(0).getHost());
        Assert.assertEquals(Integer.valueOf(5432), addresses.get(0).getPort());
    }

    /**
     * 测试带 currentSchema 参数的 URL
     */
    @Test
    public void parse_urlWithCurrentSchema_schemaExtracted() throws SQLException {
        String jdbcUrl = "jdbc:postgresql://localhost:5432/mydb?currentSchema=public";
        JdbcUrlParser parser = new PostgresJdbcUrlParser(jdbcUrl);

        Assert.assertEquals("public", parser.getSchema());
    }

    // ==================== 默认端口测试 ====================

    /**
     * 测试无端口时使用默认端口 5432
     */
    @Test
    public void parse_urlWithoutPort_defaultPortUsed() throws SQLException {
        String jdbcUrl = "jdbc:postgresql://localhost/mydb";
        JdbcUrlParser parser = new PostgresJdbcUrlParser(jdbcUrl);

        List<HostAddress> addresses = parser.getHostAddresses();
        Assert.assertEquals(1, addresses.size());
        Assert.assertEquals("localhost", addresses.get(0).getHost());
        Assert.assertEquals(Integer.valueOf(5432), addresses.get(0).getPort());
    }

    // ==================== 参数解析测试 ====================

    /**
     * 测试无参数的 URL
     */
    @Test
    public void parse_urlWithoutParameters_emptyParams() throws SQLException {
        String jdbcUrl = "jdbc:postgresql://localhost:5432/mydb";
        JdbcUrlParser parser = new PostgresJdbcUrlParser(jdbcUrl);

        Map<String, Object> params = parser.getParameters();
        Assert.assertTrue(params.isEmpty());
    }

    /**
     * 测试单个参数
     */
    @Test
    public void parse_urlWithSingleParameter_paramExtracted() throws SQLException {
        String jdbcUrl = "jdbc:postgresql://localhost:5432/mydb?ssl=true";
        JdbcUrlParser parser = new PostgresJdbcUrlParser(jdbcUrl);

        Map<String, Object> params = parser.getParameters();
        Assert.assertEquals(1, params.size());
        Assert.assertEquals("true", params.get("ssl"));
    }

    /**
     * 测试多个参数
     */
    @Test
    public void parse_urlWithMultipleParameters_allParamsExtracted() throws SQLException {
        String jdbcUrl = "jdbc:postgresql://localhost:5432/mydb?ssl=true&connectTimeout=10&ApplicationName=ODC";
        JdbcUrlParser parser = new PostgresJdbcUrlParser(jdbcUrl);

        Map<String, Object> params = parser.getParameters();
        Assert.assertEquals(3, params.size());
        Assert.assertEquals("true", params.get("ssl"));
        Assert.assertEquals("10", params.get("connectTimeout"));
        Assert.assertEquals("ODC", params.get("ApplicationName"));
    }

    /**
     * 测试参数中包含 currentSchema
     */
    @Test
    public void parse_urlWithCurrentSchemaInParams_schemaAndParamsBothCorrect() throws SQLException {
        String jdbcUrl = "jdbc:postgresql://localhost:5432/mydb?currentSchema=myschema&ssl=true";
        JdbcUrlParser parser = new PostgresJdbcUrlParser(jdbcUrl);

        Assert.assertEquals("myschema", parser.getSchema());
        Map<String, Object> params = parser.getParameters();
        Assert.assertEquals(2, params.size());
        Assert.assertEquals("myschema", params.get("currentSchema"));
        Assert.assertEquals("true", params.get("ssl"));
    }

    // ==================== 边界情况测试 ====================

    /**
     * 测试 localhost 主机名
     */
    @Test
    public void parse_localhostHost_parsedCorrectly() throws SQLException {
        String jdbcUrl = "jdbc:postgresql://localhost:5433/testdb";
        JdbcUrlParser parser = new PostgresJdbcUrlParser(jdbcUrl);

        List<HostAddress> addresses = parser.getHostAddresses();
        Assert.assertEquals("localhost", addresses.get(0).getHost());
        Assert.assertEquals(Integer.valueOf(5433), addresses.get(0).getPort());
    }

    /**
     * 测试无 currentSchema 时 schema 为 null
     */
    @Test
    public void parse_urlWithoutCurrentSchema_schemaIsNull() throws SQLException {
        String jdbcUrl = "jdbc:postgresql://localhost:5432/mydb?ssl=true";
        JdbcUrlParser parser = new PostgresJdbcUrlParser(jdbcUrl);

        Assert.assertNull(parser.getSchema());
    }

    /**
     * 测试带空参数（问号后无内容）
     */
    @Test
    public void parse_urlWithEmptyParams_emptyParamsMap() throws SQLException {
        String jdbcUrl = "jdbc:postgresql://localhost:5432/mydb?";
        JdbcUrlParser parser = new PostgresJdbcUrlParser(jdbcUrl);

        Map<String, Object> params = parser.getParameters();
        Assert.assertTrue(params.isEmpty());
    }

    /**
     * 测试参数值包含特殊字符（如编码的空格）
     */
    @Test
    public void parse_urlWithSpecialChars_paramsExtractedCorrectly() throws SQLException {
        String jdbcUrl = "jdbc:postgresql://localhost:5432/mydb?ApplicationName=My%20App";
        JdbcUrlParser parser = new PostgresJdbcUrlParser(jdbcUrl);

        Map<String, Object> params = parser.getParameters();
        Assert.assertEquals("My%20App", params.get("ApplicationName"));
    }

    /**
     * 测试复杂场景：多参数 + currentSchema + 特殊端口
     */
    @Test
    public void parse_complexUrl_allParsedCorrectly() throws SQLException {
        String jdbcUrl =
                "jdbc:postgresql://db.example.com:6432/production?currentSchema=app&ssl=true&connectTimeout=30";
        JdbcUrlParser parser = new PostgresJdbcUrlParser(jdbcUrl);

        // 验证主机
        List<HostAddress> addresses = parser.getHostAddresses();
        Assert.assertEquals(1, addresses.size());
        Assert.assertEquals("db.example.com", addresses.get(0).getHost());
        Assert.assertEquals(Integer.valueOf(6432), addresses.get(0).getPort());

        // 验证 schema
        Assert.assertEquals("app", parser.getSchema());

        // 验证参数
        Map<String, Object> params = parser.getParameters();
        Assert.assertEquals(3, params.size());
        Assert.assertEquals("app", params.get("currentSchema"));
        Assert.assertEquals("true", params.get("ssl"));
        Assert.assertEquals("30", params.get("connectTimeout"));
    }
}
