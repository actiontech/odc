/*
 * Copyright (c) 2024 OceanBase.
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
package com.oceanbase.odc.plugin.schema.hive;

import java.sql.Connection;

import org.pf4j.Extension;

import com.oceanbase.odc.common.unit.BinarySizeUnit;
import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.odc.plugin.schema.hive.utils.DBAccessorUtil;
import com.oceanbase.odc.plugin.schema.obmysql.OBMySQLTableExtension;
import com.oceanbase.tools.dbbrowser.DBBrowser;
import com.oceanbase.tools.dbbrowser.editor.DBTableEditor;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;
import com.oceanbase.tools.dbbrowser.model.DBTable;
import com.oceanbase.tools.dbbrowser.model.DBTableStats;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;
import com.oceanbase.tools.dbbrowser.stats.DBStatsAccessor;

import lombok.NonNull;

/**
 * Hive table extension. Delegates metadata browsing to the Hive schema accessor
 * and table editing to the Hive table editor.
 *
 * @since ODC_release_4.3.4
 */
@Extension
public class HiveTableExtension extends OBMySQLTableExtension {

    @Override
    public DBTable getDetail(@NonNull Connection connection, @NonNull String schemaName, @NonNull String tableName) {
        DBSchemaAccessor schemaAccessor = getSchemaAccessor(connection);
        DBTable table = new DBTable();
        table.setSchemaName(schemaName);
        table.setOwner(schemaName);
        table.setName(tableName);
        table.setColumns(schemaAccessor.listTableColumns(schemaName, tableName));
        table.setPartition(schemaAccessor.getPartition(schemaName, tableName));
        // Hive does not support constraints or indexes
        table.setType(DBObjectType.TABLE);
        table.setDDL(schemaAccessor.getTableDDL(schemaName, tableName));
        table.setTableOptions(schemaAccessor.getTableOptions(schemaName, tableName));
        table.setStats(getTableStats(connection, schemaName, tableName));
        return table;
    }

    @Override
    protected DBTableEditor getTableEditor(@NonNull Connection connection) {
        return DBAccessorUtil.getTableEditor(connection);
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
    protected DBTableStats getTableStats(@NonNull Connection connection, @NonNull String schemaName,
            @NonNull String tableName) {
        DBStatsAccessor statsAccessor = getStatsAccessor(connection);
        if (statsAccessor == null) {
            return new DBTableStats();
        }
        DBTableStats tableStats = statsAccessor.getTableStats(schemaName, tableName);
        if (tableStats == null) {
            return new DBTableStats();
        }
        Long dataSizeInBytes = tableStats.getDataSizeInBytes();
        if (dataSizeInBytes == null || dataSizeInBytes < 0) {
            tableStats.setTableSize(null);
        } else {
            tableStats.setTableSize(BinarySizeUnit.B.of(dataSizeInBytes).toString());
        }
        return tableStats;
    }

    @Override
    public boolean syncExternalTableFiles(Connection connection, String schemaName, String tableName) {
        throw new UnsupportedOperationException("Hive does not support external table file sync via this interface");
    }

    @Override
    public String generateCreateDDL(@NonNull Connection connection, @NonNull DBTable table) {
        return getTableEditor(connection).generateCreateObjectDDL(table);
    }

    @Override
    public String generateUpdateDDL(@NonNull Connection connection, @NonNull DBTable oldTable,
            @NonNull DBTable newTable) {
        return getTableEditor(connection).generateUpdateObjectDDL(oldTable, newTable);
    }

    /**
     * Generate table creation DDL without a live Connection (for logical sessions).
     */
    public String generateCreateDDL(@NonNull DBTable table) {
        return DBBrowser.objectEditor().tableEditor()
                .setDbVersion("4.0.0")
                .setType(DialectType.HIVE.getDBBrowserDialectTypeName())
                .create()
                .generateCreateObjectDDL(table);
    }

    /**
     * Generate table update DDL without a live Connection (for logical sessions).
     */
    public String generateUpdateDDL(@NonNull DBTable oldTable, @NonNull DBTable newTable) {
        return DBBrowser.objectEditor().tableEditor()
                .setDbVersion("4.0.0")
                .setType(DialectType.HIVE.getDBBrowserDialectTypeName())
                .create()
                .generateUpdateObjectDDL(oldTable, newTable);
    }
}
