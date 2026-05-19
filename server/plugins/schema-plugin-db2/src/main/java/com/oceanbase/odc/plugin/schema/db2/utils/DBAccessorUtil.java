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
package com.oceanbase.odc.plugin.schema.db2.utils;

import java.sql.Connection;

import com.oceanbase.odc.common.util.JdbcOperationsUtil;
import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.tools.dbbrowser.DBBrowser;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;

/**
 * Schema-accessor entry point for the DB2 schema plugin. Routes through
 * {@link DBBrowser#schemaAccessor()} so the call is dispatched via
 * {@code AbstractDBBrowserFactory.create(DialectType.DB2.name())} to the commit-B
 * {@code buildForDB2()} factory implementation (see {@code docs/spec/design.md} §2.4 — "通过
 * DBBrowserFactory 入口而不是直接 new Db2SchemaAccessor").
 *
 * @author actiontech-zihan
 * @since 4.3.4
 */
public class DBAccessorUtil {

    public static DBSchemaAccessor getSchemaAccessor(Connection connection) {
        return DBBrowser.schemaAccessor()
                .setJdbcOperations(JdbcOperationsUtil.getJdbcOperations(connection))
                .setType(DialectType.DB2.getDBBrowserDialectTypeName())
                .create();
    }

}
