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
package com.oceanbase.odc.plugin.connect.sqlserver;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
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

@Extension
public class SqlServerConnectionExtension extends OBMySQLConnectionExtension {

    @Override
    public String generateJdbcUrl(@NonNull JdbcUrlProperty properties) {
        String host = properties.getHost();
        Validate.notEmpty(host, "host can not be null");
        Integer port = properties.getPort();
        Validate.notNull(port, "port can not be null");
        String catalogName = properties.getCatalogName();
        String schema = properties.getDefaultSchema();
        StringBuilder jdbcUrl = new StringBuilder();
        jdbcUrl.append("jdbc:sqlserver://").append(host).append(":").append(port);
        if (StringUtils.isNotBlank(catalogName)) {
            jdbcUrl.append(";databaseName=").append(catalogName);
        }
        if (StringUtils.isNotBlank(schema)) {
            jdbcUrl.append(";currentSchema=").append(schema);
        }

        // 添加默认的 JDBC 参数
        String parameters = getJdbcUrlParameters(properties.getJdbcParameters());
        if (StringUtils.isNotBlank(parameters)) {
            jdbcUrl.append(";").append(parameters.replace("&", ";"));
        }

        return jdbcUrl.toString();
    }

    @Override
    protected Map<String, String> appendDefaultJdbcUrlParameters(Map<String, String> jdbcUrlParams) {
        if (jdbcUrlParams == null) {
            jdbcUrlParams = new java.util.HashMap<>();
        }
        // 设置 trustServerCertificate=true 以跳过 SSL 证书验证
        // 注意：生产环境应该配置正确的 SSL 证书，而不是跳过验证
        if (!jdbcUrlParams.containsKey("trustServerCertificate")) {
            jdbcUrlParams.put("trustServerCertificate", "true");
        }
        // 设置 encrypt=true 启用加密（即使信任服务器证书）
        if (!jdbcUrlParams.containsKey("encrypt")) {
            jdbcUrlParams.put("encrypt", "true");
        }
        return jdbcUrlParams;
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

    private TestResult executeTest(Connection connection, int queryTimeout,
            List<ConnectionInitializer> initializers) throws SQLException {
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
    }

    @Override
    public String getDriverClassName() {
        return OdcConstants.SQL_SERVER_DRIVER_CLASS_NAME;
    }

    @Override
    public List<ConnectionInitializer> getConnectionInitializers() {
        return Collections.emptyList();
    }

    @Override
    public JdbcUrlParser getConnectionInfo(@NonNull String jdbcUrl, String userName) throws SQLException {
        return new SqlServerJdbcUrlParser(jdbcUrl, userName);
    }

}
