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
package com.oceanbase.odc.plugin.connect.dm;

import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oceanbase.odc.core.shared.constant.OdcConstants;
import com.oceanbase.odc.plugin.connect.api.HostAddress;
import com.oceanbase.odc.plugin.connect.api.JdbcUrlParser;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * JDBC URL parser for DM (Dameng) database.
 * <p>
 * DM JDBC URL format: {@code jdbc:dm://host:port[?param=value&...]}
 * </p>
 *
 * @author
 * @since ODC_release_4.3.4
 */
@Slf4j
public class DmJdbcUrlParser implements JdbcUrlParser {
    private static final String DM_JDBC_PREFIX = "jdbc:dm://";

    private final String jdbcUrl;
    private final List<HostAddress> addresses;
    private final Map<String, Object> parameters;
    private final String schema;

    public DmJdbcUrlParser(@NonNull String jdbcUrl, String userName) throws SQLException {
        if (!jdbcUrl.startsWith(DM_JDBC_PREFIX)) {
            throw new IllegalArgumentException("Invalid JDBC URL for DM: " + jdbcUrl);
        }
        this.jdbcUrl = jdbcUrl;
        this.addresses = parseHostAndPort(jdbcUrl);
        this.parameters = parseParameters(jdbcUrl);
        this.schema = parseSchema(jdbcUrl, userName);
    }

    private List<HostAddress> parseHostAndPort(String jdbcUrl) throws SQLException {
        // Remove the prefix "jdbc:dm://"
        String remaining = jdbcUrl.substring(DM_JDBC_PREFIX.length());
        // Remove query parameters if present
        int queryIndex = remaining.indexOf('?');
        if (queryIndex >= 0) {
            remaining = remaining.substring(0, queryIndex);
        }
        // Parse host:port
        int colonIndex = remaining.lastIndexOf(':');
        if (colonIndex <= 0) {
            throw new SQLException("Failed to parse host and port from JDBC URL: " + jdbcUrl);
        }
        String host = remaining.substring(0, colonIndex);
        String portStr = remaining.substring(colonIndex + 1);
        try {
            int port = Integer.parseInt(portStr);
            HostAddress hostAddress = new HostAddress();
            hostAddress.setHost(host);
            hostAddress.setPort(port);
            return Collections.singletonList(hostAddress);
        } catch (NumberFormatException e) {
            throw new SQLException("Failed to parse port from JDBC URL: " + jdbcUrl, e);
        }
    }

    private Map<String, Object> parseParameters(String jdbcUrl) {
        Map<String, Object> paramsMap = new HashMap<>();
        int queryIndex = jdbcUrl.indexOf('?');
        if (queryIndex < 0) {
            return paramsMap;
        }
        String queryString = jdbcUrl.substring(queryIndex + 1);
        String[] pairs = queryString.split("&");
        for (String pair : pairs) {
            int equalIndex = pair.indexOf('=');
            if (equalIndex > 0) {
                String key = pair.substring(0, equalIndex);
                String value = pair.substring(equalIndex + 1);
                // Skip schema parameter as it is handled separately
                if (!"schema".equals(key)) {
                    paramsMap.put(key, value);
                }
            }
        }
        return paramsMap;
    }

    private String parseSchema(String jdbcUrl, String userName) {
        int queryIndex = jdbcUrl.indexOf('?');
        if (queryIndex >= 0) {
            String queryString = jdbcUrl.substring(queryIndex + 1);
            String[] pairs = queryString.split("&");
            for (String pair : pairs) {
                int equalIndex = pair.indexOf('=');
                if (equalIndex > 0) {
                    String key = pair.substring(0, equalIndex);
                    String value = pair.substring(equalIndex + 1);
                    if ("schema".equals(key)) {
                        return value;
                    }
                }
            }
        }
        // Default schema for DM is SYSDBA
        return OdcConstants.DM_DEFAULT_SCHEMA;
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
