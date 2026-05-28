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
package com.oceanbase.odc.plugin.connect.hive;

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
 * Parses Hive JDBC URLs of the form {@code jdbc:hive2://host:port/database;param=value}.
 * <p>
 * Uses simple string splitting rather than regex (the URL format is regular enough that string
 * operations are clearer and sufficient).
 *
 * @since ODC_release_4.3.4
 */
@Slf4j
public class HiveJdbcUrlParser implements JdbcUrlParser {

    private static final String HIVE_JDBC_PREFIX = "jdbc:hive2://";

    private final List<HostAddress> addresses;
    private final Map<String, Object> parameters;
    private final String schema;

    public HiveJdbcUrlParser(@NonNull String jdbcUrl, String userName) throws SQLException {
        if (!jdbcUrl.startsWith(HIVE_JDBC_PREFIX)) {
            throw new IllegalArgumentException("Invalid JDBC URL for Hive: " + jdbcUrl);
        }

        // Strip the prefix: "host:port/database;param1=val1;param2=val2"
        String remainder = jdbcUrl.substring(HIVE_JDBC_PREFIX.length());

        // Split host:port from the rest at the first "/"
        int slashIndex = remainder.indexOf('/');
        String hostPortPart;
        String databaseAndParams;
        if (slashIndex >= 0) {
            hostPortPart = remainder.substring(0, slashIndex);
            databaseAndParams = remainder.substring(slashIndex + 1);
        } else {
            hostPortPart = remainder;
            databaseAndParams = "";
        }

        // Parse host and port from "host:port"
        this.addresses = parseHostAndPort(hostPortPart, jdbcUrl);

        // Split database from parameters at the first ";"
        int semicolonIndex = databaseAndParams.indexOf(';');
        String database;
        String paramString;
        if (semicolonIndex >= 0) {
            database = databaseAndParams.substring(0, semicolonIndex);
            paramString = databaseAndParams.substring(semicolonIndex + 1);
        } else {
            database = databaseAndParams;
            paramString = "";
        }

        this.schema = database;
        this.parameters = parseParameters(paramString);
    }

    private List<HostAddress> parseHostAndPort(String hostPortPart, String originalUrl) throws SQLException {
        int colonIndex = hostPortPart.indexOf(':');
        if (colonIndex <= 0) {
            throw new SQLException("Failed to parse host and port from JDBC URL: " + originalUrl);
        }
        String host = hostPortPart.substring(0, colonIndex);
        int port;
        try {
            port = Integer.parseInt(hostPortPart.substring(colonIndex + 1));
        } catch (NumberFormatException e) {
            throw new SQLException("Failed to parse port from JDBC URL: " + originalUrl, e);
        }
        HostAddress hostAddress = new HostAddress();
        hostAddress.setHost(host);
        hostAddress.setPort(port);
        return Collections.singletonList(hostAddress);
    }

    private Map<String, Object> parseParameters(String paramString) {
        Map<String, Object> paramsMap = new HashMap<>();
        if (paramString == null || paramString.isEmpty()) {
            return paramsMap;
        }
        String[] params = paramString.split(";");
        for (String param : params) {
            if (param.isEmpty()) {
                continue;
            }
            int equalIndex = param.indexOf('=');
            if (equalIndex > 0) {
                String key = param.substring(0, equalIndex);
                String value = param.substring(equalIndex + 1);
                paramsMap.put(key, value);
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
