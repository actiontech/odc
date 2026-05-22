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
package com.oceanbase.odc.plugin.schema.gaussdb.utils;

import java.sql.Connection;

import com.oceanbase.odc.common.util.JdbcOperationsUtil;
import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.tools.dbbrowser.DBBrowser;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;

/**
 * Schema-accessor factory for GaussDB / openGauss.
 * <p>
 * <b>Why {@code setType(POSTGRESQL)} and not {@code setType(GAUSSDB)}?</b> The upstream
 * {@code db-browser} library (version 1.2.3, pinned by ODC parent POM) ships a hard-coded
 * {@code switch} statement in {@code DBSchemaAccessorBuilder} that recognises exactly 11 string
 * type names: {@code OB_MYSQL}, {@code OB_ORACLE}, {@code MYSQL}, {@code DORIS}, {@code TIDB},
 * {@code ORACLE}, {@code POSTGRESQL}, {@code SQL_SERVER}, {@code DM}, {@code ODP_SHARDING_OB_MYSQL}
 * and {@code FILE_SYSTEM}. Passing any other value (e.g. {@code "GAUSSDB"}) causes the builder to
 * throw {@link IllegalStateException} at runtime ("Unsupported type ...").
 * <p>
 * Because GaussDB speaks the PostgreSQL wire protocol and exposes a pg_catalog-shaped metadata
 * layer, the existing {@code PostgresSchemaAccessor} (the concrete class produced by
 * {@code setType("POSTGRESQL")}) is the correct accessor implementation for GaussDB metadata reads
 * at this stage. We therefore deliberately pin {@link DialectType#POSTGRESQL} here so the upstream
 * switch resolves to {@code PostgresSchemaAccessor} and reuse its column / index / constraint SQL.
 * <p>
 * <b>What if someone edits this to use {@code DialectType.GAUSSDB}?</b> The builder's switch
 * statement will hit its {@code default} branch and throw {@link IllegalStateException}, breaking
 * every GaussDB metadata operation (table list, column list, foreign keys, etc.). Do NOT change the
 * {@code setType} argument until {@code db-browser} ships a GaussDB-aware branch and
 * {@code PostgresSchemaAccessor} no longer suffices.
 * <p>
 * <b>When to revisit:</b> when db-browser exposes a dedicated GaussDB type (likely &gt;= 1.3.x),
 * replace the call with {@code DialectType.GAUSSDB.getDBBrowserDialectTypeName()} and remove this
 * shim. Until then the {@code POSTGRESQL} pinning is the contract between ODC and db-browser:1.2.3
 * documented in design.md §3.2.3 and compat_risks.md CR-4a / CR-15.
 */
public class DBAccessorUtil {
    public static DBSchemaAccessor getSchemaAccessor(Connection connection) {
        return DBBrowser.schemaAccessor()
                .setJdbcOperations(JdbcOperationsUtil.getJdbcOperations(connection))
                .setType(DialectType.POSTGRESQL.getDBBrowserDialectTypeName()).create();
    }

}
