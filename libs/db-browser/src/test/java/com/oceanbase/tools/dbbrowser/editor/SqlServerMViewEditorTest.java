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

import com.oceanbase.tools.dbbrowser.editor.sqlserver.SqlServerIndexEditor;
import com.oceanbase.tools.dbbrowser.editor.sqlserver.SqlServerMViewEditor;
import com.oceanbase.tools.dbbrowser.model.DBMaterializedView;

/**
 * @description: all tests for {@link SqlServerMViewEditor}
 * @author: yizhou.xw
 * @date: 2024/12
 * @since: ODC_release_4.3.4
 */
public class SqlServerMViewEditorTest {

    private SqlServerMViewEditor mViewEditor;

    @Before
    public void setUp() {
        mViewEditor = new SqlServerMViewEditor(new SqlServerIndexEditor());
    }

    @Test
    public void generateCreateObjectDDL() {
        DBMaterializedView mView = new DBMaterializedView();
        mView.setName("test_mview");
        mView.setDdl("SELECT col1 FROM dbo.test_table");

        String ddl = mViewEditor.generateCreateObjectDDL(mView);
        Assert.assertEquals("CREATE VIEW [test_mview] WITH SCHEMABINDING AS SELECT col1 FROM dbo.test_table", ddl);
    }

    @Test
    public void generateDropObjectDDL() {
        DBMaterializedView mView = new DBMaterializedView();
        mView.setName("test_mview");

        String ddl = mViewEditor.generateDropObjectDDL(mView);
        Assert.assertEquals("DROP VIEW [test_mview]", ddl);
    }

    @Test
    public void generateUpdateObjectDDL() {
        DBMaterializedView oldMView = new DBMaterializedView();
        oldMView.setName("test_mview");
        oldMView.setDdl("SELECT col1 FROM dbo.test_table");

        DBMaterializedView newMView = new DBMaterializedView();
        newMView.setName("test_mview");
        newMView.setDdl("SELECT col1, col2 FROM dbo.test_table");

        String ddl = mViewEditor.generateUpdateObjectDDL(oldMView, newMView);
        Assert.assertEquals("ALTER VIEW [test_mview] WITH SCHEMABINDING AS SELECT col1, col2 FROM dbo.test_table", ddl);
    }

}
