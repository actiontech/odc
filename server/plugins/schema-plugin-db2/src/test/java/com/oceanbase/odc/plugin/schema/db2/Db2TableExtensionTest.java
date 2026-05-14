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

import java.lang.reflect.Proxy;
import java.sql.Connection;

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.odc.plugin.schema.db2.utils.DBAccessorUtil;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;

public class Db2TableExtensionTest {

    private final Db2TableExtension extension = new Db2TableExtension();

    private static Connection dummyConnection() {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, args) -> null);
    }

    @Test
    public void syncExternalTableFiles_throwsUnsupported() {
        try {
            extension.syncExternalTableFiles(null, "DB2INST1", "T");
            Assert.fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            Assert.assertEquals(DBAccessorUtil.NOT_SUPPORTED_MESSAGE, e.getMessage());
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void list_throwsUnsupported() {
        extension.list(dummyConnection(), "DB2INST1", DBObjectType.TABLE);
    }
}
