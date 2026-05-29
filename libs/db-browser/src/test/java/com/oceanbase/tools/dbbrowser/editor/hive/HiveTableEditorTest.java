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
package com.oceanbase.tools.dbbrowser.editor.hive;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oceanbase.tools.dbbrowser.model.DBTable;
import com.oceanbase.tools.dbbrowser.model.DBTable.DBTableOptions;
import com.oceanbase.tools.dbbrowser.model.DBTableColumn;
import com.oceanbase.tools.dbbrowser.model.DBTablePartition;
import com.oceanbase.tools.dbbrowser.model.DBTablePartitionOption;
import com.oceanbase.tools.dbbrowser.model.DBTablePartitionType;

/**
 * Tests for {@link HiveTableEditor} covering design.md 5.2.3 all 8 scenarios.
 */
public class HiveTableEditorTest {

    private HiveTableEditor tableEditor;

    @Before
    public void setUp() {
        HiveColumnEditor columnEditor = new HiveColumnEditor();
        HiveIndexEditor indexEditor = new HiveIndexEditor();
        HiveConstraintEditor constraintEditor = new HiveConstraintEditor();
        HivePartitionEditor partitionEditor = new HivePartitionEditor();
        tableEditor = new HiveTableEditor(indexEditor, columnEditor, constraintEditor, partitionEditor);
    }

    /**
     * Case 1: Basic CREATE TABLE -- 3 columns, no partition, STORED AS ORC
     */
    @Test
    public void testCreateTable_basic_storedAsORC() {
        DBTable table = createBaseTable();
        DBTableOptions options = new DBTableOptions();
        options.setCompressionOption("ORC");
        table.setTableOptions(options);

        String ddl = tableEditor.generateCreateObjectDDL(table);
        Assert.assertTrue("DDL should contain CREATE TABLE",
                ddl.contains("CREATE TABLE `test_db`.`users`"));
        Assert.assertTrue("DDL should contain column id",
                ddl.contains("`id` BIGINT"));
        Assert.assertTrue("DDL should contain column name",
                ddl.contains("`name` STRING"));
        Assert.assertTrue("DDL should contain column age",
                ddl.contains("`age` INT"));
        Assert.assertTrue("DDL should contain STORED AS ORC",
                ddl.contains("STORED AS ORC"));
        Assert.assertFalse("DDL should not contain PARTITIONED BY",
                ddl.contains("PARTITIONED BY"));
    }

    /**
     * Case 2: CREATE TABLE with partition -- 3 normal columns + 1 partition column
     */
    @Test
    public void testCreateTable_withPartition() {
        DBTable table = createBaseTable();
        DBTableOptions options = new DBTableOptions();
        options.setCompressionOption("ORC");
        table.setTableOptions(options);

        // Add partition
        DBTablePartition partition = new DBTablePartition();
        DBTablePartitionOption partOption = new DBTablePartitionOption();
        partOption.setType(DBTablePartitionType.LIST);
        partOption.setColumnNames(Collections.singletonList("`dt` STRING COMMENT 'date partition'"));
        partition.setPartitionOption(partOption);
        table.setPartition(partition);

        String ddl = tableEditor.generateCreateObjectDDL(table);
        Assert.assertTrue("DDL should contain PARTITIONED BY",
                ddl.contains("PARTITIONED BY (`dt` STRING COMMENT 'date partition')"));
        Assert.assertTrue("DDL should contain STORED AS ORC",
                ddl.contains("STORED AS ORC"));
    }

    /**
     * Case 3: CREATE TABLE with ROW FORMAT -- TextFile with comma delimiter
     */
    @Test
    public void testCreateTable_withRowFormat() {
        DBTable table = createBaseTable();
        DBTableOptions options = new DBTableOptions();
        options.setRowFormat(",");
        options.setCompressionOption("TEXTFILE");
        table.setTableOptions(options);

        String ddl = tableEditor.generateCreateObjectDDL(table);
        Assert.assertTrue("DDL should contain ROW FORMAT DELIMITED",
                ddl.contains("ROW FORMAT DELIMITED"));
        Assert.assertTrue("DDL should contain FIELDS TERMINATED BY ','",
                ddl.contains("FIELDS TERMINATED BY ','"));
        Assert.assertTrue("DDL should contain STORED AS TEXTFILE",
                ddl.contains("STORED AS TEXTFILE"));
    }

    /**
     * Case 4: CREATE TABLE with table comment
     */
    @Test
    public void testCreateTable_withComment() {
        DBTable table = createBaseTable();
        DBTableOptions options = new DBTableOptions();
        options.setComment("user information table");
        options.setCompressionOption("ORC");
        table.setTableOptions(options);

        String ddl = tableEditor.generateCreateObjectDDL(table);
        Assert.assertTrue("DDL should contain COMMENT",
                ddl.contains("COMMENT 'user information table'"));
    }

    /**
     * Case 5: ALTER TABLE ADD COLUMNS -- add 1 new column
     */
    @Test
    public void testAlterTable_addColumn() {
        DBTableColumn column = createColumn("test_db", "users", "email", "STRING", "user email");
        HiveColumnEditor columnEditor = new HiveColumnEditor();
        String ddl = columnEditor.generateCreateObjectDDL(column);
        Assert.assertEquals(
                "ALTER TABLE `test_db`.`users` ADD COLUMNS (`email` STRING COMMENT 'user email');\n",
                ddl);
    }

    /**
     * Case 6: ALTER TABLE CHANGE COLUMN -- change column name/comment
     */
    @Test
    public void testAlterTable_changeColumn() {
        DBTableColumn oldColumn = createColumn("test_db", "users", "old_name", "STRING", "old comment");
        DBTableColumn newColumn = createColumn("test_db", "users", "new_name", "STRING", "new comment");
        HiveColumnEditor columnEditor = new HiveColumnEditor();
        String ddl = columnEditor.generateUpdateObjectDDL(oldColumn, newColumn);
        Assert.assertEquals(
                "ALTER TABLE `test_db`.`users` CHANGE COLUMN `old_name` `new_name` STRING COMMENT 'new comment';\n",
                ddl);
    }

    /**
     * Case 7: DROP COLUMN (not supported) -- should throw UnsupportedOperationException
     */
    @Test(expected = UnsupportedOperationException.class)
    public void testAlterTable_dropColumn_unsupported() {
        DBTableColumn column = createColumn("test_db", "users", "obsolete", "STRING", null);
        HiveColumnEditor columnEditor = new HiveColumnEditor();
        columnEditor.generateDropObjectDDL(column);
    }

    /**
     * Case 8: RENAME TABLE
     */
    @Test
    public void testRenameTable() {
        DBTable oldTable = new DBTable();
        oldTable.setSchemaName("test_db");
        oldTable.setName("old_table");

        DBTable newTable = new DBTable();
        newTable.setSchemaName("test_db");
        newTable.setName("new_table");

        String ddl = tableEditor.generateRenameObjectDDL(oldTable, newTable);
        Assert.assertEquals(
                "ALTER TABLE `test_db`.`old_table` RENAME TO `test_db`.`new_table`",
                ddl);
    }

    /**
     * Test DROP TABLE generates correct DDL
     */
    @Test
    public void testDropTable() {
        DBTable table = new DBTable();
        table.setSchemaName("test_db");
        table.setName("users");

        String ddl = tableEditor.generateDropObjectDDL(table);
        Assert.assertEquals(
                "DROP TABLE IF EXISTS `test_db`.`users`",
                ddl);
    }

    /**
     * Test full CREATE TABLE with all options combined
     */
    @Test
    public void testCreateTable_fullOptions() {
        DBTable table = createBaseTable();
        DBTableOptions options = new DBTableOptions();
        options.setComment("user data table");
        options.setRowFormat(",");
        options.setCompressionOption("TEXTFILE");
        table.setTableOptions(options);

        DBTablePartition partition = new DBTablePartition();
        DBTablePartitionOption partOption = new DBTablePartitionOption();
        partOption.setType(DBTablePartitionType.LIST);
        partOption.setColumnNames(Collections.singletonList("`dt` STRING COMMENT 'date partition'"));
        partition.setPartitionOption(partOption);
        table.setPartition(partition);

        String ddl = tableEditor.generateCreateObjectDDL(table);

        // Verify correct order: columns -> COMMENT -> PARTITIONED BY -> ROW FORMAT -> STORED AS
        int commentPos = ddl.indexOf("COMMENT 'user data table'");
        int partitionPos = ddl.indexOf("PARTITIONED BY");
        int rowFormatPos = ddl.indexOf("ROW FORMAT DELIMITED");
        int storedAsPos = ddl.indexOf("STORED AS TEXTFILE");

        Assert.assertTrue("COMMENT should appear", commentPos >= 0);
        Assert.assertTrue("PARTITIONED BY should appear", partitionPos >= 0);
        Assert.assertTrue("ROW FORMAT should appear", rowFormatPos >= 0);
        Assert.assertTrue("STORED AS should appear", storedAsPos >= 0);
        Assert.assertTrue("COMMENT before PARTITIONED BY", commentPos < partitionPos);
        Assert.assertTrue("PARTITIONED BY before ROW FORMAT", partitionPos < rowFormatPos);
        Assert.assertTrue("ROW FORMAT before STORED AS", rowFormatPos < storedAsPos);
    }

    /**
     * Test generateUpdateObjectDDL does not throw when modifying columns (Bug fix: skip
     * index/constraint editors).
     */
    @Test
    public void testUpdateObjectDDL_modifyColumn_noException() {
        DBTable oldTable = createBaseTable();
        DBTable newTable = createBaseTable();

        // Add a new column to newTable
        DBTableColumn emailColumn = createColumn("test_db", "users", "email", "STRING", "user email");
        newTable.getColumns().add(emailColumn);

        String ddl = tableEditor.generateUpdateObjectDDL(oldTable, newTable);
        Assert.assertNotNull("DDL should not be null", ddl);
        Assert.assertTrue("DDL should contain ADD COLUMNS for the new column",
                ddl.contains("ADD COLUMNS") && ddl.contains("email"));
    }

    /**
     * Test generateUpdateObjectDDL handles rename + column change together.
     */
    @Test
    public void testUpdateObjectDDL_renameAndModifyColumn() {
        DBTable oldTable = createBaseTable();
        DBTable newTable = createBaseTable();
        newTable.setName("users_v2");

        // Add a new column
        DBTableColumn emailColumn = createColumn("test_db", "users_v2", "email", "STRING", "user email");
        newTable.getColumns().add(emailColumn);

        String ddl = tableEditor.generateUpdateObjectDDL(oldTable, newTable);
        Assert.assertNotNull("DDL should not be null", ddl);
        Assert.assertTrue("DDL should contain RENAME TO",
                ddl.contains("RENAME TO") && ddl.contains("users_v2"));
        Assert.assertTrue("DDL should contain ADD COLUMNS",
                ddl.contains("ADD COLUMNS"));
    }

    /**
     * Test generateUpdateObjectDDL with no structural changes does not throw.
     * <p>
     * Note: The column editor always generates CHANGE COLUMN DDL for each matched column pair (it does
     * not compare old/new for equality), so the DDL is not empty; the key assertion is that no
     * UnsupportedOperationException is thrown (index/constraint editors are skipped).
     * </p>
     */
    @Test
    public void testUpdateObjectDDL_noStructuralChanges_noException() {
        DBTable oldTable = createBaseTable();
        DBTable newTable = createBaseTable();

        String ddl = tableEditor.generateUpdateObjectDDL(oldTable, newTable);
        Assert.assertNotNull("DDL should not be null", ddl);
        // Key: no UnsupportedOperationException was thrown (index/constraint editors skipped)
        Assert.assertFalse("DDL should not contain index-related content",
                ddl.contains("INDEX"));
    }

    /**
     * Test generateUpdateObjectDDLWithoutRenaming does not throw (shadow table comparing scenario).
     */
    @Test
    public void testUpdateObjectDDLWithoutRenaming_noException() {
        DBTable oldTable = createBaseTable();
        DBTable newTable = createBaseTable();

        DBTableColumn emailColumn = createColumn("test_db", "users", "email", "STRING", "user email");
        newTable.getColumns().add(emailColumn);

        String ddl = tableEditor.generateUpdateObjectDDLWithoutRenaming(oldTable, newTable);
        Assert.assertNotNull("DDL should not be null", ddl);
        Assert.assertTrue("DDL should contain ADD COLUMNS",
                ddl.contains("ADD COLUMNS") && ddl.contains("email"));
    }

    /**
     * Test generateUpdateObjectDDL with table comment change.
     */
    @Test
    public void testUpdateObjectDDL_commentChange() {
        DBTable oldTable = createBaseTable();
        DBTableOptions oldOpts = new DBTableOptions();
        oldOpts.setComment("old comment");
        oldTable.setTableOptions(oldOpts);

        DBTable newTable = createBaseTable();
        DBTableOptions newOpts = new DBTableOptions();
        newOpts.setComment("new comment");
        newTable.setTableOptions(newOpts);

        String ddl = tableEditor.generateUpdateObjectDDL(oldTable, newTable);
        Assert.assertNotNull("DDL should not be null", ddl);
        Assert.assertTrue("DDL should contain SET TBLPROPERTIES for comment update",
                ddl.contains("SET TBLPROPERTIES") && ddl.contains("new comment"));
    }

    // --- Helper methods ---

    private DBTable createBaseTable() {
        DBTable table = new DBTable();
        table.setSchemaName("test_db");
        table.setName("users");
        table.setColumns(new java.util.ArrayList<>(Arrays.asList(
                createColumn("test_db", "users", "id", "BIGINT", "primary key", 1),
                createColumn("test_db", "users", "name", "STRING", "user name", 2),
                createColumn("test_db", "users", "age", "INT", null, 3))));
        table.setIndexes(Collections.emptyList());
        table.setConstraints(Collections.emptyList());
        table.setTableOptions(new DBTableOptions());
        return table;
    }

    private static DBTableColumn createColumn(String schema, String table, String name,
            String typeName, String comment) {
        return createColumn(schema, table, name, typeName, comment, null);
    }

    private static DBTableColumn createColumn(String schema, String table, String name,
            String typeName, String comment, Integer ordinalPosition) {
        DBTableColumn column = new DBTableColumn();
        column.setSchemaName(schema);
        column.setTableName(table);
        column.setName(name);
        column.setTypeName(typeName);
        column.setComment(comment);
        column.setOrdinalPosition(ordinalPosition);
        return column;
    }

}
