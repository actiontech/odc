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
package com.oceanbase.odc.service.db.browser;

import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.JdbcOperations;

import com.oceanbase.odc.common.util.VersionUtils;
import com.oceanbase.odc.core.session.ConnectionSession;
import com.oceanbase.odc.core.session.ConnectionSessionConstants;
import com.oceanbase.odc.core.session.ConnectionSessionUtil;
import com.oceanbase.odc.core.shared.PreConditions;
import com.oceanbase.odc.core.shared.constant.ConnectType;
import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.odc.core.sql.execute.SyncJdbcExecutor;
import com.oceanbase.tools.dbbrowser.DBBrowser;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessorFactory;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DBSchemaAccessors {

    public static DBSchemaAccessor create(ConnectionSession connectionSession) {
        return create(connectionSession, ConnectionSessionConstants.CONSOLE_DS_KEY);
    }

    public static DBSchemaAccessor create(ConnectionSession connectionSession, String dataSourceName) {
        PreConditions.notNull(connectionSession, "connectionSession");

        ConnectType connectType = connectionSession.getConnectType();
        SyncJdbcExecutor syncJdbcExecutor =
                connectionSession.getSyncJdbcExecutor(dataSourceName);
        PreConditions.notNull(connectType, "connectType");
        PreConditions.notNull(syncJdbcExecutor, "syncJdbcExecutor");
        String dbVersion = ConnectionSessionUtil.getVersion(connectionSession);
        PreConditions.notNull(dbVersion, "obVersion");

        SyncJdbcExecutor sysSyncJdbcExecutor = null;
        String tenantName = null;
        if (VersionUtils.isGreaterThanOrEqualsTo(dbVersion, "1.4.79")) {
            try {
                sysSyncJdbcExecutor =
                        connectionSession.getSyncJdbcExecutor(ConnectionSessionConstants.SYS_DS_KEY);
                tenantName = ConnectionSessionUtil.getTenantName(connectionSession);
            } catch (Exception e) {
                log.warn("Get SYS-DATASOURCE failed, may lack of sys tenant permission，message={}", e.getMessage());
            }
        }

        return create(syncJdbcExecutor, sysSyncJdbcExecutor, connectType, dbVersion, tenantName);
    }

    public static DBSchemaAccessor create(@NonNull JdbcOperations syncJdbcExecutor, JdbcOperations sysJdbcExecutor,
            @NonNull ConnectType connectType, @NonNull String dbVersion, String tenantName) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(DBSchemaAccessorFactory.TENANT_NAME_KEY, tenantName);
        properties.put(DBSchemaAccessorFactory.SYS_OPERATIONS_KEY, sysJdbcExecutor);
        return DBBrowser.schemaAccessor()
                .setProperties(properties)
                .setDbVersion(dbVersion)
                .setJdbcOperations(syncJdbcExecutor)
                .setType(toDbBrowserType(connectType.getDialectType()))
                .create();
    }

    /**
     * Resolve the dialect string consumed by {@code db-browser:1.2.3}'s
     * {@code AbstractDBBrowserFactory#create} switch table, which only recognises a fixed set of 10
     * strings (ORACLE / MYSQL / DORIS / TIDB / OB_ORACLE / OB_MYSQL / ODP_SHARDING_OB_MYSQL /
     * POSTGRESQL / SQL_SERVER / DM / …). The raw {@link DialectType#name()} for GAUSSDB / GBASE_8A is
     * not in that set and triggers {@code IllegalStateException: "Not supported for the type, …"} at
     * {@code AbstractDBBrowserFactory.java:61}.
     * <p>
     * GaussDB and openGauss both speak the PG wire protocol and the
     * {@link com.oceanbase.tools.dbbrowser.schema.postgre.PostgresSchemaAccessor} only uses standard
     * {@code information_schema} / {@code pg_catalog} queries that have been verified to work on both
     * products (see gsclient probes on 122.9.71.90:8000 GaussDB commercial and 10.186.16.126:5432
     * openGauss in docs/test/screenshots/task-004-fix-2/). We therefore route GAUSSDB to the POSTGRESQL
     * branch here, keeping {@link DialectType#getDBBrowserDialectTypeName} itself unchanged (so plugin
     * routing / extension registry / existing DialectTypeTest assertions are not affected).
     * <p>
     * GBase-8a speaks MySQL wire protocol; route to MYSQL so console session open
     * ({@code DatasourceColumnAccessor}) succeeds. Full object-tree schema plugin remains AC-6.
     * <p>
     * Scope of this hack is the SchemaAccessor only because case 2.2.1 / 2.2.2 of Task-004-FIX-2
     * unblocks tables view by routing this single factory; the other seven db-browser factory facades
     * ({@code DBTableEditors}, {@code DBTableColumnEditors}, {@code DBTableIndexEditors},
     * {@code DBTableConstraintEditors}, {@code DBObjectOperators}, {@code DBStatsAccessors},
     * {@code DBTableService}) still pass GAUSSDB through and will surface as feature-scope
     * UnsupportedOperationException at the time the corresponding REQ-3 / REQ-4 cases are exercised.
     * Add identical routing helpers when those paths become blockers.
     */
    private static String toDbBrowserType(DialectType dialectType) {
        if (dialectType == DialectType.GAUSSDB) {
            return DialectType.POSTGRESQL.getDBBrowserDialectTypeName();
        }
        // KingBase (oracle mode): db-browser has no KINGBASE branch; reuse Oracle schema accessor
        // SQL (dual / all_*/dba_* style). JDBC remains kingbase8 via connect-plugin-kingbase.
        if (dialectType == DialectType.KINGBASE) {
            return DialectType.ORACLE.getDBBrowserDialectTypeName();
        }
        // GBase-8a speaks MySQL wire protocol; db-browser has no GBASE_8A case.
        // Route to MYSQL so console session init (DatasourceColumnAccessor) can open.
        if (dialectType == DialectType.GBASE_8A) {
            return DialectType.MYSQL.getDBBrowserDialectTypeName();
        }
        return dialectType.getDBBrowserDialectTypeName();
    }

}

