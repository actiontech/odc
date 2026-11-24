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
package com.oceanbase.odc.service.connection.database;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.sql.DataSource;

import com.oceanbase.odc.common.util.JdbcOperationsUtil;
import com.oceanbase.odc.common.util.StringUtils;
import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.odc.service.connection.model.ConnectionConfig;
import com.oceanbase.odc.service.connection.util.ConnectionMapper;
import com.oceanbase.odc.service.session.factory.OBConsoleDataSourceFactory;
import com.oceanbase.tools.dbbrowser.model.DBDatabase;

import lombok.extern.slf4j.Slf4j;

/**
 * Service-side PostgreSQL sync enumerator: {@code <catalog>.<schema>} via per-catalog DataSources.
 * <p>
 * Uses {@link ConnectionConfig} credentials (plugin ClassLoader-safe). Invoked only for
 * {@link DialectType#POSTGRESQL}; GaussDB must not call this.
 * </p>
 */
@Slf4j
final class PostgresCatalogSchemaDatabaseLister {

    private static final ConnectionMapper CONNECTION_MAPPER = ConnectionMapper.INSTANCE;

    private static final String LIST_CATALOGS_SQL = "SELECT "
            + "    datname AS name, "
            + "    datcollate AS collation, "
            + "    pg_encoding_to_char(encoding) AS charset "
            + "FROM pg_database "
            + "WHERE datistemplate = false AND datallowconn = true "
            + "ORDER BY datname";

    private static final String LIST_SCHEMAS_SQL = "SELECT nspname FROM pg_namespace "
            + "WHERE nspname NOT IN ('pg_catalog', 'information_schema') "
            + "AND nspname NOT LIKE 'pg_toast%' "
            + "AND nspname NOT LIKE 'pg_temp%' "
            + "ORDER BY nspname";

    private PostgresCatalogSchemaDatabaseLister() {}

    static List<DBDatabase> list(ConnectionConfig connectionConfig) {
        if (connectionConfig == null || DialectType.POSTGRESQL != connectionConfig.getDialectType()) {
            throw new IllegalArgumentException("PostgresCatalogSchemaDatabaseLister requires DialectType.POSTGRESQL");
        }
        List<CatalogMeta> catalogs;
        DataSource probeDs = newDataSource(connectionConfig);
        try (Connection probe = probeDs.getConnection()) {
            catalogs = JdbcOperationsUtil.getJdbcOperations(probe).query(LIST_CATALOGS_SQL,
                    (rs, rowNum) -> new CatalogMeta(rs.getString("name"), rs.getString("charset"),
                            rs.getString("collation")));
        } catch (Exception e) {
            log.warn("Failed to list PostgreSQL catalogs for datasource id={}", connectionConfig.getId(), e);
            return Collections.emptyList();
        } finally {
            closeQuietly(probeDs);
        }

        List<DBDatabase> result = new ArrayList<>();
        for (CatalogMeta catalog : catalogs) {
            ConnectionConfig catalogConfig = CONNECTION_MAPPER.clone(connectionConfig);
            catalogConfig.setCatalogName(catalog.name);
            DataSource catalogDs = newDataSource(catalogConfig);
            try (Connection catalogConn = catalogDs.getConnection()) {
                List<String> schemas = JdbcOperationsUtil.getJdbcOperations(catalogConn)
                        .queryForList(LIST_SCHEMAS_SQL, String.class);
                for (String schema : schemas) {
                    if (StringUtils.isBlank(schema) || isSystemSchema(schema)) {
                        continue;
                    }
                    String fullName = catalog.name + "." + schema;
                    DBDatabase database = new DBDatabase();
                    database.setId(fullName);
                    database.setName(fullName);
                    database.setCharset(catalog.charset);
                    database.setCollation(catalog.collation);
                    result.add(database);
                }
            } catch (Exception e) {
                log.warn(
                        "Skip PostgreSQL catalog '{}' during metadata sync (no CONNECT or connect failed): {}",
                        catalog.name, e.getMessage());
            } finally {
                closeQuietly(catalogDs);
            }
        }
        return result;
    }

    private static boolean isSystemSchema(String schema) {
        if ("pg_catalog".equals(schema) || "information_schema".equals(schema)) {
            return true;
        }
        return schema.startsWith("pg_toast") || schema.startsWith("pg_temp");
    }

    private static DataSource newDataSource(ConnectionConfig config) {
        return new OBConsoleDataSourceFactory(config, true, false, false).getDataSource();
    }

    private static void closeQuietly(DataSource dataSource) {
        if (dataSource instanceof AutoCloseable) {
            try {
                ((AutoCloseable) dataSource).close();
            } catch (Exception e) {
                log.warn("Failed to close temporary PostgreSQL sync DataSource: {}", e.getMessage());
            }
        }
    }

    private static final class CatalogMeta {
        private final String name;
        private final String charset;
        private final String collation;

        private CatalogMeta(String name, String charset, String collation) {
            this.name = name;
            this.charset = charset;
            this.collation = collation;
        }
    }
}
