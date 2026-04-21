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
package com.oceanbase.odc.plugin.connect.db2;

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
public class DB2ConnectionExtension extends OBMySQLConnectionExtension {

    @Override
    public String generateJdbcUrl(@NonNull JdbcUrlProperty properties) {
        String host = properties.getHost();
        Validate.notEmpty(host, "host can not be null");
        Integer port = properties.getPort();
        Validate.notNull(port, "port can not be null");
        String catalogName = properties.getCatalogName();
        Validate.notEmpty(catalogName, "catalogName (database) is required for DB2");

        StringBuilder jdbcUrl = new StringBuilder();
        jdbcUrl.append("jdbc:db2://").append(host).append(":").append(port)
                .append("/").append(catalogName);

        // DB2 JDBC URL parameters: jdbc:db2://host:port/db:key1=val1;key2=val2;
        // Each property pair is separated by semicolon, and the list must end with semicolon.
        String parameters = getJdbcUrlParameters(properties.getJdbcParameters());
        if (StringUtils.isNotBlank(parameters)) {
            String db2Params = parameters.replace("&", ";");
            if (!db2Params.endsWith(";")) {
                db2Params += ";";
            }
            jdbcUrl.append(":").append(db2Params);
        }
        return jdbcUrl.toString();
    }

    @Override
    protected Map<String, String> appendDefaultJdbcUrlParameters(Map<String, String> jdbcUrlParams) {
        // DB2 JDBC driver only accepts DB2-specific parameters.
        // OBConsoleDataSourceFactory injects MySQL-specific params (allowMultiQueries, autoDeserialize,
        // etc.)
        // which cause "Invalid database URL syntax" errors with the DB2 driver.
        // Filter down to only DB2-compatible parameters.
        java.util.Set<String> db2AllowedParams = new java.util.HashSet<>(java.util.Arrays.asList(
                "sslConnection", "sslTrustStoreLocation", "sslTrustStorePassword",
                "sslKeyStoreLocation", "sslKeyStorePassword",
                "currentSchema", "retrieveMessagesFromServerOnGetMessage",
                "loginTimeout", "blockingReadConnectionTimeout",
                "queryTimeoutInterruptProcessingMode", "enableSysplexWLB",
                "traceLevel", "traceFile", "traceDirectory"));
        Map<String, String> filtered = new java.util.HashMap<>();
        if (jdbcUrlParams != null) {
            for (Map.Entry<String, String> entry : jdbcUrlParams.entrySet()) {
                if (db2AllowedParams.contains(entry.getKey())) {
                    filtered.put(entry.getKey(), entry.getValue());
                }
            }
        }
        if (!filtered.containsKey("sslConnection")) {
            filtered.put("sslConnection", "false");
        }
        return filtered;
    }

    @Override
    public TestResult test(String jdbcUrl, Properties properties,
            int queryTimeout, List<ConnectionInitializer> initializers) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, properties)) {
            try (Statement statement = connection.createStatement()) {
                if (queryTimeout >= 0) {
                    statement.setQueryTimeout(queryTimeout);
                }
                // Verify connection with DB2 VALUES expression
                statement.executeQuery("VALUES 1");
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
        return OdcConstants.DB2_DRIVER_CLASS_NAME;
    }

    @Override
    public List<ConnectionInitializer> getConnectionInitializers() {
        return Collections.emptyList();
    }

    @Override
    public JdbcUrlParser getConnectionInfo(@NonNull String jdbcUrl, String userName) throws SQLException {
        return new DB2JdbcUrlParser(jdbcUrl, userName);
    }

}
