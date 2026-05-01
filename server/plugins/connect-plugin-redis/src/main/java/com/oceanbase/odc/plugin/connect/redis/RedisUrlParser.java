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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.oceanbase.odc.plugin.connect.api.HostAddress;
import com.oceanbase.odc.plugin.connect.api.JdbcUrlParser;

/**
 * Parser for redis:// URLs.
 */
public class RedisUrlParser implements JdbcUrlParser {

    private final String host;
    private final int port;

    public RedisUrlParser(String redisUrl) {
        if (redisUrl != null && redisUrl.startsWith("redis://")) {
            String hostPort = redisUrl.substring("redis://".length());
            String[] parts = hostPort.split(":");
            this.host = parts[0];
            if (parts.length > 1) {
                this.port = Integer.parseInt(parts[1].replaceAll("/.*", ""));
            } else {
                this.port = 6379;
            }
        } else {
            this.host = "localhost";
            this.port = 6379;
        }
    }

    @Override
    public List<HostAddress> getHostAddresses() {
        return Collections.singletonList(new HostAddress(host, port));
    }

    @Override
    public String getSchema() {
        return null;
    }

    @Override
    public Map<String, Object> getParameters() {
        return Collections.emptyMap();
    }
}
