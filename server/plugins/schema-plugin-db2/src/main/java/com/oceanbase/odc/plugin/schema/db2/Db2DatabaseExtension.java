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

import com.oceanbase.odc.plugin.schema.db2.utils.DBAccessorUtil;
import com.oceanbase.odc.plugin.schema.obmysql.OBMySQLDatabaseExtension;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;

/**
 * DB2 Database Extension（T-002 骨架，蓝本 {@code PostgresDatabaseExtension}）。
 * <p>
 * 仅重写 {@code getSchemaAccessor}，让 super 类所有方法（listAll / get / ...） 一律进入
 * {@link DBAccessorUtil#getSchemaAccessor(Connection)} 占位抛 UnsupportedOperationException 的代码路径，避免
 * T-002 引入半成品方法体（compat-RISK-7）。
 * <p>
 * 真实 SQL 与 schema 切换语义由 T-004 接入。
 */
@Extension
public class Db2DatabaseExtension extends OBMySQLDatabaseExtension {

    @Override
    protected DBSchemaAccessor getSchemaAccessor(Connection connection) {
        return DBAccessorUtil.getSchemaAccessor(connection);
    }
}
