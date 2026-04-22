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
import com.oceanbase.odc.plugin.connect.db2.DB2InformationExtension;
import com.oceanbase.tools.dbbrowser.DBBrowser;
import com.oceanbase.tools.dbbrowser.editor.DBTableEditor;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;
import com.oceanbase.tools.dbbrowser.stats.DBStatsAccessor;

/**
 * DB2 DB Accessor utility.
 *
 * Uses the dedicated DB2 type in db-browser library, which maps to DB2SchemaAccessor and
 * DB2StatsAccessor using SYSCAT views instead of SQL Server system views.
 */
public class DBAccessorUtil {

    private static final String DB2_TYPE = "DB2";

    public static String getDbVersion(Connection connection) {
        return new DB2InformationExtension().getDBVersion(connection);
    }

    public static DBSchemaAccessor getSchemaAccessor(Connection connection) {
        return DBBrowser.schemaAccessor()
                .setJdbcOperations(JdbcOperationsUtil.getJdbcOperations(connection))
                .setType(DB2_TYPE).create();
    }

    public static DBStatsAccessor getStatsAccessor(Connection connection) {
        return DBBrowser.statsAccessor()
                .setJdbcOperations(JdbcOperationsUtil.getJdbcOperations(connection))
                .setDbVersion(getDbVersion(connection))
                .setType(DB2_TYPE).create();
    }

    public static DBTableEditor getTableEditor(Connection connection) {
        return DBBrowser.objectEditor().tableEditor()
                .setDbVersion(getDbVersion(connection))
                .setType(DB2_TYPE).create();
    }

}
