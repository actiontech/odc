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
package com.oceanbase.odc.plugin.schema.redis;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import org.pf4j.Extension;

import com.oceanbase.odc.plugin.connect.redis.bridge.RedisBridgeUtil;
import com.oceanbase.odc.plugin.connect.redis.bridge.RedisClient;
import com.oceanbase.odc.plugin.schema.api.DatabaseExtensionPoint;
import com.oceanbase.tools.dbbrowser.model.DBDatabase;
import com.oceanbase.tools.dbbrowser.model.DBObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;

@Extension
public class RedisDatabaseExtension implements DatabaseExtensionPoint {
    private static final int DEFAULT_DATABASE_COUNT = 16;

    @Override
    public List<DBObjectIdentity> list(Connection connection) {
        List<DBObjectIdentity> result = new ArrayList<>();
        for (int index = 0; index < resolveDatabaseCount(connection); index++) {
            String name = String.valueOf(index);
            result.add(DBObjectIdentity.of(name, DBObjectType.DATABASE, name));
        }
        return result;
    }

    @Override
    public DBDatabase getDetail(Connection connection, String dbName) {
        return DBDatabase.of(dbName == null ? RedisBridgeUtil.requireContext(connection).getCurrentDatabase() : dbName);
    }

    @Override
    public List<DBDatabase> listDetails(Connection connection) {
        List<DBDatabase> result = new ArrayList<>();
        for (DBObjectIdentity identity : list(connection)) {
            result.add(DBDatabase.of(identity.getName()));
        }
        return result;
    }

    @Override
    public void create(Connection connection, DBDatabase database, String password) {
        throw new UnsupportedOperationException("Redis plugin does not support create database from ODC");
    }

    static int resolveDatabaseCount(Connection connection) {
        try {
            RedisClient client = RedisBridgeUtil.requireContext(connection).getClient();
            Object reply = client.command("CONFIG", "GET", "databases");
            if (reply instanceof List) {
                List<?> values = (List<?>) reply;
                if (values.size() >= 2) {
                    int count = Integer.parseInt(String.valueOf(values.get(1)));
                    if (count > 0) {
                        return count;
                    }
                }
            }
        } catch (Exception ignored) {
            // fall back to Redis default logical database count
        }
        return DEFAULT_DATABASE_COUNT;
    }
}
