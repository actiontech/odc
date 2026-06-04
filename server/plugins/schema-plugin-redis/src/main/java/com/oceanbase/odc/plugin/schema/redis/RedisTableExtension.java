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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.pf4j.Extension;

import com.oceanbase.odc.plugin.connect.redis.bridge.RedisBridgeUtil;
import com.oceanbase.odc.plugin.schema.api.TableExtensionPoint;
import com.oceanbase.tools.dbbrowser.model.DBObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;
import com.oceanbase.tools.dbbrowser.model.DBTable;
import com.oceanbase.tools.dbbrowser.model.DBTableColumn;

@Extension
public class RedisTableExtension implements TableExtensionPoint {
    @Override
    public List<DBObjectIdentity> list(Connection connection, String schemaName, DBObjectType tableType) {
        List<DBObjectIdentity> result = new ArrayList<>();
        for (String name : showNamesLike(connection, schemaName, "")) {
            result.add(DBObjectIdentity.of(schemaName, DBObjectType.TABLE, name));
        }
        return result;
    }

    @Override
    public List<String> showNamesLike(Connection connection, String schemaName, String tableNameLike) {
        try {
            Object reply = RedisBridgeUtil.requireContext(connection).getClient().command("SCAN", "0", "COUNT", "100");
            Set<String> names = new LinkedHashSet<>();
            if (reply instanceof List && ((List<?>) reply).size() >= 2 && ((List<?>) reply).get(1) instanceof List) {
                for (Object keyObject : (List<?>) ((List<?>) reply).get(1)) {
                    String key = String.valueOf(keyObject);
                    String name = groupName(key);
                    if (tableNameLike == null || tableNameLike.isEmpty() || name.contains(tableNameLike)) {
                        names.add(name);
                    }
                }
            }
            if (names.isEmpty()) {
                names.add("keys");
            }
            return new ArrayList<>(names);
        } catch (Exception e) {
            return Collections.singletonList("keys");
        }
    }

    @Override
    public DBTable getDetail(Connection connection, String schemaName, String tableName) {
        DBTable table = new DBTable();
        table.setName(tableName);
        table.setSchemaName(schemaName);
        table.setOwner(schemaName);
        table.setType(DBObjectType.TABLE);
        List<DBTableColumn> columns = new RedisColumnExtension().columns(schemaName, tableName);
        table.setColumns(columns);
        return table;
    }

    @Override
    public void drop(Connection connection, String schemaName, String tableName) {
        throw new UnsupportedOperationException("Redis plugin does not support drop virtual table");
    }

    @Override
    public String generateCreateDDL(Connection connection, DBTable table) {
        throw new UnsupportedOperationException("Redis plugin does not support create table DDL");
    }

    @Override
    public String generateUpdateDDL(Connection connection, DBTable oldTable, DBTable newTable) {
        throw new UnsupportedOperationException("Redis plugin does not support update table DDL");
    }

    @Override
    public boolean syncExternalTableFiles(Connection connection, String schemaName, String tableName) {
        return false;
    }

    private String groupName(String key) {
        int index = key.indexOf(':');
        if (index > 0) {
            return key.substring(0, index);
        }
        return "keys";
    }
}
