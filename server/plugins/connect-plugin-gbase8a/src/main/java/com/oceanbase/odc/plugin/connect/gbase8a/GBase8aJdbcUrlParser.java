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
package com.oceanbase.odc.plugin.connect.gbase8a;

import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oceanbase.odc.plugin.connect.api.HostAddress;
import com.oceanbase.odc.plugin.connect.api.JdbcUrlParser;

import lombok.NonNull;

/**
 * JDBC URL parser for GBase-8a: {@code jdbc:gbase://host:port[/database][?k=v&...]}.
 */
public class GBase8aJdbcUrlParser implements JdbcUrlParser {
    private static final String JDBC_PREFIX = "jdbc:gbase://";

    private final List<HostAddress> addresses;
    private final Map<String, Object> parameters;
    private final String schema;

    public GBase8aJdbcUrlParser(@NonNull String jdbcUrl) throws SQLException {
        if (!jdbcUrl.startsWith(JDBC_PREFIX)) {
            throw new IllegalArgumentException("Invalid JDBC URL for GBase-8a: " + jdbcUrl);
        }
        this.addresses = parseHostAndPort(jdbcUrl);
        this.parameters = parseParameters(jdbcUrl);
        this.schema = parseSchema(jdbcUrl);
    }

    private List<HostAddress> parseHostAndPort(String jdbcUrl) throws SQLException {
        String remaining = jdbcUrl.substring(JDBC_PREFIX.length());
        int queryIndex = remaining.indexOf('?');
        if (queryIndex >= 0) {
            remaining = remaining.substring(0, queryIndex);
        }
        int slashIndex = remaining.indexOf('/');
        if (slashIndex >= 0) {
            remaining = remaining.substring(0, slashIndex);
        }
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
        for (String pair : queryString.split("&")) {
            int equalIndex = pair.indexOf('=');
            if (equalIndex > 0) {
                paramsMap.put(pair.substring(0, equalIndex), pair.substring(equalIndex + 1));
            }
        }
        return paramsMap;
    }

    private String parseSchema(String jdbcUrl) {
        String remaining = jdbcUrl.substring(JDBC_PREFIX.length());
        int queryIndex = remaining.indexOf('?');
        if (queryIndex >= 0) {
            remaining = remaining.substring(0, queryIndex);
        }
        int slashIndex = remaining.indexOf('/');
        if (slashIndex < 0 || slashIndex == remaining.length() - 1) {
            return null;
        }
        return remaining.substring(slashIndex + 1);
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
