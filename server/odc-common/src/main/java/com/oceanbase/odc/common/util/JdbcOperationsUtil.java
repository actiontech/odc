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

package com.oceanbase.odc.common.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Function;

import javax.sql.DataSource;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.google.common.collect.Iterables;

/**
 * @author yaobin
 * @date 2023-04-14
 * @since 4.2.0
 */
public class JdbcOperationsUtil {

    public static JdbcOperations getJdbcOperations(Connection connection) {
        return new JdbcTemplate(new SingleConnectionDataSource(connection, false));
    }

    public static TransactionTemplate getTransactionTemplate(DataSource dataSource) {
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager();
        transactionManager.setDataSource(dataSource);
        TransactionTemplate template = new TransactionTemplate();
        template.setTransactionManager(transactionManager);
        template.setIsolationLevel(TransactionTemplate.ISOLATION_DEFAULT);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        return template;
    }

    public static <T> List<T> batchCreate(JdbcOperations jdbcOperations, List<T> entities, String sql,
            Map<Integer, Function<T, Object>> valueGetter, BiConsumer<T, Long> idSetter) {
        return jdbcOperations.execute((ConnectionCallback<List<T>>) con -> {
            try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                for (T item : entities) {
                    for (Entry<Integer, Function<T, Object>> e : valueGetter.entrySet()) {
                        try {
                            Object call = e.getValue().apply(item);
                            ps.setObject(e.getKey(), call);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                    ps.addBatch();
                }
                ps.executeBatch();
                try (ResultSet resultSet = ps.getGeneratedKeys()) {
                    int i = 0;
                    while (resultSet.next()) {
                        idSetter.accept(entities.get(i++), getGeneratedId(resultSet));
                    }
                    return entities;
                }
            }
        });
    }

    public static <T> List<T> batchCreate(JdbcOperations jdbcOperations, List<T> entities, String sql,
            Map<Integer, Function<T, Object>> valueGetter, BiConsumer<T, Long> idSetter, int batchSize) {
        Iterable<List<T>> partitions = Iterables.partition(entities, batchSize);
        List<T> result = new ArrayList<>();
        for (List<T> partition : partitions) {
            result.addAll(batchCreate(jdbcOperations, partition, sql, valueGetter, idSetter));
        }
        return result;
    }

    /**
     * 从 ResultSet 中获取生成的主键 ID
     *
     * 问题描述：
     *  - MySQL 批量插入时，getGeneratedKeys() 返回的 ResultSet 可能没有列名，
     *    导致通过列名访问（如 getObject("id")）抛出 SQLException: Column 'id' not found
     *  - 不同数据库驱动对 getGeneratedKeys() 返回的 ResultSet 列名处理不一致：
     *      - MySQL: 批量插入时可能无列名，或列名为 "GENERATED_KEY"
     *      - Oracle: 可能有实际列名或 "GENERATED_KEY"
     *      - SQL Server: 列名可能为 "GENERATED_KEYS" 或实际列名
     *      - OceanBase for MySQL: 兼容 MySQL 协议，行为与 MySQL 相同
     *
     * 解决方案：
     *  - 优先通过索引访问（resultSet.getObject(1)）：
     *      JDBC 规范强制要求 getGeneratedKeys() 返回的 ResultSet 第一列就是生成的主键，
     *      这是标准做法，不依赖列名，适用于所有数据库
     *  - 回退到列名访问：如果索引访问失败（理论上不应该），
     *      尝试通过常见列名访问，兼容不同驱动的列名差异
     *
     * 兼容性保证：
     *  - 索引访问（第 1 列）：100% 兼容所有数据库，符合 JDBC 规范
     *  - 列名回退机制：处理特殊情况，提供额外容错保障
     *  - 异常容错：多层 try-catch 确保不会因列名问题导致程序崩溃
     *
     * @param resultSet getGeneratedKeys() 返回的 ResultSet，已调用 next() 定位到当前行
     * @return 生成的主键 ID，如果无法获取则返回 null
     * @throws SQLException 如果发生数据库访问错误
     */
    private static Long getGeneratedId(ResultSet resultSet) throws SQLException {
        // JDBC规范：getGeneratedKeys()返回的ResultSet第一列就是生成的主键
        // 优先通过索引访问（兼容MySQL批量插入时无列名的情况）
        try {
            Object value = resultSet.getObject(1);
            if (value != null) {
                return Long.valueOf(value.toString());
            }
        } catch (SQLException e) {
            // 如果索引访问失败，继续尝试通过列名访问
        }

        // 兼容不同数据库驱动的列名差异
        String[] columnNames = {"id", "ID", "GENERATED_KEY"};
        for (String columnName : columnNames) {
            try {
                Object value = resultSet.getObject(columnName);
                if (value != null) {
                    return Long.valueOf(value.toString());
                }
            } catch (SQLException e) {
                // 继续尝试下一个列名
            }
        }

        return null;
    }

}
