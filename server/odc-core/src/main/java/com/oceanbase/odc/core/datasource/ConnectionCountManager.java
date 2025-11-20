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
package com.oceanbase.odc.core.datasource;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.oceanbase.odc.core.shared.exception.OverLimitException;

import lombok.extern.slf4j.Slf4j;

/**
 * Connection count manager for tracking database connections by url+username
 *
 * @author yh263208
 * @date 2024-01-01
 * @since ODC_release_4.3.2
 */
@Slf4j
public class ConnectionCountManager {

    private static volatile ConnectionCountManager instance;
    private final Map<String, AtomicInteger> connectionCountMap = new ConcurrentHashMap<>();
    private volatile long maxConnectionCount = -1;

    private ConnectionCountManager() {}

    public static ConnectionCountManager getInstance() {
        if (instance == null) {
            synchronized (ConnectionCountManager.class) {
                if (instance == null) {
                    instance = new ConnectionCountManager();
                }
            }
        }
        return instance;
    }

    /**
     * Initialize the max connection count limit
     *
     * @param maxConnectionCount max connection count, -1 means no limit
     */
    public void setMaxConnectionCount(long maxConnectionCount) {
        this.maxConnectionCount = maxConnectionCount;
        log.info("Set max connection count to {}", maxConnectionCount);
    }

    /**
     * Get the max connection count limit
     *
     * @return max connection count, -1 means no limit
     */
    public long getMaxConnectionCount() {
        return maxConnectionCount;
    }

    /**
     * Generate key from url and username
     * Extracts host:port from JDBC URL and combines with username
     *
     * @param url database url (JDBC URL format)
     * @param username database username
     * @return key string in format "host:port:username"
     */
    public static String generateKey(String url, String username) {
        if (url == null) {
            url = "";
        }
        if (username == null) {
            username = "";
        }
        String hostPort = extractHostAndPort(url);
        return hostPort + ":" + username;
    }

    /**
     * Extract host:port from JDBC URL
     * Supports formats:
     * - jdbc:mysql://host:port/database?params
     * - jdbc:oceanbase://host:port/database?params
     * - jdbc:postgresql://host:port/database?params
     * - jdbc:oracle:thin:@host:port:database
     * - jdbc:oracle:thin:@//host:port/database
     *
     * @param jdbcUrl JDBC URL
     * @return host:port string, or original url if parsing fails
     */
    private static String extractHostAndPort(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isEmpty()) {
            return "";
        }

        // Pattern for MySQL/OceanBase/PostgreSQL: jdbc:type://host:port/...
        Pattern mysqlPattern = Pattern.compile("jdbc:(mysql|oceanbase|postgresql)://([^/:]+)(?::([0-9]+))?");
        Matcher mysqlMatcher = mysqlPattern.matcher(jdbcUrl);
        if (mysqlMatcher.find()) {
            String host = mysqlMatcher.group(2);
            String port = mysqlMatcher.group(3);
            if (port != null && !port.isEmpty()) {
                return host + ":" + port;
            } else {
                // Use default port based on database type
                String dbType = mysqlMatcher.group(1);
                int defaultPort = getDefaultPort(dbType);
                return host + ":" + defaultPort;
            }
        }

        // Pattern for Oracle SID format: jdbc:oracle:thin:@host:port:database
        Pattern oracleSidPattern = Pattern.compile("jdbc:oracle:thin:@([^:/]+):([0-9]+)");
        Matcher oracleSidMatcher = oracleSidPattern.matcher(jdbcUrl);
        if (oracleSidMatcher.find()) {
            return oracleSidMatcher.group(1) + ":" + oracleSidMatcher.group(2);
        }

        // Pattern for Oracle Service Name format: jdbc:oracle:thin:@//host:port/database
        Pattern oracleServicePattern = Pattern.compile("jdbc:oracle:thin:@//([^:/]+):([0-9]+)");
        Matcher oracleServiceMatcher = oracleServicePattern.matcher(jdbcUrl);
        if (oracleServiceMatcher.find()) {
            return oracleServiceMatcher.group(1) + ":" + oracleServiceMatcher.group(2);
        }

        // If no pattern matches, log warning and return original URL
        log.warn("Failed to extract host:port from JDBC URL: {}, using original URL as key", jdbcUrl);
        return jdbcUrl;
    }

    /**
     * Get default port for database type
     *
     * @param dbType database type (mysql, oceanbase, postgresql, etc.)
     * @return default port number
     */
    private static int getDefaultPort(String dbType) {
        if (dbType == null) {
            return 3306; // Default to MySQL port
        }
        switch (dbType.toLowerCase()) {
            case "mysql":
                return 3306;
            case "oceanbase":
                return 2883;
            case "postgresql":
                return 5432;
            default:
                return 3306; // Default to MySQL port
        }
    }

    /**
     * Increment connection count for the given key
     *
     * @param key connection key (url+username)
     * @return current count after increment
     * @throws OverLimitException if connection count exceeds limit
     */
    public int incrementConnectionCount(String key) {
        if (maxConnectionCount > 0) {
            AtomicInteger count = connectionCountMap.computeIfAbsent(key, k -> new AtomicInteger(0));
            int currentCount = count.incrementAndGet();
            log.debug("Increment connection count, key={}, currentCount={}, maxCount={}", key, currentCount,
                    maxConnectionCount);
            if (currentCount > maxConnectionCount) {
                count.decrementAndGet();
                String message = String.format("数据库连接数超限, 当前值=%d, 最大值=%d",
                        currentCount, maxConnectionCount);
                throw new IllegalStateException(String.format(message, maxConnectionCount));
            }
            return currentCount;
        } else {
            // No limit, just increment
            AtomicInteger count = connectionCountMap.computeIfAbsent(key, k -> new AtomicInteger(0));
            int currentCount = count.incrementAndGet();
            log.debug("Increment connection count (no limit), key={}, currentCount={}", key, currentCount);
            return currentCount;
        }
    }

    /**
     * Decrement connection count for the given key
     *
     * @param key connection key (url+username)
     * @return current count after decrement
     */
    public int decrementConnectionCount(String key) {
        AtomicInteger count = connectionCountMap.get(key);
        if (count == null) {
            log.warn("Attempt to decrement connection count for non-existent key: {}", key);
            return 0;
        }
        int currentCount = count.decrementAndGet();
        log.debug("Decrement connection count, key={}, currentCount={}", key, currentCount);
        if (currentCount <= 0) {
            connectionCountMap.remove(key);
            log.debug("Removed connection count entry for key: {}", key);
        }
        return currentCount;
    }

    /**
     * Get current connection count for the given key
     *
     * @param key connection key (url+username)
     * @return current count
     */
    public int getConnectionCount(String key) {
        AtomicInteger count = connectionCountMap.get(key);
        return count == null ? 0 : count.get();
    }

    /**
     * Clear all connection counts (mainly for testing)
     */
    public void clear() {
        connectionCountMap.clear();
        log.info("Cleared all connection counts");
    }

    /**
     * Get all connection counts (mainly for monitoring)
     *
     * @return snapshot of connection count map
     */
    public Map<String, Integer> getAllConnectionCounts() {
        Map<String, Integer> snapshot = new ConcurrentHashMap<>();
        connectionCountMap.forEach((key, count) -> snapshot.put(key, count.get()));
        return snapshot;
    }

}
