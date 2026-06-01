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
package com.oceanbase.odc.plugin.connect.postgres;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.oceanbase.odc.core.shared.exception.OBException;
import com.oceanbase.odc.plugin.connect.model.diagnose.SqlExplain;

/**
 * {@link PostgresDiagnoseExtensionPoint} 单元测试
 *
 * <p>
 * 测试覆盖：
 * <ul>
 * <li>getExplain() 方法的 EXPLAIN SQL 生成和结果解析</li>
 * <li>getPhysicalPlanBySqlId() 方法抛出 UnsupportedOperationException</li>
 * <li>getPhysicalPlanBySql() 方法抛出 UnsupportedOperationException</li>
 * <li>getExecutionDetailById() 方法抛出 UnsupportedOperationException</li>
 * <li>getExecutionDetailBySql() 方法抛出 UnsupportedOperationException</li>
 * <li>getQueryProfileByTraceIdAndSessIds() 方法抛出 UnsupportedOperationException</li>
 * </ul>
 *
 * @author ODC Team
 * @date 2025-03
 * @since ODC_release_4.3.5
 */
public class PostgresDiagnoseExtensionPointTest {

    private PostgresDiagnoseExtensionPoint extension;

    @Mock
    private Statement mockStatement;

    @Mock
    private Connection mockConnection;

    @Mock
    private ResultSet mockResultSet;

    @Mock
    private ResultSetMetaData mockMetaData;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        extension = new PostgresDiagnoseExtensionPoint();
    }

    // ==================== getExplain 测试 ====================

    /**
     * 测试用例：getExplain 生成正确的 EXPLAIN SQL 并返回结果
     */
    @Test
    public void testGetExplain_SelectQuery() throws SQLException {
        String sql = "SELECT * FROM users WHERE id = 1";

        // Mock ResultSet behavior
        when(mockStatement.executeQuery("EXPLAIN " + sql)).thenReturn(mockResultSet);
        when(mockResultSet.getMetaData()).thenReturn(mockMetaData);
        when(mockMetaData.getColumnCount()).thenReturn(1);
        when(mockMetaData.getColumnName(1)).thenReturn("QUERY PLAN");
        when(mockMetaData.getColumnDisplaySize(1)).thenReturn(100);
        when(mockResultSet.next()).thenReturn(true, true, false);
        when(mockResultSet.getString(1)).thenReturn(
                "Index Scan using users_pkey on users  (cost=0.15..8.17 rows=1 width=4)",
                "  Index Cond: (id = 1)");

        SqlExplain result = extension.getExplain(mockStatement, sql);

        assertNotNull(result);
        assertNotNull(result.getOriginalText());
        assertFalse(result.getShowFormatInfo());
        assertTrue(result.getOriginalText().contains("QUERY PLAN"));
    }

    /**
     * 测试用例：getExplain 对简单查询生成正确的 EXPLAIN 语句
     */
    @Test
    public void testGetExplain_SimpleQuery() throws SQLException {
        String sql = "SELECT 1";

        when(mockStatement.executeQuery("EXPLAIN " + sql)).thenReturn(mockResultSet);
        when(mockResultSet.getMetaData()).thenReturn(mockMetaData);
        when(mockMetaData.getColumnCount()).thenReturn(1);
        when(mockMetaData.getColumnName(1)).thenReturn("QUERY PLAN");
        when(mockMetaData.getColumnDisplaySize(1)).thenReturn(50);
        when(mockResultSet.next()).thenReturn(true, false);
        when(mockResultSet.getString(1)).thenReturn("Result  (cost=0.00..0.01 rows=1 width=0)");

        SqlExplain result = extension.getExplain(mockStatement, sql);

        assertNotNull(result);
        assertNotNull(result.getOriginalText());
    }

    /**
     * 测试用例：getExplain 对 INSERT 语句生成正确的 EXPLAIN 语句
     */
    @Test
    public void testGetExplain_InsertQuery() throws SQLException {
        String sql = "INSERT INTO users (name) VALUES ('test')";

        when(mockStatement.executeQuery("EXPLAIN " + sql)).thenReturn(mockResultSet);
        when(mockResultSet.getMetaData()).thenReturn(mockMetaData);
        when(mockMetaData.getColumnCount()).thenReturn(1);
        when(mockMetaData.getColumnName(1)).thenReturn("QUERY PLAN");
        when(mockMetaData.getColumnDisplaySize(1)).thenReturn(60);
        when(mockResultSet.next()).thenReturn(true, false);
        when(mockResultSet.getString(1)).thenReturn("Insert on users  (cost=0.00..0.01 rows=1 width=0)");

        SqlExplain result = extension.getExplain(mockStatement, sql);

        assertNotNull(result);
    }

    /**
     * 测试用例：getExplain 对 UPDATE 语句生成正确的 EXPLAIN 语句
     */
    @Test
    public void testGetExplain_UpdateQuery() throws SQLException {
        String sql = "UPDATE users SET name = 'new' WHERE id = 1";

        when(mockStatement.executeQuery("EXPLAIN " + sql)).thenReturn(mockResultSet);
        when(mockResultSet.getMetaData()).thenReturn(mockMetaData);
        when(mockMetaData.getColumnCount()).thenReturn(1);
        when(mockMetaData.getColumnName(1)).thenReturn("QUERY PLAN");
        when(mockMetaData.getColumnDisplaySize(1)).thenReturn(70);
        when(mockResultSet.next()).thenReturn(true, false);
        when(mockResultSet.getString(1)).thenReturn("Update on users  (cost=0.15..8.17 rows=1 width=0)");

        SqlExplain result = extension.getExplain(mockStatement, sql);

        assertNotNull(result);
    }

    /**
     * 测试用例：getExplain 对 DELETE 语句生成正确的 EXPLAIN 语句
     */
    @Test
    public void testGetExplain_DeleteQuery() throws SQLException {
        String sql = "DELETE FROM users WHERE id = 1";

        when(mockStatement.executeQuery("EXPLAIN " + sql)).thenReturn(mockResultSet);
        when(mockResultSet.getMetaData()).thenReturn(mockMetaData);
        when(mockMetaData.getColumnCount()).thenReturn(1);
        when(mockMetaData.getColumnName(1)).thenReturn("QUERY PLAN");
        when(mockMetaData.getColumnDisplaySize(1)).thenReturn(60);
        when(mockResultSet.next()).thenReturn(true, false);
        when(mockResultSet.getString(1)).thenReturn("Delete on users  (cost=0.15..8.17 rows=1 width=0)");

        SqlExplain result = extension.getExplain(mockStatement, sql);

        assertNotNull(result);
    }

    /**
     * 测试用例：getExplain 对 JOIN 查询生成正确的 EXPLAIN 语句
     */
    @Test
    public void testGetExplain_JoinQuery() throws SQLException {
        String sql = "SELECT u.name, o.order_id FROM users u JOIN orders o ON u.id = o.user_id";

        when(mockStatement.executeQuery("EXPLAIN " + sql)).thenReturn(mockResultSet);
        when(mockResultSet.getMetaData()).thenReturn(mockMetaData);
        when(mockMetaData.getColumnCount()).thenReturn(1);
        when(mockMetaData.getColumnName(1)).thenReturn("QUERY PLAN");
        when(mockMetaData.getColumnDisplaySize(1)).thenReturn(100);
        when(mockResultSet.next()).thenReturn(true, true, true, false);
        when(mockResultSet.getString(1)).thenReturn(
                "Hash Join  (cost=11.15..24.30 rows=100 width=12)",
                "  Hash Cond: (o.user_id = u.id)",
                "  ->  Seq Scan on orders o  (cost=0.00..10.80 rows=80 width=8)");

        SqlExplain result = extension.getExplain(mockStatement, sql);

        assertNotNull(result);
        assertTrue(result.getOriginalText().contains("Hash Join"));
    }

    /**
     * 测试用例：getExplain 对子查询生成正确的 EXPLAIN 语句
     */
    @Test
    public void testGetExplain_Subquery() throws SQLException {
        String sql = "SELECT * FROM users WHERE id IN (SELECT user_id FROM orders)";

        when(mockStatement.executeQuery("EXPLAIN " + sql)).thenReturn(mockResultSet);
        when(mockResultSet.getMetaData()).thenReturn(mockMetaData);
        when(mockMetaData.getColumnCount()).thenReturn(1);
        when(mockMetaData.getColumnName(1)).thenReturn("QUERY PLAN");
        when(mockMetaData.getColumnDisplaySize(1)).thenReturn(100);
        when(mockResultSet.next()).thenReturn(true, false);
        when(mockResultSet.getString(1)).thenReturn("Hash Semi Join  (cost=11.15..24.30 rows=100 width=4)");

        SqlExplain result = extension.getExplain(mockStatement, sql);

        assertNotNull(result);
    }

    /**
     * 测试用例：getExplain 处理多列结果
     */
    @Test
    public void testGetExplain_MultipleColumns() throws SQLException {
        String sql = "SELECT * FROM users";

        when(mockStatement.executeQuery("EXPLAIN " + sql)).thenReturn(mockResultSet);
        when(mockResultSet.getMetaData()).thenReturn(mockMetaData);
        when(mockMetaData.getColumnCount()).thenReturn(2);
        when(mockMetaData.getColumnName(1)).thenReturn("QUERY PLAN");
        when(mockMetaData.getColumnName(2)).thenReturn("COST");
        when(mockMetaData.getColumnDisplaySize(1)).thenReturn(100);
        when(mockMetaData.getColumnDisplaySize(2)).thenReturn(10);
        when(mockResultSet.next()).thenReturn(true, false);
        when(mockResultSet.getString(1)).thenReturn("Seq Scan on users");
        when(mockResultSet.getString(2)).thenReturn("1.00");

        SqlExplain result = extension.getExplain(mockStatement, sql);

        assertNotNull(result);
    }

    /**
     * 测试用例：getExplain 处理空结果集
     */
    @Test
    public void testGetExplain_EmptyResult() throws SQLException {
        String sql = "SELECT * FROM empty_table";

        when(mockStatement.executeQuery("EXPLAIN " + sql)).thenReturn(mockResultSet);
        when(mockResultSet.getMetaData()).thenReturn(mockMetaData);
        when(mockMetaData.getColumnCount()).thenReturn(1);
        when(mockMetaData.getColumnName(1)).thenReturn("QUERY PLAN");
        when(mockMetaData.getColumnDisplaySize(1)).thenReturn(50);
        when(mockResultSet.next()).thenReturn(false);

        SqlExplain result = extension.getExplain(mockStatement, sql);

        assertNotNull(result);
        assertNotNull(result.getOriginalText());
    }

    /**
     * 测试用例：getExplain 处理 SQLException 异常
     */
    @Test(expected = OBException.class)
    public void testGetExplain_SqlException_ThrowsOBException() throws SQLException {
        String sql = "INVALID SQL SYNTAX";

        when(mockStatement.executeQuery("EXPLAIN " + sql))
                .thenThrow(new SQLException("syntax error"));

        extension.getExplain(mockStatement, sql);
    }

    /**
     * 测试用例：getExplain 返回的 SqlExplain 属性验证
     */
    @Test
    public void testGetExplain_SqlExplainProperties() throws SQLException {
        String sql = "SELECT 1";

        when(mockStatement.executeQuery("EXPLAIN " + sql)).thenReturn(mockResultSet);
        when(mockResultSet.getMetaData()).thenReturn(mockMetaData);
        when(mockMetaData.getColumnCount()).thenReturn(1);
        when(mockMetaData.getColumnName(1)).thenReturn("QUERY PLAN");
        when(mockMetaData.getColumnDisplaySize(1)).thenReturn(50);
        when(mockResultSet.next()).thenReturn(true, false);
        when(mockResultSet.getString(1)).thenReturn("Result");

        SqlExplain result = extension.getExplain(mockStatement, sql);

        assertNotNull(result.getOriginalText());
        assertFalse(result.getShowFormatInfo());
        // expTree and outline should be null for PostgreSQL
        assertNull(result.getExpTree());
        assertNull(result.getOutline());
    }

    // ==================== EXPLAIN SQL 格式验证测试 ====================

    /**
     * 测试用例：验证 EXPLAIN 前缀格式
     */
    @Test
    public void testExplain_PrefixFormat() throws SQLException {
        // EXPLAIN + space + sql
        String selectSql = "SELECT * FROM t";
        String expectedPrefix = "EXPLAIN ";

        when(mockStatement.executeQuery("EXPLAIN SELECT * FROM t")).thenReturn(mockResultSet);
        when(mockResultSet.getMetaData()).thenReturn(mockMetaData);
        when(mockMetaData.getColumnCount()).thenReturn(1);
        when(mockMetaData.getColumnName(1)).thenReturn("QUERY PLAN");
        when(mockMetaData.getColumnDisplaySize(1)).thenReturn(20);
        when(mockResultSet.next()).thenReturn(false);

        extension.getExplain(mockStatement, selectSql);
        // If no exception, the EXPLAIN prefix was correctly appended
    }

    // ==================== UnsupportedOperationException 测试 ====================

    /**
     * 测试用例：getPhysicalPlanBySqlId 抛出 UnsupportedOperationException
     */
    @Test(expected = UnsupportedOperationException.class)
    public void testGetPhysicalPlanBySqlId_ThrowsUnsupported() throws SQLException {
        extension.getPhysicalPlanBySqlId(mockConnection, "sql-123");
    }

    /**
     * 测试用例：getPhysicalPlanBySql 抛出 UnsupportedOperationException
     */
    @Test(expected = UnsupportedOperationException.class)
    public void testGetPhysicalPlanBySql_ThrowsUnsupported() throws SQLException {
        extension.getPhysicalPlanBySql(mockConnection, "SELECT 1");
    }

    /**
     * 测试用例：getExecutionDetailById 抛出 UnsupportedOperationException
     */
    @Test(expected = UnsupportedOperationException.class)
    public void testGetExecutionDetailById_ThrowsUnsupported() throws SQLException {
        extension.getExecutionDetailById(mockConnection, "exec-123");
    }

    /**
     * 测试用例：getExecutionDetailBySql 抛出 UnsupportedOperationException
     */
    @Test(expected = UnsupportedOperationException.class)
    public void testGetExecutionDetailBySql_ThrowsUnsupported() throws SQLException {
        extension.getExecutionDetailBySql(mockConnection, "SELECT 1");
    }

    /**
     * 测试用例：getQueryProfileByTraceIdAndSessIds 抛出 UnsupportedOperationException
     */
    @Test(expected = UnsupportedOperationException.class)
    public void testGetQueryProfileByTraceIdAndSessIds_ThrowsUnsupported() throws SQLException {
        extension.getQueryProfileByTraceIdAndSessIds(mockConnection, "trace-123", Arrays.asList("sess-1", "sess-2"));
    }

    /**
     * 测试用例：getQueryProfileByTraceIdAndSessIds 空会话列表抛出 UnsupportedOperationException
     */
    @Test(expected = UnsupportedOperationException.class)
    public void testGetQueryProfileByTraceIdAndSessIds_EmptySessionIds_ThrowsUnsupported() throws SQLException {
        extension.getQueryProfileByTraceIdAndSessIds(mockConnection, "trace-123", Collections.emptyList());
    }

    // ==================== 与 MySQL 差异对比测试 ====================

    /**
     * 测试用例：验证 PG 的 EXPLAIN 与 MySQL 类似但语法细节不同
     * <p>
     * MySQL: EXPLAIN SELECT ... (返回表格格式，列名如 id, select_type, table 等) PostgreSQL: EXPLAIN SELECT ...
     * (返回文本格式的执行计划)
     */
    @Test
    public void testExplain_PostgresVsMySQL() throws SQLException {
        String sql = "SELECT * FROM users WHERE id = 1";

        when(mockStatement.executeQuery("EXPLAIN " + sql)).thenReturn(mockResultSet);
        when(mockResultSet.getMetaData()).thenReturn(mockMetaData);
        when(mockMetaData.getColumnCount()).thenReturn(1);
        when(mockMetaData.getColumnName(1)).thenReturn("QUERY PLAN"); // PG 特有的列名
        when(mockMetaData.getColumnDisplaySize(1)).thenReturn(100);
        when(mockResultSet.next()).thenReturn(true, false);
        when(mockResultSet.getString(1)).thenReturn("Index Scan using users_pkey on users");

        SqlExplain result = extension.getExplain(mockStatement, sql);

        // PostgreSQL 返回的列名是 QUERY PLAN，不是 MySQL 的 id, select_type 等列
        assertTrue(result.getOriginalText().contains("QUERY PLAN"));
    }

    // ==================== 与 SQLServer 差异对比测试 ====================

    /**
     * 测试用例：验证 PG 的 EXPLAIN 比 SQLServer 更简单
     * <p>
     * SQLServer: 使用 SET SHOWPLAN_XML ON 或 SET STATISTICS PROFILE ON PostgreSQL: 直接使用 EXPLAIN <sql>
     */
    @Test
    public void testExplain_PostgresSimplerThanSQLServer() throws SQLException {
        String sql = "SELECT 1";

        when(mockStatement.executeQuery("EXPLAIN SELECT 1")).thenReturn(mockResultSet);
        when(mockResultSet.getMetaData()).thenReturn(mockMetaData);
        when(mockMetaData.getColumnCount()).thenReturn(1);
        when(mockMetaData.getColumnName(1)).thenReturn("QUERY PLAN");
        when(mockMetaData.getColumnDisplaySize(1)).thenReturn(20);
        when(mockResultSet.next()).thenReturn(true, false);
        when(mockResultSet.getString(1)).thenReturn("Result");

        SqlExplain result = extension.getExplain(mockStatement, sql);

        // PG 使用简单的 EXPLAIN 语法，不需要 SQLServer 那样的复杂 SET 语句
        assertNotNull(result);
    }
}
