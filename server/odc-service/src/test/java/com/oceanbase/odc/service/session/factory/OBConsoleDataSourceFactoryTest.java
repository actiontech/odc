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
package com.oceanbase.odc.service.session.factory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.odc.core.shared.constant.DialectType;

import sun.misc.Unsafe;

public class OBConsoleDataSourceFactoryTest {
    @Test
    public void getKeepAliveSql_mongodb_returnsPingCommand() throws Exception {
        Method method = OBConsoleDataSourceFactory.class.getDeclaredMethod("getKeepAliveSql", DialectType.class);
        method.setAccessible(true);

        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        OBConsoleDataSourceFactory factory = (OBConsoleDataSourceFactory) unsafe.allocateInstance(
                OBConsoleDataSourceFactory.class);
        String keepAliveSql = (String) method.invoke(factory, DialectType.MONGODB);

        Assert.assertEquals("db.runCommand({ ping: 1 })", keepAliveSql);
    }
}
