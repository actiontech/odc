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
import com.oceanbase.odc.plugin.schema.obmysql.OBMySQLTableExtension;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;

/**
 * DB2 Table Extension（T-002 骨架，蓝本 {@code PostgresTableExtension}）。
 * <p>
 * 当前阶段方法体一律落到 {@link DBAccessorUtil} 抛 UnsupportedOperationException。 T-004 接入 SYSCAT.TABLES /
 * SYSCAT.COLUMNS / SYSCAT.INDEXES 等真实 SQL。
 */
@Extension
public class Db2TableExtension extends OBMySQLTableExtension {

    @Override
    protected DBSchemaAccessor getSchemaAccessor(Connection connection) {
        return DBAccessorUtil.getSchemaAccessor(connection);
    }

    @Override
    public boolean syncExternalTableFiles(Connection connection, String schemaName, String tableName) {
        // DB2 无 external table 概念；MVP 阶段不支持
        throw new UnsupportedOperationException(DBAccessorUtil.NOT_SUPPORTED_MESSAGE);
    }
}
