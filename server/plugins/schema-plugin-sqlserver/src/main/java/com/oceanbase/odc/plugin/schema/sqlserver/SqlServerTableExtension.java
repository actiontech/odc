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
package com.oceanbase.odc.plugin.schema.sqlserver;

import java.sql.Connection;

import org.pf4j.Extension;

import com.oceanbase.odc.common.unit.BinarySizeUnit;
import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.odc.plugin.schema.obmysql.OBMySQLTableExtension;
import com.oceanbase.odc.plugin.schema.sqlserver.utils.DBAccessorUtil;
import com.oceanbase.tools.dbbrowser.DBBrowser;
import com.oceanbase.tools.dbbrowser.editor.DBTableEditor;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;
import com.oceanbase.tools.dbbrowser.model.DBTable;
import com.oceanbase.tools.dbbrowser.model.DBTableStats;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;
import com.oceanbase.tools.dbbrowser.stats.DBStatsAccessor;

import lombok.NonNull;

@Extension
public class SqlServerTableExtension extends OBMySQLTableExtension {

    @Override
    public DBTable getDetail(@NonNull Connection connection, @NonNull String schemaName, @NonNull String tableName) {
        DBSchemaAccessor schemaAccessor = getSchemaAccessor(connection);
        DBTable table = new DBTable();
        table.setSchemaName(schemaName);
        table.setOwner(schemaName);
        table.setName(tableName);
        table.setColumns(schemaAccessor.listTableColumns(schemaName, tableName));
        table.setPartition(schemaAccessor.getPartition(schemaName, tableName));
        if (!schemaAccessor.isExternalTable(schemaName, tableName)) {
            table.setConstraints(schemaAccessor.listTableConstraints(schemaName, tableName));
            table.setIndexes(schemaAccessor.listTableIndexes(schemaName, tableName));
            table.setType(DBObjectType.TABLE);
        } else {
            table.setType(DBObjectType.EXTERNAL_TABLE);
        }
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
        // SQL Server 的 statsAccessor 目前返回 null，需要处理这种情况
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
        throw new UnsupportedOperationException("not implemented yet");
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
     * 生成表创建DDL（不需要Connection，适用于逻辑会话） 参考 OBOracleSequenceExtension 的实现模式
     *
     * @param table 表对象
     * @return 生成的DDL语句
     */
    public String generateCreateDDL(@NonNull DBTable table) {
        return DBBrowser.objectEditor().tableEditor()
                .setDbVersion("4.0.0")
                .setType(DialectType.SQL_SERVER.getDBBrowserDialectTypeName())
                .create()
                .generateCreateObjectDDL(table);
    }

    /**
     * 生成表更新DDL（不需要Connection，适用于逻辑会话） 参考 OBOracleSequenceExtension 的实现模式
     *
     * @param oldTable 修改前的表对象
     * @param newTable 修改后的表对象
     * @return 生成的DDL语句
     */
    public String generateUpdateDDL(@NonNull DBTable oldTable, @NonNull DBTable newTable) {
        return DBBrowser.objectEditor().tableEditor()
                .setDbVersion("4.0.0")
                .setType(DialectType.SQL_SERVER.getDBBrowserDialectTypeName())
                .create()
                .generateUpdateObjectDDL(oldTable, newTable);
    }
}
