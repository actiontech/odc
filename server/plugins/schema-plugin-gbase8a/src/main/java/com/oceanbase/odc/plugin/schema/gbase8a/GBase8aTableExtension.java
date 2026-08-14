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
package com.oceanbase.odc.plugin.schema.gbase8a;

import java.sql.Connection;
import java.util.Collections;

import org.pf4j.Extension;

import com.oceanbase.odc.plugin.schema.gbase8a.utils.DBAccessorUtil;
import com.oceanbase.odc.plugin.schema.mysql.MySQLTableExtension;
import com.oceanbase.tools.dbbrowser.editor.DBTableEditor;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;
import com.oceanbase.tools.dbbrowser.model.DBTable;
import com.oceanbase.tools.dbbrowser.model.DBTablePartition;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;
import com.oceanbase.tools.dbbrowser.stats.DBStatsAccessor;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Extension
public class GBase8aTableExtension extends MySQLTableExtension {

    @Override
    public DBTable getDetail(@NonNull Connection connection, @NonNull String schemaName, @NonNull String tableName) {
        DBSchemaAccessor schemaAccessor = getSchemaAccessor(connection);
        DBTable table = new DBTable();
        table.setSchemaName(schemaName);
        table.setOwner(schemaName);
        table.setName(tableName);
        table.setColumns(schemaAccessor.listTableColumns(schemaName, tableName));
        table.setType(DBObjectType.TABLE);
        // Partition SQL from MySQL 5.7 uses any_value() which GBase rejects; keep empty partition.
        table.setPartition(emptyPartition(schemaName, tableName));
        try {
            if (!schemaAccessor.isExternalTable(schemaName, tableName)) {
                table.setConstraints(schemaAccessor.listTableConstraints(schemaName, tableName));
                table.setIndexes(schemaAccessor.listTableIndexes(schemaName, tableName));
            } else {
                table.setType(DBObjectType.EXTERNAL_TABLE);
            }
        } catch (Exception e) {
            log.warn("Skip constraints/indexes for GBase-8a table {}.{}, reason={}", schemaName, tableName,
                    e.getMessage());
            table.setConstraints(Collections.emptyList());
            table.setIndexes(Collections.emptyList());
        }
        try {
            table.setDDL(schemaAccessor.getTableDDL(schemaName, tableName));
        } catch (Exception e) {
            log.warn("Skip DDL for GBase-8a table {}.{}, reason={}", schemaName, tableName, e.getMessage());
        }
        try {
            table.setTableOptions(schemaAccessor.getTableOptions(schemaName, tableName));
        } catch (Exception e) {
            log.warn("Skip table options for GBase-8a table {}.{}, reason={}", schemaName, tableName, e.getMessage());
        }
        try {
            table.setStats(getTableStats(connection, schemaName, tableName));
        } catch (Exception e) {
            log.warn("Skip stats for GBase-8a table {}.{}, reason={}", schemaName, tableName, e.getMessage());
        }
        return table;
    }

    private static DBTablePartition emptyPartition(String schemaName, String tableName) {
        DBTablePartition partition = new DBTablePartition();
        partition.setSchemaName(schemaName);
        partition.setTableName(tableName);
        partition.setPartitionDefinitions(Collections.emptyList());
        return partition;
    }

    @Override
    protected DBSchemaAccessor getSchemaAccessor(Connection connection) {
        return DBAccessorUtil.getSchemaAccessor(connection);
    }

    @Override
    protected DBStatsAccessor getStatsAccessor(Connection connection) {
        return DBAccessorUtil.getStatsAccessor(connection);
    }

    @Override
    protected DBTableEditor getTableEditor(@NonNull Connection connection) {
        return DBAccessorUtil.getTableEditor(connection);
    }
}
