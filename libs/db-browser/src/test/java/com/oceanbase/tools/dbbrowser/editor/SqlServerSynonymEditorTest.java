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

import com.oceanbase.tools.dbbrowser.editor.sqlserver.SqlServerSynonymEditor;
import com.oceanbase.tools.dbbrowser.model.DBSynonym;

/**
 * @description: all tests for {@link SqlServerSynonymEditor}
 * @author: yizhou.xw
 * @date: 2024/12
 * @since: ODC_release_4.3.4
 */
public class SqlServerSynonymEditorTest {

    private SqlServerSynonymEditor synonymEditor;

    @Before
    public void setUp() {
        synonymEditor = new SqlServerSynonymEditor();
    }

    @Test
    public void generateCreateObjectDDL() {
        DBSynonym synonym = new DBSynonym();
        synonym.setSynonymName("syn1");
        synonym.setTableName("table1");
        String ddl = synonymEditor.generateCreateObjectDDL(synonym);
        Assert.assertEquals("CREATE SYNONYM [syn1] FOR table1", ddl);
    }

    @Test
    public void generateCreateDefinitionDDL() {
        DBSynonym synonym = new DBSynonym();
        synonym.setSynonymName("syn1");
        synonym.setTableName("table1");
        String ddl = synonymEditor.generateCreateDefinitionDDL(synonym);
        Assert.assertEquals("CREATE SYNONYM [syn1] FOR table1", ddl);
    }

    @Test
    public void generateUpdateObjectDDL() {
        DBSynonym oldSynonym = new DBSynonym();
        oldSynonym.setSynonymName("syn1");
        oldSynonym.setTableName("table1");

        DBSynonym newSynonym = new DBSynonym();
        newSynonym.setSynonymName("syn2");
        newSynonym.setTableName("table2");

        String ddl = synonymEditor.generateUpdateObjectDDL(oldSynonym, newSynonym);
        Assert.assertEquals("DROP SYNONYM [syn1];\nCREATE SYNONYM [syn2] FOR table2", ddl);
    }

    @Test
    public void generateDropObjectDDL() {
        DBSynonym synonym = new DBSynonym();
        synonym.setSynonymName("syn1");
        String ddl = synonymEditor.generateDropObjectDDL(synonym);
        Assert.assertEquals("DROP SYNONYM [syn1]", ddl);
    }

}
