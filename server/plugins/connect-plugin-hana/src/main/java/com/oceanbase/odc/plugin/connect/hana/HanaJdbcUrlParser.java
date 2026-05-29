/*
 * Copyright (c) 2024 OceanBase.
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
package com.oceanbase.odc.plugin.connect.hana;

import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oceanbase.odc.core.shared.constant.OdcConstants;
import com.oceanbase.odc.plugin.connect.api.HostAddress;
import com.oceanbase.odc.plugin.connect.api.JdbcUrlParser;

import lombok.NonNull;

/**
 * Parser for SAP HANA JDBC URLs.
 * <p>
 * HANA JDBC URL format: {@code jdbc:sap://host:port/?param1=value1&param2=value2}
 * <p>
 * The default schema is resolved in the following priority order:
 * <ol>
 * <li>{@code currentSchema} query parameter in the JDBC URL</li>
 * <li>Uppercase of the provided {@code userName}</li>
 * <li>{@link OdcConstants#HANA_DEFAULT_SCHEMA}</li>
 * </ol>
 *
 * @since ODC_release_4.3.4
 */
public class HanaJdbcUrlParser implements JdbcUrlParser {

    static final String HANA_JDBC_PREFIX = "jdbc:sap://";
    private static final String CURRENT_SCHEMA_KEY = "currentSchema";

    private final List<HostAddress> addresses;
    private final Map<String, Object> parameters;
    private final String schema;

    public HanaJdbcUrlParser(@NonNull String jdbcUrl, String userName) throws SQLException {
        if (!jdbcUrl.startsWith(HANA_JDBC_PREFIX)) {
            throw new IllegalArgumentException("Invalid JDBC URL for SAP HANA: " + jdbcUrl);
        }
        this.addresses = parseHostAndPort(jdbcUrl);
        Map<String, Object> allParams = parseAllParameters(jdbcUrl);
        this.schema = resolveSchema(allParams, userName);
        // Remove currentSchema from the general parameters since it is exposed via getSchema()
        allParams.remove(CURRENT_SCHEMA_KEY);
        this.parameters = allParams;
    }

    private List<HostAddress> parseHostAndPort(String jdbcUrl) throws SQLException {
        // Strip prefix "jdbc:sap://"
        String remaining = jdbcUrl.substring(HANA_JDBC_PREFIX.length());

        // Find the end of host:port -- delimited by '/' or '?' or end-of-string
        int slashIdx = remaining.indexOf('/');
        int questionIdx = remaining.indexOf('?');

        String hostPort;
        if (slashIdx >= 0) {
            hostPort = remaining.substring(0, slashIdx);
        } else if (questionIdx >= 0) {
            hostPort = remaining.substring(0, questionIdx);
        } else {
            hostPort = remaining;
        }

        int colonIdx = hostPort.lastIndexOf(':');
        if (colonIdx <= 0) {
            throw new SQLException("Failed to parse host and port from HANA JDBC URL: " + jdbcUrl);
        }
        String host = hostPort.substring(0, colonIdx);
        String portStr = hostPort.substring(colonIdx + 1);
        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            throw new SQLException("Invalid port in HANA JDBC URL: " + portStr);
        }
        HostAddress hostAddress = new HostAddress(host, port);
        return Collections.singletonList(hostAddress);
    }

    private Map<String, Object> parseAllParameters(String jdbcUrl) {
        Map<String, Object> params = new HashMap<>();
        int questionIdx = jdbcUrl.indexOf('?');
        if (questionIdx < 0 || questionIdx == jdbcUrl.length() - 1) {
            return params;
        }
        String queryString = jdbcUrl.substring(questionIdx + 1);
        String[] pairs = queryString.split("&");
        for (String pair : pairs) {
            int eqIdx = pair.indexOf('=');
            if (eqIdx > 0) {
                String key = pair.substring(0, eqIdx);
                String value = pair.substring(eqIdx + 1);
                params.put(key, value);
            }
        }
        return params;
    }

    /**
     * Resolve the effective schema name:
     * 1. Use currentSchema parameter from JDBC URL if present
     * 2. Fall back to uppercase userName
     * 3. Fall back to default HANA schema constant
     */
    private String resolveSchema(Map<String, Object> allParams, String userName) {
        Object currentSchema = allParams.get(CURRENT_SCHEMA_KEY);
        if (currentSchema != null && !currentSchema.toString().isEmpty()) {
            return currentSchema.toString();
        }
        if (userName != null && !userName.isEmpty()) {
            return userName.toUpperCase();
        }
        return OdcConstants.HANA_DEFAULT_SCHEMA;
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
