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

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oceanbase.tools.dbbrowser.editor.sqlserver.SqlServerColumnEditor;
import com.oceanbase.tools.dbbrowser.editor.sqlserver.SqlServerConstraintEditor;
import com.oceanbase.tools.dbbrowser.editor.sqlserver.SqlServerIndexEditor;
import com.oceanbase.tools.dbbrowser.editor.sqlserver.SqlServerPartitionEditor;
import com.oceanbase.tools.dbbrowser.editor.sqlserver.SqlServerTableEditor;
import com.oceanbase.tools.dbbrowser.model.DBConstraintType;
import com.oceanbase.tools.dbbrowser.model.DBTable;
import com.oceanbase.tools.dbbrowser.model.DBTable.DBTableOptions;
import com.oceanbase.tools.dbbrowser.model.DBTableColumn;
import com.oceanbase.tools.dbbrowser.model.DBTableConstraint;

/**
 * @description: all tests for {@link SqlServerTableEditor}
 * @author: yizhou.xw
 * @date: 2024/12
 * @since: ODC_release_4.3.4
 */
public class SqlServerTableEditorTest {

    private DBTableEditor tableEditor;

    @Before
    public void setUp() {
        tableEditor = new SqlServerTableEditor(new SqlServerIndexEditor(), new SqlServerColumnEditor(),
                new SqlServerConstraintEditor(), new SqlServerPartitionEditor());
    }

    private DBTable getTestTable() {
        DBTable table = new DBTable();
        table.setSchemaName("dbo");
        table.setName("test_table");

        DBTableColumn c1 = new DBTableColumn();
        c1.setName("id");
        c1.setTypeName("INT");
        c1.setNullable(false);
        c1.setOrdinalPosition(0);

        DBTableColumn c2 = new DBTableColumn();
        c2.setName("name");
        c2.setTypeName("VARCHAR");
        c2.setPrecision(50L);
        c2.setNullable(true);
        c2.setOrdinalPosition(1);

        table.setColumns(Arrays.asList(c1, c2));

        DBTableConstraint pk = new DBTableConstraint();
        pk.setName("PK_test_table");
        pk.setType(DBConstraintType.PRIMARY_KEY);
        pk.setColumnNames(Arrays.asList("id"));
        table.setConstraints(Arrays.asList(pk));

        DBTableOptions options = new DBTableOptions();
        options.setComment("test comment");
        table.setTableOptions(options);

        return table;
    }

    @Test
    public void generateCreateObjectDDL() {
        DBTable table = getTestTable();
        String ddl = tableEditor.generateCreateObjectDDL(table);

        String expected = "CREATE TABLE [dbo].[test_table] (\n"
                + "[id] INT NOT NULL,\n"
                + "[name] VARCHAR(50) NULL,\n"
                + "CONSTRAINT [PK_test_table] PRIMARY KEY ([id])\n"
                + ") ;\n"
                + "IF EXISTS (SELECT 1 FROM sys.extended_properties WHERE name = N'MS_Description' AND major_id = OBJECT_ID(N'dbo.test_table') AND minor_id = 0)\n"
                + "  EXEC sp_updateextendedproperty @name=N'MS_Description', @value='test comment', @level0type=N'SCHEMA', @level0name='dbo', @level1type=N'TABLE', @level1name='test_table'\n"
                + "ELSE\n"
                + "  EXEC sp_addextendedproperty @name=N'MS_Description', @value='test comment', @level0type=N'SCHEMA', @level0name='dbo', @level1type=N'TABLE', @level1name='test_table';\n";

        Assert.assertEquals(expected, ddl);
    }

    @Test
    public void generateDropObjectDDL() {
        DBTable table = new DBTable();
        table.setSchemaName("dbo");
        table.setName("test_table");

        String ddl = tableEditor.generateDropObjectDDL(table);
        Assert.assertEquals("DROP TABLE [dbo].[test_table]", ddl);
    }

    @Test
    public void generateUpdateObjectDDL() {
        DBTable oldTable = new DBTable();
        oldTable.setSchemaName("dbo");
        oldTable.setName("old_table");

        DBTable newTable = new DBTable();
        newTable.setSchemaName("dbo");
        newTable.setName("new_table");

        String ddl = tableEditor.generateUpdateObjectDDL(oldTable, newTable);
        Assert.assertEquals("EXEC sp_rename 'dbo.old_table', 'new_table';\n", ddl);
    }

    @Test
    public void generateRenameObjectDDL() {
        DBTable oldTable = new DBTable();
        oldTable.setSchemaName("dbo");
        oldTable.setName("old_table");

        DBTable newTable = new DBTable();
        newTable.setSchemaName("dbo");
        newTable.setName("new_table");

        String ddl = tableEditor.generateRenameObjectDDL(oldTable, newTable);
        // 当没有数据库名时，不应该添加 USE 语句
        Assert.assertEquals("EXEC sp_rename 'dbo.old_table', 'new_table'", ddl);
    }

    @Test
    public void generateUpdateTableOptionDDL() {
        DBTable oldTable = new DBTable();
        oldTable.setSchemaName("dbo");
        oldTable.setName("test_table");
        DBTableOptions oldOptions = new DBTableOptions();
        oldOptions.setComment("old comment");
        oldTable.setTableOptions(oldOptions);

        DBTable newTable = new DBTable();
        newTable.setSchemaName("dbo");
        newTable.setName("test_table");
        DBTableOptions newOptions = new DBTableOptions();
        newOptions.setComment("new comment");
        newTable.setTableOptions(newOptions);

        com.oceanbase.tools.dbbrowser.util.SqlBuilder sqlBuilder =
                new com.oceanbase.tools.dbbrowser.util.SqlServerSqlBuilder();
        tableEditor.generateUpdateTableOptionDDL(oldTable, newTable, sqlBuilder);

        String expected =
                "IF EXISTS (SELECT 1 FROM sys.extended_properties WHERE name = N'MS_Description' AND major_id = OBJECT_ID(N'dbo.test_table') AND minor_id = 0)\n"
                        + "  EXEC sp_updateextendedproperty @name=N'MS_Description', @value='new comment', @level0type=N'SCHEMA', @level0name='dbo', @level1type=N'TABLE', @level1name='test_table'\n"
                        + "ELSE\n"
                        + "  EXEC sp_addextendedproperty @name=N'MS_Description', @value='new comment', @level0type=N'SCHEMA', @level0name='dbo', @level1type=N'TABLE', @level1name='test_table';\n";

        Assert.assertEquals(expected, sqlBuilder.toString());
    }

    @Test
    public void generateCreateObjectDDL_WithDatabaseName() {
        DBTable table = getTestTable();
        // 测试 database.schema.table 格式
        table.setSchemaName("test_database.dbo");
        String ddl = tableEditor.generateCreateObjectDDL(table);

        String expected = "CREATE TABLE [test_database].[dbo].[test_table] (\n"
                + "[id] INT NOT NULL,\n"
                + "[name] VARCHAR(50) NULL,\n"
                + "CONSTRAINT [PK_test_table] PRIMARY KEY ([id])\n"
                + ") ;\n"
                + "IF EXISTS (SELECT 1 FROM sys.extended_properties WHERE name = N'MS_Description' AND major_id = OBJECT_ID(N'dbo.test_table') AND minor_id = 0)\n"
                + "  EXEC sp_updateextendedproperty @name=N'MS_Description', @value='test comment', @level0type=N'SCHEMA', @level0name='dbo', @level1type=N'TABLE', @level1name='test_table'\n"
                + "ELSE\n"
                + "  EXEC sp_addextendedproperty @name=N'MS_Description', @value='test comment', @level0type=N'SCHEMA', @level0name='dbo', @level1type=N'TABLE', @level1name='test_table';\n";

        Assert.assertEquals(expected, ddl);
    }

    @Test
    public void generateCreateObjectDDL_WithDatabaseNameOnly() {
        DBTable table = getTestTable();
        // 测试只有 schema 名的情况（不包含数据库名）
        table.setSchemaName("test_schema");
        String ddl = tableEditor.generateCreateObjectDDL(table);

        String expected = "CREATE TABLE [test_schema].[test_table] (\n"
                + "[id] INT NOT NULL,\n"
                + "[name] VARCHAR(50) NULL,\n"
                + "CONSTRAINT [PK_test_table] PRIMARY KEY ([id])\n"
                + ") ;\n"
                + "IF EXISTS (SELECT 1 FROM sys.extended_properties WHERE name = N'MS_Description' AND major_id = OBJECT_ID(N'test_schema.test_table') AND minor_id = 0)\n"
                + "  EXEC sp_updateextendedproperty @name=N'MS_Description', @value='test comment', @level0type=N'SCHEMA', @level0name='test_schema', @level1type=N'TABLE', @level1name='test_table'\n"
                + "ELSE\n"
                + "  EXEC sp_addextendedproperty @name=N'MS_Description', @value='test comment', @level0type=N'SCHEMA', @level0name='test_schema', @level1type=N'TABLE', @level1name='test_table';\n";

        Assert.assertEquals(expected, ddl);
    }

    @Test
    public void generateDropObjectDDL_WithDatabaseName() {
        DBTable table = new DBTable();
        table.setSchemaName("test_database.dbo");
        table.setName("test_table");

        String ddl = tableEditor.generateDropObjectDDL(table);
        Assert.assertEquals("DROP TABLE [test_database].[dbo].[test_table]", ddl);
    }

}
