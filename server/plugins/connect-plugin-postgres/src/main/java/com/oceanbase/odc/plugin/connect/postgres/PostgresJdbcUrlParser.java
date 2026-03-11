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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oceanbase.odc.plugin.connect.api.HostAddress;
import com.oceanbase.odc.plugin.connect.api.JdbcUrlParser;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * PostgreSQL JDBC URL 解析器
 * 
 * <p>
 * 解析 PostgreSQL JDBC URL 格式：
 * 
 * <pre>
 * jdbc:postgresql://host:port/database?currentSchema=schema&param=value
 * </pre>
 * 
 * <p>
 * 支持：
 * <ul>
 * <li>标准格式：jdbc:postgresql://host:port/database</li>
 * <li>带参数：jdbc:postgresql://host:port/database?param1=value1&param2=value2</li>
 * <li>currentSchema 参数提取为 schema</li>
 * <li>默认端口: 5432</li>
 * </ul>
 *
 * @author ODC Team
 * @date 2025-03
 * @since ODC_release_4.3.5
 */
@Slf4j
public class PostgresJdbcUrlParser implements JdbcUrlParser {

    private static final String PG_JDBC_PREFIX = "jdbc:postgresql://";
    private static final int DEFAULT_PG_PORT = 5432;

    private final String jdbcUrl;
    private final List<HostAddress> hostAddresses;
    private final Map<String, Object> parameters;
    private final String schema;

    /**
     * 构造 PostgreSQL JDBC URL 解析器
     *
     * @param jdbcUrl JDBC URL 字符串
     * @throws SQLException 如果 URL 格式无效
     */
    public PostgresJdbcUrlParser(@NonNull String jdbcUrl) throws SQLException {
        if (!jdbcUrl.startsWith(PG_JDBC_PREFIX)) {
            throw new SQLException("Invalid PostgreSQL JDBC URL, must start with 'jdbc:postgresql://': " + jdbcUrl);
        }
        this.jdbcUrl = jdbcUrl;
        this.hostAddresses = parseHostAddresses(jdbcUrl);
        this.parameters = parseParameters(jdbcUrl);
        this.schema = extractSchema(this.parameters);
    }

    /**
     * 解析主机地址列表
     * 
     * <p>
     * URL 格式: jdbc:postgresql://host:port/database?params
     * 
     * <p>
     * 解析逻辑（不使用正则）：
     * <ol>
     * <li>移除前缀 "jdbc:postgresql://"</li>
     * <li>按 "/" 分割获取 host:port 部分和 database 部分</li>
     * <li>按 ":" 分割 host 和 port</li>
     * </ol>
     */
    private List<HostAddress> parseHostAddresses(String jdbcUrl) throws SQLException {
        // 移除前缀
        String urlWithoutPrefix = jdbcUrl.substring(PG_JDBC_PREFIX.length());

        // 按 "/" 分割，第一部分是 host:port，后部分是 database 或 database?params
        int slashIndex = urlWithoutPrefix.indexOf('/');
        if (slashIndex < 0) {
            throw new SQLException("Invalid PostgreSQL JDBC URL, missing database name: " + jdbcUrl);
        }

        String hostPortPart = urlWithoutPrefix.substring(0, slashIndex);

        // 解析 host 和 port
        int colonIndex = hostPortPart.indexOf(':');
        String host;
        int port;

        if (colonIndex > 0) {
            host = hostPortPart.substring(0, colonIndex);
            String portStr = hostPortPart.substring(colonIndex + 1);
            // 处理端口后有额外内容的情况（如 IPv6 地址等，取逗号前）
            int extraIndex = portStr.indexOf(',');
            if (extraIndex > 0) {
                portStr = portStr.substring(0, extraIndex);
            }
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                throw new SQLException("Invalid port number in JDBC URL: " + portStr);
            }
        } else {
            // 无端口，使用默认端口
            host = hostPortPart;
            port = DEFAULT_PG_PORT;
        }

        if (host.isEmpty()) {
            throw new SQLException("Empty host in JDBC URL: " + jdbcUrl);
        }

        HostAddress hostAddress = new HostAddress();
        hostAddress.setHost(host);
        hostAddress.setPort(port);

        return Collections.singletonList(hostAddress);
    }

    /**
     * 解析 URL 参数
     * 
     * <p>
     * 参数格式: ?key1=value1&key2=value2
     */
    private Map<String, Object> parseParameters(String jdbcUrl) {
        Map<String, Object> params = new HashMap<>();

        // 找到 "?" 开始的参数部分
        int questionIndex = jdbcUrl.indexOf('?');
        if (questionIndex < 0) {
            return params;
        }

        String paramsPart = jdbcUrl.substring(questionIndex + 1);
        if (paramsPart.isEmpty()) {
            return params;
        }

        // 按 "&" 分割各个参数
        String[] paramPairs = paramsPart.split("&");
        for (String pair : paramPairs) {
            if (pair.isEmpty()) {
                continue;
            }
            int equalIndex = pair.indexOf('=');
            if (equalIndex > 0) {
                String key = pair.substring(0, equalIndex);
                String value = pair.substring(equalIndex + 1);
                params.put(key, value);
            } else {
                // 无值的参数
                params.put(pair, "");
            }
        }

        return params;
    }

    /**
     * 从参数中提取 schema
     * 
     * <p>
     * PG 的 schema 通过 currentSchema 参数指定
     */
    private String extractSchema(Map<String, Object> parameters) {
        Object schemaValue = parameters.get("currentSchema");
        return schemaValue != null ? schemaValue.toString() : null;
    }

    @Override
    public List<HostAddress> getHostAddresses() {
        return this.hostAddresses;
    }

    @Override
    public String getSchema() {
        return this.schema;
    }

    @Override
    public Map<String, Object> getParameters() {
        return this.parameters;
    }
}
