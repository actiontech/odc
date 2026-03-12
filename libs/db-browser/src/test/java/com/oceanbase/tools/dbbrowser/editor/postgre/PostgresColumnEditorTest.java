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
package com.oceanbase.tools.dbbrowser.editor.postgre;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oceanbase.tools.dbbrowser.model.DBTableColumn;

/**
 * PostgreSQL 列编辑器测试类
 *
 * <p>
 * 测试覆盖以下场景：
 * <ul>
 * <li>添加列（CREATE）</li>
 * <li>删除列（DROP）</li>
 * <li>重命名列（RENAME）</li>
 * <li>修改列类型</li>
 * <li>修改列默认值</li>
 * <li>修改列 NULL/NOT NULL</li>
 * <li>修改列注释</li>
 * </ul>
 * </p>
 */
public class PostgresColumnEditorTest {

    private PostgresColumnEditor editor;

    @Before
    public void setUp() {
        editor = new PostgresColumnEditor();
    }

    // ==================== 添加列测试 ====================

    @Test
    public void testGenerateCreateObjectDDL_basicColumn() {
        DBTableColumn column = createColumn("public", "users", "name", "varchar", 100L, null, true, null, null);
        String ddl = editor.generateCreateObjectDDL(column);

        Assert.assertTrue(ddl.contains("ALTER TABLE \"public\".\"users\""));
        Assert.assertTrue(ddl.contains("ADD COLUMN \"name\""));
        Assert.assertTrue(ddl.contains("varchar(100)"));
        Assert.assertTrue(ddl.contains("NULL"));
    }

    @Test
    public void testGenerateCreateObjectDDL_notNullWithDefault() {
        DBTableColumn column = createColumn("public", "users", "age", "integer", null, null, false, "0", null);
        String ddl = editor.generateCreateObjectDDL(column);

        Assert.assertTrue(ddl.contains("NOT NULL"));
        Assert.assertTrue(ddl.contains("DEFAULT 0"));
    }

    @Test
    public void testGenerateCreateObjectDDL_withComment() {
        DBTableColumn column = createColumn("public", "users", "email", "varchar", 255L, null, true, null, "用户邮箱");
        String ddl = editor.generateCreateObjectDDL(column);

        Assert.assertTrue(ddl.contains("COMMENT ON COLUMN"));
        Assert.assertTrue(ddl.contains("\"public\".\"users\".\"email\""));
        Assert.assertTrue(ddl.contains("IS '用户邮箱'"));
    }

    @Test
    public void testGenerateCreateObjectDDL_numericWithScale() {
        DBTableColumn column = createColumn("public", "products", "price", "numeric", 10L, 2, false, "0.00", null);
        String ddl = editor.generateCreateObjectDDL(column);

        Assert.assertTrue(ddl.contains("numeric(10,2)"));
    }

    // ==================== 删除列测试 ====================

    @Test
    public void testGenerateDropObjectDDL_basic() {
        DBTableColumn column = createColumn("public", "users", "old_column", "varchar", 100L, null, true, null, null);
        String ddl = editor.generateDropObjectDDL(column);

        Assert.assertEquals("ALTER TABLE \"public\".\"users\" DROP COLUMN \"old_column\";\n", ddl);
    }

    // ==================== 重命名列测试 ====================

    @Test
    public void testGenerateRenameObjectDDL_basic() {
        DBTableColumn oldColumn = createColumn("public", "users", "old_name", "varchar", 100L, null, true, null, null);
        DBTableColumn newColumn = createColumn("public", "users", "new_name", "varchar", 100L, null, true, null, null);
        String ddl = editor.generateRenameObjectDDL(oldColumn, newColumn);

        Assert.assertTrue(ddl.contains("ALTER TABLE \"public\".\"users\""));
        Assert.assertTrue(ddl.contains("RENAME COLUMN \"old_name\" TO \"new_name\""));
    }

    // ==================== 修改列测试 ====================

    @Test
    public void testGenerateUpdateObjectDDL_changeType() {
        DBTableColumn oldColumn = createColumn("public", "users", "age", "integer", null, null, true, null, null);
        DBTableColumn newColumn = createColumn("public", "users", "age", "bigint", null, null, true, null, null);
        String ddl = editor.generateUpdateObjectDDL(oldColumn, newColumn);

        Assert.assertTrue(ddl.contains("ALTER COLUMN \"age\" TYPE bigint"));
    }

    @Test
    public void testGenerateUpdateObjectDDL_changeTypeWithPrecision() {
        DBTableColumn oldColumn = createColumn("public", "users", "name", "varchar", 50L, null, true, null, null);
        DBTableColumn newColumn = createColumn("public", "users", "name", "varchar", 100L, null, true, null, null);
        String ddl = editor.generateUpdateObjectDDL(oldColumn, newColumn);

        Assert.assertTrue(ddl.contains("ALTER COLUMN \"name\" TYPE varchar(100)"));
    }

    @Test
    public void testGenerateUpdateObjectDDL_setDefaultValue() {
        DBTableColumn oldColumn = createColumn("public", "users", "status", "varchar", 20L, null, true, null, null);
        DBTableColumn newColumn =
                createColumn("public", "users", "status", "varchar", 20L, null, true, "'active'", null);
        String ddl = editor.generateUpdateObjectDDL(oldColumn, newColumn);

        Assert.assertTrue(ddl.contains("SET DEFAULT 'active'"));
    }

    @Test
    public void testGenerateUpdateObjectDDL_dropDefaultValue() {
        DBTableColumn oldColumn =
                createColumn("public", "users", "status", "varchar", 20L, null, true, "'active'", null);
        DBTableColumn newColumn = createColumn("public", "users", "status", "varchar", 20L, null, true, null, null);
        String ddl = editor.generateUpdateObjectDDL(oldColumn, newColumn);

        Assert.assertTrue(ddl.contains("DROP DEFAULT"));
    }

    @Test
    public void testGenerateUpdateObjectDDL_setNotNull() {
        DBTableColumn oldColumn = createColumn("public", "users", "email", "varchar", 255L, null, true, null, null);
        DBTableColumn newColumn = createColumn("public", "users", "email", "varchar", 255L, null, false, null, null);
        String ddl = editor.generateUpdateObjectDDL(oldColumn, newColumn);

        Assert.assertTrue(ddl.contains("SET NOT NULL"));
    }

    @Test
    public void testGenerateUpdateObjectDDL_dropNotNull() {
        DBTableColumn oldColumn = createColumn("public", "users", "email", "varchar", 255L, null, false, null, null);
        DBTableColumn newColumn = createColumn("public", "users", "email", "varchar", 255L, null, true, null, null);
        String ddl = editor.generateUpdateObjectDDL(oldColumn, newColumn);

        Assert.assertTrue(ddl.contains("DROP NOT NULL"));
    }

    @Test
    public void testGenerateUpdateObjectDDL_changeComment() {
        DBTableColumn oldColumn = createColumn("public", "users", "name", "varchar", 100L, null, true, null, "旧注释");
        DBTableColumn newColumn = createColumn("public", "users", "name", "varchar", 100L, null, true, null, "新注释");
        String ddl = editor.generateUpdateObjectDDL(oldColumn, newColumn);

        Assert.assertTrue(ddl.contains("COMMENT ON COLUMN"));
        Assert.assertTrue(ddl.contains("IS '新注释'"));
    }

    @Test
    public void testGenerateUpdateObjectDDL_multipleChanges() {
        // 修改类型 + 设置默认值 + 设置 NOT NULL + 修改注释
        DBTableColumn oldColumn = createColumn("public", "users", "age", "integer", null, null, true, null, "年龄");
        DBTableColumn newColumn = createColumn("public", "users", "age", "bigint", null, null, false, "0", "用户年龄");
        String ddl = editor.generateUpdateObjectDDL(oldColumn, newColumn);

        Assert.assertTrue("Should change type", ddl.contains("ALTER COLUMN \"age\" TYPE bigint"));
        Assert.assertTrue("Should set default", ddl.contains("SET DEFAULT 0"));
        Assert.assertTrue("Should set not null", ddl.contains("SET NOT NULL"));
        Assert.assertTrue("Should update comment", ddl.contains("IS '用户年龄'"));
    }

    @Test
    public void testGenerateUpdateObjectDDL_renameOnly() {
        DBTableColumn oldColumn = createColumn("public", "users", "old_name", "varchar", 100L, null, true, null, null);
        DBTableColumn newColumn = createColumn("public", "users", "new_name", "varchar", 100L, null, true, null, null);
        String ddl = editor.generateUpdateObjectDDL(oldColumn, newColumn);

        Assert.assertTrue(ddl.contains("RENAME COLUMN \"old_name\" TO \"new_name\""));
        // 不应该包含 ALTER COLUMN TYPE
        Assert.assertFalse(ddl.contains("ALTER COLUMN \"new_name\" TYPE"));
    }

    // ==================== 标识符转义测试 ====================

    @Test
    public void testIdentifierWithSpecialChars() {
        // 列名包含特殊字符（需要转义）
        DBTableColumn column =
                createColumn("my_schema", "my_table", "col\"umn", "varchar", 50L, null, true, null, null);
        String ddl = editor.generateCreateObjectDDL(column);

        // 验证双引号被正确转义
        Assert.assertTrue(ddl.contains("\"col\"\"umn\""));
    }

    @Test
    public void testSchemaNameWithSpecialChars() {
        DBTableColumn column = createColumn("my\"schema", "users", "name", "varchar", 100L, null, true, null, null);
        String ddl = editor.generateCreateObjectDDL(column);

        Assert.assertTrue(ddl.contains("\"my\"\"schema\""));
    }

    // ==================== 空值处理测试 ====================

    @Test
    public void testGenerateCreateObjectDDL_nullComment() {
        DBTableColumn column = createColumn("public", "users", "name", "varchar", 100L, null, true, null, null);
        String ddl = editor.generateCreateObjectDDL(column);

        // 不应该包含 COMMENT ON COLUMN
        Assert.assertFalse(ddl.contains("COMMENT ON COLUMN"));
    }

    // ==================== 辅助方法 ====================

    private DBTableColumn createColumn(String schemaName, String tableName, String name,
            String typeName, Long precision, Integer scale, Boolean nullable,
            String defaultValue, String comment) {
        DBTableColumn column = new DBTableColumn();
        column.setSchemaName(schemaName);
        column.setTableName(tableName);
        column.setName(name);
        column.setTypeName(typeName);
        column.setPrecision(precision);
        column.setScale(scale);
        column.setNullable(nullable);
        column.setDefaultValue(defaultValue);
        column.setComment(comment);
        return column;
    }
}
