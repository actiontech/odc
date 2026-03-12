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
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.oceanbase.tools.dbbrowser.model.DBFunction;
import com.oceanbase.tools.dbbrowser.model.DBPLParam;
import com.oceanbase.tools.dbbrowser.model.DBPLParamMode;

/**
 * {@link PostgresFunctionExtension} 单元测试
 *
 * <p>
 * 测试覆盖：
 * <ul>
 * <li>generateCreateTemplate() 方法生成正确的 PostgreSQL 函数模板</li>
 * <li>验证 PostgreSQL 特有语法：dollar-quoting</li>
 * </ul>
 *
 * @author ODC Team
 * @since ODC_release_4.3.5
 */
public class PostgresFunctionExtensionTest {

    private PostgresFunctionExtension functionExtension;

    @Mock
    private Connection connection;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        functionExtension = new PostgresFunctionExtension();
    }

    /**
     * 创建测试用的函数对象
     */
    private DBFunction createTestFunction() {
        DBFunction function = new DBFunction();
        function.setFunName("calculate_total");

        List<DBPLParam> params = new ArrayList<>();

        DBPLParam param1 = new DBPLParam();
        param1.setParamName("price");
        param1.setDataType("NUMERIC");
        param1.setSeqNum(1);
        param1.setParamMode(DBPLParamMode.IN);
        params.add(param1);

        DBPLParam param2 = new DBPLParam();
        param2.setParamName("quantity");
        param2.setDataType("INTEGER");
        param2.setSeqNum(2);
        param2.setParamMode(DBPLParamMode.IN);
        params.add(param2);

        function.setParams(params);
        function.setReturnType("NUMERIC");

        return function;
    }

    // ==================== generateCreateTemplate 测试 ====================

    /**
     * 测试用例：生成 CREATE FUNCTION 模板 - 基本场景
     */
    @Test
    public void test_generateCreateTemplate_Basic() {
        DBFunction function = createTestFunction();
        String template = functionExtension.generateCreateTemplate(function);

        assertNotNull("Template should not be null", template);
        // 模板生成包含函数名
        assertTrue("Template should contain function name",
                template.contains("calculate_total"));
    }

    /**
     * 测试用例：生成 CREATE FUNCTION 模板 - 包含参数
     */
    @Test
    public void test_generateCreateTemplate_WithParameters() {
        DBFunction function = createTestFunction();
        String template = functionExtension.generateCreateTemplate(function);

        assertNotNull("Template should not be null", template);
        assertTrue("Template should contain parameters",
                template.contains("price") && template.contains("quantity"));
    }

    /**
     * 测试用例：生成 CREATE FUNCTION 模板 - 包含返回类型
     */
    @Test
    public void test_generateCreateTemplate_WithReturnType() {
        DBFunction function = createTestFunction();
        String template = functionExtension.generateCreateTemplate(function);

        assertNotNull("Template should not be null", template);
        // PostgreSQL 使用 RETURNS 关键字指定返回类型
        assertTrue("Template should contain RETURNS keyword", template.contains("RETURNS"));
    }

    /**
     * 测试用例：生成 CREATE FUNCTION 模板 - 包含 dollar-quoting
     */
    @Test
    public void test_generateCreateTemplate_WithDollarQuoting() {
        DBFunction function = createTestFunction();
        String template = functionExtension.generateCreateTemplate(function);

        assertNotNull("Template should not be null", template);
        // PostgreSQL 使用 dollar-quoting ($$...$$) 包裹函数体
        assertTrue("Template should contain dollar-quoting", template.contains("$$"));
    }

    /**
     * 测试用例：生成 CREATE FUNCTION 模板 - PL/pgSQL 语言
     */
    @Test
    public void test_generateCreateTemplate_PlPgSQL() {
        DBFunction function = createTestFunction();
        String template = functionExtension.generateCreateTemplate(function);

        assertNotNull("Template should not be null", template);
        // PostgreSQL 默认使用 PL/pgSQL 语言
        assertTrue("Template should contain LANGUAGE", template.contains("LANGUAGE"));
    }

    /**
     * 测试用例：生成 CREATE OR REPLACE FUNCTION 模板
     */
    @Test
    public void test_generateCreateTemplate_OrReplace() {
        DBFunction function = createTestFunction();
        String template = functionExtension.generateCreateTemplate(function);

        assertNotNull("Template should not be null", template);
        // PostgreSQL 支持 CREATE OR REPLACE FUNCTION
        assertTrue("Template should contain CREATE OR REPLACE",
                template.contains("CREATE OR REPLACE"));
    }

    /**
     * 测试用例：生成 CREATE FUNCTION 模板 - 无参数
     */
    @Test
    public void test_generateCreateTemplate_NoParameters() {
        DBFunction function = new DBFunction();
        function.setFunName("get_current_time");
        function.setReturnType("TIMESTAMP");
        function.setParams(new ArrayList<>());

        String template = functionExtension.generateCreateTemplate(function);

        assertNotNull("Template should not be null", template);
        assertTrue("Template should contain function name", template.contains("get_current_time"));
    }

    // ==================== 继承关系测试 ====================

    /**
     * 测试用例：验证函数扩展类继承关系
     */
    @Test
    public void test_inheritance() {
        assertTrue("PostgresFunctionExtension should extend OBMySQLFunctionExtension",
                functionExtension instanceof com.oceanbase.odc.plugin.schema.obmysql.OBMySQLFunctionExtension);
    }

}
