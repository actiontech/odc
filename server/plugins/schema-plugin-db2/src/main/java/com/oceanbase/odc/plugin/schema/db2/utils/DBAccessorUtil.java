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
 * T-1.2（test 阶段补接力）已接入 DBBrowser.schemaAccessor().setType(DB2).create()。
 * <p>
 * 真实 SYSCAT SQL 由 {@code DB2SchemaAccessor} 提供；调用链通过
 * {@link com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessorFactory#buildForDB2()} 路由。
 * <p>
 * compat-RISK-7 状态：B (占位) → covered (实接)。
 */
public class DBAccessorUtil {

    /** 兼容历史 Extension 中仍直接抛错的占位错误消息（如 Db2TableExtension.syncExternalTableFiles）。 */
    public static final String NOT_SUPPORTED_MESSAGE = "Not supported for DB2 yet";

    public static DBSchemaAccessor getSchemaAccessor(Connection connection) {
        return DBBrowser.schemaAccessor()
                .setJdbcOperations(JdbcOperationsUtil.getJdbcOperations(connection))
                .setType(DialectType.DB2.getDBBrowserDialectTypeName()).create();
    }

}
