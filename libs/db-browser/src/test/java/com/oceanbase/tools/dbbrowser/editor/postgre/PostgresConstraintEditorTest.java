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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oceanbase.tools.dbbrowser.model.DBConstraintType;
import com.oceanbase.tools.dbbrowser.model.DBForeignKeyModifyRule;
import com.oceanbase.tools.dbbrowser.model.DBTableConstraint;

/**
 * PostgreSQL 约束编辑器测试类
 *
 * <p>
 * 测试覆盖以下场景：
 * <ul>
 * <li>创建主键约束</li>
 * <li>创建唯一约束</li>
 * <li>创建外键约束</li>
 * <li>创建检查约束</li>
 * <li>删除约束</li>
 * <li>重命名约束</li>
 * <li>修改约束</li>
 * </ul>
 * </p>
 */
public class PostgresConstraintEditorTest {

    private PostgresConstraintEditor editor;

    @Before
    public void setUp() {
        editor = new PostgresConstraintEditor();
    }

    // ==================== 创建约束测试 ====================

    @Test
    public void testGenerateCreateObjectDDL_primaryKey() {
        DBTableConstraint constraint = createConstraint("public", "users", "pk_users", "id",
                DBConstraintType.PRIMARY_KEY, null, null, null, null, null, null);
        String ddl = editor.generateCreateObjectDDL(constraint);

        Assert.assertTrue(ddl.contains("ALTER TABLE \"public\".\"users\""));
        Assert.assertTrue(ddl.contains("ADD CONSTRAINT \"pk_users\""));
        Assert.assertTrue(ddl.contains("PRIMARY KEY"));
        Assert.assertTrue(ddl.contains("(\"id\")"));
    }

    @Test
    public void testGenerateCreateObjectDDL_compositePrimaryKey() {
        DBTableConstraint constraint = createConstraint("public", "order_items", "pk_order_items",
                null, DBConstraintType.PRIMARY_KEY, null, null, null, null, null, null);
        constraint.setColumnNames(Arrays.asList("order_id", "item_id"));

        String ddl = editor.generateCreateObjectDDL(constraint);

        Assert.assertTrue(ddl.contains("PRIMARY KEY"));
        Assert.assertTrue(ddl.contains("(\"order_id\", \"item_id\")"));
    }

    @Test
    public void testGenerateCreateObjectDDL_uniqueConstraint() {
        DBTableConstraint constraint = createConstraint("public", "users", "uk_users_email", "email",
                DBConstraintType.UNIQUE, null, null, null, null, null, null);
        String ddl = editor.generateCreateObjectDDL(constraint);

        Assert.assertTrue(ddl.contains("ADD CONSTRAINT \"uk_users_email\""));
        Assert.assertTrue(ddl.contains("UNIQUE"));
        Assert.assertTrue(ddl.contains("(\"email\")"));
    }

    @Test
    public void testGenerateCreateObjectDDL_foreignKey() {
        DBTableConstraint constraint = createConstraint("public", "orders", "fk_orders_user",
                "user_id", DBConstraintType.FOREIGN_KEY, "public", "users", "id",
                null, null, null);
        String ddl = editor.generateCreateObjectDDL(constraint);

        Assert.assertTrue(ddl.contains("FOREIGN KEY"));
        Assert.assertTrue(ddl.contains("(\"user_id\")"));
        Assert.assertTrue(ddl.contains("REFERENCES \"public\".\"users\""));
        Assert.assertTrue(ddl.contains("(\"id\")"));
    }

    @Test
    public void testGenerateCreateObjectDDL_foreignKeyWithCascade() {
        DBTableConstraint constraint = createConstraint("public", "orders", "fk_orders_user",
                "user_id", DBConstraintType.FOREIGN_KEY, "public", "users", "id",
                DBForeignKeyModifyRule.CASCADE, null, null);
        String ddl = editor.generateCreateObjectDDL(constraint);

        Assert.assertTrue(ddl.contains("ON DELETE CASCADE"));
    }

    @Test
    public void testGenerateCreateObjectDDL_checkConstraint() {
        DBTableConstraint constraint = createConstraint("public", "products", "ck_products_price",
                null, DBConstraintType.CHECK, null, null, null, null, null, "price > 0");
        String ddl = editor.generateCreateObjectDDL(constraint);

        Assert.assertTrue(ddl.contains("ADD CONSTRAINT \"ck_products_price\""));
        Assert.assertTrue(ddl.contains("CHECK"));
        Assert.assertTrue(ddl.contains("(price > 0)"));
    }

    // ==================== 删除约束测试 ====================

    @Test
    public void testGenerateDropObjectDDL_basic() {
        DBTableConstraint constraint = createConstraint("public", "users", "pk_users", "id",
                DBConstraintType.PRIMARY_KEY, null, null, null, null, null, null);
        String ddl = editor.generateDropObjectDDL(constraint);

        Assert.assertTrue(ddl.contains("ALTER TABLE \"public\".\"users\""));
        Assert.assertTrue(ddl.contains("DROP CONSTRAINT \"pk_users\""));
    }

    // ==================== 重命名约束测试 ====================

    @Test
    public void testGenerateRenameObjectDDL_basic() {
        DBTableConstraint oldConstraint = createConstraint("public", "users", "old_name", "id",
                DBConstraintType.PRIMARY_KEY, null, null, null, null, null, null);
        DBTableConstraint newConstraint = createConstraint("public", "users", "new_name", "id",
                DBConstraintType.PRIMARY_KEY, null, null, null, null, null, null);
        String ddl = editor.generateRenameObjectDDL(oldConstraint, newConstraint);

        Assert.assertTrue(ddl.contains("ALTER TABLE \"public\".\"users\""));
        Assert.assertTrue(ddl.contains("RENAME CONSTRAINT \"old_name\" TO \"new_name\""));
    }

    // ==================== 修改约束测试 ====================

    @Test
    public void testGenerateUpdateObjectDDL_renameOnly() {
        DBTableConstraint oldConstraint = createConstraint("public", "users", "old_name", "id",
                DBConstraintType.PRIMARY_KEY, null, null, null, null, null, null);
        DBTableConstraint newConstraint = createConstraint("public", "users", "new_name", "id",
                DBConstraintType.PRIMARY_KEY, null, null, null, null, null, null);
        String ddl = editor.generateUpdateObjectDDL(oldConstraint, newConstraint);

        // 仅重命名，不应该包含 DROP + CREATE
        Assert.assertTrue(ddl.contains("RENAME CONSTRAINT"));
        Assert.assertFalse(ddl.contains("DROP CONSTRAINT"));
        Assert.assertFalse(ddl.contains("ADD CONSTRAINT"));
    }

    @Test
    public void testGenerateUpdateObjectDDL_changeColumns() {
        DBTableConstraint oldConstraint = createConstraint("public", "users", "uk_users", "email",
                DBConstraintType.UNIQUE, null, null, null, null, null, null);
        DBTableConstraint newConstraint = createConstraint("public", "users", "uk_users", "username",
                DBConstraintType.UNIQUE, null, null, null, null, null, null);
        String ddl = editor.generateUpdateObjectDDL(oldConstraint, newConstraint);

        // 结构性变更需要 DROP + CREATE
        Assert.assertTrue(ddl.contains("DROP CONSTRAINT"));
        Assert.assertTrue(ddl.contains("ADD CONSTRAINT"));
    }

    @Test
    public void testGenerateUpdateObjectDDL_changeType() {
        DBTableConstraint oldConstraint = createConstraint("public", "users", "constr", "col",
                DBConstraintType.UNIQUE, null, null, null, null, null, null);
        DBTableConstraint newConstraint = createConstraint("public", "users", "constr", "col",
                DBConstraintType.PRIMARY_KEY, null, null, null, null, null, null);
        String ddl = editor.generateUpdateObjectDDL(oldConstraint, newConstraint);

        // 约束类型变更需要 DROP + CREATE
        Assert.assertTrue(ddl.contains("DROP CONSTRAINT"));
        Assert.assertTrue(ddl.contains("ADD CONSTRAINT"));
    }

    @Test
    public void testGenerateUpdateObjectDDL_changeForeignKeyReference() {
        DBTableConstraint oldConstraint = createConstraint("public", "orders", "fk_orders",
                "user_id", DBConstraintType.FOREIGN_KEY, "public", "users", "id",
                null, null, null);
        DBTableConstraint newConstraint = createConstraint("public", "orders", "fk_orders",
                "user_id", DBConstraintType.FOREIGN_KEY, "public", "customers", "id",
                null, null, null);
        String ddl = editor.generateUpdateObjectDDL(oldConstraint, newConstraint);

        // 外键引用变更需要 DROP + CREATE
        Assert.assertTrue(ddl.contains("DROP CONSTRAINT"));
        Assert.assertTrue(ddl.contains("REFERENCES \"public\".\"customers\""));
    }

    @Test
    public void testGenerateUpdateObjectDDL_changeCheckClause() {
        DBTableConstraint oldConstraint = createConstraint("public", "products", "ck_price",
                null, DBConstraintType.CHECK, null, null, null, null, null, "price > 0");
        DBTableConstraint newConstraint = createConstraint("public", "products", "ck_price",
                null, DBConstraintType.CHECK, null, null, null, null, null, "price >= 0");
        String ddl = editor.generateUpdateObjectDDL(oldConstraint, newConstraint);

        // CHECK 子句变更需要 DROP + CREATE
        Assert.assertTrue(ddl.contains("DROP CONSTRAINT"));
        Assert.assertTrue(ddl.contains("CHECK (price >= 0)"));
    }

    @Test
    public void testGenerateUpdateObjectDDL_noChange() {
        DBTableConstraint oldConstraint = createConstraint("public", "users", "pk_users", "id",
                DBConstraintType.PRIMARY_KEY, null, null, null, null, null, null);
        DBTableConstraint newConstraint = createConstraint("public", "users", "pk_users", "id",
                DBConstraintType.PRIMARY_KEY, null, null, null, null, null, null);
        String ddl = editor.generateUpdateObjectDDL(oldConstraint, newConstraint);

        // 无变化，应返回空字符串
        Assert.assertEquals("", ddl);
    }

    // ==================== 标识符转义测试 ====================

    @Test
    public void testIdentifierWithSpecialChars() {
        DBTableConstraint constraint = createConstraint("my_schema", "my_table", "con\"str", "col\"umn",
                DBConstraintType.PRIMARY_KEY, null, null, null, null, null, null);
        String ddl = editor.generateCreateObjectDDL(constraint);

        // 验证双引号被正确转义
        Assert.assertTrue(ddl.contains("\"con\"\"str\""));
        Assert.assertTrue(ddl.contains("\"col\"\"umn\""));
    }

    // ==================== 批量操作测试 ====================

    @Test
    public void testGenerateUpdateObjectListDDL_addNewConstraint() {
        List<DBTableConstraint> oldConstraints = new ArrayList<>();
        List<DBTableConstraint> newConstraints = Arrays.asList(
                createConstraint("public", "users", "pk_users", "id",
                        DBConstraintType.PRIMARY_KEY, null, null, null, null, null, null));

        String ddl = editor.generateUpdateObjectListDDL(oldConstraints, newConstraints);

        Assert.assertTrue(ddl.contains("ADD CONSTRAINT"));
    }

    @Test
    public void testGenerateUpdateObjectListDDL_dropConstraint() {
        List<DBTableConstraint> oldConstraints = Arrays.asList(
                createConstraint("public", "users", "uk_users_email", "email",
                        DBConstraintType.UNIQUE, null, null, null, null, null, null));
        List<DBTableConstraint> newConstraints = new ArrayList<>();

        String ddl = editor.generateUpdateObjectListDDL(oldConstraints, newConstraints);

        Assert.assertTrue(ddl.contains("DROP CONSTRAINT"));
    }

    @Test
    public void testGenerateUpdateObjectListDDL_mixedOperations() {
        // 混合操作：删除一个、修改一个、新增一个
        DBTableConstraint toDrop = createConstraint("public", "users", "to_drop", "col1",
                DBConstraintType.UNIQUE, null, null, null, null, null, null);
        toDrop.setOrdinalPosition(1);

        DBTableConstraint oldToModify = createConstraint("public", "users", "to_modify", "col2",
                DBConstraintType.UNIQUE, null, null, null, null, null, null);
        oldToModify.setOrdinalPosition(2);

        DBTableConstraint newToModify = createConstraint("public", "users", "to_modify", null,
                DBConstraintType.UNIQUE, null, null, null, null, null, null);
        newToModify.setOrdinalPosition(2);
        newToModify.setColumnNames(Arrays.asList("col2", "col3"));

        DBTableConstraint toAdd = createConstraint("public", "users", "to_add", "col4",
                DBConstraintType.UNIQUE, null, null, null, null, null, null);
        // ordinalPosition 为 null 表示新增

        List<DBTableConstraint> oldConstraints = Arrays.asList(toDrop, oldToModify);
        List<DBTableConstraint> newConstraints = Arrays.asList(newToModify, toAdd);

        String ddl = editor.generateUpdateObjectListDDL(oldConstraints, newConstraints);

        // 验证包含所有操作
        Assert.assertTrue("Should contain DROP CONSTRAINT", ddl.contains("DROP CONSTRAINT"));
        Assert.assertTrue("Should contain ADD CONSTRAINT", ddl.contains("ADD CONSTRAINT"));
    }

    // ==================== 辅助方法 ====================

    private DBTableConstraint createConstraint(String schemaName, String tableName, String constraintName,
            String columnName, DBConstraintType type,
            String refSchemaName, String refTableName, String refColumnName,
            DBForeignKeyModifyRule onDeleteRule, DBForeignKeyModifyRule onUpdateRule,
            String checkClause) {
        DBTableConstraint constraint = new DBTableConstraint();
        constraint.setSchemaName(schemaName);
        constraint.setTableName(tableName);
        constraint.setName(constraintName);
        if (columnName != null) {
            constraint.setColumnNames(Arrays.asList(columnName));
        }
        constraint.setType(type);
        constraint.setReferenceSchemaName(refSchemaName);
        constraint.setReferenceTableName(refTableName);
        if (refColumnName != null) {
            constraint.setReferenceColumnNames(Arrays.asList(refColumnName));
        }
        constraint.setOnDeleteRule(onDeleteRule);
        constraint.setOnUpdateRule(onUpdateRule);
        constraint.setCheckClause(checkClause);
        return constraint;
    }
}
