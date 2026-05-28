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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oceanbase.tools.dbbrowser.model.DBObjectType;

/**
 * Tests for {@link HiveObjectOperator} covering design.md 5.2.6 scenarios.
 * <p>
 * Tests the SQL generation logic only (no JDBC execution). Uses
 * {@link HiveObjectOperator#buildDropSql} for pure-SQL verification.
 * </p>
 */
@RunWith(Parameterized.class)
public class HiveObjectOperatorTest {

    private final String caseName;
    private final DBObjectType objectType;
    private final String schemaName;
    private final String objectName;
    private final String expectedSql;

    public HiveObjectOperatorTest(String caseName, DBObjectType objectType,
            String schemaName, String objectName, String expectedSql) {
        this.caseName = caseName;
        this.objectType = objectType;
        this.schemaName = schemaName;
        this.objectName = objectName;
        this.expectedSql = expectedSql;
    }

    @Parameters(name = "{0}")
    public static Collection<Object[]> testCases() {
        return Arrays.asList(
                new Object[] {
                        "drop_table_if_exists",
                        DBObjectType.TABLE,
                        "test_db",
                        "users",
                        "DROP TABLE IF EXISTS `test_db`.`users`"
                },
                new Object[] {
                        "drop_view_if_exists",
                        DBObjectType.VIEW,
                        "test_db",
                        "user_view",
                        "DROP VIEW IF EXISTS `test_db`.`user_view`"
                },
                new Object[] {
                        "drop_table_without_schema",
                        DBObjectType.TABLE,
                        null,
                        "temp_table",
                        "DROP TABLE IF EXISTS `temp_table`"
                });
    }

    @Test
    public void testBuildDropSql() {
        String sql = HiveObjectOperator.buildDropSql(objectType, schemaName, objectName);
        Assert.assertEquals("Test case: " + caseName, expectedSql, sql);
    }

}
