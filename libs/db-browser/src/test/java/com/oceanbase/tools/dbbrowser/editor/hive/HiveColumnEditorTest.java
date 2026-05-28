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
import java.util.Collection;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oceanbase.tools.dbbrowser.model.DBTableColumn;

/**
 * Parameterized tests for {@link HiveColumnEditor} covering design.md 5.2.4 scenarios.
 */
@RunWith(Parameterized.class)
public class HiveColumnEditorTest {

    private HiveColumnEditor columnEditor;

    private final String caseName;
    private final TestType testType;
    private final DBTableColumn oldColumn;
    private final DBTableColumn newColumn;
    private final String expectedDDL;
    private final Class<? extends Exception> expectedException;

    enum TestType {
        CREATE, UPDATE, DROP
    }

    public HiveColumnEditorTest(String caseName, TestType testType,
            DBTableColumn oldColumn, DBTableColumn newColumn,
            String expectedDDL, Class<? extends Exception> expectedException) {
        this.caseName = caseName;
        this.testType = testType;
        this.oldColumn = oldColumn;
        this.newColumn = newColumn;
        this.expectedDDL = expectedDDL;
        this.expectedException = expectedException;
    }

    @Before
    public void setUp() {
        this.columnEditor = new HiveColumnEditor();
    }

    @Parameters(name = "{0}")
    public static Collection<Object[]> testCases() {
        return Arrays.asList(
                // Case 1: Add simple type columns (STRING, BIGINT)
                new Object[] {
                        "add_simple_type_column_STRING",
                        TestType.CREATE,
                        null,
                        createColumn("test_db", "users", "username", "STRING", null),
                        "ALTER TABLE `test_db`.`users` ADD COLUMNS (`username` STRING);\n",
                        null
                },
                new Object[] {
                        "add_simple_type_column_BIGINT",
                        TestType.CREATE,
                        null,
                        createColumn("test_db", "users", "user_id", "BIGINT", null),
                        "ALTER TABLE `test_db`.`users` ADD COLUMNS (`user_id` BIGINT);\n",
                        null
                },
                // Case 2: Add complex type column (ARRAY<STRING>)
                new Object[] {
                        "add_complex_type_column_ARRAY",
                        TestType.CREATE,
                        null,
                        createColumn("test_db", "users", "tags", "ARRAY<STRING>", null),
                        "ALTER TABLE `test_db`.`users` ADD COLUMNS (`tags` ARRAY<STRING>);\n",
                        null
                },
                // Case 3: Add column with comment
                new Object[] {
                        "add_column_with_comment",
                        TestType.CREATE,
                        null,
                        createColumn("test_db", "users", "email", "STRING", "user email address"),
                        "ALTER TABLE `test_db`.`users` ADD COLUMNS (`email` STRING COMMENT 'user email address');\n",
                        null
                },
                // Case 4: Modify column name (CHANGE COLUMN)
                new Object[] {
                        "change_column_name_and_comment",
                        TestType.UPDATE,
                        createColumn("test_db", "users", "old_name", "STRING", "old comment"),
                        createColumn("test_db", "users", "new_name", "STRING", "new comment"),
                        "ALTER TABLE `test_db`.`users` CHANGE COLUMN `old_name` `new_name` STRING COMMENT 'new comment';\n",
                        null
                },
                // Case 5: Drop column -- should throw UnsupportedOperationException
                new Object[] {
                        "drop_column_unsupported",
                        TestType.DROP,
                        null,
                        createColumn("test_db", "users", "obsolete_col", "STRING", null),
                        null,
                        UnsupportedOperationException.class
                });
    }

    @Test
    public void testColumnEditor() {
        switch (testType) {
            case CREATE:
                String createDDL = columnEditor.generateCreateObjectDDL(newColumn);
                Assert.assertEquals("Test case: " + caseName, expectedDDL, createDDL);
                break;
            case UPDATE:
                String updateDDL = columnEditor.generateUpdateObjectDDL(oldColumn, newColumn);
                Assert.assertEquals("Test case: " + caseName, expectedDDL, updateDDL);
                break;
            case DROP:
                try {
                    columnEditor.generateDropObjectDDL(newColumn);
                    Assert.fail("Test case: " + caseName + " -- Expected exception: " + expectedException.getName());
                } catch (Exception e) {
                    Assert.assertTrue("Test case: " + caseName + " -- Expected " + expectedException.getName()
                            + " but got " + e.getClass().getName(),
                            expectedException.isInstance(e));
                }
                break;
        }
    }

    private static DBTableColumn createColumn(String schema, String table, String name,
            String typeName, String comment) {
        DBTableColumn column = new DBTableColumn();
        column.setSchemaName(schema);
        column.setTableName(table);
        column.setName(name);
        column.setTypeName(typeName);
        column.setComment(comment);
        return column;
    }

}
