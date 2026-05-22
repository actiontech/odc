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
package com.oceanbase.odc.plugin.connect.gaussdb;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
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

/**
 * Connection extension for GaussDB-family data sources (Huawei Cloud GaussDB, openGauss). It pins
 * the JDBC URL prefix to {@code jdbc:opengauss://} and resolves the driver class name from
 * {@link OdcConstants#GAUSSDB_DRIVER_CLASS_NAME}, which is the {@code
 * org.opengauss.Driver} bundled by {@code opengauss-jdbc}.
 * <p>
 * Test connection logic mirrors {@code PostgresConnectionExtension}: open a JDBC connection with
 * the supplied properties, run any provided initializer scripts and return success / failure.
 */
@Extension
public class GaussDBConnectionExtension extends OBMySQLConnectionExtension {

    @Override
    public String generateJdbcUrl(@NonNull JdbcUrlProperty properties) {
        String host = properties.getHost();
        Validate.notEmpty(host, "host can not be null");
        Integer port = properties.getPort();
        Validate.notNull(port, "port can not be null");
        String schema = properties.getDefaultSchema();
        /*
         * Resolve the JDBC catalog (a.k.a. database name in PG/GaussDB).
         *
         * Upstream {@link com.oceanbase.odc.service.session.factory.OBConsoleDataSourceFactory} forwards
         * {@code ConnectionConfig.getCatalogName()} unchanged, which is sourced from the dedicated
         * PG-mode-only {@code connect_connection.catalog_name} column. DMS-managed GaussDB / openGauss data
         * sources surface a single "default schema" field (no separate catalog input), so that column is
         * persisted as {@code NULL} and the upstream {@link JdbcUrlProperty#getCatalogName()} arrives
         * blank. Without a fallback, the previous {@code Validate.notEmpty(catalogName, ...)} blew up the
         * periodic schema-sync loop with {@code Sync database failed: catalog name can not be null} for
         * every GaussDB-family data source, completely blocking the ODC workbench main path (CR-1, REQ-1 ~
         * REQ-6 downstream).
         *
         * Resolution order: 1. explicit {@code catalogName} when the operator supplied one; 2. {@link
         * OdcConstants#GAUSSDB_DEFAULT_CATALOG} ({@code postgres}) as the bootstrap database that ships
         * with every GaussDB-family instance out of the box. We must NOT fall back to {@code defaultSchema}
         * here, because schema (e.g. {@code public}) is a logical namespace inside a database, not a
         * database itself — routing the JDBC URL to {@code /public} would fail with {@code database
         * "public" does not exist}.
         */
        String catalogName = properties.getCatalogName();
        if (StringUtils.isBlank(catalogName)) {
            catalogName = OdcConstants.GAUSSDB_DEFAULT_CATALOG;
        }

        StringBuilder jdbcUrl = new StringBuilder();
        jdbcUrl.append("jdbc:opengauss://").append(host).append(":").append(port).append("/").append(catalogName);
        if (StringUtils.isNotBlank(schema)) {
            jdbcUrl.append("?currentSchema=").append(schema);
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
        return OdcConstants.GAUSSDB_DRIVER_CLASS_NAME;
    }

    @Override
    public List<ConnectionInitializer> getConnectionInitializers() {
        return Collections.emptyList();
    }

}
