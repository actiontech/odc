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
 * T-1.2 接线后：Db2DatabaseExtension 已通过 DBAccessorUtil 接入 DB2SchemaAccessor， 不再抛
 * UnsupportedOperationException。这里只验证「getSchemaAccessor 路径」实际拿到 DB2SchemaAccessor； 真正的
 * list/listDetails SQL 执行依赖真实 DB2 连接，落在集成测试或 web 测试用例里。
 */
public class Db2DatabaseExtensionTest {

    @Test
    public void getSchemaAccessor_returnsDB2SchemaAccessor() {
        Connection connection = Mockito.mock(Connection.class);
        DBSchemaAccessor accessor = DBAccessorUtil.getSchemaAccessor(connection);
        Assert.assertNotNull(accessor);
        Assert.assertTrue(
                "expected DB2SchemaAccessor but got " + accessor.getClass().getName(),
                accessor instanceof DB2SchemaAccessor);
    }

    @Test
    public void extension_canBeInstantiated() {
        // 仅校验 Db2DatabaseExtension 仍可正常实例化（pf4j @Extension 注解就绪）。
        Db2DatabaseExtension extension = new Db2DatabaseExtension();
        Assert.assertNotNull(extension);
    }
}
