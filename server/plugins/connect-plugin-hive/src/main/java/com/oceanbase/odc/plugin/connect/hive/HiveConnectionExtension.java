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
package com.oceanbase.odc.plugin.connect.hive;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

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
 * Hive connection extension implementing JDBC URL generation and connection testing.
 * <p>
 * JDBC URL format: {@code jdbc:hive2://host:port/database;param=value}
 * <p>
 * Uses semicolon ({@code ;}) as parameter separator (not {@code ?} or {@code &}). Default
 * authentication mode is NONE ({@code auth=noSasl}).
 *
 * @since ODC_release_4.3.4
 */
@Extension
public class HiveConnectionExtension extends OBMySQLConnectionExtension {

    @Override
    public String generateJdbcUrl(@NonNull JdbcUrlProperty properties) {
        String host = properties.getHost();
        Validate.notEmpty(host, "host can not be null");
        Integer port = properties.getPort();
        Validate.notNull(port, "port can not be null");

        StringBuilder jdbcUrl = new StringBuilder("jdbc:hive2://");
        jdbcUrl.append(host).append(":").append(port);
        jdbcUrl.append("/");

        String database = properties.getDefaultSchema();
        if (StringUtils.isNotBlank(database)) {
            jdbcUrl.append(database);
        }

        // Hive JDBC URL uses semicolon as parameter separator
        String parameters = getJdbcUrlParameters(properties.getJdbcParameters());
        if (StringUtils.isNotBlank(parameters)) {
            jdbcUrl.append(";").append(parameters.replace("&", ";"));
        }

        return jdbcUrl.toString();
    }

    /**
     * Override to discard MySQL/OceanBase-specific JDBC URL parameters injected by
     * {@code OBConsoleDataSourceFactory.getJdbcParams()} (e.g. useSSL, maxAllowedPacket,
     * allowMultiQueries, etc.). Hive JDBC driver does not recognize these parameters and their presence
     * causes connection timeouts.
     * <p>
     * Only Hive-specific defaults (e.g. {@code auth=noSasl}) are retained.
     */
    @Override
    protected String getJdbcUrlParameters(Map<String, String> jdbcUrlParams) {
        Map<String, String> hiveParams = appendDefaultJdbcUrlParameters(null);
        return Objects.isNull(hiveParams) ? null
                : hiveParams.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue())
                        .collect(Collectors.joining("&"));
    }

    @Override
    protected Map<String, String> appendDefaultJdbcUrlParameters(Map<String, String> jdbcUrlParams) {
        // Ignore the incoming map which contains MySQL-specific parameters.
        // Only add Hive-specific defaults.
        Map<String, String> hiveParams = new HashMap<>();
        // Default to NONE authentication mode (auth=noSasl).
        // Without this, the Hive JDBC driver attempts a SASL handshake which fails
        // against a non-Kerberos HiveServer2 instance.
        hiveParams.put("auth", "noSasl");
        return hiveParams;
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
        return OdcConstants.HIVE_DRIVER_CLASS_NAME;
    }

    @Override
    public List<ConnectionInitializer> getConnectionInitializers() {
        return Collections.emptyList();
    }

    @Override
    public JdbcUrlParser getConnectionInfo(@NonNull String jdbcUrl, String userName) throws SQLException {
        return new HiveJdbcUrlParser(jdbcUrl, userName);
    }

}
