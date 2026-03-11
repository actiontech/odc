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
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.Validate;
import org.pf4j.Extension;

import com.oceanbase.odc.common.util.ExceptionUtils;
import com.oceanbase.odc.common.util.StringUtils;
import com.oceanbase.odc.core.datasource.ConnectionInitializer;
import com.oceanbase.odc.core.shared.constant.OdcConstants;
import com.oceanbase.odc.plugin.connect.api.JdbcUrlParser;
import com.oceanbase.odc.plugin.connect.api.TestResult;
import com.oceanbase.odc.plugin.connect.model.JdbcUrlProperty;
import com.oceanbase.odc.plugin.connect.obmysql.OBMySQLConnectionExtension;

import lombok.NonNull;

/**
 * PostgreSQL 连接扩展实现
 *
 * <p>
 * 继承自 {@link OBMySQLConnectionExtension}，覆写 PostgreSQL 特有的连接逻辑。
 *
 * <p>
 * JDBC URL 格式：
 *
 * <pre>
 * jdbc:postgresql://host:port/catalog?currentSchema=schema&param=value
 * </pre>
 *
 * @author ODC Team
 * @date 2023
 * @since ODC_release_4.2.0
 */
@Extension
public class PostgresConnectionExtension extends OBMySQLConnectionExtension {

    /**
     * PostgreSQL 默认应用名称
     */
    private static final String DEFAULT_APP_NAME = "ODC";

    @Override
    public String generateJdbcUrl(@NonNull JdbcUrlProperty properties) {
        String host = properties.getHost();
        Validate.notEmpty(host, "host can not be null");
        Integer port = properties.getPort();
        Validate.notNull(port, "port can not be null");
        String catalogName = properties.getCatalogName();
        Validate.notEmpty(catalogName, "catalog name can not be null");
        String schema = properties.getDefaultSchema();

        StringBuilder jdbcUrl = new StringBuilder();
        jdbcUrl.append("jdbc:postgresql://").append(host).append(":").append(port).append("/").append(catalogName);

        // 构建 URL 参数，包括 currentSchema 和其他 JDBC 参数
        Map<String, String> jdbcParams = new HashMap<>();
        if (StringUtils.isNotBlank(schema)) {
            jdbcParams.put("currentSchema", schema);
        }

        // 追加默认参数
        Map<String, String> defaultParams = appendDefaultJdbcUrlParameters(properties.getJdbcParameters());
        if (defaultParams != null) {
            defaultParams.forEach((key, value) -> {
                if (!jdbcParams.containsKey(key)) {
                    jdbcParams.put(key, value);
                }
            });
        }

        // 添加用户自定义参数
        if (properties.getJdbcParameters() != null) {
            properties.getJdbcParameters().forEach(jdbcParams::putIfAbsent);
        }

        // 生成参数字符串
        if (!jdbcParams.isEmpty()) {
            String paramString = jdbcParams.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .reduce((a, b) -> a + "&" + b)
                    .orElse("");
            if (StringUtils.isNotBlank(paramString)) {
                jdbcUrl.append("?").append(paramString);
            }
        }

        return jdbcUrl.toString();
    }

    @Override
    public TestResult test(String jdbcUrl, Properties properties,
            int queryTimeout, List<ConnectionInitializer> initializers) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, properties)) {
            try (Statement statement = connection.createStatement()) {
                if (queryTimeout >= 0) {
                    statement.setQueryTimeout(queryTimeout);
                }
                if (CollectionUtils.isNotEmpty(initializers)) {
                    try {
                        for (ConnectionInitializer initializer : initializers) {
                            initializer.init(connection);
                        }
                    } catch (Exception e) {
                        return TestResult.initScriptFailed(e);
                    }
                }
                return TestResult.success();
            }
        } catch (Exception e) {
            Throwable rootCause = ExceptionUtils.getRootCause(e);
            return TestResult.unknownError(rootCause);
        }
    }

    @Override
    public String getDriverClassName() {
        return OdcConstants.POSTGRES_DRIVER_CLASS_NAME;
    }

    @Override
    public List<ConnectionInitializer> getConnectionInitializers() {
        return Collections.emptyList();
    }

    /**
     * 获取 JDBC URL 解析后的连接信息
     *
     * <p>
     * 使用 {@link PostgresJdbcUrlParser} 解析 PostgreSQL JDBC URL，提取 host、port、schema 等信息。
     *
     * @param jdbcUrl JDBC URL 字符串
     * @param userName 用户名（暂未使用）
     * @return JDBC URL 解析器实例
     * @throws SQLException 如果 URL 格式无效
     */
    @Override
    public JdbcUrlParser getConnectionInfo(@NonNull String jdbcUrl, String userName) throws SQLException {
        return new PostgresJdbcUrlParser(jdbcUrl);
    }

    /**
     * 追加 PostgreSQL 默认 JDBC URL 参数
     *
     * <p>
     * 添加默认参数：
     * <ul>
     * <li>ApplicationName=ODC - 标识应用程序名称，便于在 PostgreSQL 中追踪连接来源</li>
     * </ul>
     *
     * @param jdbcUrlParams 用户自定义的 JDBC URL 参数
     * @return 包含默认参数的参数 Map
     */
    @Override
    protected Map<String, String> appendDefaultJdbcUrlParameters(Map<String, String> jdbcUrlParams) {
        if (jdbcUrlParams == null) {
            jdbcUrlParams = new HashMap<>();
        }
        // 设置 ApplicationName 参数，用于标识连接来源
        // 该参数会在 pg_stat_activity.application_name 中显示
        if (!jdbcUrlParams.containsKey("ApplicationName")) {
            jdbcUrlParams.put("ApplicationName", DEFAULT_APP_NAME);
        }
        return jdbcUrlParams;
    }

}
