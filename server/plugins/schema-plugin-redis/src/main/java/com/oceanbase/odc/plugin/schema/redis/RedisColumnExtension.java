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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.pf4j.Extension;

import com.oceanbase.odc.plugin.schema.api.ColumnExtensionPoint;
import com.oceanbase.tools.dbbrowser.model.DBTableColumn;

@Extension
public class RedisColumnExtension implements ColumnExtensionPoint {
    @Override
    public Map<String, List<DBTableColumn>> listBasicTableColumns(Connection connection, String schemaName) {
        Map<String, List<DBTableColumn>> result = new HashMap<>();
        for (String tableName : new RedisTableExtension().showNamesLike(connection, schemaName, "")) {
            result.put(tableName, columns(schemaName, tableName));
        }
        return result;
    }

    @Override
    public Map<String, List<DBTableColumn>> listBasicViewColumns(Connection connection, String schemaName) {
        return new HashMap<>();
    }

    @Override
    public Map<String, List<DBTableColumn>> listBasicColumnsInfo(Connection connection, String schemaName) {
        return listBasicTableColumns(connection, schemaName);
    }

    List<DBTableColumn> columns(String schemaName, String tableName) {
        return Arrays.asList(column(schemaName, tableName, "key_name", 1), column(schemaName, tableName, "key_type", 2),
                column(schemaName, tableName, "value_preview", 3), column(schemaName, tableName, "ttl", 4));
    }

    private DBTableColumn column(String schemaName, String tableName, String name, int ordinal) {
        DBTableColumn column = new DBTableColumn();
        column.setSchemaName(schemaName);
        column.setTableName(tableName);
        column.setName(name);
        column.setTypeName("VARCHAR");
        column.setFullTypeName("VARCHAR");
        column.setNullable(true);
        column.setOrdinalPosition(ordinal);
        return column;
    }
}
