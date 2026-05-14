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

public class Db2DatabaseExtensionTest {

    private final Db2DatabaseExtension extension = new Db2DatabaseExtension();

    private static Connection dummyConnection() {
        // 用 Proxy 避免 @NonNull 校验 NPE；调用任何方法时返回 null（基本类型抛 NPE 也无所谓，本测试不会触达）。
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, args) -> null);
    }

    @Test
    public void list_throwsUnsupported() {
        try {
            extension.list(dummyConnection());
            Assert.fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            Assert.assertEquals(DBAccessorUtil.NOT_SUPPORTED_MESSAGE, e.getMessage());
        }
    }

    @Test
    public void listDetails_throwsUnsupported() {
        try {
            extension.listDetails(dummyConnection());
            Assert.fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            Assert.assertEquals(DBAccessorUtil.NOT_SUPPORTED_MESSAGE, e.getMessage());
        }
    }
}
