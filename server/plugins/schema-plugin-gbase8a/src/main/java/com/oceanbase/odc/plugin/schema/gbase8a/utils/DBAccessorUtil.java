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
package com.oceanbase.odc.plugin.schema.gbase8a.utils;

import java.sql.Connection;

import com.oceanbase.odc.common.util.JdbcOperationsUtil;
import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.odc.plugin.connect.gbase8a.GBase8aInformationExtension;
import com.oceanbase.odc.plugin.schema.gbase8a.GBase8aSchemaAccessor;
import com.oceanbase.tools.dbbrowser.DBBrowser;
import com.oceanbase.tools.dbbrowser.editor.DBTableEditor;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;
import com.oceanbase.tools.dbbrowser.stats.DBStatsAccessor;

/**
 * GBase-8a speaks MySQL wire protocol; column metadata uses {@link GBase8aSchemaAccessor} (no
 * DATETIME_PRECISION). Stats/table editor still use MYSQL db-browser factories.
 */
public class DBAccessorUtil {

    public static String getDbVersion(Connection connection) {
        return new GBase8aInformationExtension().getDBVersion(connection);
    }

    public static DBSchemaAccessor getSchemaAccessor(Connection connection) {
        return new GBase8aSchemaAccessor(JdbcOperationsUtil.getJdbcOperations(connection));
    }

    public static DBStatsAccessor getStatsAccessor(Connection connection) {
        return DBBrowser.statsAccessor()
                .setDbVersion(getDbVersion(connection))
                .setJdbcOperations(JdbcOperationsUtil.getJdbcOperations(connection))
                .setType(DialectType.MYSQL.getDBBrowserDialectTypeName())
                .create();
    }

    public static DBTableEditor getTableEditor(Connection connection) {
        return DBBrowser.objectEditor().tableEditor()
                .setDbVersion(DBAccessorUtil.getDbVersion(connection))
                .setType(DialectType.MYSQL.getDBBrowserDialectTypeName())
                .create();
    }
}
