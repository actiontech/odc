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

import com.oceanbase.tools.dbbrowser.editor.sqlserver.SqlServerViewEditor;
import com.oceanbase.tools.dbbrowser.model.DBView;

/**
 * @description: all tests for {@link SqlServerViewEditor}
 * @author: yizhou.xw
 * @date: 2024/12
 * @since: ODC_release_4.3.4
 */
public class SqlServerViewEditorTest {

    private SqlServerViewEditor viewEditor;

    @Before
    public void setUp() {
        viewEditor = new SqlServerViewEditor();
    }

    @Test
    public void generateCreateObjectDDL() {
        DBView view = new DBView();
        view.setDdl("CREATE VIEW [v] AS SELECT 1 as col");
        String ddl = viewEditor.generateCreateObjectDDL(view);
        Assert.assertEquals("CREATE VIEW [v] AS SELECT 1 as col", ddl);
    }

    @Test
    public void generateCreateDefinitionDDL() {
        DBView view = new DBView();
        view.setDdl("CREATE VIEW [v] AS SELECT 1 as col");
        String ddl = viewEditor.generateCreateDefinitionDDL(view);
        Assert.assertEquals("CREATE VIEW [v] AS SELECT 1 as col", ddl);
    }

    @Test
    public void generateUpdateObjectDDL() {
        DBView oldView = new DBView();
        DBView newView = new DBView();
        newView.setDdl("CREATE OR ALTER VIEW [v] AS SELECT 2 as col");
        String ddl = viewEditor.generateUpdateObjectDDL(oldView, newView);
        Assert.assertEquals("CREATE OR ALTER VIEW [v] AS SELECT 2 as col", ddl);
    }

    @Test
    public void generateDropObjectDDL() {
        DBView view = new DBView();
        view.setSchemaName("dbo");
        view.setViewName("test_view");
        String ddl = viewEditor.generateDropObjectDDL(view);
        Assert.assertEquals("DROP VIEW [dbo].[test_view]", ddl);
    }

    @Test
    public void generateRenameObjectDDL() {
        DBView oldView = new DBView();
        oldView.setSchemaName("dbo");
        oldView.setViewName("old_view");
        DBView newView = new DBView();
        newView.setViewName("new_view");
        String ddl = viewEditor.generateRenameObjectDDL(oldView, newView);
        Assert.assertEquals("EXEC sp_rename 'dbo.old_view', 'new_view'", ddl);
    }

}
