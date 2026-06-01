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

import java.net.InetAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLClientInfoException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.Validate;
import org.pf4j.Extension;

import com.oceanbase.odc.common.util.ExceptionUtils;
import com.oceanbase.odc.common.util.StringUtils;
import com.oceanbase.odc.core.datasource.ConnectionInitializer;
import com.oceanbase.odc.core.shared.constant.OdcConstants;
import com.oceanbase.odc.plugin.connect.api.TestResult;
import com.oceanbase.odc.plugin.connect.model.JdbcUrlProperty;
import com.oceanbase.odc.plugin.connect.obmysql.OBMySQLConnectionExtension;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * DB2 connection extension. Generates DB2 JDBC URLs, supplies the IBM jcc driver class name,
 * configures client info initializers for MON_GET_CONNECTION-side traceability, and runs a DB2
 * compatible test query against {@code SYSIBM.SYSDUMMY1}.
 *
 * <p>
 * Design references: {@code docs/spec/design.md} §2.3 (extension method table) / §2.7 (IBM JDBC
 * scope=provided).
 *
 * @author actiontech-zihan
 * @since 4.3.4
 */
@Slf4j
@Extension
public class Db2ConnectionExtension extends OBMySQLConnectionExtension {

    /**
     * DB2 JDBC URL template: {@code jdbc:db2://<host>:<port>/<database>:currentSchema=<schema>;}.
     *
     * <p>
     * Important: DB2 driver requires the property segment to be separated by {@code ;} and to
     * <strong>end with {@code ;}</strong>; otherwise the driver reports {@code errorcode -4461}.
     *
     * <p>
     * Catalog (database) vs schema in DB2:
     * <ul>
     * <li>{@code <database>} in the JDBC URL maps to the DB2 catalog/database name (e.g.
     * {@code testdb});
     * <li>{@code currentSchema=} maps to the in-database schema (e.g. {@code DB2INST1}).
     * </ul>
     *
     * <p>
     * Upstream (DMS-EE buildDatasourceBaseInfo, compat-RISK-5 D-02) only carries the DB2 database name
     * via the {@code defaultSchema} field of
     * {@link com.oceanbase.odc.service.connection.model.ConnectionConfig} when the user does not also
     * fill a separate {@code catalogName}. To keep that contract working without forcing a CE/EE schema
     * change to the create-datasource request body, we fall back to
     * {@link JdbcUrlProperty#getDefaultSchema()} when {@link JdbcUrlProperty#getCatalogName()} is
     * blank.
     *
     * <p>
     * When the resolved catalog and the {@code defaultSchema} reference the same string we omit the
     * {@code currentSchema=} segment entirely; DB2 then defaults the schema to
     * {@code user.toUpperCase()} (the DB2 implicit-schema convention) via the JDBC driver, which is
     * exactly what {@link com.oceanbase.odc.service.connection.model.ConnectionConfig#getDefaultSchema}
     * resolves to for DB2 (B-20).
     */
    @Override
    public String generateJdbcUrl(@NonNull JdbcUrlProperty properties) {
        String host = properties.getHost();
        Validate.notEmpty(host, "host can not be null");
        Integer port = properties.getPort();
        Validate.notNull(port, "port can not be null");
        String catalogName = properties.getCatalogName();
        String schema = properties.getDefaultSchema();
        // Fallback chain: when an explicit catalog/database name is not provided by the caller,
        // treat the defaultSchema field as the DB2 database name. This is the contract DMS-EE
        // currently relies on (CreateDatasourceRequest carries only defaultSchema, not catalogName).
        if (StringUtils.isEmpty(catalogName)) {
            catalogName = schema;
        }
        Validate.notEmpty(catalogName,
                "DB2 catalog (database name) can not be null; expected non-empty catalogName or defaultSchema");

        StringBuilder jdbcUrl = new StringBuilder();
        jdbcUrl.append("jdbc:db2://").append(host).append(":").append(port).append("/").append(catalogName);

        if (StringUtils.isNotBlank(schema) && !schema.equalsIgnoreCase(catalogName)) {
            // schema explicitly differs from the catalog (or the caller really meant a schema
            // override); honour it. DB2 driver requires the property segment to end with ';'.
            jdbcUrl.append(":currentSchema=").append(schema.toUpperCase()).append(";");
        }
        return jdbcUrl.toString();
    }

    @Override
    public String getDriverClassName() {
        return OdcConstants.DB2_DRIVER_CLASS_NAME;
    }

    /**
     * DB2 test connection: open the JDBC connection, run the initializers, and validate the session
     * with {@code SELECT 1 FROM SYSIBM.SYSDUMMY1}. DB2 enforces a {@code FROM} clause, so a bare
     * {@code SELECT 1} is invalid (see B-24 / B-S2 in design.md §2.5).
     */
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
                statement.execute("SELECT 1 FROM SYSIBM.SYSDUMMY1");
                return TestResult.success();
            }
        } catch (Exception e) {
            Throwable rootCause = ExceptionUtils.getRootCause(e);
            return TestResult.unknownError(rootCause);
        }
    }

    /**
     * Adds {@code setClientInfo(ApplicationName=ODC, ClientUser, ClientHostname)} so DB2's
     * {@code MON_GET_CONNECTION} / {@code APPLICATION_HANDLE} side has enough breadcrumbs to trace ODC
     * issued sessions (see B-S4 in plan.md / design.md §11.1 R-03).
     *
     * <p>
     * Some DB2 fixpacks &lt; 12 fail with {@link SQLClientInfoException} on certain keys; we therefore
     * wrap each call in try/catch and only log — never propagate — so connection establishment is not
     * blocked by an optional convenience.
     */
    @Override
    public List<ConnectionInitializer> getConnectionInitializers() {
        List<ConnectionInitializer> initializers = new ArrayList<>();
        initializers.add(connection -> {
            safeSetClientInfo(connection, "ApplicationName", "ODC");
            String user = safeGetUser(connection);
            if (StringUtils.isNotBlank(user)) {
                safeSetClientInfo(connection, "ClientUser", user);
            }
            String host = safeLocalHostName();
            if (StringUtils.isNotBlank(host)) {
                safeSetClientInfo(connection, "ClientHostname", host);
            }
        });
        return Collections.unmodifiableList(initializers);
    }

    private static void safeSetClientInfo(Connection connection, String key, String value) {
        try {
            connection.setClientInfo(key, value);
        } catch (SQLClientInfoException e) {
            log.warn("DB2 setClientInfo failed (key={}); fallback to no-op. reason={}", key,
                    e.getMessage());
        } catch (Exception e) {
            log.warn("DB2 setClientInfo unexpected error (key={}); fallback to no-op. reason={}", key,
                    e.getMessage());
        }
    }

    private static String safeGetUser(Connection connection) {
        try {
            return connection.getMetaData().getUserName();
        } catch (Exception e) {
            return null;
        }
    }

    private static String safeLocalHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return null;
        }
    }

}
