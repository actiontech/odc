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
package com.oceanbase.odc.plugin.connect.kingbase;

import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oceanbase.odc.plugin.connect.api.HostAddress;
import com.oceanbase.odc.plugin.connect.api.JdbcUrlParser;

import lombok.NonNull;

/**
 * Parser for {@code jdbc:kingbase8://host:port/database[?params]}.
 */
public class KingBaseJdbcUrlParser implements JdbcUrlParser {
    private static final String PREFIX = "jdbc:kingbase8://";

    private final List<HostAddress> addresses;
    private final Map<String, Object> parameters;
    private final String schema;

    public KingBaseJdbcUrlParser(@NonNull String jdbcUrl) throws SQLException {
        if (!jdbcUrl.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Invalid JDBC URL for KingBase: " + jdbcUrl);
        }
        String remaining = jdbcUrl.substring(PREFIX.length());
        String query = null;
        int queryIndex = remaining.indexOf('?');
        if (queryIndex >= 0) {
            query = remaining.substring(queryIndex + 1);
            remaining = remaining.substring(0, queryIndex);
        }
        int slashIndex = remaining.indexOf('/');
        String hostPort = slashIndex >= 0 ? remaining.substring(0, slashIndex) : remaining;
        this.schema = slashIndex >= 0 && slashIndex < remaining.length() - 1
                ? remaining.substring(slashIndex + 1)
                : null;
        this.addresses = parseHostPort(hostPort, jdbcUrl);
        this.parameters = parseParameters(query);
    }

    private List<HostAddress> parseHostPort(String hostPort, String jdbcUrl) throws SQLException {
        int colonIndex = hostPort.lastIndexOf(':');
        if (colonIndex <= 0) {
            throw new SQLException("Failed to parse host and port from JDBC URL: " + jdbcUrl);
        }
        String host = hostPort.substring(0, colonIndex);
        try {
            int port = Integer.parseInt(hostPort.substring(colonIndex + 1));
            HostAddress hostAddress = new HostAddress();
            hostAddress.setHost(host);
            hostAddress.setPort(port);
            return Collections.singletonList(hostAddress);
        } catch (NumberFormatException e) {
            throw new SQLException("Failed to parse port from JDBC URL: " + jdbcUrl, e);
        }
    }

    private Map<String, Object> parseParameters(String query) {
        Map<String, Object> paramsMap = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return paramsMap;
        }
        for (String pair : query.split("&")) {
            int equalIndex = pair.indexOf('=');
            if (equalIndex > 0) {
                paramsMap.put(pair.substring(0, equalIndex), pair.substring(equalIndex + 1));
            }
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
}
