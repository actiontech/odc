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

import com.oceanbase.tools.dbbrowser.model.DBIndexType;
import com.oceanbase.tools.dbbrowser.model.DBTableIndex;

/**
 * PostgreSQL 索引编辑器测试类
 *
 * <p>
 * 测试覆盖以下场景：
 * <ul>
 * <li>创建普通索引</li>
 * <li>创建唯一索引</li>
 * <li>删除索引</li>
 * <li>重命名索引</li>
 * <li>修改索引结构</li>
 * <li>批量索引操作</li>
 * </ul>
 * </p>
 */
public class PostgresIndexEditorTest {

    private PostgresIndexEditor editor;

    @Before
    public void setUp() {
        editor = new PostgresIndexEditor();
    }

    // ==================== 创建索引测试 ====================

    @Test
    public void testGenerateCreateObjectDDL_basicIndex() {
        DBTableIndex index =
                createIndex("public", "users", "idx_users_name", Arrays.asList("name"), DBIndexType.NORMAL, false);
        String ddl = editor.generateCreateObjectDDL(index);

        Assert.assertTrue(ddl.contains("CREATE INDEX \"idx_users_name\""));
        Assert.assertTrue(ddl.contains("ON \"public\".\"users\""));
        Assert.assertTrue(ddl.contains("USING btree"));
        Assert.assertTrue(ddl.contains("(\"name\")"));
    }

    @Test
    public void testGenerateCreateObjectDDL_uniqueIndex() {
        DBTableIndex index =
                createIndex("public", "users", "idx_users_email", Arrays.asList("email"), DBIndexType.UNIQUE, true);
        String ddl = editor.generateCreateObjectDDL(index);

        Assert.assertTrue(ddl.contains("CREATE UNIQUE INDEX"));
        Assert.assertTrue(ddl.contains("\"idx_users_email\""));
    }

    @Test
    public void testGenerateCreateObjectDDL_multiColumnIndex() {
        DBTableIndex index = createIndex("public", "orders", "idx_orders_composite",
                Arrays.asList("user_id", "created_at"), DBIndexType.NORMAL, false);
        String ddl = editor.generateCreateObjectDDL(index);

        Assert.assertTrue(ddl.contains("\"user_id\""));
        Assert.assertTrue(ddl.contains("\"created_at\""));
    }

    @Test
    public void testGenerateCreateObjectDDL_fulltextIndex() {
        DBTableIndex index = createIndex("public", "articles", "idx_articles_content",
                Arrays.asList("content"), DBIndexType.FULLTEXT, false);
        String ddl = editor.generateCreateObjectDDL(index);

        // 全文索引使用 gin
        Assert.assertTrue(ddl.contains("USING gin"));
    }

    // ==================== 删除索引测试 ====================

    @Test
    public void testGenerateDropObjectDDL_basic() {
        DBTableIndex index =
                createIndex("public", "users", "idx_users_name", Arrays.asList("name"), DBIndexType.NORMAL, false);
        String ddl = editor.generateDropObjectDDL(index);

        // PostgreSQL 索引是 Schema 级别对象，使用 DROP INDEX "schema"."index_name" 格式
        Assert.assertTrue(ddl.contains("DROP INDEX \"public\".\"idx_users_name\""));
        // 不应该包含 ON table
        Assert.assertFalse(ddl.contains("ON"));
    }

    // ==================== 重命名索引测试 ====================

    @Test
    public void testGenerateRenameObjectDDL_basic() {
        DBTableIndex oldIndex =
                createIndex("public", "users", "old_idx_name", Arrays.asList("name"), DBIndexType.NORMAL, false);
        DBTableIndex newIndex =
                createIndex("public", "users", "new_idx_name", Arrays.asList("name"), DBIndexType.NORMAL, false);
        String ddl = editor.generateRenameObjectDDL(oldIndex, newIndex);

        Assert.assertTrue(ddl.contains("ALTER INDEX \"public\".\"old_idx_name\""));
        Assert.assertTrue(ddl.contains("RENAME TO \"new_idx_name\""));
    }

    // ==================== 修改索引测试 ====================

    @Test
    public void testGenerateUpdateObjectDDL_renameOnly() {
        DBTableIndex oldIndex =
                createIndex("public", "users", "old_name", Arrays.asList("name"), DBIndexType.NORMAL, false);
        DBTableIndex newIndex =
                createIndex("public", "users", "new_name", Arrays.asList("name"), DBIndexType.NORMAL, false);
        String ddl = editor.generateUpdateObjectDDL(oldIndex, newIndex);

        // 仅重命名，不应该是 DROP + CREATE
        Assert.assertTrue(ddl.contains("ALTER INDEX"));
        Assert.assertTrue(ddl.contains("RENAME TO"));
        Assert.assertFalse(ddl.contains("DROP INDEX"));
        Assert.assertFalse(ddl.contains("CREATE INDEX"));
    }

    @Test
    public void testGenerateUpdateObjectDDL_changeColumns() {
        // 修改索引列：从单列变为多列
        DBTableIndex oldIndex =
                createIndex("public", "users", "idx_users", Arrays.asList("name"), DBIndexType.NORMAL, false);
        DBTableIndex newIndex =
                createIndex("public", "users", "idx_users", Arrays.asList("name", "email"), DBIndexType.NORMAL, false);
        String ddl = editor.generateUpdateObjectDDL(oldIndex, newIndex);

        // 结构性变更需要 DROP + CREATE
        Assert.assertTrue(ddl.contains("DROP INDEX"));
        Assert.assertTrue(ddl.contains("CREATE INDEX"));
    }

    @Test
    public void testGenerateUpdateObjectDDL_changeToUnique() {
        // 修改为唯一索引
        DBTableIndex oldIndex =
                createIndex("public", "users", "idx_users_email", Arrays.asList("email"), DBIndexType.NORMAL, false);
        DBTableIndex newIndex =
                createIndex("public", "users", "idx_users_email", Arrays.asList("email"), DBIndexType.UNIQUE, true);
        String ddl = editor.generateUpdateObjectDDL(oldIndex, newIndex);

        // 结构性变更需要 DROP + CREATE
        Assert.assertTrue(ddl.contains("DROP INDEX"));
        Assert.assertTrue(ddl.contains("CREATE UNIQUE INDEX"));
    }

    @Test
    public void testGenerateUpdateObjectDDL_noChange() {
        DBTableIndex oldIndex =
                createIndex("public", "users", "idx_users_name", Arrays.asList("name"), DBIndexType.NORMAL, false);
        DBTableIndex newIndex =
                createIndex("public", "users", "idx_users_name", Arrays.asList("name"), DBIndexType.NORMAL, false);
        String ddl = editor.generateUpdateObjectDDL(oldIndex, newIndex);

        // 无变化，应返回空字符串
        Assert.assertEquals("", ddl);
    }

    // ==================== 标识符转义测试 ====================

    @Test
    public void testIdentifierWithSpecialChars() {
        DBTableIndex index =
                createIndex("my_schema", "my_table", "idx\"name", Arrays.asList("col\"umn"), DBIndexType.NORMAL, false);
        String ddl = editor.generateCreateObjectDDL(index);

        // 验证双引号被正确转义
        Assert.assertTrue(ddl.contains("\"idx\"\"name\""));
        Assert.assertTrue(ddl.contains("\"col\"\"umn\""));
    }

    // ==================== 批量操作测试 ====================

    @Test
    public void testGenerateUpdateObjectListDDL_addNewIndex() {
        List<DBTableIndex> oldIndexes = new ArrayList<>();
        List<DBTableIndex> newIndexes = Arrays.asList(
                createIndex("public", "users", "idx_users_name", Arrays.asList("name"), DBIndexType.NORMAL, false));

        String ddl = editor.generateUpdateObjectListDDL(oldIndexes, newIndexes);

        Assert.assertTrue(ddl.contains("CREATE INDEX"));
    }

    @Test
    public void testGenerateUpdateObjectListDDL_dropIndex() {
        List<DBTableIndex> oldIndexes = Arrays.asList(
                createIndex("public", "users", "idx_users_name", Arrays.asList("name"), DBIndexType.NORMAL, false));
        List<DBTableIndex> newIndexes = new ArrayList<>();

        String ddl = editor.generateUpdateObjectListDDL(oldIndexes, newIndexes);

        Assert.assertTrue(ddl.contains("DROP INDEX"));
    }

    @Test
    public void testGenerateUpdateObjectListDDL_renameIndex() {
        // 重命名索引：结构相同，名称不同
        List<DBTableIndex> oldIndexes = Arrays.asList(
                createIndex("public", "users", "old_idx_name", Arrays.asList("name"), DBIndexType.NORMAL, false));
        List<DBTableIndex> newIndexes = Arrays.asList(
                createIndex("public", "users", "new_idx_name", Arrays.asList("name"), DBIndexType.NORMAL, false));

        String ddl = editor.generateUpdateObjectListDDL(oldIndexes, newIndexes);

        // 应该生成 RENAME 语句
        Assert.assertTrue(ddl.contains("ALTER INDEX"));
        Assert.assertTrue(ddl.contains("RENAME TO"));
    }

    @Test
    public void testGenerateUpdateObjectListDDL_mixedOperations() {
        // 混合操作：删除一个、修改一个、新增一个
        List<DBTableIndex> oldIndexes = Arrays.asList(
                createIndex("public", "users", "idx_to_drop", Arrays.asList("col1"), DBIndexType.NORMAL, false),
                createIndex("public", "users", "idx_to_modify", Arrays.asList("col2"), DBIndexType.NORMAL, false));
        List<DBTableIndex> newIndexes = Arrays.asList(
                createIndex("public", "users", "idx_to_modify", Arrays.asList("col2", "col3"), DBIndexType.NORMAL,
                        false),
                createIndex("public", "users", "idx_to_add", Arrays.asList("col4"), DBIndexType.NORMAL, false));

        String ddl = editor.generateUpdateObjectListDDL(oldIndexes, newIndexes);

        // 验证包含所有操作
        Assert.assertTrue("Should contain DROP INDEX", ddl.contains("DROP INDEX"));
        Assert.assertTrue("Should contain CREATE INDEX", ddl.contains("CREATE INDEX"));
    }

    // ==================== 辅助方法 ====================

    private DBTableIndex createIndex(String schemaName, String tableName, String indexName,
            List<String> columnNames, DBIndexType type, Boolean unique) {
        DBTableIndex index = new DBTableIndex();
        index.setSchemaName(schemaName);
        index.setTableName(tableName);
        index.setName(indexName);
        index.setColumnNames(columnNames);
        index.setType(type);
        index.setUnique(unique);
        return index;
    }
}
