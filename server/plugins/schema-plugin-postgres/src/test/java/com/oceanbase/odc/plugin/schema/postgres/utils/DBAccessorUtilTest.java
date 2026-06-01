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
package com.oceanbase.odc.plugin.schema.postgres.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;

import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcOperations;

import com.oceanbase.odc.common.util.JdbcOperationsUtil;
import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.tools.dbbrowser.DBBrowser;
import com.oceanbase.tools.dbbrowser.editor.DBObjectOperator;
import com.oceanbase.tools.dbbrowser.editor.DBTableEditor;
import com.oceanbase.tools.dbbrowser.editor.postgre.PostgresObjectOperator;
import com.oceanbase.tools.dbbrowser.editor.postgre.PostgresTableEditor;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;
import com.oceanbase.tools.dbbrowser.schema.postgre.PostgresSchemaAccessor;
import com.oceanbase.tools.dbbrowser.stats.DBStatsAccessor;
import com.oceanbase.tools.dbbrowser.stats.postgres.PostgresStatsAccessor;

/**
 * {@link DBAccessorUtil} 单元测试
 *
 * <p>
 * 测试覆盖：
 * <ul>
 * <li>getSchemaAccessor() 方法返回正确的实例类型</li>
 * <li>getStatsAccessor() 方法返回正确的实例类型</li>
 * <li>getTableEditor() 方法返回正确的实例类型</li>
 * <li>getObjectOperator() 方法返回正确的实例类型</li>
 * <li>DB_BROWSER_TYPE 常量正确性验证</li>
 * </ul>
 *
 * @author ODC Team
 * @date 2025-03
 * @since ODC_release_4.3.5
 */
public class DBAccessorUtilTest {

    private static final String TEST_PG_VERSION = "15.0";

    // ==================== DialectType 常量验证测试 ====================

    /**
     * 测试用例：验证 POSTGRESQL dialect type 正确
     */
    @Test
    public void testDialectType_PostgreSql() {
        DialectType dialectType = DialectType.POSTGRESQL;
        String dbBrowserType = dialectType.getDBBrowserDialectTypeName();

        assertNotNull("DB Browser type should not be null", dbBrowserType);
        assertEquals("postgresql", dbBrowserType.toLowerCase());
    }

    // ==================== DBBrowser 工厂方法验证测试 ====================

    /**
     * 测试用例：验证 DBBrowser.schemaAccessor() 工厂返回正确类型的 accessor
     */
    @Test
    public void testDBBrowser_schemaAccessor_ReturnsPostgresSchemaAccessor() {
        JdbcOperations mockJdbcOps = mock(JdbcOperations.class);

        DBSchemaAccessor accessor = DBBrowser.schemaAccessor()
                .setJdbcOperations(mockJdbcOps)
                .setType(DialectType.POSTGRESQL.getDBBrowserDialectTypeName())
                .create();

        assertNotNull("DBSchemaAccessor should not be null", accessor);
        assertTrue("Should be instance of PostgresSchemaAccessor",
                accessor instanceof PostgresSchemaAccessor);
    }

    /**
     * 测试用例：验证 DBBrowser.statsAccessor() 工厂返回正确类型的 accessor
     */
    @Test
    public void testDBBrowser_statsAccessor_ReturnsPostgresStatsAccessor() {
        JdbcOperations mockJdbcOps = mock(JdbcOperations.class);

        DBStatsAccessor accessor = DBBrowser.statsAccessor()
                .setJdbcOperations(mockJdbcOps)
                .setDbVersion(TEST_PG_VERSION)
                .setType(DialectType.POSTGRESQL.getDBBrowserDialectTypeName())
                .create();

        assertNotNull("DBStatsAccessor should not be null", accessor);
        assertTrue("Should be instance of PostgresStatsAccessor",
                accessor instanceof PostgresStatsAccessor);
    }

    /**
     * 测试用例：验证 DBBrowser.objectEditor().tableEditor() 工厂返回正确类型的 editor
     */
    @Test
    public void testDBBrowser_tableEditor_ReturnsPostgresTableEditor() {
        DBTableEditor editor = DBBrowser.objectEditor().tableEditor()
                .setDbVersion(TEST_PG_VERSION)
                .setType(DialectType.POSTGRESQL.getDBBrowserDialectTypeName())
                .create();

        assertNotNull("DBTableEditor should not be null", editor);
        assertTrue("Should be instance of PostgresTableEditor",
                editor instanceof PostgresTableEditor);
    }

    /**
     * 测试用例：验证 DBBrowser.objectEditor().objectOperator() 工厂返回正确类型的 operator
     */
    @Test
    public void testDBBrowser_objectOperator_ReturnsPostgresObjectOperator() {
        JdbcOperations mockJdbcOps = mock(JdbcOperations.class);

        DBObjectOperator operator = DBBrowser.objectEditor().objectOperator()
                .setJdbcOperations(mockJdbcOps)
                .setType(DialectType.POSTGRESQL.getDBBrowserDialectTypeName())
                .create();

        assertNotNull("DBObjectOperator should not be null", operator);
        assertTrue("Should be instance of PostgresObjectOperator",
                operator instanceof PostgresObjectOperator);
    }

    // ==================== DBAccessorUtil 静态方法测试 ====================

    /**
     * 测试用例：getSchemaAccessor 返回 PostgresSchemaAccessor 实例
     */
    @Test
    public void testGetSchemaAccessor_ReturnsPostgresSchemaAccessor() {
        Connection mockConnection = mock(Connection.class);
        JdbcOperations mockJdbcOps = mock(JdbcOperations.class);

        try (MockedStatic<JdbcOperationsUtil> mockedStatic =
                Mockito.mockStatic(JdbcOperationsUtil.class)) {

            mockedStatic.when(() -> JdbcOperationsUtil.getJdbcOperations(mockConnection))
                    .thenReturn(mockJdbcOps);

            DBSchemaAccessor accessor = DBAccessorUtil.getSchemaAccessor(mockConnection);

            assertNotNull("DBSchemaAccessor should not be null", accessor);
            assertTrue("Should be instance of PostgresSchemaAccessor",
                    accessor instanceof PostgresSchemaAccessor);
        }
    }

    /**
     * 测试用例：getStatsAccessor 返回 PostgresStatsAccessor 实例
     */
    @Test
    public void testGetStatsAccessor_ReturnsPostgresStatsAccessor() {
        Connection mockConnection = mock(Connection.class);
        JdbcOperations mockJdbcOps = mock(JdbcOperations.class);

        // Mock 版本查询
        when(mockJdbcOps.queryForObject(
                "SELECT current_setting('server_version');", String.class))
                        .thenReturn(TEST_PG_VERSION);

        try (MockedStatic<JdbcOperationsUtil> mockedJdbcOpsUtil =
                Mockito.mockStatic(JdbcOperationsUtil.class)) {

            mockedJdbcOpsUtil.when(() -> JdbcOperationsUtil.getJdbcOperations(mockConnection))
                    .thenReturn(mockJdbcOps);

            DBStatsAccessor accessor = DBAccessorUtil.getStatsAccessor(mockConnection);

            assertNotNull("DBStatsAccessor should not be null", accessor);
            assertTrue("Should be instance of PostgresStatsAccessor",
                    accessor instanceof PostgresStatsAccessor);
        }
    }

    /**
     * 测试用例：getTableEditor 返回 PostgresTableEditor 实例
     */
    @Test
    public void testGetTableEditor_ReturnsPostgresTableEditor() {
        Connection mockConnection = mock(Connection.class);
        JdbcOperations mockJdbcOps = mock(JdbcOperations.class);

        // Mock 版本查询
        when(mockJdbcOps.queryForObject(
                "SELECT current_setting('server_version');", String.class))
                        .thenReturn(TEST_PG_VERSION);

        try (MockedStatic<JdbcOperationsUtil> mockedJdbcOpsUtil =
                Mockito.mockStatic(JdbcOperationsUtil.class)) {

            mockedJdbcOpsUtil.when(() -> JdbcOperationsUtil.getJdbcOperations(mockConnection))
                    .thenReturn(mockJdbcOps);

            DBTableEditor editor = DBAccessorUtil.getTableEditor(mockConnection);

            assertNotNull("DBTableEditor should not be null", editor);
            assertTrue("Should be instance of PostgresTableEditor",
                    editor instanceof PostgresTableEditor);
        }
    }

    /**
     * 测试用例：getObjectOperator 返回 PostgresObjectOperator 实例
     */
    @Test
    public void testGetObjectOperator_ReturnsPostgresObjectOperator() {
        Connection mockConnection = mock(Connection.class);
        JdbcOperations mockJdbcOps = mock(JdbcOperations.class);

        try (MockedStatic<JdbcOperationsUtil> mockedStatic =
                Mockito.mockStatic(JdbcOperationsUtil.class)) {

            mockedStatic.when(() -> JdbcOperationsUtil.getJdbcOperations(mockConnection))
                    .thenReturn(mockJdbcOps);

            DBObjectOperator operator = DBAccessorUtil.getObjectOperator(mockConnection);

            assertNotNull("DBObjectOperator should not be null", operator);
            assertTrue("Should be instance of PostgresObjectOperator",
                    operator instanceof PostgresObjectOperator);
        }
    }

    // ==================== 综合测试 ====================

    /**
     * 测试用例：所有方法不抛出异常
     */
    @Test
    public void testAllMethods_NoException() {
        Connection mockConnection = mock(Connection.class);
        JdbcOperations mockJdbcOps = mock(JdbcOperations.class);

        // Mock 版本查询
        when(mockJdbcOps.queryForObject(
                "SELECT current_setting('server_version');", String.class))
                        .thenReturn(TEST_PG_VERSION);

        try (MockedStatic<JdbcOperationsUtil> mockedStatic =
                Mockito.mockStatic(JdbcOperationsUtil.class)) {

            mockedStatic.when(() -> JdbcOperationsUtil.getJdbcOperations(mockConnection))
                    .thenReturn(mockJdbcOps);

            try {
                DBSchemaAccessor schemaAccessor = DBAccessorUtil.getSchemaAccessor(mockConnection);
                DBStatsAccessor statsAccessor = DBAccessorUtil.getStatsAccessor(mockConnection);
                DBTableEditor tableEditor = DBAccessorUtil.getTableEditor(mockConnection);
                DBObjectOperator objectOperator = DBAccessorUtil.getObjectOperator(mockConnection);

                assertNotNull("DBSchemaAccessor should not be null", schemaAccessor);
                assertNotNull("DBStatsAccessor should not be null", statsAccessor);
                assertNotNull("DBTableEditor should not be null", tableEditor);
                assertNotNull("DBObjectOperator should not be null", objectOperator);
            } catch (Exception e) {
                throw new AssertionError("Should not throw exception", e);
            }
        }
    }
}
