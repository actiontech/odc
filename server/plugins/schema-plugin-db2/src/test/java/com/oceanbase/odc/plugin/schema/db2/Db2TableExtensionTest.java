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

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.oceanbase.odc.plugin.schema.db2.utils.DBAccessorUtil;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;
import com.oceanbase.tools.dbbrowser.schema.db2.DB2SchemaAccessor;

/**
 * T-1.2 接线后：Db2TableExtension 通过 DBAccessorUtil 拿到 DB2SchemaAccessor； syncExternalTableFiles 仍保留
 * UnsupportedOperationException（DB2 无 external table 概念）。
 */
public class Db2TableExtensionTest {

    private final Db2TableExtension extension = new Db2TableExtension();

    @Test
    public void syncExternalTableFiles_throwsUnsupported() {
        // DB2 没有 external table 概念，MVP 阶段不支持。
        try {
            extension.syncExternalTableFiles(null, "DB2INST1", "T");
            Assert.fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            Assert.assertEquals(DBAccessorUtil.NOT_SUPPORTED_MESSAGE, e.getMessage());
        }
    }

    @Test
    public void getSchemaAccessor_returnsDB2SchemaAccessor() {
        Connection connection = Mockito.mock(Connection.class);
        DBSchemaAccessor accessor = DBAccessorUtil.getSchemaAccessor(connection);
        Assert.assertNotNull(accessor);
        Assert.assertTrue(
                "expected DB2SchemaAccessor but got " + accessor.getClass().getName(),
                accessor instanceof DB2SchemaAccessor);
    }
}
