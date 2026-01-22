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

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.tools.dbbrowser.model.DBProcedure;
import com.oceanbase.tools.dbbrowser.template.sqlserver.SqlServerProcedureTemplate;

/**
 * {@link SqlServerProcedureTemplateTest}
 *
 * @author yizhou.xw
 * @date 2024/12
 * @since ODC_release_4.3.4
 */
public class SqlServerProcedureTemplateTest {

    @Test
    public void generateCreateObjectTemplate_basicProcedure_generateSucceed() {
        DBObjectTemplate<DBProcedure> template = new SqlServerProcedureTemplate();
        DBProcedure procedure = DBProcedure.of("test_proc", null);

        String result = template.generateCreateObjectTemplate(procedure);
        Assert.assertTrue(result.contains("USE [请填写数据库名];"));
        Assert.assertTrue(result.contains("CREATE PROCEDURE"));
        Assert.assertTrue(result.contains("[dbo].[test_proc]"));
        Assert.assertTrue(result.contains("AS"));
        Assert.assertTrue(result.contains("BEGIN"));
        Assert.assertTrue(result.contains("END"));
    }

    @Test
    public void generateCreateObjectTemplate_procedureWithSchema_generateSucceed() {
        DBObjectTemplate<DBProcedure> template = new SqlServerProcedureTemplate();
        DBProcedure procedure = DBProcedure.of("test_proc", null);

        String result = template.generateCreateObjectTemplate(procedure);
        // 由于当前实现中schemaName为null，会使用默认值
        Assert.assertTrue(result.contains("USE [请填写数据库名];"));
        Assert.assertTrue(result.contains("[dbo].[test_proc]"));
    }

    @Test
    public void generateCreateObjectTemplate_specialCharactersInName_generateSucceed() {
        DBObjectTemplate<DBProcedure> template = new SqlServerProcedureTemplate();
        DBProcedure procedure = DBProcedure.of("test proc name", null);

        String result = template.generateCreateObjectTemplate(procedure);
        Assert.assertTrue(result.contains("USE [请填写数据库名];"));
        Assert.assertTrue(result.contains("[test proc name]"));
    }

    @Test
    public void generateCreateObjectTemplate_procedureStructure_generateSucceed() {
        DBObjectTemplate<DBProcedure> template = new SqlServerProcedureTemplate();
        DBProcedure procedure = DBProcedure.of("test_proc", null);

        String result = template.generateCreateObjectTemplate(procedure);
        // 验证基本结构
        Assert.assertTrue(result.startsWith("USE [请填写数据库名];"));
        Assert.assertTrue(result.contains("CREATE PROCEDURE"));
        Assert.assertTrue(result.contains("@param1 INT"));
        Assert.assertTrue(result.contains("AS"));
        Assert.assertTrue(result.contains("BEGIN"));
        Assert.assertTrue(result.contains("SET NOCOUNT ON;"));
        Assert.assertTrue(result.contains("SELECT @param1;"));
        Assert.assertTrue(result.contains("END"));
    }

    @Test
    public void generateCreateObjectTemplate_procedureWithPackage_generateSucceed() {
        DBObjectTemplate<DBProcedure> template = new SqlServerProcedureTemplate();
        DBProcedure procedure = DBProcedure.of("pkg", "test_proc", null);

        String result = template.generateCreateObjectTemplate(procedure);
        Assert.assertTrue(result.contains("USE [请填写数据库名];"));
        Assert.assertTrue(result.contains("[dbo].[test_proc]"));
    }

}
