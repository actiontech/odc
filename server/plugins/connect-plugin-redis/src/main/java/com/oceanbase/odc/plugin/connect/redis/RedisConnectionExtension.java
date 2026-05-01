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

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.pf4j.Extension;

import com.oceanbase.odc.core.datasource.ConnectionInitializer;
import com.oceanbase.odc.plugin.connect.api.ConnectionExtensionPoint;
import com.oceanbase.odc.plugin.connect.api.JdbcUrlParser;
import com.oceanbase.odc.plugin.connect.api.TestResult;
import com.oceanbase.odc.plugin.connect.model.JdbcUrlProperty;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.Jedis;

/**
 * Redis connection extension that handles connection testing via Jedis. Redis does not use JDBC, so
 * generateJdbcUrl returns a redis:// URI and getDriverClassName returns an empty string.
 */
@Slf4j
@Extension
public class RedisConnectionExtension implements ConnectionExtensionPoint {

    private static final int CONNECT_TIMEOUT_MILLIS = 5000;

    @Override
    public String generateJdbcUrl(@NonNull JdbcUrlProperty properties) {
        String host = properties.getHost();
        Integer port = properties.getPort();
        if (port == null) {
            port = 6379;
        }
        // Return a redis:// URI format. This is not a real JDBC URL but
        // serves as a connection descriptor for the Redis plugin.
        return String.format("redis://%s:%d", host, port);
    }

    @Override
    public String getDriverClassName() {
        // Redis does not use JDBC drivers
        return "";
    }

    @Override
    public List<ConnectionInitializer> getConnectionInitializers() {
        return Collections.emptyList();
    }

    @Override
    public JdbcUrlParser getConnectionInfo(@NonNull String jdbcUrl, String userName) throws SQLException {
        return new RedisUrlParser(jdbcUrl);
    }

    @Override
    public TestResult test(String jdbcUrl, Properties properties, int queryTimeout,
            List<ConnectionInitializer> initializers) {
        try {
            // Parse connection info from the redis:// URL
            String host = "localhost";
            int port = 6379;
            if (jdbcUrl != null && jdbcUrl.startsWith("redis://")) {
                String hostPort = jdbcUrl.substring("redis://".length());
                String[] parts = hostPort.split(":");
                host = parts[0];
                if (parts.length > 1) {
                    port = Integer.parseInt(parts[1].replaceAll("/.*", ""));
                }
            }

            String user = properties.getProperty("user");
            String password = properties.getProperty("password");

            DefaultJedisClientConfig.Builder configBuilder = DefaultJedisClientConfig.builder()
                    .connectionTimeoutMillis(CONNECT_TIMEOUT_MILLIS)
                    .socketTimeoutMillis(CONNECT_TIMEOUT_MILLIS);

            if (password != null && !password.isEmpty()) {
                configBuilder.password(password);
            }
            if (user != null && !user.isEmpty()) {
                configBuilder.user(user);
            }

            try (Jedis jedis = new Jedis(host, port, configBuilder.build())) {
                String pong = jedis.ping();
                if ("PONG".equalsIgnoreCase(pong)) {
                    return TestResult.success();
                } else {
                    return TestResult.unknownError(new RuntimeException("Unexpected PING response: " + pong));
                }
            }
        } catch (Exception e) {
            log.warn("Redis connection test failed", e);
            return TestResult.unknownError(e);
        }
    }
}
