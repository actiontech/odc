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
import java.util.List;

import org.pf4j.Extension;

import com.mongodb.client.MongoDatabase;
import com.oceanbase.odc.plugin.connect.mongodb.bridge.MongoBridgeUtil;
import com.oceanbase.odc.plugin.schema.api.TableExtensionPoint;
import com.oceanbase.tools.dbbrowser.model.DBObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;
import com.oceanbase.tools.dbbrowser.model.DBTable;
import com.oceanbase.tools.dbbrowser.model.DBTableColumn;

@Extension
public class MongoTableExtension implements TableExtensionPoint {
    @Override
    public List<DBObjectIdentity> list(Connection connection, String schemaName, DBObjectType tableType) {
        List<DBObjectIdentity> result = new ArrayList<>();
        MongoDatabase db = MongoBridgeUtil.requireContext(connection).getClient().getDatabase(schemaName);
        for (String name : db.listCollectionNames()) {
            result.add(DBObjectIdentity.of(schemaName, DBObjectType.TABLE, name));
        }
        return result;
    }

    @Override
    public List<String> showNamesLike(Connection connection, String schemaName, String tableNameLike) {
        List<String> result = new ArrayList<>();
        for (DBObjectIdentity identity : list(connection, schemaName, DBObjectType.TABLE)) {
            if (identity.getName().contains(tableNameLike)) {
                result.add(identity.getName());
            }
        }
        return result;
    }

    @Override
    public DBTable getDetail(Connection connection, String schemaName, String tableName) {
        DBTable table = new DBTable();
        table.setName(tableName);
        table.setSchemaName(schemaName);
        table.setOwner(schemaName);
        table.setType(DBObjectType.TABLE);
        List<DBTableColumn> columns = new MongoColumnExtension()
                .listBasicTableColumns(connection, schemaName).get(tableName);
        table.setColumns(columns != null ? columns : new ArrayList<>());
        return table;
    }

    @Override
    public void drop(Connection connection, String schemaName, String tableName) {
        throw new UnsupportedOperationException("MongoDB plugin does not support drop collection from ODC");
    }

    @Override
    public String generateCreateDDL(Connection connection, DBTable table) {
        throw new UnsupportedOperationException("MongoDB plugin does not support relational DDL generation");
    }

    @Override
    public String generateUpdateDDL(Connection connection, DBTable oldTable, DBTable newTable) {
        throw new UnsupportedOperationException("MongoDB plugin does not support relational DDL generation");
    }

    @Override
    public boolean syncExternalTableFiles(Connection connection, String schemaName, String tableName) {
        return false;
    }
}
