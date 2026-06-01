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
package com.oceanbase.odc.plugin.schema.postgres.utils;

import java.sql.Connection;

import com.oceanbase.odc.common.util.JdbcOperationsUtil;
import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.odc.plugin.connect.postgres.PostgresInformationExtension;
import com.oceanbase.tools.dbbrowser.DBBrowser;
import com.oceanbase.tools.dbbrowser.editor.DBObjectOperator;
import com.oceanbase.tools.dbbrowser.editor.DBTableEditor;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;
import com.oceanbase.tools.dbbrowser.stats.DBStatsAccessor;

/**
 * ODC PostgreSQL schema plugin 工具类，提供数据库访问器实例的工厂方法
 * 
 * @author ODC Team
 * @since ODC_release_4.3.4
 */
public class DBAccessorUtil {

    private static final String DB_BROWSER_TYPE = DialectType.POSTGRESQL.getDBBrowserDialectTypeName();

    /**
     * 获取 PostgreSQL schema 访问器
     * 
     * @param connection 数据库连接
     * @return DBSchemaAccessor 实例
     */
    public static DBSchemaAccessor getSchemaAccessor(Connection connection) {
        return DBBrowser.schemaAccessor()
                .setJdbcOperations(JdbcOperationsUtil.getJdbcOperations(connection))
                .setType(DB_BROWSER_TYPE).create();
    }

    /**
     * 获取 PostgreSQL 统计信息访问器
     * 
     * @param connection 数据库连接
     * @return DBStatsAccessor 实例
     */
    public static DBStatsAccessor getStatsAccessor(Connection connection) {
        return DBBrowser.statsAccessor()
                .setJdbcOperations(JdbcOperationsUtil.getJdbcOperations(connection))
                .setDbVersion(getDbVersion(connection))
                .setType(DB_BROWSER_TYPE).create();
    }

    /**
     * 获取 PostgreSQL 表编辑器
     * 
     * @param connection 数据库连接
     * @return DBTableEditor 实例
     */
    public static DBTableEditor getTableEditor(Connection connection) {
        return DBBrowser.objectEditor().tableEditor()
                .setDbVersion(getDbVersion(connection))
                .setType(DB_BROWSER_TYPE).create();
    }

    /**
     * 获取 PostgreSQL 对象操作器
     * 
     * @param connection 数据库连接
     * @return DBObjectOperator 实例
     */
    public static DBObjectOperator getObjectOperator(Connection connection) {
        return DBBrowser.objectEditor().objectOperator()
                .setJdbcOperations(JdbcOperationsUtil.getJdbcOperations(connection))
                .setType(DB_BROWSER_TYPE).create();
    }

    /**
     * 获取 PostgreSQL 数据库版本
     * 
     * @param connection 数据库连接
     * @return 数据库版本字符串
     */
    private static String getDbVersion(Connection connection) {
        return new PostgresInformationExtension().getDBVersion(connection);
    }

}
