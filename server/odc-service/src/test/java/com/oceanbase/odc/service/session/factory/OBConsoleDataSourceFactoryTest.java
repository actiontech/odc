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
package com.oceanbase.odc.service.session.factory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.oceanbase.odc.core.shared.constant.ConnectType;
import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.odc.service.connection.model.ConnectionConfig;

/**
 * Unit tests for {@link OBConsoleDataSourceFactory#getJdbcParams(ConnectionConfig)} dialect-aware
 * branching (D2, Refs actiontech/dms-ee#827).
 *
 * <p>
 * Background: 修复前该方法把 MySQL/OB-MySQL 专属 JDBC URL 参数（autoDeserialize / allowMultiQueries /
 * sendConnectionAttributes / maxAllowedPacket / noDatetimeStringSync / jdbcCompliantTruncation /
 * defaultConnectionAttributesBanList / allowLoadLocalInfile* / useSSL / zeroDateTimeBehavior 等）硬塞
 * 所有 dialect，导致 DB2 IBM JCC URL 拼接报 {@code ERRORCODE=-4461 数据库 URL 语法...无效}。修复后 DB2
 * 走专属分支只透传用户显式参数，MySQL/Oracle/PG/SqlServer/DM 等保留 legacy 行为不退化。
 */
public class OBConsoleDataSourceFactoryTest {

    /**
     * D2 正路径：DB2 dialect 下，输出的 jdbcUrlParams 必须不含任何 MySQL 专属键。
     */
    @Test
    public void getJdbcParams_db2_doesNotIncludeMysqlOnlyKeys() {
        ConnectionConfig config = newDb2Config();
        Map<String, String> params = OBConsoleDataSourceFactory.getJdbcParams(config);

        String[] mysqlOnly = new String[] {"autoDeserialize", "allowMultiQueries", "maxAllowedPacket",
                "noDatetimeStringSync", "jdbcCompliantTruncation", "sendConnectionAttributes",
                "defaultConnectionAttributesBanList", "useSSL", "zeroDateTimeBehavior",
                "allowLoadLocalInfile", "allowUrlInLocalInfile", "allowLoadLocalInfileInPath"};
        for (String key : mysqlOnly) {
            assertFalse("DB2 jdbcUrlParams must NOT contain MySQL-only key '" + key + "', got: " + params,
                    params.containsKey(key));
        }
    }

    /**
     * D2 正路径：DB2 dialect 下，用户通过 connectionConfig.jdbcUrlParameters 显式声明的自定义键值 必须被透传（尊重用户输入；例如 IBM JCC 的
     * retrieveMessagesFromServerOnGetMessage 之外 的扩展项）。
     */
    @Test
    public void getJdbcParams_db2_passesThroughUserJdbcUrlParameters() {
        ConnectionConfig config = newDb2Config();
        Map<String, Object> userParams = new HashMap<>();
        userParams.put("currentSchema", "TEST_DB2_A");
        userParams.put("blockingReadConnectionTimeout", "30");
        userParams.put("noNull", null); // null values must be skipped (no NPE)
        config.setJdbcUrlParameters(userParams);

        Map<String, String> params = OBConsoleDataSourceFactory.getJdbcParams(config);
        assertEquals("TEST_DB2_A", params.get("currentSchema"));
        assertEquals("30", params.get("blockingReadConnectionTimeout"));
        assertFalse("null-valued entries must be dropped", params.containsKey("noNull"));
    }

    /**
     * D2 回归保护：MYSQL dialect 下，输出必须包含 fix 前的全部 MySQL legacy 参数（防止本次修复 不慎重构 MySQL 路径，使既有 MySQL/OB-MySQL
     * 数据源连接行为退化）。
     *
     * <p>
     * 断言点取自修复前 OBConsoleDataSourceFactory 老实现 L149-L160 + L196-L199 硬编码集合。
     */
    @Test
    public void getJdbcParams_mysql_legacyKeysFullyPreserved() {
        ConnectionConfig config = newMysqlConfig();
        Map<String, String> params = OBConsoleDataSourceFactory.getJdbcParams(config);

        assertEquals("64000000", params.get("maxAllowedPacket"));
        assertEquals("true", params.get("allowMultiQueries"));
        assertEquals("5000", params.get("connectTimeout"));
        assertEquals("true", params.get("noDatetimeStringSync"));
        assertEquals("false", params.get("jdbcCompliantTruncation"));
        assertEquals("true", params.get("sendConnectionAttributes"));
        assertEquals("__client_ip", params.get("defaultConnectionAttributesBanList"));

        // arbitrary file reading vulnerability fix
        assertEquals("false", params.get("allowLoadLocalInfile"));
        assertEquals("false", params.get("allowUrlInLocalInfile"));
        assertEquals("", params.get("allowLoadLocalInfileInPath"));
        assertEquals("false", params.get("autoDeserialize"));

        // SSL default (no ssl config)
        assertEquals("false", params.get("useSSL"));

        // zeroDateTimeBehavior comes from OdcConstants.DEFAULT_ZERO_DATE_TIME_BEHAVIOR; just ensure
        // present.
        assertTrue("MySQL legacy jdbcUrlParams must keep zeroDateTimeBehavior",
                params.containsKey("zeroDateTimeBehavior"));
    }

    /**
     * D2 回归保护：OB_MYSQL dialect 走 legacy 分支（与 MYSQL 完全相同）。
     */
    @Test
    public void getJdbcParams_obMysql_legacyKeysFullyPreserved() {
        ConnectionConfig config = newMysqlConfig();
        config.setType(ConnectType.OB_MYSQL);
        assertEquals(DialectType.OB_MYSQL, config.getDialectType());
        Map<String, String> params = OBConsoleDataSourceFactory.getJdbcParams(config);

        // 关键回归键不丢
        assertEquals("64000000", params.get("maxAllowedPacket"));
        assertEquals("true", params.get("allowMultiQueries"));
        assertEquals("false", params.get("autoDeserialize"));
    }

    private static ConnectionConfig newDb2Config() {
        ConnectionConfig config = new ConnectionConfig();
        // dialectType 由 ConnectType 派生（ConnectionConfig.getDialectType()），不暴露 setter。
        config.setType(ConnectType.DB2);
        config.setHost("10.186.16.126");
        config.setPort(50000);
        config.setUsername("db2inst1");
        config.setPassword("Db2Passw0rd!");
        config.setCatalogName("testdb");
        return config;
    }

    private static ConnectionConfig newMysqlConfig() {
        ConnectionConfig config = new ConnectionConfig();
        // dialectType 由 ConnectType 派生（ConnectionConfig.getDialectType()），不暴露 setter。
        config.setType(ConnectType.MYSQL);
        config.setHost("127.0.0.1");
        config.setPort(3306);
        config.setUsername("root");
        config.setPassword("root");
        return config;
    }
}
