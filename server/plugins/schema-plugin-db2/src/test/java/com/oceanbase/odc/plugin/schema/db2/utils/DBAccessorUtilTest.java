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

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;
import com.oceanbase.tools.dbbrowser.schema.db2.DB2SchemaAccessor;

/**
 * T-1.2 接线后：DBAccessorUtil#getSchemaAccessor 须返回 DB2SchemaAccessor 而非抛 UOE。
 * <p>
 * Connection 用 Mockito mock；Spring SingleConnectionDataSource 在构造期不触碰底层 connection，
 * DB2SchemaAccessor 也只在真正执行 SQL 时才使用 JdbcOperations，所以构造期 mock 即可，无需真连 DB2。
 */
public class DBAccessorUtilTest {

    @Test
    public void getSchemaAccessor_returnsDB2SchemaAccessor() {
        Connection connection = Mockito.mock(Connection.class);
        DBSchemaAccessor accessor = DBAccessorUtil.getSchemaAccessor(connection);
        Assert.assertNotNull("schema accessor must not be null", accessor);
        Assert.assertTrue(
                "expected DB2SchemaAccessor but got " + accessor.getClass().getName(),
                accessor instanceof DB2SchemaAccessor);
    }

    @Test
    public void notSupportedMessage_keepsDb2Marker() {
        // 历史 Extension（Db2TriggerExtension / Db2SynonymExtension / Db2TableExtension#syncExternalTableFiles
        // 等）仍直接抛 UnsupportedOperationException(NOT_SUPPORTED_MESSAGE)，message 必须保留 DB2 关键字便于排障。
        Assert.assertTrue(
                "NOT_SUPPORTED_MESSAGE must mention DB2 for diagnostics",
                DBAccessorUtil.NOT_SUPPORTED_MESSAGE.contains("DB2"));
    }
}
