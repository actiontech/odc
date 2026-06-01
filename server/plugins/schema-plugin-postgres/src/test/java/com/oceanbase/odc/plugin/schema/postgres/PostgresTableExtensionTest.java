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
package com.oceanbase.odc.plugin.schema.postgres;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * {@link PostgresTableExtension} 单元测试
 *
 * <p>
 * 测试覆盖：
 * <ul>
 * <li>继承关系验证</li>
 * <li>异常场景测试</li>
 * </ul>
 *
 * @author ODC Team
 * @since ODC_release_4.3.5
 */
public class PostgresTableExtensionTest {

    private PostgresTableExtension tableExtension;

    @Mock
    private Connection connection;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        tableExtension = new PostgresTableExtension();
    }

    // ==================== 继承关系测试 ====================

    /**
     * 测试用例：验证表扩展类继承关系
     */
    @Test
    public void test_inheritance() {
        assertTrue("PostgresTableExtension should extend OBMySQLTableExtension",
                tableExtension instanceof com.oceanbase.odc.plugin.schema.obmysql.OBMySQLTableExtension);
    }

    /**
     * 测试用例：验证扩展类可以正常实例化
     */
    @Test
    public void test_canInstantiate() {
        assertNotNull("Extension should be instantiable", tableExtension);
    }

    // ==================== 异常场景测试 ====================

    /**
     * 测试用例：syncExternalTableFiles 应抛出 UnsupportedOperationException
     */
    @Test(expected = UnsupportedOperationException.class)
    public void test_syncExternalTableFiles_ThrowsException() {
        tableExtension.syncExternalTableFiles(connection, "public", "external_table");
    }

}
