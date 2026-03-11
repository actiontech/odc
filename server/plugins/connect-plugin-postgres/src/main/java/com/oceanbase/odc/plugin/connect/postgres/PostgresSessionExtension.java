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
import java.sql.SQLException;
import java.util.Objects;

import org.pf4j.Extension;

import com.oceanbase.odc.common.util.JdbcOperationsUtil;
import com.oceanbase.odc.plugin.connect.model.DBClientInfo;
import com.oceanbase.odc.plugin.connect.obmysql.OBMySQLSessionExtension;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * PostgreSQL 会话扩展实现
 * <p>
 * 继承 OBMySQLSessionExtension，覆写 PostgreSQL 特有的会话管理方法。 PostgreSQL 与 MySQL 在会话管理方面有较大差异：
 * <ul>
 * <li>Schema 切换：PG 使用 SET search_path TO，而非 USE 或 SET SCHEMA</li>
 * <li>连接 ID：PG 使用 pg_backend_pid() 函数获取</li>
 * <li>终止查询/会话：PG 使用 pg_cancel_backend/pg_terminate_backend 函数</li>
 * <li>变量获取：PG 使用 current_setting() 函数</li>
 * </ul>
 *
 * @author ODC Team
 * @date 2025-03
 * @since ODC_release_4.3.5
 */
@Slf4j
@Extension
public class PostgresSessionExtension extends OBMySQLSessionExtension {

    /**
     * 切换当前 Schema
     * <p>
     * PostgreSQL 使用 search_path 来控制 schema 搜索路径。 执行 SET search_path TO <schemaName> 切换当前 schema。
     *
     * @param connection 数据库连接
     * @param schemaName 目标 Schema 名称
     * @throws SQLException 执行 SQL 时发生异常
     */
    @Override
    public void switchSchema(Connection connection, String schemaName) throws SQLException {
        String currentSchema = getCurrentSchema(connection);
        if (Objects.equals(currentSchema, schemaName)) {
            return;
        }
        // PostgreSQL: 使用 SET search_path TO 切换 schema
        // 注意：如果 schema 名称包含特殊字符或大小写敏感，需要用双引号包裹
        String escapedSchema = escapeIdentifier(schemaName);
        String sql = "SET search_path TO " + escapedSchema;
        JdbcOperationsUtil.getJdbcOperations(connection).execute(sql);
    }

    /**
     * 获取当前 Schema 名称
     * <p>
     * 执行 SELECT current_schema() 获取当前 schema。
     *
     * @param connection 数据库连接
     * @return 当前 Schema 名称，如果获取失败返回 null
     */
    @Override
    public String getCurrentSchema(Connection connection) {
        String querySql = "SELECT current_schema()";
        try {
            return JdbcOperationsUtil.getJdbcOperations(connection).queryForObject(querySql, String.class);
        } catch (Exception e) {
            log.warn("Failed to get current schema from PostgreSQL, message={}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取当前数据库名称
     * <p>
     * 执行 SELECT current_database() 获取当前连接的数据库名称。 PostgreSQL 中，连接建立后即绑定到特定数据库，无法像 MySQL 那样通过 USE 切换数据库。
     *
     * @param connection 数据库连接
     * @return 当前数据库名称，如果获取失败返回 null
     */
    public String getCurrentDatabase(Connection connection) {
        String querySql = "SELECT current_database()";
        try {
            return JdbcOperationsUtil.getJdbcOperations(connection).queryForObject(querySql, String.class);
        } catch (Exception e) {
            log.warn("Failed to get current database from PostgreSQL, message={}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取当前连接的唯一标识
     * <p>
     * PostgreSQL 使用 pg_backend_pid() 函数获取当前连接的后端进程 ID， 这是终止查询和会话时所需的关键标识。
     *
     * @param connection 数据库连接
     * @return 连接 ID（后端进程 ID），如果获取失败返回空字符串
     */
    @Override
    public String getConnectionId(Connection connection) {
        String querySql = "SELECT pg_backend_pid()";
        try {
            Object result = JdbcOperationsUtil.getJdbcOperations(connection).queryForObject(querySql, Object.class);
            return result == null ? "" : result.toString();
        } catch (Exception e) {
            log.warn("Failed to get connection ID from PostgreSQL using pg_backend_pid(), message={}", e.getMessage());
            return "";
        }
    }

    /**
     * 生成终止指定查询的 SQL 语句
     * <p>
     * PostgreSQL 使用 pg_cancel_backend(pid) 函数取消正在执行的查询， 但不会终止会话本身。会话仍然保持连接状态，可以执行新的查询。
     *
     * @param connectionId 连接 ID（后端进程 ID）
     * @return 终止查询的 SQL 语句
     */
    @Override
    public String getKillQuerySql(@NonNull String connectionId) {
        // PostgreSQL: 使用 pg_cancel_backend 取消查询但不终止会话
        return "SELECT pg_cancel_backend(" + connectionId + ")";
    }

    /**
     * 生成终止指定会话的 SQL 语句
     * <p>
     * PostgreSQL 使用 pg_terminate_backend(pid) 函数终止会话， 这会断开客户端连接并释放相关资源。
     *
     * @param connectionId 连接 ID（后端进程 ID）
     * @return 终止会话的 SQL 语句
     */
    @Override
    public String getKillSessionSql(@NonNull String connectionId) {
        // PostgreSQL: 使用 pg_terminate_backend 终止会话
        return "SELECT pg_terminate_backend(" + connectionId + ")";
    }

    /**
     * 获取指定会话变量的值
     * <p>
     * PostgreSQL 使用 current_setting('parameter_name') 函数获取配置参数。 参数名称可以是任何在 postgresql.conf 中定义的设置项。
     *
     * @param connection 数据库连接
     * @param variableName 变量名称
     * @return 变量值，如果获取失败返回 null
     */
    @Override
    public String getVariable(Connection connection, String variableName) {
        String querySql = "SELECT current_setting('" + variableName + "')";
        try {
            return JdbcOperationsUtil.getJdbcOperations(connection).queryForObject(querySql, String.class);
        } catch (Exception e) {
            log.warn("Failed to get variable {} from PostgreSQL, message={}", variableName, e.getMessage());
            return null;
        }
    }

    /**
     * 设置客户端信息
     * <p>
     * PostgreSQL 可通过设置 application_name 来标识客户端应用， 但更详细的客户端信息设置需要额外扩展。当前实现返回 false 表示不支持。
     *
     * @param connection 数据库连接
     * @param clientInfo 客户端信息
     * @return 是否设置成功
     */
    @Override
    public boolean setClientInfo(Connection connection, DBClientInfo clientInfo) {
        // PostgreSQL 不支持类似 MySQL/OB 的 dbms_application_info
        return false;
    }

    /**
     * 转义标识符
     * <p>
     * PostgreSQL 使用双引号包裹大小写敏感或包含特殊字符的标识符。 标识符内的双引号需要转义为双写双引号。
     *
     * @param identifier 标识符
     * @return 转义后的标识符
     */
    private String escapeIdentifier(String identifier) {
        if (identifier == null) {
            return null;
        }
        // 双引号转义为双写双引号
        String escaped = identifier.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

}
