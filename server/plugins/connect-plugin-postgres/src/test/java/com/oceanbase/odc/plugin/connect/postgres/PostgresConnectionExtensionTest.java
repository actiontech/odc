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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oceanbase.odc.core.shared.constant.OdcConstants;
import com.oceanbase.odc.plugin.connect.api.HostAddress;
import com.oceanbase.odc.plugin.connect.api.JdbcUrlParser;
import com.oceanbase.odc.plugin.connect.model.JdbcUrlProperty;

/**
 * {@link PostgresConnectionExtension} 单元测试
 *
 * <p>
 * 测试覆盖：
 * <ul>
 * <li>generateJdbcUrl() 方法的各种参数组合</li>
 * <li>getConnectionInfo() 方法的 URL 解析正确性</li>
 * <li>appendDefaultJdbcUrlParameters() 方法的默认参数添加</li>
 * <li>getDriverClassName() 方法返回正确的驱动类名</li>
 * </ul>
 *
 * @author ODC Team
 * @date 2025-03
 * @since ODC_release_4.3.5
 */
public class PostgresConnectionExtensionTest {

    private PostgresConnectionExtension extension;

    @Before
    public void setUp() {
        extension = new PostgresConnectionExtension();
    }

    // ==================== generateJdbcUrl 测试 ====================

    /**
     * 测试用例：生成基本 JDBC URL（仅必填参数）
     */
    @Test
    public void testGenerateJdbcUrl_Basic() {
        JdbcUrlProperty property = new JdbcUrlProperty("localhost", 5432, null, null, null, null, "postgres");
        String jdbcUrl = extension.generateJdbcUrl(property);

        Assert.assertEquals("jdbc:postgresql://localhost:5432/postgres?ApplicationName=ODC", jdbcUrl);
    }

    /**
     * 测试用例：生成带 schema 的 JDBC URL
     */
    @Test
    public void testGenerateJdbcUrl_WithSchema() {
        JdbcUrlProperty property = new JdbcUrlProperty("localhost", 5432, "public", null, null, null, "postgres");
        String jdbcUrl = extension.generateJdbcUrl(property);

        Assert.assertTrue(jdbcUrl.contains("currentSchema=public"));
        Assert.assertTrue(jdbcUrl.contains("ApplicationName=ODC"));
    }

    /**
     * 测试用例：生成带自定义 JDBC 参数的 URL
     */
    @Test
    public void testGenerateJdbcUrl_WithJdbcParameters() {
        Map<String, String> jdbcParams = new HashMap<>();
        jdbcParams.put("ssl", "true");
        jdbcParams.put("sslmode", "require");

        JdbcUrlProperty property = new JdbcUrlProperty("localhost", 5432, "public", jdbcParams, null, null, "postgres");
        String jdbcUrl = extension.generateJdbcUrl(property);

        Assert.assertTrue(jdbcUrl.contains("ssl=true"));
        Assert.assertTrue(jdbcUrl.contains("sslmode=require"));
        Assert.assertTrue(jdbcUrl.contains("currentSchema=public"));
        Assert.assertTrue(jdbcUrl.contains("ApplicationName=ODC"));
    }

    /**
     * 测试用例：用户自定义 ApplicationName 应覆盖默认值
     */
    @Test
    public void testGenerateJdbcUrl_UserDefinedApplicationName() {
        Map<String, String> jdbcParams = new HashMap<>();
        jdbcParams.put("ApplicationName", "MyApp");

        JdbcUrlProperty property = new JdbcUrlProperty("localhost", 5432, null, jdbcParams, null, null, "postgres");
        String jdbcUrl = extension.generateJdbcUrl(property);

        Assert.assertTrue(jdbcUrl.contains("ApplicationName=MyApp"));
        Assert.assertFalse(jdbcUrl.contains("ApplicationName=ODC"));
    }

    /**
     * 测试用例：非标准端口
     */
    @Test
    public void testGenerateJdbcUrl_NonStandardPort() {
        JdbcUrlProperty property = new JdbcUrlProperty("192.168.1.100", 15432, "myschema", null, null, null, "mydb");
        String jdbcUrl = extension.generateJdbcUrl(property);

        Assert.assertTrue(jdbcUrl.contains("192.168.1.100:15432"));
        Assert.assertTrue(jdbcUrl.contains("/mydb"));
        Assert.assertTrue(jdbcUrl.contains("currentSchema=myschema"));
    }

    /**
     * 测试用例：host 参数为空应抛出异常
     */
    @Test(expected = IllegalArgumentException.class)
    public void testGenerateJdbcUrl_NullHost() {
        JdbcUrlProperty property = new JdbcUrlProperty(null, 5432, null, null, null, null, "postgres");
        extension.generateJdbcUrl(property);
    }

    /**
     * 测试用例：port 参数为空应抛出异常
     */
    @Test(expected = NullPointerException.class)
    public void testGenerateJdbcUrl_NullPort() {
        JdbcUrlProperty property = new JdbcUrlProperty("localhost", null, null, null, null, null, "postgres");
        extension.generateJdbcUrl(property);
    }

    /**
     * 测试用例：catalogName 参数为空应抛出异常
     */
    @Test(expected = IllegalArgumentException.class)
    public void testGenerateJdbcUrl_NullCatalogName() {
        JdbcUrlProperty property = new JdbcUrlProperty("localhost", 5432, null, null, null, null, null);
        extension.generateJdbcUrl(property);
    }

    // ==================== getConnectionInfo 测试 ====================

    /**
     * 测试用例：解析标准 JDBC URL
     */
    @Test
    public void testGetConnectionInfo_StandardUrl() throws SQLException {
        String jdbcUrl = "jdbc:postgresql://localhost:5432/mydb?currentSchema=public&ssl=true";
        JdbcUrlParser parser = extension.getConnectionInfo(jdbcUrl, "testuser");

        List<HostAddress> addresses = parser.getHostAddresses();
        Assert.assertEquals(1, addresses.size());
        Assert.assertEquals("localhost", addresses.get(0).getHost());
        Assert.assertEquals(Integer.valueOf(5432), addresses.get(0).getPort());
        Assert.assertEquals("public", parser.getSchema());
        Assert.assertEquals("true", parser.getParameters().get("ssl"));
    }

    /**
     * 测试用例：解析不带端口的 URL（应使用默认端口）
     */
    @Test
    public void testGetConnectionInfo_WithoutPort() throws SQLException {
        String jdbcUrl = "jdbc:postgresql://db.example.com/testdb";
        JdbcUrlParser parser = extension.getConnectionInfo(jdbcUrl, null);

        List<HostAddress> addresses = parser.getHostAddresses();
        Assert.assertEquals("db.example.com", addresses.get(0).getHost());
        Assert.assertEquals(Integer.valueOf(5432), addresses.get(0).getPort());
    }

    /**
     * 测试用例：解析无 schema 参数的 URL
     */
    @Test
    public void testGetConnectionInfo_WithoutSchema() throws SQLException {
        String jdbcUrl = "jdbc:postgresql://localhost:5432/mydb";
        JdbcUrlParser parser = extension.getConnectionInfo(jdbcUrl, "testuser");

        Assert.assertNull(parser.getSchema());
    }

    /**
     * 测试用例：解析无效 URL 格式应抛出异常
     */
    @Test(expected = SQLException.class)
    public void testGetConnectionInfo_InvalidUrlFormat() throws SQLException {
        String jdbcUrl = "jdbc:mysql://localhost:3306/mydb";
        extension.getConnectionInfo(jdbcUrl, null);
    }

    /**
     * 测试用例：解析缺少数据库名的 URL 应抛出异常
     */
    @Test(expected = SQLException.class)
    public void testGetConnectionInfo_MissingDatabase() throws SQLException {
        String jdbcUrl = "jdbc:postgresql://localhost:5432/";
        extension.getConnectionInfo(jdbcUrl, null);
    }

    // ==================== getDriverClassName 测试 ====================

    /**
     * 测试用例：获取正确的驱动类名
     */
    @Test
    public void testGetDriverClassName() {
        String driverClassName = extension.getDriverClassName();
        Assert.assertEquals(OdcConstants.POSTGRES_DRIVER_CLASS_NAME, driverClassName);
    }

    // ==================== getConnectionInitializers 测试 ====================

    /**
     * 测试用例：PG 不需要初始化脚本
     */
    @Test
    public void testGetConnectionInitializers() {
        List<?> initializers = extension.getConnectionInitializers();
        Assert.assertTrue(initializers.isEmpty());
    }

    // ==================== 综合测试 ====================

    /**
     * 测试用例：生成 URL 并解析，数据一致性验证
     */
    @Test
    public void testGenerateAndParseUrl_Consistency() throws SQLException {
        // 给定参数
        String host = "pg.example.com";
        int port = 5432;
        String database = "appdb";
        String schema = "appschema";

        // 生成 URL
        JdbcUrlProperty property = new JdbcUrlProperty(host, port, schema, null, null, null, database);
        String generatedUrl = extension.generateJdbcUrl(property);

        // 解析 URL
        JdbcUrlParser parser = extension.getConnectionInfo(generatedUrl, "appuser");

        // 验证一致性
        List<HostAddress> addresses = parser.getHostAddresses();
        Assert.assertEquals(host, addresses.get(0).getHost());
        Assert.assertEquals(Integer.valueOf(port), addresses.get(0).getPort());
        Assert.assertEquals(schema, parser.getSchema());
        // 验证默认参数已添加
        Assert.assertEquals("ODC", parser.getParameters().get("ApplicationName"));
    }
}
