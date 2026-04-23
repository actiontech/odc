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
package com.oceanbase.odc.plugin.schema.dm;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oceanbase.tools.dbbrowser.model.DBConstraintType;
import com.oceanbase.tools.dbbrowser.model.DBTable;
import com.oceanbase.tools.dbbrowser.model.DBTable.DBTableOptions;
import com.oceanbase.tools.dbbrowser.model.DBTableColumn;
import com.oceanbase.tools.dbbrowser.model.DBTableConstraint;

/**
 * Test cases for {@link DmTableExtension}.
 * <p>
 * Tests DDL generation logic using DM (Oracle-compatible) table editor. DM uses Oracle-style
 * double-quote identifiers in generated DDL.
 * </p>
 *
 * @author
 * @since ODC_release_4.3.4
 */
public class DmTableExtensionTest {

    private DmTableExtension tableExtension;

    @Before
    public void setUp() {
        tableExtension = new DmTableExtension();
    }

    private DBTable createTestTable() {
        DBTable table = new DBTable();
        table.setSchemaName("SYSDBA");
        table.setName("TEST_TABLE");

        DBTableColumn c1 = new DBTableColumn();
        c1.setName("ID");
        c1.setTypeName("NUMBER");
        c1.setPrecision(10L);
        c1.setScale(0);
        c1.setNullable(false);
        c1.setOrdinalPosition(0);

        DBTableColumn c2 = new DBTableColumn();
        c2.setName("NAME");
        c2.setTypeName("VARCHAR2");
        c2.setPrecision(100L);
        c2.setNullable(true);
        c2.setOrdinalPosition(1);

        table.setColumns(Arrays.asList(c1, c2));

        DBTableConstraint pk = new DBTableConstraint();
        pk.setName("PK_TEST_TABLE");
        pk.setType(DBConstraintType.PRIMARY_KEY);
        pk.setColumnNames(Arrays.asList("ID"));
        table.setConstraints(Arrays.asList(pk));

        DBTableOptions options = new DBTableOptions();
        options.setComment("test comment");
        table.setTableOptions(options);

        return table;
    }

    @Test
    public void test_generateCreateDDL_ContainsCreateTable() {
        DBTable table = createTestTable();
        String ddl = tableExtension.generateCreateDDL(table);

        Assert.assertNotNull("DDL should not be null", ddl);
        Assert.assertTrue("DDL should contain CREATE TABLE",
                ddl.contains("CREATE TABLE"));
    }

    @Test
    public void test_generateCreateDDL_ContainsSchemaAndTableName() {
        DBTable table = createTestTable();
        String ddl = tableExtension.generateCreateDDL(table);

        Assert.assertNotNull("DDL should not be null", ddl);
        Assert.assertTrue("DDL should contain schema name",
                ddl.contains("\"SYSDBA\""));
        Assert.assertTrue("DDL should contain table name",
                ddl.contains("\"TEST_TABLE\""));
    }

    @Test
    public void test_generateCreateDDL_ContainsColumnDefinitions() {
        DBTable table = createTestTable();
        String ddl = tableExtension.generateCreateDDL(table);

        Assert.assertNotNull("DDL should not be null", ddl);
        Assert.assertTrue("DDL should contain ID column",
                ddl.contains("\"ID\""));
        Assert.assertTrue("DDL should contain NAME column",
                ddl.contains("\"NAME\""));
        Assert.assertTrue("DDL should contain NOT NULL for ID",
                ddl.contains("NOT NULL"));
    }

    @Test
    public void test_generateCreateDDL_ContainsPrimaryKey() {
        DBTable table = createTestTable();
        String ddl = tableExtension.generateCreateDDL(table);

        Assert.assertNotNull("DDL should not be null", ddl);
        Assert.assertTrue("DDL should contain PRIMARY KEY constraint",
                ddl.contains("PRIMARY KEY"));
        Assert.assertTrue("DDL should contain constraint name",
                ddl.contains("\"PK_TEST_TABLE\""));
    }

    @Test
    public void test_generateCreateDDL_ContainsComment() {
        DBTable table = createTestTable();
        String ddl = tableExtension.generateCreateDDL(table);

        Assert.assertNotNull("DDL should not be null", ddl);
        Assert.assertTrue("DDL should contain COMMENT ON TABLE statement",
                ddl.contains("COMMENT ON TABLE"));
        Assert.assertTrue("DDL should contain the comment text",
                ddl.contains("test comment"));
    }

    @Test
    public void test_generateUpdateDDL_RenameTable() {
        DBTable oldTable = createTestTable();
        oldTable.setName("OLD_TABLE");

        DBTable newTable = createTestTable();
        newTable.setName("NEW_TABLE");

        String ddl = tableExtension.generateUpdateDDL(oldTable, newTable);

        Assert.assertNotNull("Update DDL should not be null", ddl);
        Assert.assertTrue("Update DDL should contain RENAME or ALTER TABLE",
                ddl.contains("RENAME") || ddl.contains("ALTER TABLE"));
    }

    @Test
    public void test_generateCreateDDL_MultipleColumns() {
        DBTable table = new DBTable();
        table.setSchemaName("SYSDBA");
        table.setName("EMPLOYEE");

        DBTableColumn c1 = new DBTableColumn();
        c1.setName("EMP_ID");
        c1.setTypeName("NUMBER");
        c1.setPrecision(10L);
        c1.setScale(0);
        c1.setNullable(false);
        c1.setOrdinalPosition(0);

        DBTableColumn c2 = new DBTableColumn();
        c2.setName("EMP_NAME");
        c2.setTypeName("VARCHAR2");
        c2.setPrecision(50L);
        c2.setNullable(false);
        c2.setOrdinalPosition(1);

        DBTableColumn c3 = new DBTableColumn();
        c3.setName("SALARY");
        c3.setTypeName("NUMBER");
        c3.setPrecision(12L);
        c3.setScale(2);
        c3.setNullable(true);
        c3.setOrdinalPosition(2);

        table.setColumns(Arrays.asList(c1, c2, c3));

        DBTableConstraint pk = new DBTableConstraint();
        pk.setName("PK_EMPLOYEE");
        pk.setType(DBConstraintType.PRIMARY_KEY);
        pk.setColumnNames(Arrays.asList("EMP_ID"));
        table.setConstraints(Arrays.asList(pk));
        table.setTableOptions(new DBTableOptions());

        String ddl = tableExtension.generateCreateDDL(table);

        Assert.assertNotNull("DDL should not be null", ddl);
        Assert.assertTrue("DDL should contain CREATE TABLE",
                ddl.contains("CREATE TABLE"));
        Assert.assertTrue("DDL should contain schema.table format",
                ddl.contains("\"SYSDBA\".\"EMPLOYEE\""));
        Assert.assertTrue("DDL should contain EMP_ID column",
                ddl.contains("\"EMP_ID\""));
        Assert.assertTrue("DDL should contain EMP_NAME column",
                ddl.contains("\"EMP_NAME\""));
        Assert.assertTrue("DDL should contain SALARY column",
                ddl.contains("\"SALARY\""));
        Assert.assertTrue("DDL should contain PK constraint",
                ddl.contains("\"PK_EMPLOYEE\""));
    }

    @Test
    public void test_generateCreateDDL_NoConstraints() {
        DBTable table = new DBTable();
        table.setSchemaName("SYSDBA");
        table.setName("SIMPLE_TABLE");

        DBTableColumn c1 = new DBTableColumn();
        c1.setName("COL1");
        c1.setTypeName("VARCHAR2");
        c1.setPrecision(255L);
        c1.setNullable(true);
        c1.setOrdinalPosition(0);

        table.setColumns(Arrays.asList(c1));
        table.setConstraints(Arrays.asList());
        table.setTableOptions(new DBTableOptions());

        String ddl = tableExtension.generateCreateDDL(table);

        Assert.assertNotNull("DDL should not be null", ddl);
        Assert.assertTrue("DDL should contain CREATE TABLE",
                ddl.contains("CREATE TABLE"));
        Assert.assertTrue("DDL should contain column name",
                ddl.contains("\"COL1\""));
    }

    @Test
    public void test_syncExternalTableFiles_ThrowsUnsupported() {
        try {
            tableExtension.syncExternalTableFiles(null, "SYSDBA", "TEST_TABLE");
            Assert.fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }
}
