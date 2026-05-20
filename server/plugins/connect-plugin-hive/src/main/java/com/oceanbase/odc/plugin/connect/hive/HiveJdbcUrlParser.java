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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oceanbase.odc.plugin.connect.api.HostAddress;
import com.oceanbase.odc.plugin.connect.api.JdbcUrlParser;
import com.oceanbase.odc.plugin.connect.model.JdbcUrlProperty;

import lombok.NonNull;

/**
 * Build and parse {@code jdbc:hive2://host:port[/db][;k=v]} URLs. Used by both
 * {@link HiveConnectionExtension#generateJdbcUrl(JdbcUrlProperty)} (build path) and
 * {@link HiveConnectionExtension#getConnectionInfo(String, String)} (parse path).
 *
 * <p>
 * Reference: Hive JDBC URL syntax
 * {@code jdbc:hive2://host:port/db;auth=NOSASL;transport_mode=binary;httpPath=...;ssl=...}.
 *
 * @since ODC_release_4.3.4
 */
public class HiveJdbcUrlParser implements JdbcUrlParser {

    private static final String URL_PREFIX = "jdbc:hive2://";

    private String host;
    private Integer port;
    private String schema;
    private final Map<String, Object> parameters = new HashMap<>();

    /**
     * No-arg constructor for build mode (use {@link #build(JdbcUrlProperty)}).
     */
    public HiveJdbcUrlParser() {}

    /**
     * Parse mode: consume an existing JDBC URL string.
     */
    public HiveJdbcUrlParser(@NonNull String jdbcUrl) {
        if (!jdbcUrl.startsWith(URL_PREFIX)) {
            throw new IllegalArgumentException("Invalid Hive jdbc url: " + jdbcUrl);
        }
        String body = jdbcUrl.substring(URL_PREFIX.length());
        // split off ";key=value;..." session conf chunk first
        int semi = body.indexOf(';');
        String hostPortDb = semi >= 0 ? body.substring(0, semi) : body;
        String kvPart = semi >= 0 ? body.substring(semi + 1) : "";
        // hostPortDb := host:port[/db]
        int slash = hostPortDb.indexOf('/');
        String hostPort = slash >= 0 ? hostPortDb.substring(0, slash) : hostPortDb;
        this.schema = slash >= 0 ? hostPortDb.substring(slash + 1) : "";
        int colon = hostPort.indexOf(':');
        if (colon <= 0 || colon == hostPort.length() - 1) {
            throw new IllegalArgumentException("Invalid Hive jdbc url host:port: " + hostPort);
        }
        this.host = hostPort.substring(0, colon);
        try {
            this.port = Integer.parseInt(hostPort.substring(colon + 1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid Hive jdbc url port: " + hostPort, e);
        }
        for (String pair : kvPart.split(";")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            if (eq > 0) {
                parameters.put(pair.substring(0, eq), pair.substring(eq + 1));
            }
        }
    }

    /**
     * Build a Hive JDBC url from a {@link JdbcUrlProperty}. KV parameters in
     * {@link JdbcUrlProperty#getJdbcParameters()} are appended as Hive session conf {@code ;k=v}.
     */
    public String build(@NonNull JdbcUrlProperty properties) {
        if (properties.getHost() == null || properties.getHost().isEmpty()) {
            throw new IllegalArgumentException("host can not be empty");
        }
        if (properties.getPort() == null) {
            throw new IllegalArgumentException("port can not be null");
        }
        StringBuilder url = new StringBuilder(URL_PREFIX);
        url.append(properties.getHost()).append(':').append(properties.getPort());
        if (properties.getDefaultSchema() != null && !properties.getDefaultSchema().isEmpty()) {
            url.append('/').append(properties.getDefaultSchema());
        }
        Map<String, String> kv = properties.getJdbcParameters();
        if (kv != null && !kv.isEmpty()) {
            for (Map.Entry<String, String> e : kv.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                url.append(';').append(e.getKey()).append('=').append(e.getValue());
            }
        }
        return url.toString();
    }

    @Override
    public List<HostAddress> getHostAddresses() {
        if (host == null || port == null) {
            return Collections.emptyList();
        }
        List<HostAddress> list = new ArrayList<>(1);
        list.add(new HostAddress(host, port));
        return list;
    }

    @Override
    public String getSchema() {
        return schema;
    }

    @Override
    public Map<String, Object> getParameters() {
        return new HashMap<>(parameters);
    }
}
