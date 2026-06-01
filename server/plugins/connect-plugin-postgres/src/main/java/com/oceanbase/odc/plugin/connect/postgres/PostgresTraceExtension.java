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

import java.sql.SQLException;
import java.sql.Statement;

import org.pf4j.Extension;

import com.oceanbase.odc.core.sql.execute.model.SqlExecTime;
import com.oceanbase.odc.plugin.connect.api.TraceExtensionPoint;

import lombok.extern.slf4j.Slf4j;

/**
 * PostgreSQL Trace 扩展点实现
 * 
 * <p>
 * PostgreSQL 不提供像 OceanBase 那样的内置 trace 机制来获取详细的执行时间信息。 该实现返回空的 {@link SqlExecTime} 对象作为占位实现，与
 * SQLServer 的实现保持一致。
 * 
 * <p>
 * 与其他数据库的差异：
 * <ul>
 * <li>OceanBase: 支持 trace 机制，可以获取详细的执行时间和链路信息</li>
 * <li>SQLServer: 不支持内置 trace，返回空对象占位</li>
 * <li>PostgreSQL: 不支持内置 trace，返回空对象占位（与 SQLServer 一致）</li>
 * </ul>
 * 
 * <p>
 * 未来如果需要获取 PostgreSQL 的执行时间信息，可以考虑：
 * <ul>
 * <li>使用 {@code EXPLAIN ANALYZE} 获取实际执行时间（但会执行 SQL）</li>
 * <li>使用 {@code pg_stat_statements} 扩展获取历史查询统计信息</li>
 * </ul>
 *
 * @author ODC Team
 * @date 2025-03
 * @since ODC_release_4.3.5
 */
@Slf4j
@Extension
public class PostgresTraceExtension implements TraceExtensionPoint {

    /**
     * 获取 SQL 执行详情
     * 
     * <p>
     * PostgreSQL 不提供内置的 trace 机制来获取详细的执行时间信息， 该方法返回空的 {@link SqlExecTime} 对象作为占位实现。
     * 
     * <p>
     * 实现与 SQLServer 保持一致，都返回空对象占位。
     *
     * @param statement JDBC Statement 对象
     * @param version PostgreSQL 版本号
     * @return 空的 SqlExecTime 对象
     * @throws SQLException 不会抛出，仅接口要求
     */
    @Override
    public SqlExecTime getExecuteDetail(Statement statement, String version) throws SQLException {
        SqlExecTime sqlExecTime = new SqlExecTime();
        // PostgreSQL does not provide built-in trace mechanism like OceanBase
        // to get detailed execution time information.
        // Return empty SqlExecTime object as placeholder (consistent with SQLServer implementation)
        //
        // Future options for getting execution time info:
        // 1. Use EXPLAIN ANALYZE to get actual execution time (but it executes the SQL)
        // 2. Use pg_stat_statements extension to get historical query statistics
        return sqlExecTime;
    }
}
