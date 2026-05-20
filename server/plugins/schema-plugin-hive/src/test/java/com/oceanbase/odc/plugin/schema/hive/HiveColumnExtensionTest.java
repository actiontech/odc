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
package com.oceanbase.odc.plugin.schema.hive;

import java.sql.Connection;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.oceanbase.tools.dbbrowser.model.DBObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;
import com.oceanbase.tools.dbbrowser.model.DBTable;
import com.oceanbase.tools.dbbrowser.model.DBTableColumn;

/**
 * Unit tests for {@link HiveColumnExtension}. The extension routes {@code SHOW TABLES} results
 * through {@link HiveTableExtension#getDetail(Connection, String, String)}; the table type field
 * (TABLE vs VIEW) of each detail decides which bucket a row lands in. We stub the inner
 * {@code HiveTableExtension} so the test deterministically exercises the routing without any JDBC.
 *
 * <p>
 * Covers compat-RISK R-7.1 (SHOW TABLES happy path) and R-11 (single malformed entry must not abort
 * the listing).
 */
public class HiveColumnExtensionTest {

    private static final String SCHEMA = "default";

    @Test
    public void listBasicTableColumns_routesOnlyTablesIntoTableBucket() {
        HiveTableExtension stub = stubTableExt(
                row(DBObjectType.TABLE, "t1", col("id", "primary"), col("name", null)),
                row(DBObjectType.VIEW, "v1", col("vcol", "from view")));
        HiveColumnExtension ext = withStub(stub);

        Connection conn = Mockito.mock(Connection.class);
        Map<String, List<DBTableColumn>> tables = ext.listBasicTableColumns(conn, SCHEMA);
        Assert.assertEquals(1, tables.size());
        Assert.assertTrue(tables.containsKey("t1"));
        List<DBTableColumn> cols = tables.get("t1");
        Assert.assertEquals(2, cols.size());
        Assert.assertEquals("id", cols.get(0).getName());
        Assert.assertEquals("primary", cols.get(0).getComment());
        Assert.assertEquals(SCHEMA, cols.get(0).getSchemaName());
        Assert.assertEquals("t1", cols.get(0).getTableName());
        Assert.assertEquals("name", cols.get(1).getName());
        Assert.assertNull(cols.get(1).getComment());
    }

    @Test
    public void listBasicViewColumns_routesOnlyViewsIntoViewBucket() {
        HiveTableExtension stub = stubTableExt(
                row(DBObjectType.TABLE, "t1", col("id", null)),
                row(DBObjectType.VIEW, "v1", col("vcol", "from view")));
        HiveColumnExtension ext = withStub(stub);

        Connection conn = Mockito.mock(Connection.class);
        Map<String, List<DBTableColumn>> views = ext.listBasicViewColumns(conn, SCHEMA);
        Assert.assertEquals(1, views.size());
        Assert.assertTrue(views.containsKey("v1"));
        Assert.assertEquals("vcol", views.get("v1").get(0).getName());
        Assert.assertEquals("from view", views.get("v1").get(0).getComment());
    }

    @Test
    public void listBasicColumnsInfo_mergesTableAndViewBuckets() {
        HiveTableExtension stub = stubTableExt(
                row(DBObjectType.TABLE, "t1", col("id", null)),
                row(DBObjectType.VIEW, "v1", col("vcol", null)));
        HiveColumnExtension ext = withStub(stub);

        Connection conn = Mockito.mock(Connection.class);
        Map<String, List<DBTableColumn>> all = ext.listBasicColumnsInfo(conn, SCHEMA);
        Assert.assertEquals(2, all.size());
        Assert.assertTrue(all.containsKey("t1"));
        Assert.assertTrue(all.containsKey("v1"));
    }

    @Test
    public void listBasicTableColumns_malformedEntry_isSkipped() {
        // R-11: when a single object's getDetail blows up the lister must keep going on the rest.
        Connection conn = Mockito.mock(Connection.class);
        HiveTableExtension stub = Mockito.mock(HiveTableExtension.class);
        Mockito.when(stub.list(Mockito.any(), Mockito.eq(SCHEMA), Mockito.eq(DBObjectType.TABLE)))
                .thenReturn(Arrays.asList(
                        DBObjectIdentity.of(SCHEMA, DBObjectType.TABLE, "broken"),
                        DBObjectIdentity.of(SCHEMA, DBObjectType.TABLE, "good")));
        Mockito.when(stub.getDetail(Mockito.any(), Mockito.eq(SCHEMA), Mockito.eq("broken")))
                .thenThrow(new IllegalStateException("describe failed"));
        DBTable good = buildDetail(DBObjectType.TABLE, "good", col("g", null));
        Mockito.when(stub.getDetail(Mockito.any(), Mockito.eq(SCHEMA), Mockito.eq("good")))
                .thenReturn(good);

        HiveColumnExtension ext = withStub(stub);
        Map<String, List<DBTableColumn>> tables = ext.listBasicTableColumns(conn, SCHEMA);
        Assert.assertEquals(1, tables.size());
        Assert.assertTrue(tables.containsKey("good"));
        Assert.assertFalse(tables.containsKey("broken"));
    }

    @Test
    public void listBasicTableColumns_nullColumns_yieldEmptyList() {
        HiveTableExtension stub = Mockito.mock(HiveTableExtension.class);
        Mockito.when(stub.list(Mockito.any(), Mockito.eq(SCHEMA), Mockito.eq(DBObjectType.TABLE)))
                .thenReturn(Collections.singletonList(
                        DBObjectIdentity.of(SCHEMA, DBObjectType.TABLE, "empty_cols")));
        DBTable detail = new DBTable();
        detail.setName("empty_cols");
        detail.setType(DBObjectType.TABLE);
        detail.setColumns(null);
        Mockito.when(stub.getDetail(Mockito.any(), Mockito.eq(SCHEMA), Mockito.eq("empty_cols")))
                .thenReturn(detail);

        HiveColumnExtension ext = withStub(stub);
        Connection conn = Mockito.mock(Connection.class);
        Map<String, List<DBTableColumn>> tables = ext.listBasicTableColumns(conn, SCHEMA);
        Assert.assertEquals(1, tables.size());
        Assert.assertTrue(tables.get("empty_cols").isEmpty());
    }

    // ---------- helpers ----------

    /** Convenience to build a HiveColumnExtension whose internal HiveTableExtension is the stub. */
    private static HiveColumnExtension withStub(final HiveTableExtension stub) {
        return new HiveColumnExtension() {
            @Override
            HiveTableExtension newTableExtension() {
                return stub;
            }
        };
    }

    private static DBTableColumn col(String name, String comment) {
        DBTableColumn c = new DBTableColumn();
        c.setName(name);
        c.setComment(comment);
        return c;
    }

    private static DetailRow row(DBObjectType type, String name, DBTableColumn... cols) {
        return new DetailRow(type, name, Arrays.asList(cols));
    }

    private static DBTable buildDetail(DBObjectType type, String name, DBTableColumn... cols) {
        DBTable t = new DBTable();
        t.setName(name);
        t.setType(type);
        t.setColumns(Arrays.asList(cols));
        return t;
    }

    /**
     * Build a HiveTableExtension stub that returns the supplied {@link DetailRow}s as the listing
     * result; {@code getDetail} is also stubbed to return each row's pre-built {@link DBTable}.
     */
    private static HiveTableExtension stubTableExt(DetailRow... rows) {
        HiveTableExtension stub = Mockito.mock(HiveTableExtension.class);
        java.util.List<DBObjectIdentity> identities = new java.util.ArrayList<>();
        for (DetailRow r : rows) {
            identities.add(DBObjectIdentity.of(SCHEMA, DBObjectType.TABLE, r.name));
        }
        Mockito.when(stub.list(Mockito.any(), Mockito.eq(SCHEMA), Mockito.eq(DBObjectType.TABLE)))
                .thenReturn(identities);
        for (DetailRow r : rows) {
            DBTable detail = new DBTable();
            detail.setName(r.name);
            detail.setType(r.type);
            detail.setColumns(r.columns);
            Mockito.when(stub.getDetail(Mockito.any(), Mockito.eq(SCHEMA), Mockito.eq(r.name)))
                    .thenReturn(detail);
        }
        return stub;
    }

    private static final class DetailRow {
        final DBObjectType type;
        final String name;
        final List<DBTableColumn> columns;

        DetailRow(DBObjectType type, String name, List<DBTableColumn> columns) {
            this.type = type;
            this.name = name;
            this.columns = columns;
        }
    }
}
