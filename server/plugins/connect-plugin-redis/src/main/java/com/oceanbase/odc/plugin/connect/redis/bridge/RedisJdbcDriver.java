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
package com.oceanbase.odc.plugin.connect.redis.bridge;

import java.net.URI;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Logger;

import com.oceanbase.odc.plugin.connect.redis.RedisJdbcUrlParser;

public class RedisJdbcDriver implements Driver {
    static {
        try {
            DriverManager.registerDriver(new RedisJdbcDriver());
        } catch (SQLException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) {
            return null;
        }
        try {
            RedisClient client = newClient(url, info, 5);
            RedisJdbcUrlParser parser = new RedisJdbcUrlParser(url, null);
            String database = parser.getSchema() == null ? "0" : parser.getSchema();
            if (!"0".equals(database)) {
                client.command("SELECT", database);
            }
            String version = parseVersion(client.command("INFO", "server"));
            return RedisBridgeUtil.newConnection(new RedisSessionContext(client, database, version));
        } catch (Exception e) {
            throw new SQLException("Failed to connect Redis", e);
        }
    }

    public static RedisClient newClient(String jdbcUrl, Properties properties, int queryTimeoutSeconds)
            throws Exception {
        URI uri = new URI(jdbcUrl.substring("jdbc:".length()));
        int timeoutMillis = queryTimeoutSeconds <= 0 ? 5000 : queryTimeoutSeconds * 1000;
        RedisClient client = new RedisClient(uri.getHost(), uri.getPort() <= 0 ? 6379 : uri.getPort(), timeoutMillis);
        String user = properties == null ? null : properties.getProperty("user");
        String password = properties == null ? null : properties.getProperty("password");
        if (password != null && !password.isEmpty()) {
            if (user != null && !user.isEmpty()) {
                client.command("AUTH", user, password);
            } else {
                client.command("AUTH", password);
            }
        }
        String database = uri.getPath() == null || uri.getPath().isEmpty() || "/".equals(uri.getPath()) ? "0"
                : uri.getPath().substring(1);
        if (!"0".equals(database)) {
            client.command("SELECT", database);
        }
        return client;
    }

    private String parseVersion(Object info) {
        if (!(info instanceof String)) {
            return "unknown";
        }
        for (String line : ((String) info).split("\\r?\\n")) {
            if (line.startsWith("redis_version:")) {
                return line.substring("redis_version:".length()).trim();
            }
        }
        return "unknown";
    }

    @Override
    public boolean acceptsURL(String url) {
        return url != null && url.startsWith("jdbc:redis://");
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
        return new DriverPropertyInfo[0];
    }

    @Override
    public int getMajorVersion() {
        return 1;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public Logger getParentLogger() {
        return Logger.getGlobal();
    }
}
