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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.pf4j.Extension;

import com.oceanbase.odc.common.util.tableformat.BorderStyle;
import com.oceanbase.odc.common.util.tableformat.CellStyle;
import com.oceanbase.odc.common.util.tableformat.CellStyle.AbbreviationStyle;
import com.oceanbase.odc.common.util.tableformat.CellStyle.HorizontalAlign;
import com.oceanbase.odc.common.util.tableformat.CellStyle.NullStyle;
import com.oceanbase.odc.common.util.tableformat.Table;
import com.oceanbase.odc.core.shared.constant.ErrorCodes;
import com.oceanbase.odc.core.shared.exception.OBException;
import com.oceanbase.odc.core.shared.model.SqlExecDetail;
import com.oceanbase.odc.plugin.connect.api.SqlDiagnoseExtensionPoint;
import com.oceanbase.odc.plugin.connect.model.diagnose.SqlExplain;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * PostgreSQL SQL 诊断扩展点实现
 * 
 * <p>
 * 实现 PostgreSQL 的 SQL 执行计划获取功能。 PostgreSQL 使用 {@code EXPLAIN <sql>} 语法获取执行计划文本。
 * 
 * <p>
 * 与 MySQL/SQLServer 的差异：
 * <ul>
 * <li>MySQL: 使用 {@code EXPLAIN <sql>} 返回表格格式</li>
 * <li>SQLServer: 使用 {@code SET SHOWPLAN_XML ON} 等复杂语法</li>
 * <li>PostgreSQL: 使用 {@code EXPLAIN <sql>} 返回文本格式的执行计划</li>
 * </ul>
 *
 * @author ODC Team
 * @date 2025-03
 * @since ODC_release_4.3.5
 */
@Slf4j
@Extension
public class PostgresDiagnoseExtensionPoint implements SqlDiagnoseExtensionPoint {

    /**
     * 获取 SQL 执行计划
     * 
     * <p>
     * PostgreSQL 使用 {@code EXPLAIN <sql>} 语法获取执行计划。 该语法不会实际执行 SQL，只返回查询优化器的预估执行计划。
     * 
     * <p>
     * 如果需要获取实际执行的统计信息，可以使用 {@code EXPLAIN ANALYZE <sql>}， 但该语法会执行 SQL，可能不适用于所有场景，因此默认使用
     * {@code EXPLAIN}。
     *
     * @param statement JDBC Statement 对象
     * @param sql 要获取执行计划的 SQL 语句
     * @return SqlExplain 对象，包含执行计划文本
     * @throws SQLException 如果执行 EXPLAIN 失败
     */
    @Override
    public SqlExplain getExplain(Statement statement, @NonNull String sql) throws SQLException {
        String explainSql = "EXPLAIN " + sql;
        SqlExplain sqlExplain = new SqlExplain();
        try {
            ResultSet resultSet = statement.executeQuery(explainSql);
            ResultSetMetaData metaData = resultSet.getMetaData();
            int colCount = metaData.getColumnCount();
            Table table = new Table(colCount, BorderStyle.HORIZONTAL_ONLY);
            CellStyle cs = new CellStyle(HorizontalAlign.LEFT, AbbreviationStyle.DOTS, NullStyle.NULL_TEXT);
            for (int i = 1; i <= colCount; i++) {
                table.setColumnWidth(i - 1, 10, metaData.getColumnDisplaySize(i));
                table.addCell(metaData.getColumnName(i), cs);
            }
            while (resultSet.next()) {
                for (int i = 1; i <= colCount; i++) {
                    table.addCell(resultSet.getString(i), cs);
                }
            }
            sqlExplain.setOriginalText(table.render().toString());
            sqlExplain.setShowFormatInfo(false);
        } catch (Exception e) {
            log.warn("Failed to get explain plan from PostgreSQL", e);
            throw OBException.executeFailed(ErrorCodes.ObGetPlanExplainFailed, e.getMessage());
        }
        return sqlExplain;
    }

    /**
     * 根据 SQL ID 获取物理执行计划
     * 
     * <p>
     * PostgreSQL 不支持通过 SQL ID 获取物理执行计划的方式， 该方法始终抛出 {@link UnsupportedOperationException}。
     *
     * @param connection JDBC Connection 对象
     * @param sqlId SQL 标识符（不支持）
     * @return 不返回，始终抛出异常
     * @throws UnsupportedOperationException PostgreSQL 不支持此功能
     */
    @Override
    public SqlExplain getPhysicalPlanBySqlId(Connection connection, @NonNull String sqlId) throws SQLException {
        throw new UnsupportedOperationException("Not supported for PostgreSQL mode");
    }

    /**
     * 根据 SQL 语句获取物理执行计划
     * 
     * <p>
     * PostgreSQL 不支持获取物理执行计划的接口， 该方法始终抛出 {@link UnsupportedOperationException}。
     *
     * @param connection JDBC Connection 对象
     * @param sql SQL 语句（不支持）
     * @return 不返回，始终抛出异常
     * @throws UnsupportedOperationException PostgreSQL 不支持此功能
     */
    @Override
    public SqlExplain getPhysicalPlanBySql(Connection connection, @NonNull String sql) throws SQLException {
        throw new UnsupportedOperationException("Not supported for PostgreSQL mode");
    }

    /**
     * 根据 ID 获取执行详情
     * 
     * <p>
     * PostgreSQL 不支持根据 ID 获取执行详情的接口， 该方法始终抛出 {@link UnsupportedOperationException}。
     *
     * @param connection JDBC Connection 对象
     * @param id 执行 ID（不支持）
     * @return 不返回，始终抛出异常
     * @throws UnsupportedOperationException PostgreSQL 不支持此功能
     */
    @Override
    public SqlExecDetail getExecutionDetailById(Connection connection, @NonNull String id) throws SQLException {
        throw new UnsupportedOperationException("Not supported for PostgreSQL mode");
    }

    /**
     * 根据 SQL 语句获取执行详情
     * 
     * <p>
     * PostgreSQL 不支持根据 SQL 语句获取执行详情的接口， 该方法始终抛出 {@link UnsupportedOperationException}。
     *
     * @param connection JDBC Connection 对象
     * @param sql SQL 语句（不支持）
     * @return 不返回，始终抛出异常
     * @throws UnsupportedOperationException PostgreSQL 不支持此功能
     */
    @Override
    public SqlExecDetail getExecutionDetailBySql(Connection connection, @NonNull String sql) throws SQLException {
        throw new UnsupportedOperationException("Not supported for PostgreSQL mode");
    }

    /**
     * 根据 Trace ID 和会话 ID 获取查询 Profile
     * 
     * <p>
     * PostgreSQL 不支持通过 Trace ID 获取查询 Profile 的方式， 该方法始终抛出 {@link UnsupportedOperationException}。
     *
     * @param connection JDBC Connection 对象
     * @param traceId Trace 标识符（不支持）
     * @param sessionIds 会话 ID 列表（不支持）
     * @return 不返回，始终抛出异常
     * @throws UnsupportedOperationException PostgreSQL 不支持此功能
     */
    @Override
    public SqlExplain getQueryProfileByTraceIdAndSessIds(Connection connection, @NonNull String traceId,
            @NonNull List<String> sessionIds) throws SQLException {
        throw new UnsupportedOperationException("Not supported for PostgreSQL mode");
    }
}
