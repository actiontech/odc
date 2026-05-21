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
package com.oceanbase.odc.plugin.connect.mongodb.bridge;

import java.util.UUID;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;

public class MongoSessionContext {
    private final MongoClient client;
    private final String connectionId = UUID.randomUUID().toString();
    private final String serverVersion;
    private String currentDatabase;

    public MongoSessionContext(MongoClient client, String currentDatabase, String serverVersion) {
        this.client = client;
        this.currentDatabase = currentDatabase == null || currentDatabase.isEmpty() ? "admin" : currentDatabase;
        this.serverVersion = serverVersion == null || serverVersion.isEmpty() ? "unknown" : serverVersion;
    }

    public MongoClient getClient() {
        return client;
    }

    public MongoDatabase getDatabase() {
        return client.getDatabase(currentDatabase);
    }

    public String getCurrentDatabase() {
        return currentDatabase;
    }

    public void setCurrentDatabase(String currentDatabase) {
        this.currentDatabase = currentDatabase;
    }

    public String getServerVersion() {
        return serverVersion;
    }

    public String getConnectionId() {
        return connectionId;
    }
}
