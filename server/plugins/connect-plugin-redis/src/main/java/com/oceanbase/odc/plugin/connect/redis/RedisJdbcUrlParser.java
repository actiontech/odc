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
package com.oceanbase.odc.plugin.connect.redis;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.oceanbase.odc.plugin.connect.api.HostAddress;
import com.oceanbase.odc.plugin.connect.api.JdbcUrlParser;

import lombok.NonNull;

public class RedisJdbcUrlParser implements JdbcUrlParser {
    private static final String PREFIX = "jdbc:redis://";

    private final List<HostAddress> hostAddresses;
    private final Map<String, Object> parameters;
    private final String schema;

    public RedisJdbcUrlParser(@NonNull String jdbcUrl, String userName) throws SQLException {
        if (!jdbcUrl.startsWith(PREFIX)) {
            throw new SQLException("Invalid Redis JDBC url: " + jdbcUrl);
        }
        try {
            URI uri = new URI(jdbcUrl.substring("jdbc:".length()));
            HostAddress address = new HostAddress();
            address.setHost(uri.getHost());
            address.setPort(uri.getPort());
            this.hostAddresses = Collections.singletonList(address);
            this.schema = uri.getPath() == null || uri.getPath().isEmpty() || "/".equals(uri.getPath()) ? "0"
                    : uri.getPath().substring(1);
            this.parameters = parseQuery(uri.getQuery());
        } catch (URISyntaxException e) {
            throw new SQLException("Failed to parse Redis JDBC url", e);
        }
    }

    private Map<String, Object> parseQuery(String query) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (query == null || query.isEmpty()) {
            return result;
        }
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) {
                result.put(parts[0], parts[1]);
            }
        }
        return result;
    }

    @Override
    public List<HostAddress> getHostAddresses() {
        return hostAddresses;
    }

    @Override
    public String getSchema() {
        return schema;
    }

    @Override
    public Map<String, Object> getParameters() {
        return parameters;
    }
}
