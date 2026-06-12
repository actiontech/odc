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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RedisScanHelper {
    private RedisScanHelper() {}

    public static List<String> scanKeys(Connection connection, int count) throws SQLException {
        if (connection == null) {
            return Collections.emptyList();
        }
        try (Statement statement = connection.createStatement()) {
            if (!statement.execute("SCAN 0 COUNT " + count)) {
                return Collections.emptyList();
            }
            try (ResultSet resultSet = statement.getResultSet()) {
                return readKeyColumn(resultSet);
            }
        }
    }

    public static List<String> scanKeysViaClient(Connection connection, int count) throws Exception {
        Object reply = RedisBridgeUtil.requireContext(connection).getClient().command("SCAN", "0", "COUNT",
                String.valueOf(count));
        return parseScanKeys(reply);
    }

    public static List<String> scanKeysPreferStatement(Connection connection, int count) throws Exception {
        try {
            List<String> keys = scanKeys(connection, count);
            if (!keys.isEmpty()) {
                return keys;
            }
        } catch (SQLException ignored) {
            // fall back to direct client command
        }
        return scanKeysViaClient(connection, count);
    }

    public static List<String> parseScanKeys(Object reply) {
        List<String> keys = new ArrayList<>();
        if (!(reply instanceof List)) {
            return keys;
        }
        List<?> parts = (List<?>) reply;
        if (parts.size() < 2) {
            return keys;
        }
        if (parts.get(1) instanceof List) {
            for (Object key : (List<?>) parts.get(1)) {
                appendKey(keys, key);
            }
            return keys;
        }
        for (int index = 1; index < parts.size(); index++) {
            appendKey(keys, parts.get(index));
        }
        return keys;
    }

    private static void appendKey(List<String> keys, Object key) {
        if (key != null) {
            String value = String.valueOf(key);
            if (!value.isEmpty()) {
                keys.add(value);
            }
        }
    }

    private static List<String> readKeyColumn(ResultSet resultSet) throws SQLException {
        List<String> keys = new ArrayList<>();
        if (resultSet == null) {
            return keys;
        }
        ResultSetMetaData metaData = resultSet.getMetaData();
        int keyColumnIndex = 0;
        if (metaData != null) {
            for (int i = 1; i <= metaData.getColumnCount(); i++) {
                String label = metaData.getColumnLabel(i);
                if (label != null && "key".equalsIgnoreCase(label)) {
                    keyColumnIndex = i;
                    break;
                }
            }
            if (keyColumnIndex == 0 && metaData.getColumnCount() >= 2) {
                keyColumnIndex = 2;
            }
        }
        while (resultSet.next()) {
            String key = keyColumnIndex > 0 ? resultSet.getString(keyColumnIndex) : null;
            if (key == null || key.isEmpty()) {
                key = resultSet.getString("key");
            }
            appendKey(keys, key);
        }
        return keys;
    }
}
