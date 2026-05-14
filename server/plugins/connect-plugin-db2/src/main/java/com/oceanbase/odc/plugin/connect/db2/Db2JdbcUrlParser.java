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
package com.oceanbase.odc.plugin.connect.db2;

import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.oceanbase.odc.plugin.connect.api.HostAddress;
import com.oceanbase.odc.plugin.connect.api.JdbcUrlParser;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * DB2 JDBC URL 解析器（T-002 完整实现，蓝本 {@code SqlServerJdbcUrlParser}）。
 * <p>
 * 支持形式：
 * 
 * <pre>
 *     jdbc:db2://&lt;host&gt;:&lt;port&gt;/&lt;catalog&gt;[:k=v;k=v;]
 * </pre>
 * 
 * 设计要点：
 * <ul>
 * <li>{@link #supports(String)} 提供静态判定，pf4j 装载与 ConnectionTesting 路径需要。</li>
 * <li>{@link #parse(String)} 提供静态反向解析，便于 T-003 在 ConnectionTesting 加 DB2 case 直接复用， 避免每次 new
 * {@code Db2JdbcUrlParser} 时 throw SQLException。</li>
 * <li>构造方法保持与 {@code SqlServerJdbcUrlParser} 相同形态（实现 {@link JdbcUrlParser#getHostAddresses()} /
 * {@link JdbcUrlParser#getSchema()} / {@link JdbcUrlParser#getParameters()}）。</li>
 * </ul>
 */
@Slf4j
public class Db2JdbcUrlParser implements JdbcUrlParser {

    /** DB2 JDBC URL 前缀。 */
    public static final String DB2_JDBC_PREFIX = "jdbc:db2:";
    /** Type-4 driver URL 前缀（host:port/catalog 形式）。 */
    public static final String DB2_JDBC_HOST_PREFIX = "jdbc:db2://";

    /**
     * 形如：{@code jdbc:db2://host:port/catalog} 或 {@code jdbc:db2://host:port/catalog:k=v;k=v;}
     */
    private static final Pattern URL_PATTERN = Pattern.compile(
            "^jdbc:db2://([^:/]+)(?::(\\d+))?(?:/([^:?;]*))?(?::(.*))?$");

    private final String jdbcUrl;
    private final List<HostAddress> addresses;
    private final Map<String, Object> parameters;
    private final String schema;

    public Db2JdbcUrlParser(@NonNull String jdbcUrl) throws SQLException {
        this(jdbcUrl, null);
    }

    public Db2JdbcUrlParser(@NonNull String jdbcUrl, String userName) throws SQLException {
        if (!supports(jdbcUrl)) {
            throw new IllegalArgumentException("Invalid JDBC URL for DB2: " + jdbcUrl);
        }
        this.jdbcUrl = jdbcUrl;
        Matcher matcher = URL_PATTERN.matcher(jdbcUrl);
        if (!matcher.matches()) {
            throw new SQLException("Failed to parse DB2 JDBC URL: " + jdbcUrl);
        }
        HostAddress hostAddress = new HostAddress();
        hostAddress.setHost(matcher.group(1));
        String portToken = matcher.group(2);
        if (portToken != null && !portToken.isEmpty()) {
            hostAddress.setPort(Integer.parseInt(portToken));
        }
        this.addresses = Collections.singletonList(hostAddress);
        this.parameters = parseParameters(matcher.group(4));
        // DB2 catalog 与 schema 不是同一个概念，但工作台侧把 catalog 当作 "数据库"，
        // schema 通过 jdbcParameter currentSchema 透传；这里 schema 字段对外暴露的是 "currentSchema" 参数值。
        Object currentSchema = this.parameters.get("currentSchema");
        this.schema = currentSchema == null ? null : currentSchema.toString();
    }

    /**
     * pf4j 装载与 ConnectionTesting 路径判定本 parser 是否承接给定 URL。
     */
    public static boolean supports(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.startsWith(DB2_JDBC_PREFIX);
    }

    /**
     * 静态反向解析：{@code jdbc:db2://host:port/catalog} → {@link HostAddress}。
     * <p>
     * 仅供 ConnectionTesting / DataSourceFactory 在 switch(DB2) 分支构造 HostAddress 时复用， 避免重复 new
     * Db2JdbcUrlParser；解析失败返回 {@code null}。
     */
    public static HostAddress parse(String jdbcUrl) {
        if (!supports(jdbcUrl)) {
            return null;
        }
        Matcher matcher = URL_PATTERN.matcher(jdbcUrl);
        if (!matcher.matches()) {
            return null;
        }
        HostAddress ha = new HostAddress();
        ha.setHost(matcher.group(1));
        String portToken = matcher.group(2);
        if (portToken != null && !portToken.isEmpty()) {
            try {
                ha.setPort(Integer.parseInt(portToken));
            } catch (NumberFormatException ignore) {
                // 解析失败按缺省 null 处理
            }
        }
        return ha;
    }

    /**
     * 静态反向解析 catalog（"数据库名"），方便后续工作台显示当前数据库。
     */
    public static String parseCatalog(String jdbcUrl) {
        if (!supports(jdbcUrl)) {
            return null;
        }
        Matcher matcher = URL_PATTERN.matcher(jdbcUrl);
        if (!matcher.matches()) {
            return null;
        }
        String catalog = matcher.group(3);
        return (catalog == null || catalog.isEmpty()) ? null : catalog;
    }

    private Map<String, Object> parseParameters(String paramSegment) {
        Map<String, Object> paramsMap = new HashMap<>();
        if (paramSegment == null || paramSegment.isEmpty()) {
            return paramsMap;
        }
        // DB2 用 ";" 分隔每个 k=v
        for (String token : paramSegment.split(";")) {
            if (token.isEmpty()) {
                continue;
            }
            int equalIndex = token.indexOf('=');
            if (equalIndex <= 0) {
                continue;
            }
            String key = token.substring(0, equalIndex);
            String value = token.substring(equalIndex + 1);
            paramsMap.put(key, value);
        }
        return paramsMap;
    }

    @Override
    public List<HostAddress> getHostAddresses() {
        return this.addresses;
    }

    @Override
    public String getSchema() {
        return this.schema;
    }

    @Override
    public Map<String, Object> getParameters() {
        return this.parameters;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }
}
