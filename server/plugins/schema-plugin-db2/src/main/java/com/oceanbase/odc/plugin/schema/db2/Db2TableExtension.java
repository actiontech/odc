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
package com.oceanbase.odc.plugin.schema.db2;

import java.sql.Connection;

import org.pf4j.Extension;

import com.oceanbase.odc.common.unit.BinarySizeUnit;
import com.oceanbase.odc.common.util.JdbcOperationsUtil;
import com.oceanbase.odc.plugin.schema.db2.utils.DBAccessorUtil;
import com.oceanbase.odc.plugin.schema.obmysql.OBMySQLTableExtension;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;
import com.oceanbase.tools.dbbrowser.model.DBTable;
import com.oceanbase.tools.dbbrowser.model.DBTableStats;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;
import com.oceanbase.tools.dbbrowser.stats.DBStatsAccessor;
import com.oceanbase.tools.dbbrowser.stats.db2.Db2StatsAccessor;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * DB2 table extension.
 *
 * <p>
 * Inherits from OB-MySQL extension to reuse the operator/editor/listing paths that don't depend on
 * OB-specific SQL, and overrides:
 *
 * <ol>
 * <li>{@link #getSchemaAccessor(Connection)} — routes to the DB2 dialect schema accessor (initially
 * added by feat-839 commit-A; see {@code Db2SchemaAccessor}).
 * <li>{@link #getStatsAccessor(Connection)} — fix-H bug D-2 (table-detail 500): the inherited
 * OB-MySQL path calls {@code OBUtils.getObVersion(connection)} → {@code "show variables like
 * 'version_comment'"} which DB2 jcc rejects with {@code ERRORCODE=-4476 (executeQuery used for
 * update)}, collapsing the whole 5-tab table-detail page. The DB2 stats accessor takes no version
 * and bypasses OBUtils entirely. See {@code Db2StatsAccessor}.
 * <li>{@link #getDetail(Connection, String, String)} — fix-H bug D-2: the inherited implementation
 * feeds {@code OBMySQLGetDBTableByParser} the result of {@code getTableDDL} and also calls
 * {@code listTableColumnGroups}; for DB2 our schema accessor returns an empty DDL string (db2look
 * out-of-scope per design.md §6) and the MySQL DDL parser would mis-treat that as an invalid
 * statement. Re-assemble {@link DBTable} from the four DB2-native accessor calls so the
 * table-detail page renders columns/constraints/indexes/stats without depending on a MySQL DDL
 * parser.
 * </ol>
 *
 * @since ODC_release_4.3.4 (Issue dms-ee#839, fix-H)
 */
@Slf4j
@Extension
public class Db2TableExtension extends OBMySQLTableExtension {

    @Override
    protected DBSchemaAccessor getSchemaAccessor(Connection connection) {
        return DBAccessorUtil.getSchemaAccessor(connection);
    }

    @Override
    protected DBStatsAccessor getStatsAccessor(Connection connection) {
        // fix-H bug D-2: bypass DBAccessorUtil#getStatsAccessor (OB-MySQL plugin) — its
        // getDbVersion() runs `show variables like 'version_comment'` which DB2 jcc rejects
        // with ERRORCODE=-4476 (executeQuery used for update). The DB2 stats accessor doesn't
        // need a version probe; instantiate it directly against the live JdbcOperations.
        return new Db2StatsAccessor(JdbcOperationsUtil.getJdbcOperations(connection));
    }

    @Override
    public DBTable getDetail(@NonNull Connection connection, @NonNull String schemaName,
            @NonNull String tableName) {
        // fix-H bug D-2: re-implement the aggregator for DB2 because the inherited OB-MySQL
        // version invokes the MySQL-dialect DDL parser on a string that DB2 cannot supply
        // (Db2SchemaAccessor#getTableDDL returns "" by design — db2look is out of scope per
        // design.md §6) and also probes for materialized-view columns groups which DB2 doesn't
        // expose. Build the same DBTable payload from four DB2-native accessor calls so the
        // table-detail 5 tabs (列 / 索引 / 约束 / DDL / 触发器) get real data.
        DBSchemaAccessor schemaAccessor = getSchemaAccessor(connection);

        DBTable table = new DBTable();
        table.setSchemaName(schemaName);
        table.setOwner(schemaName);
        table.setName(tableName);
        table.setColumns(schemaAccessor.listTableColumns(schemaName, tableName));
        table.setConstraints(schemaAccessor.listTableConstraints(schemaName, tableName));
        table.setIndexes(schemaAccessor.listTableIndexes(schemaName, tableName));
        table.setType(DBObjectType.TABLE);
        table.setPartition(null);
        table.setDDL(schemaAccessor.getTableDDL(schemaName, tableName));
        table.setTableOptions(schemaAccessor.getTableOptions(schemaName, tableName));
        table.setStats(getDb2TableStats(connection, schemaName, tableName));
        return table;
    }

    private DBTableStats getDb2TableStats(@NonNull Connection connection, @NonNull String schemaName,
            @NonNull String tableName) {
        // Mirrors the inherited getTableStats() but uses the DB2 stats accessor; defensive try/catch
        // because SYSCAT.TABLES CARD/NPAGES can legitimately return -1 on freshly created tables that
        // haven't been RUNSTATS'd yet and we don't want the page to 500 over a stats glitch.
        try {
            DBStatsAccessor statsAccessor = getStatsAccessor(connection);
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
        } catch (Exception e) {
            log.warn("DB2 getTableStats failed for {}.{}, returning empty stats", schemaName, tableName, e);
            return new DBTableStats();
        }
    }

    @Override
    public boolean syncExternalTableFiles(Connection connection, String schemaName, String tableName) {
        // DB2 has no external-table support in this release. Return false instead of throwing so the
        // upstream sync flow doesn't 500.
        return false;
    }
}
