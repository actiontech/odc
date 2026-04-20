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
package com.oceanbase.odc.plugin.schema.dm;

import java.sql.Connection;

import org.pf4j.Extension;

import com.oceanbase.odc.common.unit.BinarySizeUnit;
import com.oceanbase.odc.common.util.JdbcOperationsUtil;
import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.odc.plugin.schema.dm.utils.DBAccessorUtil;
import com.oceanbase.odc.plugin.schema.obmysql.OBMySQLTableExtension;
import com.oceanbase.tools.dbbrowser.DBBrowser;
import com.oceanbase.tools.dbbrowser.editor.DBTableEditor;
import com.oceanbase.tools.dbbrowser.editor.GeneralSqlStatementBuilder;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;
import com.oceanbase.tools.dbbrowser.model.DBTable;
import com.oceanbase.tools.dbbrowser.model.DBTableStats;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;
import com.oceanbase.tools.dbbrowser.stats.DBStatsAccessor;
import com.oceanbase.tools.dbbrowser.util.OracleSqlBuilder;

import lombok.NonNull;

/**
 * Table extension for DM (Dameng) database.
 * <p>
 * Provides table listing, detail retrieval, DDL generation, and table statistics. Inherits from
 * {@link OBMySQLTableExtension} and overrides accessor/editor methods to use DM-specific
 * implementations.
 * </p>
 *
 * @author
 * @since ODC_release_4.3.4
 */
@Extension
public class DmTableExtension extends OBMySQLTableExtension {

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
    public void drop(@NonNull Connection connection, @NonNull String schemaName, @NonNull String tableName) {
        String sql = GeneralSqlStatementBuilder.drop(
                new OracleSqlBuilder(), DBObjectType.TABLE, schemaName, tableName);
        JdbcOperationsUtil.getJdbcOperations(connection).execute(sql);
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
     * Generate table creation DDL without Connection (for logical sessions).
     *
     * @param table table object
     * @return generated DDL statement
     */
    public String generateCreateDDL(@NonNull DBTable table) {
        return DBBrowser.objectEditor().tableEditor()
                .setDbVersion("8.0.0")
                .setType(DialectType.DM.getDBBrowserDialectTypeName())
                .create()
                .generateCreateObjectDDL(table);
    }

    /**
     * Generate table update DDL without Connection (for logical sessions).
     *
     * @param oldTable old table object
     * @param newTable new table object
     * @return generated DDL statement
     */
    public String generateUpdateDDL(@NonNull DBTable oldTable, @NonNull DBTable newTable) {
        return DBBrowser.objectEditor().tableEditor()
                .setDbVersion("8.0.0")
                .setType(DialectType.DM.getDBBrowserDialectTypeName())
                .create()
                .generateUpdateObjectDDL(oldTable, newTable);
    }
}
