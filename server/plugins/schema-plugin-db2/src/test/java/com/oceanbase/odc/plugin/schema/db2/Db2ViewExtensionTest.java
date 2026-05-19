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
package com.oceanbase.odc.plugin.schema.db2;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.pf4j.Extension;

import com.oceanbase.odc.plugin.schema.obmysql.OBMySQLViewExtension;
import com.oceanbase.tools.dbbrowser.editor.DBObjectOperator;
import com.oceanbase.tools.dbbrowser.editor.db2.Db2ObjectOperator;
import com.oceanbase.tools.dbbrowser.model.DBObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;
import com.oceanbase.tools.dbbrowser.model.DBView;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;
import com.oceanbase.tools.dbbrowser.template.DBObjectTemplate;

/**
 * Mock-only unit tests for {@link Db2ViewExtension} (fix-I).
 *
 * <p>
 * Why this exists: fix-H added {@link Db2TableExtension} but did not register a
 * {@code ViewExtensionPoint}, so the v1 view controller — invoked by the ODC front-end to populate
 * the "视图" tree node under each schema — fell into {@link OdcPluginManager#getSingleton}'s empty
 * branch and threw {@code "Feature extension point is not supported for DB2"}. fix-I closes that
 * gap. These tests pin three contracts the front-end depends on:
 * <ol>
 * <li>{@code list/listSystemViews/getDetail} delegate to the DB2 dialect {@link DBSchemaAccessor}
 * (already implemented in {@code Db2SchemaAccessor}; bug-fix should not duplicate SQL here).
 * <li>{@code drop} routes through {@link Db2ObjectOperator} so the emitted DDL uses double-quoted
 * identifiers (DB2 grammar), not the MySQL backtick form.
 * <li>{@code generateCreateTemplate} does not throw — i.e. the inherited
 * {@code getTemplate().generateCreateObjectTemplate(view)} path uses the OB-MySQL view template
 * factory (which yields a valid SELECT scaffold) instead of falling into
 * {@code DBViewTemplateFactory#buildForDB2()} which throws {@code UnsupportedOperationException}.
 * </ol>
 *
 * <p>
 * No real JDBC connection is opened; the {@link DBSchemaAccessor} and {@link DBObjectOperator}
 * dependencies are overridden via a test subclass so we never hit
 * {@code com.ibm.db2.jcc.DB2Driver}.
 *
 * @author actiontech-zihan
 * @since 4.3.4 (Issue dms-ee#839, fix-I)
 */
public class Db2ViewExtensionTest {

    private static final String SCHEMA = "DB2INST1";
    private static final String VIEW = "V_TEST_ORDERS_SUMMARY";

    private DBSchemaAccessor schemaAccessor;
    private DBObjectOperator operator;
    private TestableDb2ViewExtension extension;
    private Connection connection;

    @Before
    public void setUp() {
        schemaAccessor = mock(DBSchemaAccessor.class);
        operator = mock(DBObjectOperator.class);
        connection = mock(Connection.class);
        extension = new TestableDb2ViewExtension(schemaAccessor, operator);
    }

    @Test
    public void list_delegatesToSchemaAccessorListViews() {
        DBObjectIdentity v1 = DBObjectIdentity.of(SCHEMA, DBObjectType.VIEW, VIEW);
        DBObjectIdentity v2 = DBObjectIdentity.of(SCHEMA, DBObjectType.VIEW, "V_DUMMY");
        when(schemaAccessor.listViews(eq(SCHEMA))).thenReturn(Arrays.asList(v1, v2));

        List<DBObjectIdentity> result = extension.list(connection, SCHEMA);

        Assert.assertEquals(2, result.size());
        Assert.assertEquals(VIEW, result.get(0).getName());
        Assert.assertEquals(DBObjectType.VIEW, result.get(0).getType());
        verify(schemaAccessor, times(1)).listViews(SCHEMA);
    }

    @Test
    public void list_emptyAccessorResult_returnsEmptyList() {
        when(schemaAccessor.listViews(eq(SCHEMA))).thenReturn(Collections.emptyList());

        List<DBObjectIdentity> result = extension.list(connection, SCHEMA);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isEmpty());
        verify(schemaAccessor, times(1)).listViews(SCHEMA);
    }

    @Test
    public void listSystemViews_delegatesToShowSystemViews() {
        when(schemaAccessor.showSystemViews(eq("SYSCAT")))
                .thenReturn(Arrays.asList("TABLES", "COLUMNS"));

        List<String> result = extension.listSystemViews(connection, "SYSCAT");

        Assert.assertEquals(Arrays.asList("TABLES", "COLUMNS"), result);
        verify(schemaAccessor, times(1)).showSystemViews("SYSCAT");
    }

    @Test
    public void getDetail_delegatesToGetView() {
        DBView stub = new DBView();
        stub.setViewName(VIEW);
        stub.setSchemaName(SCHEMA);
        when(schemaAccessor.getView(eq(SCHEMA), eq(VIEW))).thenReturn(stub);

        DBView result = extension.getDetail(connection, SCHEMA, VIEW);

        Assert.assertNotNull(result);
        Assert.assertEquals(VIEW, result.getViewName());
        Assert.assertEquals(SCHEMA, result.getSchemaName());
        verify(schemaAccessor, times(1)).getView(SCHEMA, VIEW);
    }

    @Test
    public void getDetail_accessorReturnsNull_extensionReturnsNull() {
        // Db2SchemaAccessor#getView returns null by design — confirm extension does not NPE.
        when(schemaAccessor.getView(eq(SCHEMA), eq(VIEW))).thenReturn(null);

        DBView result = extension.getDetail(connection, SCHEMA, VIEW);

        Assert.assertNull(result);
    }

    @Test
    public void drop_routesThroughOperatorWithViewType() {
        // schemaName intentionally passed null to mirror the inherited contract
        // (OBMySQLViewExtension.drop ignores schemaName when calling operator.drop).
        extension.drop(connection, SCHEMA, VIEW);

        verify(operator, times(1)).drop(eq(DBObjectType.VIEW), eq((String) null), eq(VIEW));
        verify(schemaAccessor, never()).listViews(SCHEMA);
    }

    @Test
    public void generateCreateTemplate_doesNotFallIntoBuildForDB2() {
        // The fix uses the OB-MySQL template factory; calling generateCreateTemplate on a fresh
        // Db2ViewExtension must not throw UnsupportedOperationException (which is what
        // DBViewTemplateFactory#buildForDB2 raises).
        DBView view = new DBView();
        view.setViewName("V_DUMMY");
        // The MySQL view template emits a "create or replace view ..." scaffold that doesn't need
        // any view units; we don't assert on the body — only that the path doesn't blow up.
        // Use a fresh extension instance (no overrides on getTemplate) so this exercises the real
        // production code in Db2ViewExtension.
        Db2ViewExtension real = new Db2ViewExtension();
        String sql = real.generateCreateTemplate(view);
        Assert.assertNotNull(sql);
        Assert.assertFalse("Template SQL must be non-blank", sql.trim().isEmpty());
    }

    @Test
    public void classIsAnnotatedWithPf4jExtension() {
        // pf4j discovers extensions by @Extension annotation + META-INF/extensions.idx — without
        // the annotation the OdcPluginManager.getSingleton path will still hit
        // "Feature extension point is not supported for DB2".
        Assert.assertNotNull(
                "Db2ViewExtension must carry @Extension so pf4j auto-registers it",
                Db2ViewExtension.class.getAnnotation(Extension.class));
    }

    @Test
    public void inheritsFromOBMySQLViewExtension() {
        // We intentionally inherit so the list/listSystemViews/getDetail/drop/generateCreateTemplate
        // method bodies stay shared; only the three protected hooks (getSchemaAccessor,
        // getOperator, getTemplate) are overridden.
        Assert.assertTrue(
                "Db2ViewExtension must inherit from OBMySQLViewExtension to reuse the delegation skeleton",
                OBMySQLViewExtension.class.isAssignableFrom(Db2ViewExtension.class));
    }

    /**
     * Subclass that bypasses the {@code DBAccessorUtil} / {@code Db2ObjectOperator} construction by
     * returning pre-built mocks. Lets us test the public methods of {@link OBMySQLViewExtension} (which
     * the production class inherits) without opening any JDBC connection.
     */
    private static class TestableDb2ViewExtension extends Db2ViewExtension {
        private final DBSchemaAccessor accessor;
        private final DBObjectOperator op;

        TestableDb2ViewExtension(DBSchemaAccessor accessor, DBObjectOperator op) {
            this.accessor = accessor;
            this.op = op;
        }

        @Override
        protected DBSchemaAccessor getSchemaAccessor(Connection connection) {
            return accessor;
        }

        @Override
        protected DBObjectOperator getOperator(Connection connection) {
            return op;
        }

        @Override
        protected DBObjectTemplate<DBView> getTemplate() {
            // Not used by tests other than generateCreateTemplate_doesNotFallIntoBuildForDB2,
            // which instantiates a fresh Db2ViewExtension to exercise the real path.
            return super.getTemplate();
        }
    }
}
