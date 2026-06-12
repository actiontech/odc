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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RedisResultMapper {
    public RedisTabularResult map(RedisParsedCommand command, Object reply) {
        String name = command.getCommand();
        if ("SCAN".equals(name)) {
            return scan(reply);
        }
        if ("HGETALL".equals(name)) {
            return pairs("field", "value", reply);
        }
        if ("PING".equals(name)) {
            return single("result", reply);
        }
        if (command.isWrite()) {
            return single("ack", reply);
        }
        if (reply instanceof List) {
            return list("value", (List<?>) reply);
        }
        return single("value", reply);
    }

    public RedisTabularResult single(String column, Object value) {
        return new RedisTabularResult(Collections.singletonList(column), Collections.singletonList("VARCHAR"),
                Collections.singletonList(Collections.singletonList(value)));
    }

    public RedisTabularResult list(String column, List<?> values) {
        List<List<Object>> rows = new ArrayList<>();
        if (values != null) {
            for (Object value : values) {
                rows.add(Collections.singletonList(value));
            }
        }
        return new RedisTabularResult(Collections.singletonList(column), Collections.singletonList("VARCHAR"), rows);
    }

    private RedisTabularResult pairs(String keyColumn, String valueColumn, Object reply) {
        List<List<Object>> rows = new ArrayList<>();
        if (reply instanceof List) {
            List<?> list = (List<?>) reply;
            for (int i = 0; i < list.size(); i += 2) {
                Object key = list.get(i);
                Object value = i + 1 < list.size() ? list.get(i + 1) : null;
                rows.add(Arrays.asList(key, value));
            }
        }
        return new RedisTabularResult(Arrays.asList(keyColumn, valueColumn), Arrays.asList("VARCHAR", "VARCHAR"), rows);
    }

    private RedisTabularResult scan(Object reply) {
        List<List<Object>> rows = new ArrayList<>();
        if (reply instanceof List && !((List<?>) reply).isEmpty()) {
            Object cursor = ((List<?>) reply).get(0);
            for (String key : RedisScanHelper.parseScanKeys(reply)) {
                rows.add(Arrays.asList(cursor, key));
            }
        }
        return new RedisTabularResult(Arrays.asList("cursor", "key"), Arrays.asList("VARCHAR", "VARCHAR"), rows);
    }
}
