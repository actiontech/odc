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
package com.oceanbase.odc.plugin.connect.sqlserver;

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
 * @author yizhou.xw
 * @date 2024/12
 * @since ODC_release_4.3.4
 */
@Slf4j
public class SqlServerJdbcUrlParser implements JdbcUrlParser {
    private static final String SQL_SERVER_JDBC_PREFIX = "jdbc:sqlserver://";
    private static final Pattern URL_PATTERN = Pattern.compile(
            "jdbc:sqlserver://([^:;]+):(\\d+)(?:;([^;]*))?");
    private static final Pattern DATABASE_NAME_PATTERN = Pattern.compile("databaseName=([^;]+)");
    private static final Pattern SCHEMA_PATTERN = Pattern.compile("currentSchema=([^;]+)");

    private final String jdbcUrl;
    private final List<HostAddress> addresses;
    private final Map<String, Object> parameters;
    private final String schema;

    public SqlServerJdbcUrlParser(@NonNull String jdbcUrl, String userName) throws SQLException {
        if (!jdbcUrl.startsWith(SQL_SERVER_JDBC_PREFIX)) {
            throw new IllegalArgumentException("Invalid JDBC URL for SQL Server: " + jdbcUrl);
        }
        this.jdbcUrl = jdbcUrl;
        this.addresses = parseHostAndPort(jdbcUrl);
        this.parameters = parseParameters(jdbcUrl);
        this.schema = parseSchema(jdbcUrl);
    }

    private List<HostAddress> parseHostAndPort(String jdbcUrl) throws SQLException {
        Matcher matcher = URL_PATTERN.matcher(jdbcUrl);
        if (matcher.find()) {
            HostAddress hostAddress = new HostAddress();
            hostAddress.setHost(matcher.group(1));
            hostAddress.setPort(Integer.parseInt(matcher.group(2)));
            return Collections.singletonList(hostAddress);
        }
        throw new SQLException("Failed to parse host and port from JDBC URL: " + jdbcUrl);
    }

    private Map<String, Object> parseParameters(String jdbcUrl) {
        Map<String, Object> paramsMap = new HashMap<>();

        int paramsIndex = jdbcUrl.indexOf(';');
        if (paramsIndex > 0) {
            String paramsString = jdbcUrl.substring(paramsIndex + 1);
            String[] paramsArray = paramsString.split(";");
            for (String param : paramsArray) {
                int equalIndex = param.indexOf('=');
                if (equalIndex > 0) {
                    String key = param.substring(0, equalIndex);
                    String value = param.substring(equalIndex + 1);
                    // Skip databaseName and currentSchema as they are handled separately
                    if (!"databaseName".equals(key) && !"currentSchema".equals(key)) {
                        paramsMap.put(key, value);
                    }
                }
            }
        }
        return paramsMap;
    }

    private String parseSchema(String jdbcUrl) {
        Matcher schemaMatcher = SCHEMA_PATTERN.matcher(jdbcUrl);
        if (schemaMatcher.find()) {
            return schemaMatcher.group(1);
        }
        // Default schema for SQL Server is 'dbo'
        return "dbo";
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
}
