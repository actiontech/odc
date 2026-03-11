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
import static org.mockito.Mockito.mock;

import java.sql.SQLException;
import java.sql.Statement;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import com.oceanbase.odc.core.sql.execute.model.SqlExecTime;

/**
 * {@link PostgresTraceExtension} 单元测试
 *
 * <p>
 * 测试覆盖：
 * <ul>
 * <li>getExecuteDetail() 返回空 SqlExecTime 对象</li>
 * <li>返回对象的各属性为 null 或默认值</li>
 * <li>不抛出异常的占位实现验证</li>
 * </ul>
 *
 * @author ODC Team
 * @date 2025-03
 * @since ODC_release_4.3.5
 */
public class PostgresTraceExtensionTest {

    private PostgresTraceExtension extension;

    @Mock
    private Statement mockStatement;

    @Before
    public void setUp() {
        extension = new PostgresTraceExtension();
        mockStatement = mock(Statement.class);
    }

    // ==================== getExecuteDetail 基础测试 ====================

    /**
     * 测试用例：getExecuteDetail 返回非 null 对象
     */
    @Test
    public void testGetExecuteDetail_ReturnsNonNull() throws SQLException {
        SqlExecTime result = extension.getExecuteDetail(mockStatement, "14.0");

        assertNotNull(result);
    }

    /**
     * 测试用例：getExecuteDetail 返回的对象属性为 null
     */
    @Test
    public void testGetExecuteDetail_ReturnsEmptyObject() throws SQLException {
        SqlExecTime result = extension.getExecuteDetail(mockStatement, "14.0");

        assertNull(result.getTraceId());
        assertNull(result.getElapsedMicroseconds());
        assertNull(result.getExecuteMicroseconds());
        assertNull(result.getLastPacketSendTimestamp());
        assertNull(result.getLastPacketResponseTimestamp());
        assertNull(result.getTraceSpan());
    }

    /**
     * 测试用例：getExecuteDetail 返回的对象 withFullLinkTrace 为 false
     */
    @Test
    public void testGetExecuteDetail_WithFullLinkTraceIsFalse() throws SQLException {
        SqlExecTime result = extension.getExecuteDetail(mockStatement, "14.0");

        assertFalse(result.isWithFullLinkTrace());
    }

    // ==================== 不同版本号测试 ====================

    /**
     * 测试用例：不同 PostgreSQL 版本都返回相同的空对象
     */
    @Test
    public void testGetExecuteDetail_DifferentVersions_ReturnsSameEmptyObject() throws SQLException {
        SqlExecTime result11 = extension.getExecuteDetail(mockStatement, "11.0");
        SqlExecTime result12 = extension.getExecuteDetail(mockStatement, "12.0");
        SqlExecTime result13 = extension.getExecuteDetail(mockStatement, "13.0");
        SqlExecTime result14 = extension.getExecuteDetail(mockStatement, "14.0");
        SqlExecTime result15 = extension.getExecuteDetail(mockStatement, "15.0");

        // All should return empty objects
        assertNotNull(result11);
        assertNotNull(result12);
        assertNotNull(result13);
        assertNotNull(result14);
        assertNotNull(result15);

        assertNull(result11.getTraceId());
        assertNull(result12.getTraceId());
        assertNull(result13.getTraceId());
        assertNull(result14.getTraceId());
        assertNull(result15.getTraceId());
    }

    /**
     * 测试用例：null 版本号不抛异常
     */
    @Test
    public void testGetExecuteDetail_NullVersion_ReturnsEmptyObject() throws SQLException {
        SqlExecTime result = extension.getExecuteDetail(mockStatement, null);

        assertNotNull(result);
        assertNull(result.getTraceId());
    }

    /**
     * 测试用例：空字符串版本号不抛异常
     */
    @Test
    public void testGetExecuteDetail_EmptyVersion_ReturnsEmptyObject() throws SQLException {
        SqlExecTime result = extension.getExecuteDetail(mockStatement, "");

        assertNotNull(result);
        assertNull(result.getTraceId());
    }

    // ==================== 占位实现验证测试 ====================

    /**
     * 测试用例：验证占位实现不抛出异常
     */
    @Test
    public void testGetExecuteDetail_DoesNotThrowException() throws SQLException {
        // 调用多次确保没有异常抛出
        for (int i = 0; i < 5; i++) {
            SqlExecTime result = extension.getExecuteDetail(mockStatement, "14.0");
            assertNotNull(result);
        }
    }

    /**
     * 测试用例：验证每次调用返回新的对象实例
     */
    @Test
    public void testGetExecuteDetail_ReturnsNewInstance() throws SQLException {
        SqlExecTime result1 = extension.getExecuteDetail(mockStatement, "14.0");
        SqlExecTime result2 = extension.getExecuteDetail(mockStatement, "14.0");

        // 不是同一个实例
        assertTrue(result1 != result2);

        // 但内容都为空
        assertNull(result1.getTraceId());
        assertNull(result2.getTraceId());
    }

    // ==================== 与 SQLServer 对比测试 ====================

    /**
     * 测试用例：验证 PG 与 SQLServer Trace 实现行为一致
     * <p>
     * 两者都返回空的 SqlExecTime 对象作为占位
     */
    @Test
    public void testGetExecuteDetail_ConsistentWithSQLServer() throws SQLException {
        SqlExecTime result = extension.getExecuteDetail(mockStatement, "14.0");

        // 与 SQLServer 一致：返回空对象
        assertNull(result.getTraceId());
        assertNull(result.getElapsedMicroseconds());
        assertNull(result.getExecuteMicroseconds());
    }

    // ==================== 与 OceanBase 差异对比测试 ====================

    /**
     * 测试用例：验证 PG 与 OceanBase Trace 实现不同
     * <p>
     * OceanBase 有内置 trace 机制，可以获取详细执行时间 PostgreSQL 没有内置 trace，返回空对象占位
     */
    @Test
    public void testGetExecuteDetail_DifferentFromOceanBase() throws SQLException {
        SqlExecTime result = extension.getExecuteDetail(mockStatement, "14.0");

        // PG 没有像 OB 那样返回详细的 trace 信息
        assertNull(result.getTraceId());
        assertNull(result.getElapsedMicroseconds());
        assertFalse(result.isWithFullLinkTrace());
    }

    // ==================== Statement 参数忽略测试 ====================

    /**
     * 测试用例：验证 Statement 参数被忽略
     */
    @Test
    public void testGetExecuteDetail_IgnoresStatement() throws SQLException {
        // 传入 null statement 也应该返回空对象
        SqlExecTime result = extension.getExecuteDetail(null, "14.0");

        assertNotNull(result);
        assertNull(result.getTraceId());
    }

    // ==================== SqlExecTime 属性完整性测试 ====================

    /**
     * 测试用例：验证返回的 SqlExecTime 所有属性都被正确初始化
     */
    @Test
    public void testGetExecuteDetail_AllPropertiesDefault() throws SQLException {
        SqlExecTime result = extension.getExecuteDetail(mockStatement, "14.0");

        // 验证所有属性都是默认值
        assertNull(result.getTraceId());
        assertNull(result.getElapsedMicroseconds());
        assertNull(result.getExecuteMicroseconds());
        assertNull(result.getLastPacketSendTimestamp());
        assertNull(result.getLastPacketResponseTimestamp());
        assertNull(result.getTraceSpan());
        assertFalse(result.isWithFullLinkTrace());
        assertNull(result.getTraceEmptyReason());
    }

    // ==================== 多次调用稳定性测试 ====================

    /**
     * 测试用例：多次调用的一致性测试
     */
    @Test
    public void testGetExecuteDetail_MultipleCallsConsistent() throws SQLException {
        for (int i = 0; i < 10; i++) {
            SqlExecTime result = extension.getExecuteDetail(mockStatement, "14.0");
            assertNotNull("Result should not be null on call " + i, result);
            assertNull("TraceId should be null on call " + i, result.getTraceId());
        }
    }

    // ==================== 边界条件测试 ====================

    /**
     * 测试用例：验证不同版本格式处理
     */
    @Test
    public void testGetExecuteDetail_VersionFormats() throws SQLException {
        // 标准版本格式
        SqlExecTime result1 = extension.getExecuteDetail(mockStatement, "14.0.0");
        assertNotNull(result1);

        // 带后缀的版本格式
        SqlExecTime result2 = extension.getExecuteDetail(mockStatement, "14.0.0-enterprise");
        assertNotNull(result2);

        // 简化版本格式
        SqlExecTime result3 = extension.getExecuteDetail(mockStatement, "14");
        assertNotNull(result3);

        // 特殊版本格式
        SqlExecTime result4 = extension.getExecuteDetail(mockStatement, "PostgreSQL 14.0");
        assertNotNull(result4);
    }
}
