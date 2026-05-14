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

import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;

/**
 * T-002 占位 DBAccessorUtil（蓝本 schema-plugin-postgres / schema-plugin-sqlserver 同名类）。
 * <p>
 * 当前阶段：所有调用都抛 {@link UnsupportedOperationException}（compat-RISK-7）。 T-004 接力实现，目标返回值：
 *
 * <pre>
 * DBBrowser.schemaAccessor()
 *         .setJdbcOperations(JdbcOperationsUtil.getJdbcOperations(connection))
 *         .setType(DialectType.DB2.getDBBrowserDialectTypeName())
 *         .create();
 * </pre>
 *
 * 这一调用链需要 {@code db-browser} 的 13 个 Factory 子类先接 {@code buildForDB2()}（T-003/T-004）。
 */
public class DBAccessorUtil {

    /** 占位错误消息。 */
    public static final String NOT_SUPPORTED_MESSAGE = "Not supported for DB2 yet";

    public static DBSchemaAccessor getSchemaAccessor(Connection connection) {
        // TODO(T-004): 接入 DBBrowser.schemaAccessor().setType(DB2).create()
        throw new UnsupportedOperationException(NOT_SUPPORTED_MESSAGE);
    }
}
