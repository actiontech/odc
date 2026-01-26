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

import com.oceanbase.tools.dbbrowser.model.DBTrigger;
import com.oceanbase.tools.dbbrowser.template.sqlserver.SqlServerTriggerTemplate;

/**
 * {@link SqlServerTriggerTemplateTest}
 *
 * @author yizhou.xw
 * @date 2024/12
 * @since ODC_release_4.3.4
 */
public class SqlServerTriggerTemplateTest {

    @Test
    public void generateCreateObjectTemplate_basicTrigger_generateSucceed() {
        DBObjectTemplate<DBTrigger> template = new SqlServerTriggerTemplate();
        DBTrigger trigger = new DBTrigger();
        trigger.setTriggerName("trg_test");
        trigger.setTableName("table_test");

        String result = template.generateCreateObjectTemplate(trigger);
        Assert.assertTrue(result.contains("USE [请填写数据库名];"));
        Assert.assertTrue(result.contains("CREATE TRIGGER"));
        Assert.assertTrue(result.contains("[dbo].[trg_test]"));
        Assert.assertTrue(result.contains("ON"));
        Assert.assertTrue(result.contains("[dbo].[table_test]"));
        Assert.assertTrue(result.contains("AFTER INSERT, UPDATE, DELETE"));
        Assert.assertTrue(result.contains("AS"));
        Assert.assertTrue(result.contains("BEGIN"));
        Assert.assertTrue(result.contains("END"));
    }

    @Test
    public void generateCreateObjectTemplate_triggerWithDatabaseAndSchema_generateSucceed() {
        DBObjectTemplate<DBTrigger> template = new SqlServerTriggerTemplate();
        DBTrigger trigger = new DBTrigger();
        trigger.setTriggerName("trg_test");
        trigger.setTableName("table_test");
        trigger.setSchemaName("testdb.sales");

        String result = template.generateCreateObjectTemplate(trigger);
        Assert.assertTrue(result.contains("USE [testdb];"));
        Assert.assertTrue(result.contains("[sales].[trg_test]"));
        Assert.assertTrue(result.contains("[sales].[table_test]"));
    }

    @Test
    public void generateCreateObjectTemplate_triggerWithSchemaOnly_generateSucceed() {
        DBObjectTemplate<DBTrigger> template = new SqlServerTriggerTemplate();
        DBTrigger trigger = new DBTrigger();
        trigger.setTriggerName("trg_test");
        trigger.setTableName("table_test");
        trigger.setSchemaName("sales");

        String result = template.generateCreateObjectTemplate(trigger);
        // 当只有schema名时，databaseName为null，会使用默认值
        Assert.assertTrue(result.contains("USE [请填写数据库名];"));
        Assert.assertTrue(result.contains("[sales].[trg_test]"));
        Assert.assertTrue(result.contains("[sales].[table_test]"));
    }

    @Test
    public void generateCreateObjectTemplate_triggerWithoutSchema_generateSucceed() {
        DBObjectTemplate<DBTrigger> template = new SqlServerTriggerTemplate();
        DBTrigger trigger = new DBTrigger();
        trigger.setTriggerName("trg_test");
        trigger.setTableName("table_test");
        // schemaName为null

        String result = template.generateCreateObjectTemplate(trigger);
        Assert.assertTrue(result.contains("USE [请填写数据库名];"));
        Assert.assertTrue(result.contains("[dbo].[trg_test]"));
        Assert.assertTrue(result.contains("[dbo].[table_test]"));
    }

    @Test
    public void generateCreateObjectTemplate_specialCharactersInNames_generateSucceed() {
        DBObjectTemplate<DBTrigger> template = new SqlServerTriggerTemplate();
        DBTrigger trigger = new DBTrigger();
        trigger.setTriggerName("trg test trigger");
        trigger.setTableName("test table");
        trigger.setSchemaName("my db.my schema");

        String result = template.generateCreateObjectTemplate(trigger);
        Assert.assertTrue(result.contains("USE [my db];"));
        Assert.assertTrue(result.contains("[my schema]"));
        Assert.assertTrue(result.contains("[trg test trigger]"));
        Assert.assertTrue(result.contains("[test table]"));
    }

    @Test
    public void generateCreateObjectTemplate_triggerStructure_generateSucceed() {
        DBObjectTemplate<DBTrigger> template = new SqlServerTriggerTemplate();
        DBTrigger trigger = new DBTrigger();
        trigger.setTriggerName("trg_test");
        trigger.setTableName("table_test");

        String result = template.generateCreateObjectTemplate(trigger);
        // 验证基本结构
        Assert.assertTrue(result.startsWith("USE [请填写数据库名];"));
        Assert.assertTrue(result.contains("CREATE TRIGGER"));
        Assert.assertTrue(result.contains("ON"));
        Assert.assertTrue(result.contains("AFTER INSERT, UPDATE, DELETE"));
        Assert.assertTrue(result.contains("AS"));
        Assert.assertTrue(result.contains("BEGIN"));
        Assert.assertTrue(result.contains("SET NOCOUNT ON;"));
        Assert.assertTrue(result.contains("-- Your trigger logic here"));
        Assert.assertTrue(result.contains("END"));
    }

}
