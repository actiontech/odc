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
package com.oceanbase.odc.plugin.schema.mongodb;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.Document;
import org.pf4j.Extension;

import com.mongodb.client.MongoCollection;
import com.oceanbase.odc.plugin.connect.mongodb.bridge.MongoBridgeUtil;
import com.oceanbase.odc.plugin.schema.api.ColumnExtensionPoint;
import com.oceanbase.tools.dbbrowser.model.DBTableColumn;

@Extension
public class MongoColumnExtension implements ColumnExtensionPoint {
    @Override
    public Map<String, List<DBTableColumn>> listBasicTableColumns(Connection connection, String schemaName) {
        Map<String, List<DBTableColumn>> result = new HashMap<>();
        for (String collectionName : MongoBridgeUtil.requireContext(connection).getClient()
                .getDatabase(schemaName).listCollectionNames()) {
            result.put(collectionName, inferColumns(connection, schemaName, collectionName));
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

    private List<DBTableColumn> inferColumns(Connection connection, String schemaName, String collectionName) {
        Set<String> fields = new LinkedHashSet<>();
        MongoCollection<Document> collection = MongoBridgeUtil.requireContext(connection)
                .getClient().getDatabase(schemaName).getCollection(collectionName);
        Document sample = collection.find().first();
        if (sample != null) {
            fields.addAll(sample.keySet());
        }
        List<DBTableColumn> columns = new ArrayList<>();
        int ordinal = 1;
        for (String field : fields) {
            DBTableColumn column = new DBTableColumn();
            column.setSchemaName(schemaName);
            column.setTableName(collectionName);
            column.setName(field);
            column.setTypeName("document");
            column.setFullTypeName("document");
            column.setNullable(true);
            column.setOrdinalPosition(ordinal++);
            columns.add(column);
        }
        return columns;
    }
}
