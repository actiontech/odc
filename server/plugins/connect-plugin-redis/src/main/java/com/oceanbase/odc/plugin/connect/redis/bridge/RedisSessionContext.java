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

import java.util.UUID;

public class RedisSessionContext {
    private final RedisClient client;
    private final String connectionId = UUID.randomUUID().toString();
    private final String serverVersion;
    private String currentDatabase;

    public RedisSessionContext(RedisClient client, String currentDatabase, String serverVersion) {
        this.client = client;
        this.currentDatabase = currentDatabase == null || currentDatabase.isEmpty() ? "0" : currentDatabase;
        this.serverVersion = serverVersion == null || serverVersion.isEmpty() ? "unknown" : serverVersion;
    }

    public RedisClient getClient() {
        return client;
    }

    public String getCurrentDatabase() {
        return currentDatabase;
    }

    public void setCurrentDatabase(String currentDatabase) {
        this.currentDatabase = currentDatabase == null || currentDatabase.isEmpty() ? "0" : currentDatabase;
    }

    public String getServerVersion() {
        return serverVersion;
    }

    public String getConnectionId() {
        return connectionId;
    }
}
