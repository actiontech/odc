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
package com.oceanbase.odc.plugin.connect.hana;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
 * SAP HANA connection extension.
 * <p>
 * Generates JDBC URLs in the format {@code jdbc:sap://host:port/?param=value}, tests connections
 * using {@code SELECT 1 FROM DUMMY} (HANA does not support bare {@code SELECT 1} without a FROM
 * clause).
 *
 * @since ODC_release_4.3.4
 */
@Extension
public class HanaConnectionExtension extends OBMySQLConnectionExtension {

    @Override
    public String generateJdbcUrl(@NonNull JdbcUrlProperty properties) {
        String host = properties.getHost();
        Validate.notEmpty(host, "host can not be null");
        Integer port = properties.getPort();
        Validate.notNull(port, "port can not be null");
        String catalogName = properties.getCatalogName();
        String defaultSchema = properties.getDefaultSchema();

        StringBuilder jdbcUrl = new StringBuilder();
        jdbcUrl.append("jdbc:sap://").append(host).append(":").append(port).append("/");

        // Build HANA-specific query parameters
        Map<String, String> hanaParams = new LinkedHashMap<>();
        if (StringUtils.isNotBlank(catalogName)) {
            hanaParams.put("databaseName", catalogName);
        }
        if (StringUtils.isNotBlank(defaultSchema)) {
            hanaParams.put("currentSchema", defaultSchema);
        }

        String parameters = getJdbcUrlParameters(hanaParams);
        if (StringUtils.isNotBlank(parameters)) {
            jdbcUrl.append("?").append(parameters);
        }
        return jdbcUrl.toString();
    }

    /**
     * Override to discard MySQL/OceanBase-specific JDBC URL parameters injected by
     * {@code OBConsoleDataSourceFactory.getJdbcParams()} (e.g. useSSL, maxAllowedPacket,
     * allowMultiQueries, etc.). The SAP HANA ngdbc driver does not recognize these parameters and their
     * presence causes connection failures or timeouts.
     * <p>
     * Only HANA-specific parameters (databaseName, currentSchema) are retained.
     */
    @Override
    protected String getJdbcUrlParameters(Map<String, String> jdbcUrlParams) {
        Map<String, String> hanaParams = appendDefaultJdbcUrlParameters(jdbcUrlParams);
        return Objects.isNull(hanaParams) || hanaParams.isEmpty() ? null
                : hanaParams.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue())
                        .collect(Collectors.joining("&"));
    }

    @Override
    protected Map<String, String> appendDefaultJdbcUrlParameters(Map<String, String> jdbcUrlParams) {
        // Discard the incoming map which contains MySQL-specific parameters from
        // OBConsoleDataSourceFactory.getJdbcParams(). Only retain HANA-specific params.
        Map<String, String> hanaParams = new HashMap<>();
        if (jdbcUrlParams != null) {
            // Whitelist: only keep known HANA JDBC parameters
            for (String key : HANA_JDBC_PARAM_WHITELIST) {
                if (jdbcUrlParams.containsKey(key)) {
                    hanaParams.put(key, jdbcUrlParams.get(key));
                }
            }
        }
        return hanaParams;
    }

    /**
     * Known SAP HANA ngdbc JDBC parameters that may be passed through to the URL.
     */
    private static final java.util.Set<String> HANA_JDBC_PARAM_WHITELIST = new java.util.HashSet<>(
            java.util.Arrays.asList(
                    "databaseName", "currentSchema", "encrypt", "validateCertificate",
                    "hostNameInCertificate", "trustStore", "trustStorePassword",
                    "keyStore", "keyStorePassword", "reconnect", "communicationTimeout",
                    "packetSize", "splitBatchCommands", "locale", "sessionVariable"));

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
                // HANA requires FROM DUMMY for SELECT without a real table
                statement.execute("SELECT 1 FROM DUMMY");
                return TestResult.success();
            }
        } catch (Exception e) {
            Throwable rootCause = ExceptionUtils.getRootCause(e);
            return TestResult.unknownError(rootCause != null ? rootCause : e);
        }
    }

    @Override
    public String getDriverClassName() {
        return OdcConstants.HANA_DRIVER_CLASS_NAME;
    }

    @Override
    public List<ConnectionInitializer> getConnectionInitializers() {
        return Collections.emptyList();
    }

    @Override
    public JdbcUrlParser getConnectionInfo(@NonNull String jdbcUrl, String userName) throws SQLException {
        return new HanaJdbcUrlParser(jdbcUrl, userName);
    }
}
