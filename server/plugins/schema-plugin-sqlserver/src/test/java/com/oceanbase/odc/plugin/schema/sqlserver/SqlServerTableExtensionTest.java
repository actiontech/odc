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
package com.oceanbase.odc.plugin.schema.sqlserver;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.oceanbase.tools.dbbrowser.model.DBConstraintType;
import com.oceanbase.tools.dbbrowser.model.DBTable;
import com.oceanbase.tools.dbbrowser.model.DBTable.DBTableOptions;
import com.oceanbase.tools.dbbrowser.model.DBTableColumn;
import com.oceanbase.tools.dbbrowser.model.DBTableConstraint;

/**
 * Test cases for {@link SqlServerTableExtension}
 * 
 * @author yizhou.xw
 * @date 2024/12
 * @since ODC_release_4.3.4
 */
public class SqlServerTableExtensionTest {

    private SqlServerTableExtension tableExtension;

    @Mock
    private Connection connection;

    @Before
    public void setUp() throws SQLException {
        MockitoAnnotations.initMocks(this);
        tableExtension = new SqlServerTableExtension();
    }

    private DBTable createTestTable() {
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
    public void test_generateCreateDDL_WithConnection() {
        DBTable table = createTestTable();
        String ddl = tableExtension.generateCreateDDL(connection, table);

        // 验证生成的 DDL 包含正确的 SQL Server 语法
        Assert.assertNotNull(ddl);
        Assert.assertTrue(ddl.contains("CREATE TABLE"));
        Assert.assertTrue(ddl.contains("[dbo].[test_table]"));
        Assert.assertTrue(ddl.contains("[id] INT NOT NULL"));
        Assert.assertTrue(ddl.contains("[name] VARCHAR(50) NULL"));
        Assert.assertTrue(ddl.contains("CONSTRAINT [PK_test_table] PRIMARY KEY"));
    }

    @Test
    public void test_generateCreateDDL_WithDatabaseName() {
        DBTable table = createTestTable();
        // 测试 database.schema.table 格式
        table.setSchemaName("wenshu_test.dbo");
        String ddl = tableExtension.generateCreateDDL(connection, table);

        // 验证生成的 DDL 使用三部分名称格式
        Assert.assertNotNull(ddl);
        Assert.assertTrue(ddl.contains("CREATE TABLE"));
        Assert.assertTrue(ddl.contains("[wenshu_test].[dbo].[test_table]"));
    }

    @Test
    public void test_generateCreateDDL_WithDatabaseNameOnly() {
        DBTable table = createTestTable();
        // 测试只有数据库名的情况，应该默认使用 dbo schema
        table.setSchemaName("wenshu_test");
        String ddl = tableExtension.generateCreateDDL(connection, table);

        // 验证生成的 DDL 使用三部分名称格式，默认 schema 为 dbo
        Assert.assertNotNull(ddl);
        Assert.assertTrue(ddl.contains("CREATE TABLE"));
        Assert.assertTrue(ddl.contains("[wenshu_test].[dbo].[test_table]"));
    }

    @Test
    public void test_generateCreateDDL_WithoutConnection() {
        DBTable table = createTestTable();
        String ddl = tableExtension.generateCreateDDL(table);

        // 验证逻辑会话的 DDL 生成（不需要 Connection）
        Assert.assertNotNull(ddl);
        Assert.assertTrue(ddl.contains("CREATE TABLE"));
        Assert.assertTrue(ddl.contains("[dbo].[test_table]"));
    }

    @Test
    public void test_generateUpdateDDL_WithConnection() {
        DBTable oldTable = createTestTable();
        oldTable.setName("old_table");

        DBTable newTable = createTestTable();
        newTable.setName("new_table");

        String ddl = tableExtension.generateUpdateDDL(connection, oldTable, newTable);

        // 验证生成的更新 DDL
        Assert.assertNotNull(ddl);
        // SQL Server 使用 sp_rename 来重命名表
        Assert.assertTrue(ddl.contains("sp_rename") || ddl.contains("ALTER TABLE"));
    }

    @Test
    public void test_generateUpdateDDL_WithDatabaseName() {
        DBTable oldTable = createTestTable();
        oldTable.setSchemaName("wenshu_test.dbo");
        oldTable.setName("old_table");

        DBTable newTable = createTestTable();
        newTable.setSchemaName("wenshu_test.dbo");
        newTable.setName("new_table");

        String ddl = tableExtension.generateUpdateDDL(connection, oldTable, newTable);

        // 验证生成的更新 DDL 包含数据库名
        Assert.assertNotNull(ddl);
        // 当使用 database.schema.table 格式时，应该包含 USE 语句或三部分名称
        Assert.assertTrue(ddl.contains("USE") || ddl.contains("wenshu_test"));
    }

    @Test
    public void test_generateUpdateDDL_WithoutConnection() {
        DBTable oldTable = createTestTable();
        oldTable.setName("old_table");

        DBTable newTable = createTestTable();
        newTable.setName("new_table");

        String ddl = tableExtension.generateUpdateDDL(oldTable, newTable);

        // 验证逻辑会话的更新 DDL 生成（不需要 Connection）
        Assert.assertNotNull(ddl);
        Assert.assertTrue(ddl.contains("sp_rename") || ddl.contains("ALTER TABLE"));
    }

    @Test
    public void test_generateCreateDDL_WithComplexTable() {
        DBTable table = new DBTable();
        table.setSchemaName("wenshu_test.dbo");
        table.setName("user");

        DBTableColumn c1 = new DBTableColumn();
        c1.setName("id");
        c1.setTypeName("bigint");
        c1.setNullable(false);
        c1.setOrdinalPosition(0);

        DBTableColumn c2 = new DBTableColumn();
        c2.setName("name");
        c2.setTypeName("nvarchar");
        c2.setPrecision(255L);
        c2.setNullable(true);
        c2.setOrdinalPosition(1);

        DBTableColumn c3 = new DBTableColumn();
        c3.setName("address");
        c3.setTypeName("varchar");
        c3.setPrecision(255L);
        c3.setNullable(true);
        c3.setOrdinalPosition(2);

        table.setColumns(Arrays.asList(c1, c2, c3));

        DBTableConstraint pk = new DBTableConstraint();
        pk.setName("primary_id");
        pk.setType(DBConstraintType.PRIMARY_KEY);
        pk.setColumnNames(Arrays.asList("id"));
        table.setConstraints(Arrays.asList(pk));

        String ddl = tableExtension.generateCreateDDL(connection, table);

        // 验证生成的 DDL 符合 SQL Server 语法，使用三部分名称
        Assert.assertNotNull(ddl);
        Assert.assertTrue("DDL should contain database.schema.table format",
                ddl.contains("[wenshu_test].[dbo].[user]"));
        Assert.assertTrue("DDL should contain id column", ddl.contains("[id]"));
        Assert.assertTrue("DDL should contain name column", ddl.contains("[name]"));
        Assert.assertTrue("DDL should contain address column", ddl.contains("[address]"));
        Assert.assertTrue("DDL should contain primary key constraint",
                ddl.contains("CONSTRAINT [primary_id] PRIMARY KEY"));
    }

}
