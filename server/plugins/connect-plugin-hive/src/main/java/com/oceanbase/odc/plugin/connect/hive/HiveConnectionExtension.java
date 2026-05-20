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

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.apache.commons.collections4.CollectionUtils;
import org.pf4j.Extension;

import com.oceanbase.odc.common.util.ExceptionUtils;
import com.oceanbase.odc.common.util.StringUtils;
import com.oceanbase.odc.core.datasource.ConnectionInitializer;
import com.oceanbase.odc.plugin.connect.api.ConnectionExtensionPoint;
import com.oceanbase.odc.plugin.connect.api.HostAddress;
import com.oceanbase.odc.plugin.connect.api.JdbcUrlParser;
import com.oceanbase.odc.plugin.connect.api.TestResult;
import com.oceanbase.odc.plugin.connect.hive.initializer.HiveConnectionInitializer;
import com.oceanbase.odc.plugin.connect.model.JdbcUrlProperty;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Hive {@link ConnectionExtensionPoint} implementation. Wires the Apache hive-jdbc driver
 * ({@code org.apache.hive.jdbc.HiveDriver}, decision #13 in design §4.1.4) into ODC.
 *
 * <p>
 * This class implements the 5 contract methods declared in {@link ConnectionExtensionPoint}:
 * {@link #generateJdbcUrl(JdbcUrlProperty)}, {@link #getDriverClassName()},
 * {@link #getConnectionInitializers()}, {@link #getConnectionInfo(String, String)} and
 * {@link #test(String, Properties, int, List)}. Deliberately does NOT extend any OB-family
 * extension to keep the Hive plugin classloader free of OB-MySQL transitive dependencies
 * (compat-RISK R-1.4 / R-6.2 isolation goal).
 *
 * @since ODC_release_4.3.4
 */
@Slf4j
@Extension
public class HiveConnectionExtension implements ConnectionExtensionPoint {

    /** Driver class name. Matches design.md §4.1.4 decision #13. */
    public static final String HIVE_DRIVER_CLASS_NAME = "org.apache.hive.jdbc.HiveDriver";

    @Override
    public String generateJdbcUrl(@NonNull JdbcUrlProperty properties) {
        return new HiveJdbcUrlParser().build(properties);
    }

    @Override
    public String getDriverClassName() {
        return HIVE_DRIVER_CLASS_NAME;
    }

    @Override
    public List<ConnectionInitializer> getConnectionInitializers() {
        return Collections.singletonList(new HiveConnectionInitializer());
    }

    @Override
    public JdbcUrlParser getConnectionInfo(@NonNull String jdbcUrl, String userName) throws SQLException {
        try {
            return new HiveJdbcUrlParser(jdbcUrl);
        } catch (IllegalArgumentException e) {
            throw new SQLException("Failed to parse Hive jdbc url: " + jdbcUrl, e);
        }
    }

    @Override
    public TestResult test(String jdbcUrl, Properties properties, int queryTimeout,
            List<ConnectionInitializer> initializers) {
        HostAddress hostAddress;
        try {
            hostAddress = getConnectionInfo(jdbcUrl, null).getHostAddresses().get(0);
        } catch (SQLException | IndexOutOfBoundsException e) {
            return TestResult.unknownError(e);
        }
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
            if (rootCause == null) {
                log.warn("Failed to get connection when test Hive connection, jdbcUrl={}", jdbcUrl, e);
                return TestResult.unknownError(e);
            }
            log.warn("Failed to get connection when test Hive connection, jdbcUrl={}", jdbcUrl, e);
            String host = hostAddress.getHost();
            Integer port = hostAddress.getPort();
            if (rootCause instanceof ConnectException) {
                return TestResult.unknownPort(port);
            } else if (rootCause instanceof SocketTimeoutException) {
                return TestResult.hostUnreachable(host);
            } else if (rootCause instanceof UnknownHostException) {
                return TestResult.unknownHost(host);
            } else if (StringUtils.containsIgnoreCase(rootCause.getMessage(), "access denied")
                    || StringUtils.containsIgnoreCase(rootCause.getMessage(), "authentication failed")) {
                return TestResult.accessDenied(rootCause.getLocalizedMessage());
            }
            return TestResult.unknownError(rootCause);
        }
    }
}
