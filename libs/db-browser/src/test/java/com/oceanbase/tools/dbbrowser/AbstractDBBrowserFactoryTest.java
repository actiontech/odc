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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.oceanbase.tools.dbbrowser.editor.DBMViewEditor;
import com.oceanbase.tools.dbbrowser.editor.DBMViewEditorFactory;
import com.oceanbase.tools.dbbrowser.editor.DBMViewIndexEditorFactory;
import com.oceanbase.tools.dbbrowser.editor.DBObjectEditor;
import com.oceanbase.tools.dbbrowser.editor.DBObjectOperator;
import com.oceanbase.tools.dbbrowser.editor.DBObjectOperatorFactory;
import com.oceanbase.tools.dbbrowser.editor.DBSequenceEditorFactory;
import com.oceanbase.tools.dbbrowser.editor.DBSynonymEditorFactory;
import com.oceanbase.tools.dbbrowser.editor.DBTableColumnEditor;
import com.oceanbase.tools.dbbrowser.editor.DBTableColumnEditorFactory;
import com.oceanbase.tools.dbbrowser.editor.DBTableConstraintEditor;
import com.oceanbase.tools.dbbrowser.editor.DBTableConstraintEditorFactory;
import com.oceanbase.tools.dbbrowser.editor.DBTableEditor;
import com.oceanbase.tools.dbbrowser.editor.DBTableEditorFactory;
import com.oceanbase.tools.dbbrowser.editor.DBTableIndexEditor;
import com.oceanbase.tools.dbbrowser.editor.DBTableIndexEditorFactory;
import com.oceanbase.tools.dbbrowser.editor.DBTablePartitionEditor;
import com.oceanbase.tools.dbbrowser.editor.DBTablePartitionEditorFactory;
import com.oceanbase.tools.dbbrowser.model.DBFunction;
import com.oceanbase.tools.dbbrowser.model.DBMaterializedView;
import com.oceanbase.tools.dbbrowser.model.DBPackage;
import com.oceanbase.tools.dbbrowser.model.DBProcedure;
import com.oceanbase.tools.dbbrowser.model.DBSequence;
import com.oceanbase.tools.dbbrowser.model.DBSynonym;
import com.oceanbase.tools.dbbrowser.model.DBTrigger;
import com.oceanbase.tools.dbbrowser.model.DBType;
import com.oceanbase.tools.dbbrowser.model.DBView;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessorFactory;
import com.oceanbase.tools.dbbrowser.stats.DBStatsAccessor;
import com.oceanbase.tools.dbbrowser.stats.DBStatsAccessorFactory;
import com.oceanbase.tools.dbbrowser.template.DBFunctionTemplateFactory;
import com.oceanbase.tools.dbbrowser.template.DBMViewTemplateFactory;
import com.oceanbase.tools.dbbrowser.template.DBObjectTemplate;
import com.oceanbase.tools.dbbrowser.template.DBPackageTemplateFactory;
import com.oceanbase.tools.dbbrowser.template.DBProcedureTemplateFactory;
import com.oceanbase.tools.dbbrowser.template.DBTriggerTemplateFactory;
import com.oceanbase.tools.dbbrowser.template.DBTypeTemplateFactory;
import com.oceanbase.tools.dbbrowser.template.DBViewTemplateFactory;

/**
 * Map-case unit tests covering {@code buildForDB2()} across every concrete subclass of
 * {@link AbstractDBBrowserFactory} (commit-1 of T-003).
 *
 * <p>
 * This commit ships placeholder implementations only: every {@code buildForDB2()} throws
 * {@link UnsupportedOperationException} with the grep-friendly keyword {@code "Not supported for
 * DB2 yet"} (see {@code docs/dev/compat_risks.md} compat-RISK-7). commit-2 of T-003 wires the
 * Schema / TableEditor / ColumnEditor factories to real {@code DB2*} implementations and the
 * corresponding rows in {@link #factories()} will then be updated.
 */
public class AbstractDBBrowserFactoryTest {

    private static final String DB2_NOT_SUPPORTED = "Not supported for DB2 yet";

    /**
     * Every concrete factory subclass with its name (for assertion failure messages). Keep this list
     * aligned with {@link AbstractDBBrowserFactory}'s set of abstract {@code buildForXxx} methods so
     * that adding a new factory subclass forces this test to be updated.
     */
    private static List<Map.Entry<String, AbstractDBBrowserFactory<?>>> factories() {
        Map<String, AbstractDBBrowserFactory<?>> m = new java.util.LinkedHashMap<>();
        m.put("DBSchemaAccessorFactory", new DBSchemaAccessorFactory());
        m.put("DBTableEditorFactory", new DBTableEditorFactory());
        m.put("DBTableColumnEditorFactory", new DBTableColumnEditorFactory());
        m.put("DBTableConstraintEditorFactory", new DBTableConstraintEditorFactory());
        m.put("DBTableIndexEditorFactory", new DBTableIndexEditorFactory());
        m.put("DBTablePartitionEditorFactory", new DBTablePartitionEditorFactory());
        m.put("DBObjectOperatorFactory", new DBObjectOperatorFactory());
        m.put("DBSequenceEditorFactory", new DBSequenceEditorFactory());
        m.put("DBSynonymEditorFactory", new DBSynonymEditorFactory());
        m.put("DBMViewEditorFactory", new DBMViewEditorFactory());
        m.put("DBMViewIndexEditorFactory", new DBMViewIndexEditorFactory());
        m.put("DBStatsAccessorFactory", new DBStatsAccessorFactory());
        m.put("DBFunctionTemplateFactory", new DBFunctionTemplateFactory());
        m.put("DBMViewTemplateFactory", new DBMViewTemplateFactory());
        m.put("DBPackageTemplateFactory", new DBPackageTemplateFactory());
        m.put("DBProcedureTemplateFactory", new DBProcedureTemplateFactory());
        m.put("DBTriggerTemplateFactory", new DBTriggerTemplateFactory());
        m.put("DBTypeTemplateFactory", new DBTypeTemplateFactory());
        m.put("DBViewTemplateFactory", new DBViewTemplateFactory());
        return new java.util.ArrayList<>(m.entrySet());
    }

    @Test
    public void factoryMatrix_buildForDB2_allThrowUnsupportedWithDb2Keyword() {
        List<Map.Entry<String, AbstractDBBrowserFactory<?>>> matrix = factories();
        // sanity guard: keep the matrix size pinned so adding/removing a factory subclass forces
        // a deliberate test update (compat-RISK-7 coverage owner per docs/dev/compat_risks.md).
        assertEquals("factory subclass count drifted; update both code and case_ids list",
                19, matrix.size());
        for (Map.Entry<String, AbstractDBBrowserFactory<?>> e : matrix) {
            try {
                e.getValue().buildForDB2();
                fail("Expected UnsupportedOperationException from " + e.getKey() + ".buildForDB2()");
            } catch (UnsupportedOperationException ex) {
                assertNotNull(e.getKey() + " threw UOE without a message", ex.getMessage());
                assertTrue(e.getKey() + " message should contain DB2 keyword: " + ex.getMessage(),
                        ex.getMessage().contains("DB2"));
                assertEquals(e.getKey() + " message should match standard skeleton text",
                        DB2_NOT_SUPPORTED, ex.getMessage());
            }
        }
    }

    @Test
    public void createWithTypeDB2_DBSchemaAccessorFactory_routesToBuildForDB2() {
        DBSchemaAccessorFactory factory = (DBSchemaAccessorFactory) new DBSchemaAccessorFactory()
                .setType(DBBrowserFactory.DB2);
        assertCreateDispatchToBuildForDB2(factory, "DBSchemaAccessorFactory");
    }

    @Test
    public void createWithTypeDB2_DBTableEditorFactory_routesToBuildForDB2() {
        DBTableEditorFactory factory = (DBTableEditorFactory) new DBTableEditorFactory()
                .setType(DBBrowserFactory.DB2);
        assertCreateDispatchToBuildForDB2(factory, "DBTableEditorFactory");
    }

    @Test
    public void createWithTypeDB2_DBTableColumnEditorFactory_routesToBuildForDB2() {
        DBTableColumnEditorFactory factory = new DBTableColumnEditorFactory();
        factory.setType(DBBrowserFactory.DB2);
        assertCreateDispatchToBuildForDB2(factory, "DBTableColumnEditorFactory");
    }

    @Test
    public void buildForDB2_DBTableConstraintEditorFactory_unsupportedWithDb2Keyword() {
        DBTableConstraintEditor editor = null;
        try {
            DBTableConstraintEditorFactory f = new DBTableConstraintEditorFactory();
            f.setType(DBBrowserFactory.DB2);
            editor = (DBTableConstraintEditor) f.create();
            fail("Expected UOE");
        } catch (UnsupportedOperationException ex) {
            assertTrue(ex.getMessage().contains("DB2"));
        }
        // editor remains null (placeholder)
        assertEquals("editor must remain null when factory threw", null, editor);
    }

    @Test
    public void buildForDB2_DBTableIndexEditorFactory_unsupportedWithDb2Keyword() {
        try {
            DBTableIndexEditor ignored = new DBTableIndexEditorFactory().buildForDB2();
            fail("Expected UOE; got " + ignored);
        } catch (UnsupportedOperationException ex) {
            assertTrue(ex.getMessage().contains("DB2"));
        }
    }

    @Test
    public void buildForDB2_DBTablePartitionEditorFactory_unsupportedWithDb2Keyword() {
        try {
            DBTablePartitionEditor ignored = new DBTablePartitionEditorFactory().buildForDB2();
            fail("Expected UOE; got " + ignored);
        } catch (UnsupportedOperationException ex) {
            assertTrue(ex.getMessage().contains("DB2"));
        }
    }

    @Test
    public void buildForDB2_DBObjectOperatorFactory_unsupportedWithDb2Keyword() {
        try {
            DBObjectOperator ignored = new DBObjectOperatorFactory().buildForDB2();
            fail("Expected UOE; got " + ignored);
        } catch (UnsupportedOperationException ex) {
            assertTrue(ex.getMessage().contains("DB2"));
        }
    }

    @Test
    public void buildForDB2_DBSequenceEditorFactory_unsupportedWithDb2Keyword() {
        try {
            DBObjectEditor<DBSequence> ignored = new DBSequenceEditorFactory().buildForDB2();
            fail("Expected UOE; got " + ignored);
        } catch (UnsupportedOperationException ex) {
            assertTrue(ex.getMessage().contains("DB2"));
        }
    }

    @Test
    public void buildForDB2_DBSynonymEditorFactory_unsupportedWithDb2Keyword() {
        try {
            DBObjectEditor<DBSynonym> ignored = new DBSynonymEditorFactory().buildForDB2();
            fail("Expected UOE; got " + ignored);
        } catch (UnsupportedOperationException ex) {
            assertTrue(ex.getMessage().contains("DB2"));
        }
    }

    @Test
    public void buildForDB2_DBMViewEditorFactory_unsupportedWithDb2Keyword() {
        try {
            DBMViewEditor ignored = new DBMViewEditorFactory().buildForDB2();
            fail("Expected UOE; got " + ignored);
        } catch (UnsupportedOperationException ex) {
            assertTrue(ex.getMessage().contains("DB2"));
        }
    }

    @Test
    public void buildForDB2_DBMViewIndexEditorFactory_unsupportedWithDb2Keyword() {
        try {
            DBTableIndexEditor ignored = new DBMViewIndexEditorFactory().buildForDB2();
            fail("Expected UOE; got " + ignored);
        } catch (UnsupportedOperationException ex) {
            assertTrue(ex.getMessage().contains("DB2"));
        }
    }

    @Test
    public void buildForDB2_DBStatsAccessorFactory_unsupportedWithDb2Keyword() {
        try {
            DBStatsAccessor ignored = new DBStatsAccessorFactory().buildForDB2();
            fail("Expected UOE; got " + ignored);
        } catch (UnsupportedOperationException ex) {
            assertTrue(ex.getMessage().contains("DB2"));
        }
    }

    @Test
    public void buildForDB2_DBFunctionTemplateFactory_unsupportedWithDb2Keyword() {
        try {
            DBObjectTemplate<DBFunction> ignored = new DBFunctionTemplateFactory().buildForDB2();
            fail("Expected UOE; got " + ignored);
        } catch (UnsupportedOperationException ex) {
            assertTrue(ex.getMessage().contains("DB2"));
        }
    }

    @Test
    public void buildForDB2_DBMViewTemplateFactory_unsupportedWithDb2Keyword() {
        try {
            DBObjectTemplate<DBMaterializedView> ignored = new DBMViewTemplateFactory()
                    .buildForDB2();
            fail("Expected UOE; got " + ignored);
        } catch (UnsupportedOperationException ex) {
            assertTrue(ex.getMessage().contains("DB2"));
        }
    }

    @Test
    public void buildForDB2_DBPackageTemplateFactory_unsupportedWithDb2Keyword() {
        try {
            DBObjectTemplate<DBPackage> ignored = new DBPackageTemplateFactory().buildForDB2();
            fail("Expected UOE; got " + ignored);
        } catch (UnsupportedOperationException ex) {
            assertTrue(ex.getMessage().contains("DB2"));
        }
    }

    @Test
    public void buildForDB2_DBProcedureTemplateFactory_unsupportedWithDb2Keyword() {
        try {
            DBObjectTemplate<DBProcedure> ignored = new DBProcedureTemplateFactory().buildForDB2();
            fail("Expected UOE; got " + ignored);
        } catch (UnsupportedOperationException ex) {
            assertTrue(ex.getMessage().contains("DB2"));
        }
    }

    @Test
    public void buildForDB2_DBTriggerTemplateFactory_unsupportedWithDb2Keyword() {
        try {
            DBObjectTemplate<DBTrigger> ignored = new DBTriggerTemplateFactory().buildForDB2();
            fail("Expected UOE; got " + ignored);
        } catch (UnsupportedOperationException ex) {
            assertTrue(ex.getMessage().contains("DB2"));
        }
    }

    @Test
    public void buildForDB2_DBTypeTemplateFactory_unsupportedWithDb2Keyword() {
        try {
            DBObjectTemplate<DBType> ignored = new DBTypeTemplateFactory().buildForDB2();
            fail("Expected UOE; got " + ignored);
        } catch (UnsupportedOperationException ex) {
            assertTrue(ex.getMessage().contains("DB2"));
        }
    }

    @Test
    public void buildForDB2_DBViewTemplateFactory_unsupportedWithDb2Keyword() {
        try {
            DBObjectTemplate<DBView> ignored = new DBViewTemplateFactory().buildForDB2();
            fail("Expected UOE; got " + ignored);
        } catch (UnsupportedOperationException ex) {
            assertTrue(ex.getMessage().contains("DB2"));
        }
    }

    @Test
    public void createWithTypeDB2_DBSchemaAccessorFactory_dispatchesToBuildForDB2WithSameMessage() {
        // Sanity: AbstractDBBrowserFactory.create() switch case for DB2 must call buildForDB2()
        // (not throw IllegalStateException from default branch).
        DBSchemaAccessorFactory f = new DBSchemaAccessorFactory();
        f.setType(DBBrowserFactory.DB2);
        try {
            f.create();
            fail("Expected UOE thrown by buildForDB2()");
        } catch (UnsupportedOperationException ex) {
            // dispatched correctly
            assertEquals(DB2_NOT_SUPPORTED, ex.getMessage());
        } catch (IllegalStateException ex) {
            fail("Switch fell through to default branch (= buildForDB2 not wired): " + ex);
        }
    }

    @Test
    public void createWithTypeOTHER_AbstractFactory_throwsIllegalStateNotUnsupported() {
        // Sanity: unknown type still routes to default IllegalStateException branch and does NOT
        // accidentally call buildForDB2 (would be a regression).
        DBSchemaAccessorFactory f = new DBSchemaAccessorFactory();
        f.setType("OB_PROXY");
        try {
            f.create();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException ex) {
            assertTrue(ex.getMessage().contains("Not supported for the type"));
            assertTrue(ex.getMessage().contains("OB_PROXY"));
        }
    }

    @Test
    public void factoryMatrix_buildForDB2_messageDistinctFromGenericNotSupportedYet() {
        // grep-friendly assertion: every DB2 placeholder uses "Not supported for DB2 yet"
        // (not the generic "Not supported yet" reused by Postgres / Doris / etc.).
        // This protects compat-RISK-10 (readable error messages).
        for (Map.Entry<String, AbstractDBBrowserFactory<?>> e : factories()) {
            try {
                e.getValue().buildForDB2();
                fail("Expected UOE from " + e.getKey());
            } catch (UnsupportedOperationException ex) {
                String msg = ex.getMessage();
                assertTrue(e.getKey() + " message must reference DB2: " + msg, msg.contains("DB2"));
            }
        }
    }

    @Test
    public void factoryMatrix_consistencyCheck_factoriesListInSyncWithSourceFiles() {
        // Lightweight sanity: we expect at least these 19 well-known factory names; if a future
        // commit drops one of them this list will need to be edited deliberately.
        List<String> expected = Arrays.asList(
                "DBSchemaAccessorFactory",
                "DBTableEditorFactory",
                "DBTableColumnEditorFactory",
                "DBTableConstraintEditorFactory",
                "DBTableIndexEditorFactory",
                "DBTablePartitionEditorFactory",
                "DBObjectOperatorFactory",
                "DBSequenceEditorFactory",
                "DBSynonymEditorFactory",
                "DBMViewEditorFactory",
                "DBMViewIndexEditorFactory",
                "DBStatsAccessorFactory",
                "DBFunctionTemplateFactory",
                "DBMViewTemplateFactory",
                "DBPackageTemplateFactory",
                "DBProcedureTemplateFactory",
                "DBTriggerTemplateFactory",
                "DBTypeTemplateFactory",
                "DBViewTemplateFactory");
        Map<String, AbstractDBBrowserFactory<?>> actual = new HashMap<>();
        for (Map.Entry<String, AbstractDBBrowserFactory<?>> e : factories()) {
            actual.put(e.getKey(), e.getValue());
        }
        for (String name : expected) {
            assertTrue("missing factory in matrix: " + name, actual.containsKey(name));
        }
        assertEquals(expected.size(), actual.size());
    }

    private static void assertCreateDispatchToBuildForDB2(AbstractDBBrowserFactory<?> factory,
            String name) {
        try {
            factory.create();
            fail("Expected UOE; " + name + " buildForDB2 must throw for placeholder commit");
        } catch (UnsupportedOperationException ex) {
            assertEquals("dispatch path from create() should land on buildForDB2() (commit-1)",
                    DB2_NOT_SUPPORTED, ex.getMessage());
        }
    }

    // suppress unused warnings for type-only imports
    @SuppressWarnings("unused")
    private void unusedImportAnchors(DBSchemaAccessor a, DBTableEditor b, DBTableColumnEditor c) {}

}
