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
package com.oceanbase.odc.plugin.connect.gbase8a;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.Statement;
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
 * GBase-8a connection extension using official {@code jdbc:gbase://} connector.
 * <p>
 * Virtual cluster (VC) is carried via JDBC parameter {@code vcName} (e.g. {@code vcName=vc1}).
 * </p>
 */
@Extension
public class GBase8aConnectionExtension extends OBMySQLConnectionExtension {

    private static final String JDBC_URL_PREFIX = "jdbc:gbase://";

    /**
     * OB/MySQL client attributes that official GBase JDBC rejects with "driver not support property".
     */
    private static final Set<String> UNSUPPORTED_JDBC_PARAMS;

    static {
        Set<String> unsupported = new HashSet<>();
        unsupported.add("sendConnectionAttributes");
        unsupported.add("defaultConnectionAttributesBanList");
        unsupported.add("zeroDateTimeBehavior");
        unsupported.add("noDatetimeStringSync");
        unsupported.add("jdbcCompliantTruncation");
        unsupported.add("tinyInt1isBit");
        unsupported.add("socksProxyHost");
        unsupported.add("socksProxyPort");
        unsupported.add("socksProxyRemoteDns");
        unsupported.add("oracle.net.socksProxyHost");
        unsupported.add("oracle.net.socksProxyPort");
        unsupported.add("oracle.net.socksRemoteDNS");
        unsupported.add("oracle.jdbc.javaNio");
        unsupported.add("trustServerCertificate");
        unsupported.add("disableSslHostnameVerification");
        unsupported.add("trustStore");
        unsupported.add("trustStorePassword");
        unsupported.add("keyStore");
        unsupported.add("keyStorePassword");
        unsupported.add("allowLoadLocalInfile");
        unsupported.add("allowUrlInLocalInfile");
        unsupported.add("allowLoadLocalInfileInPath");
        unsupported.add("autoDeserialize");
        UNSUPPORTED_JDBC_PARAMS = Collections.unmodifiableSet(unsupported);
    }

    @Override
    public String generateJdbcUrl(@NonNull JdbcUrlProperty properties) {
        String host = properties.getHost();
        Validate.notEmpty(host, "host can not be empty");
        Integer port = properties.getPort();
        Validate.notNull(port, "port can not be null");
        String defaultSchema = properties.getDefaultSchema();

        StringBuilder jdbcUrl = new StringBuilder(JDBC_URL_PREFIX).append(host).append(":").append(port);
        if (StringUtils.isNotBlank(defaultSchema)) {
            jdbcUrl.append("/").append(defaultSchema);
        }
        String parameters = getJdbcUrlParameters(properties.getJdbcParameters());
        if (StringUtils.isNotBlank(parameters)) {
            jdbcUrl.append("?").append(parameters);
        }
        return jdbcUrl.toString();
    }

    @Override
    public String getDriverClassName() {
        return OdcConstants.GBASE_8A_DRIVER_CLASS_NAME;
    }

    @Override
    public List<ConnectionInitializer> getConnectionInitializers() {
        return Collections.emptyList();
    }

    @Override
    protected Map<String, String> appendDefaultJdbcUrlParameters(Map<String, String> jdbcUrlParams) {
        if (jdbcUrlParams == null || jdbcUrlParams.isEmpty()) {
            return jdbcUrlParams;
        }
        Map<String, String> filtered = new HashMap<>();
        for (Map.Entry<String, String> entry : jdbcUrlParams.entrySet()) {
            if (entry.getKey() == null || UNSUPPORTED_JDBC_PARAMS.contains(entry.getKey())) {
                continue;
            }
            filtered.put(entry.getKey(), entry.getValue());
        }
        return filtered;
    }

    @Override
    public TestResult test(String jdbcUrl, Properties properties, int queryTimeout,
            List<ConnectionInitializer> initializers) {
        // Use Driver.connect via plugin ClassLoader — DriverManager often reports
        // "No suitable driver" when the official GBase driver is only on the plugin CL.
        try {
            Class<?> driverClass = Class.forName(getDriverClassName(), true, getClass().getClassLoader());
            Driver driver = (Driver) driverClass.getDeclaredConstructor().newInstance();
            try (Connection connection = driver.connect(jdbcUrl, properties)) {
                if (connection == null) {
                    return TestResult.unknownError(
                            new SQLException("GBase driver returned null connection for url: " + jdbcUrl));
                }
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
            }
        } catch (Exception e) {
            Throwable rootCause = ExceptionUtils.getRootCause(e);
            return TestResult.unknownError(rootCause);
        }
    }

    @Override
    public JdbcUrlParser getConnectionInfo(@NonNull String jdbcUrl, String userName) throws SQLException {
        return new GBase8aJdbcUrlParser(jdbcUrl);
    }
}
