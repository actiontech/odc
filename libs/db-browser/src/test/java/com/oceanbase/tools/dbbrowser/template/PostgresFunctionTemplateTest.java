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
package com.oceanbase.tools.dbbrowser.template;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.oceanbase.tools.dbbrowser.model.DBFunction;
import com.oceanbase.tools.dbbrowser.model.DBPLParam;
import com.oceanbase.tools.dbbrowser.model.DBPLParamMode;
import com.oceanbase.tools.dbbrowser.template.postgre.PostgresFunctionTemplate;

/**
 * {@link PostgresFunctionTemplateTest}
 *
 * @author odc
 * @since ODC_release_4.3.4
 */
public class PostgresFunctionTemplateTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void generateCreateObjectTemplate_basicFunction_generateSucceed() {
        DBObjectTemplate<DBFunction> template = new PostgresFunctionTemplate();
        DBFunction function = DBFunction.of("f_test", "INTEGER");

        String result = template.generateCreateObjectTemplate(function);

        Assert.assertTrue(result.contains("CREATE OR REPLACE FUNCTION"));
        Assert.assertTrue(result.contains("\"f_test\""));
        Assert.assertTrue(result.contains("RETURNS INTEGER"));
        Assert.assertTrue(result.contains("LANGUAGE plpgsql"));
        Assert.assertTrue(result.contains("AS $$"));
        Assert.assertTrue(result.contains("BEGIN"));
        Assert.assertTrue(result.contains("END;"));
        Assert.assertTrue(result.contains("$$;"));
    }

    @Test
    public void generateCreateObjectTemplate_functionWithoutName_expThrown() {
        DBObjectTemplate<DBFunction> template = new PostgresFunctionTemplate();
        DBFunction function = new DBFunction();

        thrown.expectMessage("Function name can not be blank");
        thrown.expect(NullPointerException.class);
        template.generateCreateObjectTemplate(function);
    }

    @Test
    public void generateCreateObjectTemplate_functionWithReturnTypeVarChar_generateSucceed() {
        DBObjectTemplate<DBFunction> template = new PostgresFunctionTemplate();
        DBFunction function = DBFunction.of("f_get_name", "VARCHAR(100)");

        String result = template.generateCreateObjectTemplate(function);

        Assert.assertTrue(result.contains("RETURNS VARCHAR(100)"));
    }

    @Test
    public void generateCreateObjectTemplate_functionWithParameters_generateSucceed() {
        DBObjectTemplate<DBFunction> template = new PostgresFunctionTemplate();
        DBFunction function = DBFunction.of("f_add", "INTEGER");

        DBPLParam param1 = new DBPLParam();
        param1.setParamName("p_a");
        param1.setDataType("INTEGER");
        param1.setParamMode(DBPLParamMode.IN);

        DBPLParam param2 = new DBPLParam();
        param2.setParamName("p_b");
        param2.setDataType("INTEGER");
        param2.setParamMode(DBPLParamMode.IN);

        function.setParams(Arrays.asList(param1, param2));

        String result = template.generateCreateObjectTemplate(function);

        Assert.assertTrue(result.contains("IN \"p_a\" INTEGER"));
        Assert.assertTrue(result.contains("IN \"p_b\" INTEGER"));
    }

    @Test
    public void generateCreateObjectTemplate_functionWithDefaultParameter_generateSucceed() {
        DBObjectTemplate<DBFunction> template = new PostgresFunctionTemplate();
        DBFunction function = DBFunction.of("f_greet", "VARCHAR");

        DBPLParam param = new DBPLParam();
        param.setParamName("p_name");
        param.setDataType("VARCHAR");
        param.setParamMode(DBPLParamMode.IN);
        param.setDefaultValue("'World'");

        function.setParams(Arrays.asList(param));

        String result = template.generateCreateObjectTemplate(function);

        Assert.assertTrue(result.contains("DEFAULT 'World'"));
    }

    @Test
    public void generateCreateObjectTemplate_functionWithOutParameter_generateSucceed() {
        DBObjectTemplate<DBFunction> template = new PostgresFunctionTemplate();
        DBFunction function = DBFunction.of("f_get_user", "RECORD");

        DBPLParam param1 = new DBPLParam();
        param1.setParamName("p_id");
        param1.setDataType("INTEGER");
        param1.setParamMode(DBPLParamMode.IN);

        DBPLParam param2 = new DBPLParam();
        param2.setParamName("p_name");
        param2.setDataType("VARCHAR");
        param2.setParamMode(DBPLParamMode.OUT);

        function.setParams(Arrays.asList(param1, param2));

        String result = template.generateCreateObjectTemplate(function);

        Assert.assertTrue(result.contains("IN \"p_id\" INTEGER"));
        Assert.assertTrue(result.contains("OUT \"p_name\" VARCHAR"));
    }

    @Test
    public void generateCreateObjectTemplate_functionWithInOutParameter_generateSucceed() {
        DBObjectTemplate<DBFunction> template = new PostgresFunctionTemplate();
        DBFunction function = DBFunction.of("f_swap", "VOID");

        DBPLParam param1 = new DBPLParam();
        param1.setParamName("p_a");
        param1.setDataType("INTEGER");
        param1.setParamMode(DBPLParamMode.INOUT);

        function.setParams(Arrays.asList(param1));

        String result = template.generateCreateObjectTemplate(function);

        Assert.assertTrue(result.contains("INOUT \"p_a\" INTEGER"));
    }

    @Test
    public void generateCreateObjectTemplate_specialCharactersInName_generateSucceed() {
        DBObjectTemplate<DBFunction> template = new PostgresFunctionTemplate();
        DBFunction function = DBFunction.of("f test function", "INTEGER");

        String result = template.generateCreateObjectTemplate(function);

        Assert.assertTrue(result.contains("\"f test function\""));
    }

    @Test
    public void generateCreateObjectTemplate_functionReturnsSetof_generateSucceed() {
        DBObjectTemplate<DBFunction> template = new PostgresFunctionTemplate();
        DBFunction function = DBFunction.of("f_get_all_users", "SETOF users");

        String result = template.generateCreateObjectTemplate(function);

        Assert.assertTrue(result.contains("RETURNS SETOF users"));
    }

    @Test
    public void generateCreateObjectTemplate_functionReturnsTable_generateSucceed() {
        DBObjectTemplate<DBFunction> template = new PostgresFunctionTemplate();
        DBFunction function = DBFunction.of("f_get_users", "TABLE(id INTEGER, name VARCHAR)");

        String result = template.generateCreateObjectTemplate(function);

        Assert.assertTrue(result.contains("RETURNS TABLE(id INTEGER, name VARCHAR)"));
    }

    @Test
    public void generateCreateObjectTemplate_functionWithNoReturnType_usesDefault() {
        DBObjectTemplate<DBFunction> template = new PostgresFunctionTemplate();
        DBFunction function = new DBFunction();
        function.setFunName("f_test");
        // No return type set

        String result = template.generateCreateObjectTemplate(function);

        Assert.assertTrue(result.contains("RETURNS INTEGER")); // Default
    }

    @Test
    public void generateCreateObjectTemplate_functionStructure_generateSucceed() {
        DBObjectTemplate<DBFunction> template = new PostgresFunctionTemplate();
        DBFunction function = DBFunction.of("f_test", "INTEGER");

        String result = template.generateCreateObjectTemplate(function);

        // Verify complete structure
        Assert.assertTrue(result.startsWith("CREATE OR REPLACE FUNCTION"));
        Assert.assertTrue(result.contains("\"f_test\" ("));
        Assert.assertTrue(result.contains("RETURNS INTEGER"));
        Assert.assertTrue(result.contains("LANGUAGE plpgsql"));
        Assert.assertTrue(result.contains("AS $$"));
        Assert.assertTrue(result.contains("BEGIN"));
        Assert.assertTrue(result.contains("-- Enter your function code here"));
        Assert.assertTrue(result.contains("RETURN NULL;"));
        Assert.assertTrue(result.contains("END;"));
        Assert.assertTrue(result.contains("$$;"));
    }

    @Test
    public void generateCreateObjectTemplate_functionWithComplexTypes_generateSucceed() {
        DBObjectTemplate<DBFunction> template = new PostgresFunctionTemplate();
        DBFunction function = DBFunction.of("f_process", "JSONB");

        DBPLParam param = new DBPLParam();
        param.setParamName("p_data");
        param.setDataType("JSONB");
        param.setParamMode(DBPLParamMode.IN);

        function.setParams(Arrays.asList(param));

        String result = template.generateCreateObjectTemplate(function);

        Assert.assertTrue(result.contains("IN \"p_data\" JSONB"));
        Assert.assertTrue(result.contains("RETURNS JSONB"));
    }

    @Test
    public void generateCreateObjectTemplate_functionWithNoParamMode_generateSucceed() {
        DBObjectTemplate<DBFunction> template = new PostgresFunctionTemplate();
        DBFunction function = DBFunction.of("f_test", "INTEGER");

        DBPLParam param = new DBPLParam();
        param.setParamName("p_value");
        param.setDataType("INTEGER");
        // No param mode set

        function.setParams(Arrays.asList(param));

        String result = template.generateCreateObjectTemplate(function);

        // Without mode, no IN/OUT prefix
        Assert.assertTrue(result.contains("\"p_value\" INTEGER"));
        Assert.assertFalse(result.contains("IN \"p_value\""));
    }

    @Test
    public void generateCreateObjectTemplate_functionWithUnknownParamMode_generateSucceed() {
        DBObjectTemplate<DBFunction> template = new PostgresFunctionTemplate();
        DBFunction function = DBFunction.of("f_test", "INTEGER");

        DBPLParam param = new DBPLParam();
        param.setParamName("p_value");
        param.setDataType("INTEGER");
        param.setParamMode(DBPLParamMode.UNKNOWN);

        function.setParams(Arrays.asList(param));

        String result = template.generateCreateObjectTemplate(function);

        // UNKNOWN mode should not add IN/OUT prefix
        Assert.assertTrue(result.contains("\"p_value\" INTEGER"));
        Assert.assertFalse(result.contains("UNKNOWN"));
    }

}
