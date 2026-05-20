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
package com.oceanbase.odc.service.dml;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oceanbase.odc.service.dml.model.DataModifyUnit;
import com.oceanbase.tools.dbbrowser.model.DBTableColumn;
import com.oceanbase.tools.dbbrowser.util.Db2SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;

/**
 * fix-L commit-2 (Issue dms-ee#839, bug N2): unit tests for the DB2 DML chain.
 *
 * <p>
 * Strategy: drive the existing {@link InsertGenerator} / {@link UpdateGenerator} /
 * {@link DeleteGenerator} with a mocked {@link DMLBuilder} that returns a {@link Db2SqlBuilder}, so
 * the assertions focus purely on the SQL surface (identifier quoting, value quoting). This avoids
 * needing a real {@link com.oceanbase.odc.core.session.ConnectionSession}, in line with the
 * mock-only unit-test policy used elsewhere in odc-service (see {@code plan.md §3.2.2}).
 *
 * <p>
 * Regression target: before fix-L the DB2 path was routed through {@link MySQLDMLBuilder} and
 * emitted MySQL-style backtick identifiers — e.g.
 * {@code insert into `DB2INST1`.`TEST_ORDERS`(`ID`,...) values (...)} — which DB2 rejects with
 * {@code SQLCODE=-7 / SQLSTATE=42601} in the parser. The asserts below pin the absence of backticks
 * and the presence of ANSI double-quoted identifiers, which is DB2's native syntax.
 *
 * @since ODC_release_4.3.4 (Issue dms-ee#839)
 */
public class Db2DMLBuilderTest {

    private DMLBuilder dmlBuilder;

    @Before
    public void setUp() {
        dmlBuilder = mock(DMLBuilder.class);
        when(dmlBuilder.createSQLBuilder()).thenAnswer(invocation -> new Db2SqlBuilder());
        when(dmlBuilder.getSchema()).thenReturn("DB2INST1");
        when(dmlBuilder.getTableName()).thenReturn("TEST_ORDERS");
        when(dmlBuilder.toSQLString(any(DataValue.class)))
                .thenAnswer(inv -> "'" + ((DataValue) inv.getArgument(0)).getValue() + "'");
    }

    /**
     * Case 1 — INSERT emits ANSI double-quoted identifiers (DB2 native) instead of MySQL backticks.
     */
    @Test
    public void insertGenerator_emitsDoubleQuotedIdentifiersForDb2() {
        DataModifyUnit idUnit = newInsertUnit("ID", "int", "100");
        DataModifyUnit nameUnit = newInsertUnit("CUSTOMER", "varchar", "Alice");
        when(dmlBuilder.getModifyUnits()).thenReturn(Arrays.asList(idUnit, nameUnit));

        String sql = new InsertGenerator(dmlBuilder).generate();

        Assert.assertFalse("DB2 INSERT must not contain MySQL backticks: " + sql, sql.contains("`"));
        Assert.assertTrue("DB2 INSERT must quote schema/table with double quotes: " + sql,
                sql.contains("\"DB2INST1\".\"TEST_ORDERS\""));
        Assert.assertTrue("DB2 INSERT must quote column names with double quotes: " + sql,
                sql.contains("\"ID\"") && sql.contains("\"CUSTOMER\""));
        Assert.assertTrue("DB2 INSERT must start with 'insert into': " + sql,
                sql.startsWith("insert into"));
    }

    /**
     * Case 2 — UPDATE emits ANSI double-quoted identifiers and quoted values.
     */
    @Test
    public void updateGenerator_emitsDoubleQuotedIdentifiersForDb2() {
        DataModifyUnit customerUnit = newUpdateUnit("CUSTOMER", "varchar", "Alice", "Alice_E41");
        DataModifyUnit idUnit = newUpdateUnit("ID", "int", "1", "1");
        when(dmlBuilder.getModifyUnits()).thenReturn(Arrays.asList(customerUnit, idUnit));
        when(dmlBuilder.containsPrimaryKeys()).thenReturn(true);
        when(dmlBuilder.containsPrimaryKeyOrRowId()).thenReturn(true);
        // appendWhereClause on the mock is a no-op by default; emulate the minimal DB2 WHERE shape
        // so UpdateGenerator can complete without NPE. We append a trivial PK predicate.
        org.mockito.Mockito.doAnswer(inv -> {
            DataModifyUnit u = inv.getArgument(0);
            SqlBuilder b = inv.getArgument(1);
            if ("ID".equals(u.getColumnName())) {
                b.identifier("ID").append("=").append(u.getOldData()).append(" and ");
            }
            return null;
        }).when(dmlBuilder).appendWhereClause(any(DataModifyUnit.class), any(SqlBuilder.class));

        Map<String, DBTableColumn> col2Type = new HashMap<>();
        DBTableColumn customerColumn = new DBTableColumn();
        customerColumn.setName("CUSTOMER");
        customerColumn.setTypeName("varchar");
        col2Type.put("CUSTOMER", customerColumn);
        DBTableColumn idColumn = new DBTableColumn();
        idColumn.setName("ID");
        idColumn.setTypeName("int");
        col2Type.put("ID", idColumn);

        String sql = new UpdateGenerator(dmlBuilder, col2Type).generate();

        Assert.assertFalse("DB2 UPDATE must not contain MySQL backticks: " + sql, sql.contains("`"));
        Assert.assertTrue("DB2 UPDATE must quote column names with double quotes: " + sql,
                sql.contains("\"CUSTOMER\""));
        Assert.assertTrue("DB2 UPDATE must start with 'update': " + sql,
                sql.toLowerCase().startsWith("update "));
    }

    /**
     * Case 3 — DELETE emits ANSI double-quoted identifiers (DB2 native) instead of MySQL backticks.
     */
    @Test
    public void deleteGenerator_emitsDoubleQuotedIdentifiersForDb2() {
        DataModifyUnit idUnit = newUpdateUnit("ID", "int", "1", "1");
        when(dmlBuilder.getModifyUnits()).thenReturn(Collections.singletonList(idUnit));
        when(dmlBuilder.containsPrimaryKeys()).thenReturn(true);
        when(dmlBuilder.containsPrimaryKeyOrRowId()).thenReturn(true);
        org.mockito.Mockito.doAnswer(inv -> {
            DataModifyUnit u = inv.getArgument(0);
            SqlBuilder b = inv.getArgument(1);
            b.identifier(u.getColumnName()).append("=").append(u.getOldData()).append(" and ");
            return null;
        }).when(dmlBuilder).appendWhereClause(any(DataModifyUnit.class), any(SqlBuilder.class));

        String sql = new DeleteGenerator(dmlBuilder).generate();

        Assert.assertFalse("DB2 DELETE must not contain MySQL backticks: " + sql, sql.contains("`"));
        Assert.assertTrue("DB2 DELETE must quote table with double quotes: " + sql,
                sql.contains("\"DB2INST1\".\"TEST_ORDERS\""));
        Assert.assertTrue("DB2 DELETE must start with 'delete from': " + sql,
                sql.toLowerCase().startsWith("delete from "));
    }

    // -------------------- helpers --------------------

    private DataModifyUnit newInsertUnit(String column, String type, String newValue) {
        DataModifyUnit unit = new DataModifyUnit();
        unit.setSchemaName("DB2INST1");
        unit.setTableName("TEST_ORDERS");
        unit.setColumnName(column);
        unit.setColumnType(type);
        unit.setNewData(newValue);
        unit.setUseDefault(false);
        return unit;
    }

    private DataModifyUnit newUpdateUnit(String column, String type, String oldValue, String newValue) {
        DataModifyUnit unit = newInsertUnit(column, type, newValue);
        unit.setOldData(oldValue);
        return unit;
    }
}
