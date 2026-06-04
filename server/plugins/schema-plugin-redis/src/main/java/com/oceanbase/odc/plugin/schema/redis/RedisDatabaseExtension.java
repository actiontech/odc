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
import com.oceanbase.odc.plugin.schema.api.DatabaseExtensionPoint;
import com.oceanbase.tools.dbbrowser.model.DBDatabase;
import com.oceanbase.tools.dbbrowser.model.DBObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;

@Extension
public class RedisDatabaseExtension implements DatabaseExtensionPoint {
    @Override
    public List<DBObjectIdentity> list(Connection connection) {
        List<DBObjectIdentity> result = new ArrayList<>();
        result.add(DBObjectIdentity.of("0", DBObjectType.DATABASE, "0"));
        String current = RedisBridgeUtil.requireContext(connection).getCurrentDatabase();
        if (!"0".equals(current)) {
            result.add(DBObjectIdentity.of(current, DBObjectType.DATABASE, current));
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
}
