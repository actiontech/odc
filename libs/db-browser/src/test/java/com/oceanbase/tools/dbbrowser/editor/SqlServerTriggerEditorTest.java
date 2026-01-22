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
package com.oceanbase.tools.dbbrowser.editor;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oceanbase.tools.dbbrowser.editor.sqlserver.SqlServerTriggerEditor;
import com.oceanbase.tools.dbbrowser.model.DBTrigger;

/**
 * @description: all tests for {@link SqlServerTriggerEditor}
 * @author: yizhou.xw
 * @date: 2024/12
 * @since: ODC_release_4.3.4
 */
public class SqlServerTriggerEditorTest {

    private SqlServerTriggerEditor triggerEditor;

    @Before
    public void setUp() {
        triggerEditor = new SqlServerTriggerEditor();
    }

    @Test
    public void generateCreateObjectDDL() {
        DBTrigger trigger = new DBTrigger();
        trigger.setDdl("CREATE TRIGGER [trg] ON [tbl] AFTER INSERT AS BEGIN PRINT 'hi' END");
        String ddl = triggerEditor.generateCreateObjectDDL(trigger);
        Assert.assertEquals("CREATE TRIGGER [trg] ON [tbl] AFTER INSERT AS BEGIN PRINT 'hi' END", ddl);
    }

    @Test
    public void generateCreateDefinitionDDL() {
        DBTrigger trigger = new DBTrigger();
        trigger.setDdl("CREATE TRIGGER [trg] ON [tbl] AFTER INSERT AS BEGIN PRINT 'hi' END");
        String ddl = triggerEditor.generateCreateDefinitionDDL(trigger);
        Assert.assertEquals("CREATE TRIGGER [trg] ON [tbl] AFTER INSERT AS BEGIN PRINT 'hi' END", ddl);
    }

    @Test
    public void generateUpdateObjectDDL() {
        DBTrigger oldTrigger = new DBTrigger();
        DBTrigger newTrigger = new DBTrigger();
        newTrigger.setDdl("CREATE OR ALTER TRIGGER [trg] ON [tbl] AFTER INSERT AS BEGIN PRINT 'updated' END");
        String ddl = triggerEditor.generateUpdateObjectDDL(oldTrigger, newTrigger);
        Assert.assertEquals("CREATE OR ALTER TRIGGER [trg] ON [tbl] AFTER INSERT AS BEGIN PRINT 'updated' END", ddl);
    }

    @Test
    public void generateDropObjectDDL() {
        DBTrigger trigger = new DBTrigger();
        trigger.setSchemaName("dbo");
        trigger.setTriggerName("test_trigger");
        String ddl = triggerEditor.generateDropObjectDDL(trigger);
        Assert.assertEquals("DROP TRIGGER [dbo].[test_trigger]", ddl);
    }

    @Test
    public void generateRenameObjectDDL() {
        DBTrigger oldTrigger = new DBTrigger();
        oldTrigger.setSchemaName("dbo");
        oldTrigger.setTriggerName("old_trg");
        DBTrigger newTrigger = new DBTrigger();
        newTrigger.setTriggerName("new_trg");
        String ddl = triggerEditor.generateRenameObjectDDL(oldTrigger, newTrigger);
        Assert.assertEquals("EXEC sp_rename 'dbo.old_trg', 'new_trg'", ddl);
    }

}
