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
package com.oceanbase.odc.plugin.schema.gaussdb;

import java.sql.Connection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.pf4j.Extension;

import com.oceanbase.odc.core.shared.constant.OdcConstants;
import com.oceanbase.odc.plugin.schema.gaussdb.utils.DBAccessorUtil;
import com.oceanbase.odc.plugin.schema.obmysql.OBMySQLTableExtension;
import com.oceanbase.tools.dbbrowser.model.DBObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;
import com.oceanbase.tools.dbbrowser.model.DBTable;
import com.oceanbase.tools.dbbrowser.model.DBTable.DBTableOptions;
import com.oceanbase.tools.dbbrowser.model.DBTableStats;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;

import lombok.NonNull;

/**
 * GaussDB table extension. Delegates the actual schema-accessor wiring to {@link DBAccessorUtil},
 * which internally pins the db-browser type to {@code POSTGRESQL} (see DBAccessorUtil javadoc for
 * rationale).
 *
 * <p>
 * This class also overrides {@link #list}, {@link #showNamesLike}, {@link #getDetail} and
 * {@link #syncExternalTableFiles} to avoid the parent's OB-MySQL specific code paths (e.g.
 * {@code showExternalTables} + {@code OBMySQLGetDBTableByParser}) which fail on PostgreSQL-family
 * databases where db-browser:1.2.3 has gaps in its {@code PostgresSchemaAccessor} implementation.
 */
@Extension
public class GaussDBTableExtension extends OBMySQLTableExtension {

    @Override
    protected DBSchemaAccessor getSchemaAccessor(Connection connection) {
        return DBAccessorUtil.getSchemaAccessor(connection);
    }

    /**
     * Override the parent's {@link OBMySQLTableExtension#list} so we never call
     * {@code schemaAccessor.showExternalTables(schemaName)} for the {@code EXTERNAL_TABLE} branch
     * (which is unsupported by {@code PostgresSchemaAccessor} in db-browser:1.2.3 and would throw
     * {@link UnsupportedOperationException}, causing the workbench metadata/identities API to return
     * HTTP 500). GaussDB does not currently expose foreign tables as ODC external tables; returning
     * {@link Collections#emptyList()} for {@code EXTERNAL_TABLE} is the correct semantic here and
     * matches the contract of {@link #syncExternalTableFiles}.
     */
    @Override
    public List<DBObjectIdentity> list(@NonNull Connection connection, @NonNull String schemaName,
            @NonNull DBObjectType tableType) {
        switch (tableType) {
            case TABLE:
                List<String> tableNames = getSchemaAccessor(connection).showTables(schemaName).stream()
                        .filter(name -> !StringUtils.startsWithIgnoreCase(name, OdcConstants.CONTAINER_TABLE_PREFIX)
                                && !StringUtils.startsWithIgnoreCase(name, OdcConstants.MATERIALIZED_VIEW_LOG_PREFIX))
                        .collect(Collectors.toList());
                return tableNames.stream().map(name -> {
                    DBObjectIdentity identity = new DBObjectIdentity();
                    identity.setType(DBObjectType.TABLE);
                    identity.setSchemaName(schemaName);
                    identity.setName(name);
                    return identity;
                }).collect(Collectors.toList());
            case EXTERNAL_TABLE:
                // GaussDB exposes foreign tables via pg_foreign_table, but they are not modelled
                // as ODC external tables today; return empty to keep the workbench metadata API
                // happy without breaking the contract.
                return Collections.emptyList();
            default:
                throw new IllegalArgumentException("Unsupported table type: " + tableType);
        }
    }

    /**
     * GaussDB uses information_schema-based table queries. The parent's implementation calls
     * {@code schemaAccessor.showTablesLike}, which is currently unimplemented in
     * {@code PostgresSchemaAccessor}. We provide a local fallback that uses {@link #list} + client-side
     * LIKE matching to keep the search/autocomplete features working.
     */
    @Override
    public List<String> showNamesLike(@NonNull Connection connection, @NonNull String schemaName,
            @NonNull String tableNameLike) {
        List<DBObjectIdentity> all = list(connection, schemaName, DBObjectType.TABLE);
        if (StringUtils.isBlank(tableNameLike)) {
            return all.stream().map(DBObjectIdentity::getName).collect(Collectors.toList());
        }
        // Convert SQL LIKE wildcards (%, _) into a regex for client-side filtering.
        String pattern = "^" + tableNameLike.replace(".", "\\.")
                .replace("%", ".*")
                .replace("_", ".") + "$";
        return all.stream().map(DBObjectIdentity::getName)
                .filter(name -> name.matches(pattern))
                .collect(Collectors.toList());
    }

    /**
     * Override {@link OBMySQLTableExtension#getDetail} because the parent's MySQL DDL parser
     * ({@code OBMySQLGetDBTableByParser}) cannot understand PostgreSQL-family DDL, and several of the
     * helper queries it routes through {@code PostgresSchemaAccessor}
     * ({@code getTableDDL}/{@code listTableColumns}/{@code listTableConstraints}/
     * {@code listTableIndexes}/{@code getTableOptions}) throw {@link UnsupportedOperationException} in
     * db-browser:1.2.3.
     *
     * <p>
     * Here we read columns / indexes / constraints via {@code information_schema} + {@code pg_catalog}
     * directly through {@link DBAccessorUtil}'s extended accessor, and we leave partition / DDL
     * synthesis to a future iteration (set to {@code null} / empty so the JSON response is well-formed
     * and the UI renders the table even when DDL is unavailable).
     */
    @Override
    public DBTable getDetail(@NonNull Connection connection, @NonNull String schemaName,
            @NonNull String tableName) {
        DBSchemaAccessor schemaAccessor = getSchemaAccessor(connection);
        DBTable table = new DBTable();
        table.setSchemaName(schemaName);
        table.setOwner(schemaName);
        table.setName(tableName);
        table.setType(DBObjectType.TABLE);
        table.setColumns(schemaAccessor.listTableColumns(schemaName, tableName));
        try {
            table.setConstraints(schemaAccessor.listTableConstraints(schemaName, tableName));
        } catch (UnsupportedOperationException e) {
            table.setConstraints(Collections.emptyList());
        }
        try {
            table.setIndexes(schemaAccessor.listTableIndexes(schemaName, tableName));
        } catch (UnsupportedOperationException e) {
            table.setIndexes(Collections.emptyList());
        }
        try {
            table.setDDL(schemaAccessor.getTableDDL(schemaName, tableName));
        } catch (UnsupportedOperationException e) {
            table.setDDL(null);
        }
        try {
            table.setTableOptions(schemaAccessor.getTableOptions(schemaName, tableName));
        } catch (UnsupportedOperationException e) {
            // DBTableOptions has no schemaName slot; the owning DBTable already carries it.
            table.setTableOptions(new DBTableOptions());
        }
        try {
            table.setStats(getTableStats(connection, schemaName, tableName));
        } catch (Exception e) {
            DBTableStats stats = new DBTableStats();
            table.setStats(stats);
        }
        try {
            table.setColumnGroups(schemaAccessor.listTableColumnGroups(schemaName, tableName));
        } catch (Exception e) {
            // eat the exception - column groups are an OB-specific concept
        }
        return table;
    }

    /**
     * GaussDB does not support external tables in the ODC sense. Return {@code false} so the workbench
     * can degrade gracefully instead of bubbling an HTTP 500 to the UI.
     */
    @Override
    public boolean syncExternalTableFiles(Connection connection, String schemaName, String tableName) {
        return false;
    }
}
