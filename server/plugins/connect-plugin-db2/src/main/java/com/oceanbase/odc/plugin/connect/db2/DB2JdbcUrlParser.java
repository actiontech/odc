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

import com.oceanbase.odc.plugin.connect.api.HostAddress;
import com.oceanbase.odc.plugin.connect.api.JdbcUrlParser;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Parser for DB2 JDBC URL format: jdbc:db2://host:port/database[:param=value;...]
 */
@Slf4j
public class DB2JdbcUrlParser implements JdbcUrlParser {
    private static final String DB2_JDBC_PREFIX = "jdbc:db2://";

    private final String jdbcUrl;
    private final List<HostAddress> addresses;
    private final Map<String, Object> parameters;
    private final String schema;

    public DB2JdbcUrlParser(@NonNull String jdbcUrl, String userName) throws SQLException {
        if (!jdbcUrl.startsWith(DB2_JDBC_PREFIX)) {
            throw new IllegalArgumentException("Invalid JDBC URL for DB2: " + jdbcUrl);
        }
        this.jdbcUrl = jdbcUrl;
        this.addresses = parseHostAndPort(jdbcUrl);
        this.parameters = parseParameters(jdbcUrl);
        // DB2 default schema is the uppercase username
        this.schema = (userName != null) ? userName.toUpperCase() : null;
    }

    private List<HostAddress> parseHostAndPort(String jdbcUrl) throws SQLException {
        // jdbc:db2://host:port/database[:params]
        String afterPrefix = jdbcUrl.substring(DB2_JDBC_PREFIX.length());
        int slashIndex = afterPrefix.indexOf('/');
        String hostPort = (slashIndex > 0) ? afterPrefix.substring(0, slashIndex) : afterPrefix;
        int colonIndex = hostPort.indexOf(':');
        if (colonIndex <= 0) {
            throw new SQLException("Failed to parse host and port from DB2 JDBC URL: " + jdbcUrl);
        }
        HostAddress hostAddress = new HostAddress();
        hostAddress.setHost(hostPort.substring(0, colonIndex));
        hostAddress.setPort(Integer.parseInt(hostPort.substring(colonIndex + 1)));
        return Collections.singletonList(hostAddress);
    }

    private Map<String, Object> parseParameters(String jdbcUrl) {
        // DB2 JDBC URL parameters come after the database name, separated by colon then semicolons
        Map<String, Object> paramsMap = new HashMap<>();
        String afterPrefix = jdbcUrl.substring(DB2_JDBC_PREFIX.length());
        int slashIndex = afterPrefix.indexOf('/');
        if (slashIndex > 0) {
            String dbAndParams = afterPrefix.substring(slashIndex + 1);
            int colonIndex = dbAndParams.indexOf(':');
            if (colonIndex > 0) {
                String paramsString = dbAndParams.substring(colonIndex + 1);
                String[] pairs = paramsString.split(";");
                for (String pair : pairs) {
                    int eqIndex = pair.indexOf('=');
                    if (eqIndex > 0) {
                        paramsMap.put(pair.substring(0, eqIndex), pair.substring(eqIndex + 1));
                    }
                }
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
