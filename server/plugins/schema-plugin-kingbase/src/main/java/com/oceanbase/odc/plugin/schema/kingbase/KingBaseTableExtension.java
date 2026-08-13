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
package com.oceanbase.odc.plugin.schema.kingbase;

import java.sql.Connection;
import java.util.List;
import java.util.Locale;

import org.pf4j.Extension;

import com.oceanbase.odc.common.unit.BinarySizeUnit;
import com.oceanbase.odc.plugin.schema.kingbase.utils.DBAccessorUtil;
import com.oceanbase.odc.plugin.schema.oboracle.OBOracleTableExtension;
import com.oceanbase.tools.dbbrowser.editor.DBTableEditor;
import com.oceanbase.tools.dbbrowser.model.DBObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;
import com.oceanbase.tools.dbbrowser.model.DBTable;
import com.oceanbase.tools.dbbrowser.model.DBTableStats;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;
import com.oceanbase.tools.dbbrowser.stats.DBStatsAccessor;

import lombok.NonNull;

/**
 * Table listing / detail for KingBase oracle mode.
 * <p>
 * KingBase dictionary casing is inconsistent: {@code ALL_TABLES.OWNER} is uppercase while
 * {@code ALL_TAB_COLS.OWNER} is lowercase. Normalize accordingly.
 * </p>
 */
@Extension
public class KingBaseTableExtension extends OBOracleTableExtension {

    @Override
    public List<DBObjectIdentity> list(@NonNull Connection connection, @NonNull String schemaName,
            @NonNull DBObjectType tableType) {
        return super.list(connection, schemaName.toUpperCase(Locale.ROOT), tableType);
    }

    @Override
    public DBTable getDetail(@NonNull Connection connection, @NonNull String schemaName, @NonNull String tableName) {
        DBSchemaAccessor schemaAccessor = getSchemaAccessor(connection);
        String tableOwner = schemaName.toUpperCase(Locale.ROOT);
        String columnOwner = schemaName.toLowerCase(Locale.ROOT);
        DBTable table = new DBTable();
        table.setSchemaName(tableOwner);
        table.setOwner(tableOwner);
        table.setName(tableName);
        table.setColumns(schemaAccessor.listTableColumns(columnOwner, tableName));
        try {
            table.setPartition(schemaAccessor.getPartition(tableOwner, tableName));
        } catch (Exception e) {
            // KingBase dict may lack Oracle partition columns; do not block table page.
        }
        if (!schemaAccessor.isExternalTable(tableOwner, tableName)) {
            try {
                table.setConstraints(schemaAccessor.listTableConstraints(tableOwner, tableName));
            } catch (Exception e) {
                // optional metadata
            }
            try {
                table.setIndexes(schemaAccessor.listTableIndexes(tableOwner, tableName));
            } catch (Exception e) {
                // KingBase ALL_INDEXES may lack VISIBILITY; table data page must still open.
            }
            table.setType(DBObjectType.TABLE);
        } else {
            table.setType(DBObjectType.EXTERNAL_TABLE);
        }
        try {
            table.setDDL(schemaAccessor.getTableDDL(tableOwner, tableName));
        } catch (Exception e) {
            // optional
        }
        try {
            table.setTableOptions(schemaAccessor.getTableOptions(tableOwner, tableName));
        } catch (Exception e) {
            // optional
        }
        try {
            table.setStats(getTableStats(connection, tableOwner, tableName));
        } catch (Exception e) {
            // optional
        }
        return table;
    }

    @Override
    public boolean syncExternalTableFiles(Connection connection, String schemaName, String tableName) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    protected DBTableStats getTableStats(@NonNull Connection connection, @NonNull String schemaName,
            @NonNull String tableName) {
        DBStatsAccessor statsAccessor = getStatsAccessor(connection);
        DBTableStats tableStats = statsAccessor.getTableStats(schemaName, tableName);
        Long dataSizeInBytes = tableStats.getDataSizeInBytes();
        if (dataSizeInBytes == null || dataSizeInBytes < 0) {
            tableStats.setTableSize(null);
        } else {
            tableStats.setTableSize(BinarySizeUnit.B.of(dataSizeInBytes).toString());
        }
        return tableStats;
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
    protected DBTableEditor getTableEditor(Connection connection) {
        return DBAccessorUtil.getTableEditor(connection);
    }

}
