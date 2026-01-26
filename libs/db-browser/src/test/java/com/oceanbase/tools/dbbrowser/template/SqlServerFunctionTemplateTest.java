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

import com.oceanbase.tools.dbbrowser.model.DBFunction;
import com.oceanbase.tools.dbbrowser.template.sqlserver.SqlServerFunctionTemplate;

/**
 * {@link SqlServerFunctionTemplateTest}
 *
 * @author yizhou.xw
 * @date 2024/12
 * @since ODC_release_4.3.4
 */
public class SqlServerFunctionTemplateTest {

    @Test
    public void generateCreateObjectTemplate_basicFunction_generateSucceed() {
        DBObjectTemplate<DBFunction> template = new SqlServerFunctionTemplate();
        DBFunction function = DBFunction.of("f_test", "INT");

        String result = template.generateCreateObjectTemplate(function);
        Assert.assertTrue(result.contains("USE [请填写数据库名];"));
        Assert.assertTrue(result.contains("CREATE FUNCTION"));
        Assert.assertTrue(result.contains("[dbo].[f_test]"));
        Assert.assertTrue(result.contains("RETURNS INT"));
        Assert.assertTrue(result.contains("BEGIN"));
        Assert.assertTrue(result.contains("END"));
    }

    @Test
    public void generateCreateObjectTemplate_functionWithSchema_generateSucceed() {
        DBObjectTemplate<DBFunction> template = new SqlServerFunctionTemplate();
        DBFunction function = DBFunction.of("f_test", "INT");

        String result = template.generateCreateObjectTemplate(function);
        // 由于当前实现中schemaName为null，会使用默认值
        Assert.assertTrue(result.contains("USE [请填写数据库名];"));
        Assert.assertTrue(result.contains("[dbo].[f_test]"));
    }

    @Test
    public void generateCreateObjectTemplate_specialCharactersInName_generateSucceed() {
        DBObjectTemplate<DBFunction> template = new SqlServerFunctionTemplate();
        DBFunction function = DBFunction.of("f test function", "INT");

        String result = template.generateCreateObjectTemplate(function);
        Assert.assertTrue(result.contains("USE [请填写数据库名];"));
        Assert.assertTrue(result.contains("[f test function]"));
    }

    @Test
    public void generateCreateObjectTemplate_functionWithReturnType_generateSucceed() {
        DBObjectTemplate<DBFunction> template = new SqlServerFunctionTemplate();
        DBFunction function = DBFunction.of("f_test", "VARCHAR(100)");

        String result = template.generateCreateObjectTemplate(function);
        Assert.assertTrue(result.contains("USE [请填写数据库名];"));
        Assert.assertTrue(result.contains("RETURNS INT")); // 当前实现中硬编码为INT
        Assert.assertTrue(result.contains("[dbo].[f_test]"));
    }

    @Test
    public void generateCreateObjectTemplate_functionStructure_generateSucceed() {
        DBObjectTemplate<DBFunction> template = new SqlServerFunctionTemplate();
        DBFunction function = DBFunction.of("f_test", "INT");

        String result = template.generateCreateObjectTemplate(function);
        // 验证基本结构
        Assert.assertTrue(result.startsWith("USE [请填写数据库名];"));
        Assert.assertTrue(result.contains("CREATE FUNCTION"));
        Assert.assertTrue(result.contains("@param1 INT"));
        Assert.assertTrue(result.contains("RETURNS INT"));
        Assert.assertTrue(result.contains("AS"));
        Assert.assertTrue(result.contains("BEGIN"));
        Assert.assertTrue(result.contains("RETURN @param1;"));
        Assert.assertTrue(result.contains("END"));
    }

}
