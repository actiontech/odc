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
package com.oceanbase.odc.plugin.connect.dm;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

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
 * Connection extension for DM (Dameng) database.
 * <p>
 * Generates JDBC URL in format: {@code jdbc:dm://host:port[?param=value&...]}
 * </p>
 *
 * @author
 * @since ODC_release_4.3.4
 */
@Extension
public class DmConnectionExtension extends OBMySQLConnectionExtension {

    /**
     * Parameters added for MySQL-compatible drivers. Kept out of {@code jdbc:dm://} URLs.
     * {@code connectTimeout} is intentionally absent: DM JDBC recognizes it.
     */
    private static final Set<String> MYSQL_INCOMPATIBLE_JDBC_PARAMETERS = new HashSet<>(Arrays.asList(
            "allowLoadLocalInfile",
            "allowLoadLocalInfileInPath",
            "allowMultiQueries",
            "allowUrlInLocalInfile",
            "autoDeserialize",
            "defaultConnectionAttributesBanList",
            "disableSslHostnameVerification",
            "enableFullLinkTrace",
            "jdbcCompliantTruncation",
            "keyStore",
            "keyStorePassword",
            "maxAllowedPacket",
            "noDatetimeStringSync",
            "oracle.jdbc.javaNio",
            "oracle.net.socksProxyHost",
            "oracle.net.socksProxyPort",
            "sendConnectionAttributes",
            "socksProxyHost",
            "socksProxyPort",
            "trustServerCertificate",
            "trustStore",
            "trustStorePassword",
            "useSSL",
            "zeroDateTimeBehavior"));

    @Override
    public String generateJdbcUrl(@NonNull JdbcUrlProperty properties) {
        String host = properties.getHost();
        Validate.notEmpty(host, "host can not be null");
        Integer port = properties.getPort();
        Validate.notNull(port, "port can not be null");
        String schema = properties.getDefaultSchema();

        StringBuilder jdbcUrl = new StringBuilder();
        jdbcUrl.append("jdbc:dm://").append(host).append(":").append(port);

        // Build query parameters
        Map<String, String> jdbcParams = properties.getJdbcParameters();
        if (StringUtils.isNotBlank(schema)) {
            if (jdbcParams == null) {
                jdbcParams = new java.util.HashMap<>();
            }
            jdbcParams.put("schema", schema);
        }
        String parameters = getJdbcUrlParameters(jdbcParams);
        if (StringUtils.isNotBlank(parameters)) {
            jdbcUrl.append("?").append(parameters);
        }

        return jdbcUrl.toString();
    }

    /**
     * Drop MySQL/OceanBase JDBC parameters injected by
     * {@code OBConsoleDataSourceFactory.getJdbcParams()}. DM accepts {@code connectTimeout} and
     * {@code schema}; the remaining keys are not DM driver properties.
     */
    @Override
    protected Map<String, String> appendDefaultJdbcUrlParameters(Map<String, String> jdbcUrlParams) {
        if (jdbcUrlParams == null || jdbcUrlParams.isEmpty()) {
            return jdbcUrlParams;
        }
        Map<String, String> retained = new HashMap<>();
        for (Map.Entry<String, String> entry : jdbcUrlParams.entrySet()) {
            if (entry.getValue() == null || MYSQL_INCOMPATIBLE_JDBC_PARAMETERS.contains(entry.getKey())) {
                continue;
            }
            retained.put(entry.getKey(), entry.getValue());
        }
        return retained.isEmpty() ? null : retained;
    }

    @Override
    public String getDriverClassName() {
        return OdcConstants.DM_DRIVER_CLASS_NAME;
    }

    @Override
    public List<ConnectionInitializer> getConnectionInitializers() {
        return Collections.emptyList();
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
                statement.execute("SELECT 1");
                return TestResult.success();
            }
        } catch (Exception e) {
            Throwable rootCause = ExceptionUtils.getRootCause(e);
            return TestResult.unknownError(rootCause);
        }
    }

    @Override
    public JdbcUrlParser getConnectionInfo(@NonNull String jdbcUrl, String userName) throws SQLException {
        return new DmJdbcUrlParser(jdbcUrl, userName);
    }

}
