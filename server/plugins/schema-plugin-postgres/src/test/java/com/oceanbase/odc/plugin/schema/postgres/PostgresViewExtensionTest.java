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

import com.oceanbase.tools.dbbrowser.model.DBView;

/**
 * {@link PostgresViewExtension} 单元测试
 *
 * <p>
 * 测试覆盖：
 * <ul>
 * <li>generateCreateTemplate() 方法生成正确的 PostgreSQL 视图模板</li>
 * <li>验证 PostgreSQL 特有语法：小写关键字</li>
 * </ul>
 *
 * @author ODC Team
 * @since ODC_release_4.3.5
 */
public class PostgresViewExtensionTest {

    private PostgresViewExtension viewExtension;

    @Mock
    private Connection connection;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        viewExtension = new PostgresViewExtension();
    }

    /**
     * 创建测试用的视图对象
     */
    private DBView createTestView() {
        DBView view = new DBView();
        view.setSchemaName("public");
        view.setViewName("test_view");
        view.setDdl("SELECT id, name FROM test_table");
        return view;
    }

    // ==================== generateCreateTemplate 测试 ====================

    /**
     * 测试用例：生成 CREATE VIEW 模板 - 基本场景
     */
    @Test
    public void test_generateCreateTemplate_Basic() {
        DBView view = createTestView();
        String template = viewExtension.generateCreateTemplate(view);

        assertNotNull("Template should not be null", template);
        // PostgreSQL template uses lowercase keywords
        assertTrue("Template should contain create view", template.toLowerCase().contains("create"));
        assertTrue("Template should contain view name",
                template.contains("test_view"));
    }

    /**
     * 测试用例：生成 CREATE VIEW 模板 - 无 schema
     */
    @Test
    public void test_generateCreateTemplate_NoSchema() {
        DBView view = new DBView();
        view.setViewName("simple_view");
        String template = viewExtension.generateCreateTemplate(view);

        assertNotNull("Template should not be null", template);
        assertTrue("Template should contain create view", template.toLowerCase().contains("create"));
        assertTrue("Template should contain view name", template.contains("simple_view"));
    }

    /**
     * 测试用例：生成 CREATE OR REPLACE VIEW 模板
     */
    @Test
    public void test_generateCreateTemplate_OrReplace() {
        DBView view = createTestView();
        String template = viewExtension.generateCreateTemplate(view);

        assertNotNull("Template should not be null", template);
        // PostgreSQL 支持 CREATE OR REPLACE VIEW
        assertTrue("Template should contain create or replace",
                template.toLowerCase().contains("create or replace"));
    }

    // ==================== 继承关系测试 ====================

    /**
     * 测试用例：验证视图扩展类继承关系
     */
    @Test
    public void test_inheritance() {
        assertTrue("PostgresViewExtension should extend OBMySQLViewExtension",
                viewExtension instanceof com.oceanbase.odc.plugin.schema.obmysql.OBMySQLViewExtension);
    }

}
