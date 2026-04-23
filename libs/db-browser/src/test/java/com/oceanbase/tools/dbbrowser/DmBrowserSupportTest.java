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
package com.oceanbase.tools.dbbrowser;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.tools.dbbrowser.editor.DBMViewEditorFactory;
import com.oceanbase.tools.dbbrowser.editor.DBMViewIndexEditorFactory;
import com.oceanbase.tools.dbbrowser.editor.DBSequenceEditorFactory;
import com.oceanbase.tools.dbbrowser.editor.DBSynonymEditorFactory;
import com.oceanbase.tools.dbbrowser.editor.DBTableColumnEditorFactory;
import com.oceanbase.tools.dbbrowser.editor.DBTableConstraintEditorFactory;
import com.oceanbase.tools.dbbrowser.editor.DBTableEditorFactory;
import com.oceanbase.tools.dbbrowser.editor.DBTableIndexEditorFactory;
import com.oceanbase.tools.dbbrowser.editor.DBTablePartitionEditorFactory;
import com.oceanbase.tools.dbbrowser.editor.GeneralSqlStatementBuilder;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;
import com.oceanbase.tools.dbbrowser.template.DBFunctionTemplateFactory;
import com.oceanbase.tools.dbbrowser.template.DBProcedureTemplateFactory;
import com.oceanbase.tools.dbbrowser.template.DBTriggerTemplateFactory;
import com.oceanbase.tools.dbbrowser.template.DBTypeTemplateFactory;
import com.oceanbase.tools.dbbrowser.template.DBViewTemplateFactory;
import com.oceanbase.tools.dbbrowser.util.DmSqlBuilder;

/**
 * Unit tests for DM (DaMeng) database support in db-browser. Tests factory routing for the DM
 * database type, and verifies that DM-specific components produce correct SQL output.
 *
 * These tests do not require a real database connection.
 */
public class DmBrowserSupportTest {

    // =================== Factory Routing Tests ===================

    @Test
    public void editorFactories_dmType_createSuccessfully() {
        // Editor factories that delegate to Oracle via buildForOracle()
        // Verify they can be instantiated without errors

        DBTableEditorFactory tableEditorFactory = new DBTableEditorFactory();
        tableEditorFactory.setType(DBBrowserFactory.DM);
        tableEditorFactory.setDbVersion("8.0");
        Assert.assertNotNull(tableEditorFactory.create());

        DBTableColumnEditorFactory columnEditorFactory = new DBTableColumnEditorFactory();
        columnEditorFactory.setType(DBBrowserFactory.DM);
        Assert.assertNotNull(columnEditorFactory.create());

        DBTableConstraintEditorFactory constraintEditorFactory = new DBTableConstraintEditorFactory();
        constraintEditorFactory.setType(DBBrowserFactory.DM);
        Assert.assertNotNull(constraintEditorFactory.create());

        DBTableIndexEditorFactory indexEditorFactory = new DBTableIndexEditorFactory();
        indexEditorFactory.setType(DBBrowserFactory.DM);
        Assert.assertNotNull(indexEditorFactory.create());

        DBTablePartitionEditorFactory partitionEditorFactory = new DBTablePartitionEditorFactory();
        partitionEditorFactory.setType(DBBrowserFactory.DM);
        partitionEditorFactory.setDbVersion("8.0");
        Assert.assertNotNull(partitionEditorFactory.create());

        DBSequenceEditorFactory sequenceEditorFactory = new DBSequenceEditorFactory();
        sequenceEditorFactory.setType(DBBrowserFactory.DM);
        Assert.assertNotNull(sequenceEditorFactory.create());

        DBSynonymEditorFactory synonymEditorFactory = new DBSynonymEditorFactory();
        synonymEditorFactory.setType(DBBrowserFactory.DM);
        Assert.assertNotNull(synonymEditorFactory.create());

        // DBMViewEditorFactory.buildForOracle() is UnsupportedOperationException,
        // so DM also inherits that unsupported status - verify it throws correctly.
        DBMViewEditorFactory mViewEditorFactory = new DBMViewEditorFactory();
        mViewEditorFactory.setType(DBBrowserFactory.DM);
        try {
            mViewEditorFactory.create();
            Assert.fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected: Oracle MView editor is not supported, DM inherits this
        }

        // DBMViewIndexEditorFactory.buildForOracle() is UnsupportedOperationException,
        // so DM also inherits that unsupported status - verify it throws correctly.
        DBMViewIndexEditorFactory mViewIndexEditorFactory = new DBMViewIndexEditorFactory();
        mViewIndexEditorFactory.setType(DBBrowserFactory.DM);
        try {
            mViewIndexEditorFactory.create();
            Assert.fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected: Oracle MView index editor is not supported, DM inherits this
        }
    }

    @Test
    public void templateFactories_dmType_createSuccessfully() {
        // Template factories that delegate to Oracle via buildForOracle()

        DBFunctionTemplateFactory functionTemplateFactory = new DBFunctionTemplateFactory();
        functionTemplateFactory.setType(DBBrowserFactory.DM);
        Assert.assertNotNull(functionTemplateFactory.create());

        DBProcedureTemplateFactory procedureTemplateFactory = new DBProcedureTemplateFactory();
        procedureTemplateFactory.setType(DBBrowserFactory.DM);
        Assert.assertNotNull(procedureTemplateFactory.create());

        DBViewTemplateFactory viewTemplateFactory = new DBViewTemplateFactory();
        viewTemplateFactory.setType(DBBrowserFactory.DM);
        Assert.assertNotNull(viewTemplateFactory.create());

        DBTriggerTemplateFactory triggerTemplateFactory = new DBTriggerTemplateFactory();
        triggerTemplateFactory.setType(DBBrowserFactory.DM);
        Assert.assertNotNull(triggerTemplateFactory.create());

        DBTypeTemplateFactory typeTemplateFactory = new DBTypeTemplateFactory();
        typeTemplateFactory.setType(DBBrowserFactory.DM);
        Assert.assertNotNull(typeTemplateFactory.create());
    }

    @Test
    public void dmBrowserFactoryConstant_isDM() {
        Assert.assertEquals("DM", DBBrowserFactory.DM);
    }

    // =================== DmObjectOperator SQL Generation Tests ===================

    @Test
    public void dropTable_dmSqlBuilder_generatesCorrectDropSql() {
        Map<String, String> testCases = new LinkedHashMap<>();
        testCases.put(
                "DROP TABLE \"SCHEMA1\".\"TABLE1\"",
                GeneralSqlStatementBuilder.drop(new DmSqlBuilder(), DBObjectType.TABLE, "SCHEMA1", "TABLE1"));
        testCases.put(
                "DROP VIEW \"SCHEMA1\".\"VIEW1\"",
                GeneralSqlStatementBuilder.drop(new DmSqlBuilder(), DBObjectType.VIEW, "SCHEMA1", "VIEW1"));
        testCases.put(
                "DROP FUNCTION \"SCHEMA1\".\"FUNC1\"",
                GeneralSqlStatementBuilder.drop(new DmSqlBuilder(), DBObjectType.FUNCTION, "SCHEMA1", "FUNC1"));
        testCases.put(
                "DROP PROCEDURE \"SCHEMA1\".\"PROC1\"",
                GeneralSqlStatementBuilder.drop(new DmSqlBuilder(), DBObjectType.PROCEDURE, "SCHEMA1", "PROC1"));
        testCases.put(
                "DROP SEQUENCE \"SCHEMA1\".\"SEQ1\"",
                GeneralSqlStatementBuilder.drop(new DmSqlBuilder(), DBObjectType.SEQUENCE, "SCHEMA1", "SEQ1"));
        testCases.put(
                "DROP TRIGGER \"SCHEMA1\".\"TRIG1\"",
                GeneralSqlStatementBuilder.drop(new DmSqlBuilder(), DBObjectType.TRIGGER, "SCHEMA1", "TRIG1"));
        testCases.put(
                "DROP PACKAGE \"SCHEMA1\".\"PKG1\"",
                GeneralSqlStatementBuilder.drop(new DmSqlBuilder(), DBObjectType.PACKAGE, "SCHEMA1", "PKG1"));
        testCases.put(
                "DROP PACKAGE BODY \"SCHEMA1\".\"PKG1\"",
                GeneralSqlStatementBuilder.drop(new DmSqlBuilder(), DBObjectType.PACKAGE_BODY, "SCHEMA1", "PKG1"));
        testCases.put(
                "DROP TYPE \"SCHEMA1\".\"TYPE1\"",
                GeneralSqlStatementBuilder.drop(new DmSqlBuilder(), DBObjectType.TYPE, "SCHEMA1", "TYPE1"));
        testCases.put(
                "DROP SYNONYM \"SCHEMA1\".\"SYN1\"",
                GeneralSqlStatementBuilder.drop(new DmSqlBuilder(), DBObjectType.SYNONYM, "SCHEMA1", "SYN1"));
        testCases.put(
                "DROP PUBLIC SYNONYM \"SYN1\"",
                GeneralSqlStatementBuilder.drop(new DmSqlBuilder(), DBObjectType.PUBLIC_SYNONYM, null, "SYN1"));

        for (Map.Entry<String, String> entry : testCases.entrySet()) {
            Assert.assertEquals(entry.getKey(), entry.getValue());
        }
    }

    @Test
    public void dropTable_objectNameWithSpecialChars_escapedCorrectly() {
        String actual = GeneralSqlStatementBuilder.drop(new DmSqlBuilder(), DBObjectType.TABLE,
                "SCHEMA1", "table\"with\"quotes");
        Assert.assertEquals("DROP TABLE \"SCHEMA1\".\"table\"\"with\"\"quotes\"", actual);
    }
}
