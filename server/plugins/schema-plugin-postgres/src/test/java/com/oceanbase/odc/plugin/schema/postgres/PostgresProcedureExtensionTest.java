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

import com.oceanbase.tools.dbbrowser.model.DBPLParam;
import com.oceanbase.tools.dbbrowser.model.DBPLParamMode;
import com.oceanbase.tools.dbbrowser.model.DBProcedure;

/**
 * {@link PostgresProcedureExtension} 单元测试
 *
 * <p>
 * 测试覆盖：
 * <ul>
 * <li>generateCreateTemplate() 方法生成正确的 PostgreSQL 存储过程模板</li>
 * <li>验证 PostgreSQL 特有语法：dollar-quoting</li>
 * </ul>
 *
 * @author ODC Team
 * @since ODC_release_4.3.5
 */
public class PostgresProcedureExtensionTest {

    private PostgresProcedureExtension procedureExtension;

    @Mock
    private Connection connection;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        procedureExtension = new PostgresProcedureExtension();
    }

    /**
     * 创建测试用的存储过程对象
     */
    private DBProcedure createTestProcedure() {
        DBProcedure procedure = new DBProcedure();
        procedure.setProName("transfer_funds");

        List<DBPLParam> params = new ArrayList<>();

        DBPLParam param1 = new DBPLParam();
        param1.setParamName("from_account");
        param1.setDataType("INTEGER");
        param1.setSeqNum(1);
        param1.setParamMode(DBPLParamMode.IN);
        params.add(param1);

        DBPLParam param2 = new DBPLParam();
        param2.setParamName("to_account");
        param2.setDataType("INTEGER");
        param2.setSeqNum(2);
        param2.setParamMode(DBPLParamMode.IN);
        params.add(param2);

        DBPLParam param3 = new DBPLParam();
        param3.setParamName("amount");
        param3.setDataType("NUMERIC");
        param3.setSeqNum(3);
        param3.setParamMode(DBPLParamMode.IN);
        params.add(param3);

        procedure.setParams(params);

        return procedure;
    }

    // ==================== generateCreateTemplate 测试 ====================

    /**
     * 测试用例：生成 CREATE PROCEDURE 模板 - 基本场景
     */
    @Test
    public void test_generateCreateTemplate_Basic() {
        DBProcedure procedure = createTestProcedure();
        String template = procedureExtension.generateCreateTemplate(procedure);

        assertNotNull("Template should not be null", template);
        // 模板生成包含过程名
        assertTrue("Template should contain procedure name",
                template.contains("transfer_funds"));
    }

    /**
     * 测试用例：生成 CREATE PROCEDURE 模板 - 包含参数
     */
    @Test
    public void test_generateCreateTemplate_WithParameters() {
        DBProcedure procedure = createTestProcedure();
        String template = procedureExtension.generateCreateTemplate(procedure);

        assertNotNull("Template should not be null", template);
        assertTrue("Template should contain parameters",
                template.contains("from_account") && template.contains("to_account") && template.contains("amount"));
    }

    /**
     * 测试用例：生成 CREATE PROCEDURE 模板 - 包含 dollar-quoting
     */
    @Test
    public void test_generateCreateTemplate_WithDollarQuoting() {
        DBProcedure procedure = createTestProcedure();
        String template = procedureExtension.generateCreateTemplate(procedure);

        assertNotNull("Template should not be null", template);
        // PostgreSQL 使用 dollar-quoting ($$...$$) 包裹过程体
        assertTrue("Template should contain dollar-quoting", template.contains("$$"));
    }

    /**
     * 测试用例：生成 CREATE PROCEDURE 模板 - PL/pgSQL 语言
     */
    @Test
    public void test_generateCreateTemplate_PlPgSQL() {
        DBProcedure procedure = createTestProcedure();
        String template = procedureExtension.generateCreateTemplate(procedure);

        assertNotNull("Template should not be null", template);
        // PostgreSQL 默认使用 PL/pgSQL 语言
        assertTrue("Template should contain LANGUAGE", template.contains("LANGUAGE"));
    }

    /**
     * 测试用例：生成 CREATE OR REPLACE PROCEDURE 模板
     */
    @Test
    public void test_generateCreateTemplate_OrReplace() {
        DBProcedure procedure = createTestProcedure();
        String template = procedureExtension.generateCreateTemplate(procedure);

        assertNotNull("Template should not be null", template);
        // PostgreSQL 支持 CREATE OR REPLACE PROCEDURE (PG 11+)
        assertTrue("Template should contain CREATE OR REPLACE",
                template.contains("CREATE OR REPLACE"));
    }

    /**
     * 测试用例：生成 CREATE PROCEDURE 模板 - 无参数
     */
    @Test
    public void test_generateCreateTemplate_NoParameters() {
        DBProcedure procedure = new DBProcedure();
        procedure.setProName("cleanup_logs");
        procedure.setParams(new ArrayList<>());

        String template = procedureExtension.generateCreateTemplate(procedure);

        assertNotNull("Template should not be null", template);
        assertTrue("Template should contain procedure name", template.contains("cleanup_logs"));
    }

    /**
     * 测试用例：生成 CREATE PROCEDURE 模板 - 包含 IN/OUT 参数
     */
    @Test
    public void test_generateCreateTemplate_WithInOutParameters() {
        DBProcedure procedure = new DBProcedure();
        procedure.setProName("get_user_info");

        List<DBPLParam> params = new ArrayList<>();

        DBPLParam param1 = new DBPLParam();
        param1.setParamName("user_id");
        param1.setDataType("INTEGER");
        param1.setParamMode(DBPLParamMode.IN);
        param1.setSeqNum(1);
        params.add(param1);

        DBPLParam param2 = new DBPLParam();
        param2.setParamName("user_name");
        param2.setDataType("VARCHAR");
        param2.setParamMode(DBPLParamMode.OUT);
        param2.setSeqNum(2);
        params.add(param2);

        procedure.setParams(params);
        String template = procedureExtension.generateCreateTemplate(procedure);

        assertNotNull("Template should not be null", template);
        assertTrue("Template should contain IN parameter", template.contains("user_id"));
        assertTrue("Template should contain OUT parameter", template.contains("user_name"));
    }

    // ==================== 继承关系测试 ====================

    /**
     * 测试用例：验证存储过程扩展类继承关系
     */
    @Test
    public void test_inheritance() {
        assertTrue("PostgresProcedureExtension should extend OBMySQLProcedureExtension",
                procedureExtension instanceof com.oceanbase.odc.plugin.schema.obmysql.OBMySQLProcedureExtension);
    }

}
