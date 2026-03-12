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

import com.oceanbase.tools.dbbrowser.model.DBPLParam;
import com.oceanbase.tools.dbbrowser.model.DBPLParamMode;
import com.oceanbase.tools.dbbrowser.model.DBProcedure;
import com.oceanbase.tools.dbbrowser.template.postgre.PostgresProcedureTemplate;

/**
 * {@link PostgresProcedureTemplateTest}
 *
 * @author odc
 * @since ODC_release_4.3.4
 */
public class PostgresProcedureTemplateTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void generateCreateObjectTemplate_basicProcedure_generateSucceed() {
        DBObjectTemplate<DBProcedure> template = new PostgresProcedureTemplate();
        DBProcedure procedure = DBProcedure.of("test_proc", null);

        String result = template.generateCreateObjectTemplate(procedure);

        Assert.assertTrue(result.contains("CREATE OR REPLACE PROCEDURE"));
        Assert.assertTrue(result.contains("\"test_proc\""));
        Assert.assertTrue(result.contains("LANGUAGE plpgsql"));
        Assert.assertTrue(result.contains("AS $$"));
        Assert.assertTrue(result.contains("BEGIN"));
        Assert.assertTrue(result.contains("END;"));
        Assert.assertTrue(result.contains("$$;"));
    }

    @Test
    public void generateCreateObjectTemplate_procedureWithoutName_expThrown() {
        DBObjectTemplate<DBProcedure> template = new PostgresProcedureTemplate();
        DBProcedure procedure = new DBProcedure();

        thrown.expectMessage("Procedure name can not be blank");
        thrown.expect(NullPointerException.class);
        template.generateCreateObjectTemplate(procedure);
    }

    @Test
    public void generateCreateObjectTemplate_procedureWithParameters_generateSucceed() {
        DBObjectTemplate<DBProcedure> template = new PostgresProcedureTemplate();
        DBProcedure procedure = DBProcedure.of("insert_user", null);

        DBPLParam param1 = new DBPLParam();
        param1.setParamName("p_name");
        param1.setDataType("VARCHAR");
        param1.setParamMode(DBPLParamMode.IN);

        DBPLParam param2 = new DBPLParam();
        param2.setParamName("p_email");
        param2.setDataType("VARCHAR");
        param2.setParamMode(DBPLParamMode.IN);

        procedure.setParams(Arrays.asList(param1, param2));

        String result = template.generateCreateObjectTemplate(procedure);

        Assert.assertTrue(result.contains("IN \"p_name\" VARCHAR"));
        Assert.assertTrue(result.contains("IN \"p_email\" VARCHAR"));
    }

    @Test
    public void generateCreateObjectTemplate_procedureWithDefaultParameter_generateSucceed() {
        DBObjectTemplate<DBProcedure> template = new PostgresProcedureTemplate();
        DBProcedure procedure = DBProcedure.of("greet_user", null);

        DBPLParam param = new DBPLParam();
        param.setParamName("p_greeting");
        param.setDataType("VARCHAR");
        param.setParamMode(DBPLParamMode.IN);
        param.setDefaultValue("'Hello'");

        procedure.setParams(Arrays.asList(param));

        String result = template.generateCreateObjectTemplate(procedure);

        Assert.assertTrue(result.contains("DEFAULT 'Hello'"));
    }

    @Test
    public void generateCreateObjectTemplate_procedureWithOutParameter_generateSucceed() {
        DBObjectTemplate<DBProcedure> template = new PostgresProcedureTemplate();
        DBProcedure procedure = DBProcedure.of("get_user_count", null);

        DBPLParam param = new DBPLParam();
        param.setParamName("p_count");
        param.setDataType("INTEGER");
        param.setParamMode(DBPLParamMode.OUT);

        procedure.setParams(Arrays.asList(param));

        String result = template.generateCreateObjectTemplate(procedure);

        Assert.assertTrue(result.contains("OUT \"p_count\" INTEGER"));
    }

    @Test
    public void generateCreateObjectTemplate_procedureWithInOutParameter_generateSucceed() {
        DBObjectTemplate<DBProcedure> template = new PostgresProcedureTemplate();
        DBProcedure procedure = DBProcedure.of("double_value", null);

        DBPLParam param = new DBPLParam();
        param.setParamName("p_value");
        param.setDataType("INTEGER");
        param.setParamMode(DBPLParamMode.INOUT);

        procedure.setParams(Arrays.asList(param));

        String result = template.generateCreateObjectTemplate(procedure);

        Assert.assertTrue(result.contains("INOUT \"p_value\" INTEGER"));
    }

    @Test
    public void generateCreateObjectTemplate_specialCharactersInName_generateSucceed() {
        DBObjectTemplate<DBProcedure> template = new PostgresProcedureTemplate();
        DBProcedure procedure = DBProcedure.of("test proc name", null);

        String result = template.generateCreateObjectTemplate(procedure);

        Assert.assertTrue(result.contains("\"test proc name\""));
    }

    @Test
    public void generateCreateObjectTemplate_procedureStructure_generateSucceed() {
        DBObjectTemplate<DBProcedure> template = new PostgresProcedureTemplate();
        DBProcedure procedure = DBProcedure.of("test_proc", null);

        String result = template.generateCreateObjectTemplate(procedure);

        // Verify complete structure
        Assert.assertTrue(result.startsWith("CREATE OR REPLACE PROCEDURE"));
        Assert.assertTrue(result.contains("\"test_proc\" ("));
        Assert.assertTrue(result.contains("LANGUAGE plpgsql"));
        Assert.assertTrue(result.contains("AS $$"));
        Assert.assertTrue(result.contains("BEGIN"));
        Assert.assertTrue(result.contains("-- Enter your procedure code here"));
        Assert.assertTrue(result.contains("NULL;"));
        Assert.assertTrue(result.contains("END;"));
        Assert.assertTrue(result.contains("$$;"));
    }

    @Test
    public void generateCreateObjectTemplate_procedureWithPackage_generateSucceed() {
        DBObjectTemplate<DBProcedure> template = new PostgresProcedureTemplate();
        DBProcedure procedure = DBProcedure.of("pkg", "test_proc", null);

        String result = template.generateCreateObjectTemplate(procedure);

        // In PostgreSQL, package name is not used in procedure creation
        Assert.assertTrue(result.contains("\"test_proc\""));
    }

    @Test
    public void generateCreateObjectTemplate_procedureWithMixedParameters_generateSucceed() {
        DBObjectTemplate<DBProcedure> template = new PostgresProcedureTemplate();
        DBProcedure procedure = DBProcedure.of("process_user", null);

        DBPLParam param1 = new DBPLParam();
        param1.setParamName("p_id");
        param1.setDataType("INTEGER");
        param1.setParamMode(DBPLParamMode.IN);

        DBPLParam param2 = new DBPLParam();
        param2.setParamName("p_name");
        param2.setDataType("VARCHAR");
        param2.setParamMode(DBPLParamMode.INOUT);

        DBPLParam param3 = new DBPLParam();
        param3.setParamName("p_created");
        param3.setDataType("BOOLEAN");
        param3.setParamMode(DBPLParamMode.OUT);

        procedure.setParams(Arrays.asList(param1, param2, param3));

        String result = template.generateCreateObjectTemplate(procedure);

        Assert.assertTrue(result.contains("IN \"p_id\" INTEGER"));
        Assert.assertTrue(result.contains("INOUT \"p_name\" VARCHAR"));
        Assert.assertTrue(result.contains("OUT \"p_created\" BOOLEAN"));
    }

    @Test
    public void generateCreateObjectTemplate_procedureWithComplexTypes_generateSucceed() {
        DBObjectTemplate<DBProcedure> template = new PostgresProcedureTemplate();
        DBProcedure procedure = DBProcedure.of("process_json", null);

        DBPLParam param = new DBPLParam();
        param.setParamName("p_data");
        param.setDataType("JSONB");
        param.setParamMode(DBPLParamMode.IN);

        procedure.setParams(Arrays.asList(param));

        String result = template.generateCreateObjectTemplate(procedure);

        Assert.assertTrue(result.contains("IN \"p_data\" JSONB"));
    }

    @Test
    public void generateCreateObjectTemplate_procedureNoParameters_generateSucceed() {
        DBObjectTemplate<DBProcedure> template = new PostgresProcedureTemplate();
        DBProcedure procedure = DBProcedure.of("cleanup_logs", null);
        // No parameters set

        String result = template.generateCreateObjectTemplate(procedure);

        Assert.assertTrue(result.contains("\"cleanup_logs\" ()"));
        Assert.assertTrue(result.contains("LANGUAGE plpgsql"));
        Assert.assertTrue(result.contains("AS $$"));
    }

    @Test
    public void generateCreateObjectTemplate_procedureWithNoParamMode_generateSucceed() {
        DBObjectTemplate<DBProcedure> template = new PostgresProcedureTemplate();
        DBProcedure procedure = DBProcedure.of("test_proc", null);

        DBPLParam param = new DBPLParam();
        param.setParamName("p_value");
        param.setDataType("INTEGER");
        // No param mode set

        procedure.setParams(Arrays.asList(param));

        String result = template.generateCreateObjectTemplate(procedure);

        // Without mode, no IN/OUT prefix
        Assert.assertTrue(result.contains("\"p_value\" INTEGER"));
        Assert.assertFalse(result.contains("IN \"p_value\""));
    }

    @Test
    public void generateCreateObjectTemplate_procedureWithUnknownParamMode_generateSucceed() {
        DBObjectTemplate<DBProcedure> template = new PostgresProcedureTemplate();
        DBProcedure procedure = DBProcedure.of("test_proc", null);

        DBPLParam param = new DBPLParam();
        param.setParamName("p_value");
        param.setDataType("INTEGER");
        param.setParamMode(DBPLParamMode.UNKNOWN);

        procedure.setParams(Arrays.asList(param));

        String result = template.generateCreateObjectTemplate(procedure);

        // UNKNOWN mode should not add IN/OUT prefix
        Assert.assertTrue(result.contains("\"p_value\" INTEGER"));
        Assert.assertFalse(result.contains("UNKNOWN"));
    }

}
