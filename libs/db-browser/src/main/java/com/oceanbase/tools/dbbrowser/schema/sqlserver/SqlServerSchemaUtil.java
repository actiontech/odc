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
package com.oceanbase.tools.dbbrowser.schema.sqlserver;

import org.springframework.jdbc.core.JdbcOperations;

import com.oceanbase.tools.dbbrowser.util.StringUtils;

/**
 * SQL Server Schema 工具类 用于解析 SQL Server 的 schemaName，支持 database.schema 格式
 *
 * @author yizhou.xw
 * @date 2024/12
 * @since ODC_release_4.3.4
 */
public class SqlServerSchemaUtil {

    /**
     * 解析 schemaName，支持两种格式： 1. "database.schema" - 数据库名和 schema 名（如 "wenshu_test.dbo"） 2. "schema" - 只有
     * schema 名，使用当前数据库（如 "dbo"）
     * 
     * 这是 SQL Server 专用的解析方法，用于处理默认将 database.schema 作为 schema 传入的场景。
     * 
     * 注意：当只传入 schema 名时（不包含 "."），返回 [null, schemaName] 表示使用当前数据库。
     *
     * @param schemaName 可能是 database.schema 格式或 schema 名
     * @return [databaseName, schemaName] 数组，如果 schemaName 为空则返回 [null, "dbo"]
     */
    public static String[] parseDatabaseAndSchema(String schemaName) {
        return parseDatabaseAndSchema(schemaName, null);
    }

    /**
     * 解析 schemaName，支持两种格式，并在 schemaName 为空时尝试获取当前数据库 1. "database.schema" - 数据库名和 schema 名（如
     * "wenshu_test.dbo"） 2. "schema" - 只有 schema 名，使用当前数据库（如 "dbo"）
     * 
     * 当 schemaName 为空时，会尝试通过 JdbcOperations 获取当前数据库名。 如果无法获取当前数据库，会抛出 IllegalArgumentException。
     * 
     * 注意：当只传入 schema 名时（不包含 "."），返回 [null, schemaName] 表示使用当前数据库。 调用者需要处理 databaseName 为 null 的情况，通过
     * JdbcOperations 获取当前数据库名。
     *
     * @param schemaName 可能是 database.schema 格式或 schema 名，可能为 null
     * @param jdbcOperations JdbcOperations 用于获取当前数据库名（当 schemaName 为空时）
     * @return [databaseName, schemaName] 数组，databaseName 可能为 null（表示使用当前数据库）
     * @throws IllegalArgumentException 如果 schemaName 为空且无法获取当前数据库
     */
    public static String[] parseDatabaseAndSchema(String schemaName, JdbcOperations jdbcOperations) {
        if (StringUtils.isBlank(schemaName)) {
            // 如果schemaName为空，尝试获取当前数据库名
            if (jdbcOperations != null) {
                try {
                    String currentDb = jdbcOperations.queryForObject("SELECT DB_NAME()", String.class);
                    if (StringUtils.isNotBlank(currentDb)) {
                        return new String[] {currentDb, "dbo"};
                    }
                } catch (Exception e) {
                    // 忽略异常
                }
            }
            return new String[] {null, "dbo"};
        }

        if (schemaName.contains(".")) {
            // 包含 "."，解析为 database.schema 格式
            String[] parts = schemaName.split("\\.", 2);
            if (parts.length == 2 && StringUtils.isNotBlank(parts[0]) && StringUtils.isNotBlank(parts[1])) {
                return new String[] {parts[0], parts[1]};
            }
            // 如果格式不正确，假设是 schema 名，使用当前数据库
            return new String[] {null, schemaName};
        } else {
            // 不包含 "."，假设是 schema 名，使用当前数据库
            return new String[] {null, schemaName};
        }
    }

    /**
     * 合并数据库名和 schema 名为全称（database.schema）
     * 
     * @param databaseName 数据库名
     * @param schemaName schema 名
     * @return database.schema 格式的全称
     */
    public static String buildFullSchemaName(String databaseName, String schemaName) {
        if (StringUtils.isBlank(databaseName)) {
            return schemaName;
        }
        return databaseName + "." + (StringUtils.isBlank(schemaName) ? "dbo" : schemaName);
    }

    /**
     * 获取当前数据库名，如果 databaseName 为 null
     * 
     * @param databaseName 数据库名，可能为 null
     * @param jdbcOperations JdbcOperations 用于获取当前数据库名
     * @return 数据库名，如果 databaseName 不为 null 则返回它，否则返回当前数据库名
     */
    public static String getDatabaseName(String databaseName, JdbcOperations jdbcOperations) {
        if (databaseName != null) {
            return databaseName;
        }
        if (jdbcOperations != null) {
            try {
                String currentDb = jdbcOperations.queryForObject("SELECT DB_NAME()", String.class);
                if (StringUtils.isNotBlank(currentDb)) {
                    return currentDb;
                }
            } catch (Exception e) {
                // 忽略异常
            }
        }
        throw new IllegalArgumentException("Cannot get current database name");
    }

}
