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
package com.oceanbase.odc.plugin.schema.postgres;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import com.oceanbase.odc.common.util.JdbcOperationsUtil;
import com.oceanbase.odc.common.util.StringUtils;
import com.oceanbase.odc.plugin.schema.api.SchemaSyncConnectionContext;
import com.oceanbase.tools.dbbrowser.model.DBDatabase;
import com.oceanbase.tools.dbbrowser.model.DBObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;

import lombok.extern.slf4j.Slf4j;

/**
 * Enumerate PostgreSQL databases as ODC library nodes {@code <catalog>.<schema>}.
 * <p>
 * Cross-catalog schema listing requires a dedicated JDBC connection per catalog. Prefer
 * {@link SchemaSyncConnectionContext} credentials (set by sync). A single catalog connect/list
 * failure is logged and skipped so the rest of the instance still syncs.
 * </p>
 * <p>
 * <b>GaussDB isolation:</b> only invoked from {@link PostgresDatabaseExtension}; GaussDB keeps its
 * own extension and catalog-only listing.
 * </p>
 */
@Slf4j
final class PostgresCatalogSchemaEnumerator {

    private static final String LIST_CATALOGS_SQL = "SELECT "
            + "    datname AS name, "
            + "    datcollate AS collation, "
            + "    pg_encoding_to_char(encoding) AS charset "
            + "FROM pg_database "
            + "WHERE datistemplate = false AND datallowconn = true "
            + "ORDER BY datname";

    /**
     * Non-system schemas: exclude pg_catalog / information_schema and pg_toast* / pg_temp* /
     * pg_toast_temp* (covered by pg_toast%).
     */
    private static final String LIST_SCHEMAS_SQL = "SELECT nspname FROM pg_namespace "
            + "WHERE nspname NOT IN ('pg_catalog', 'information_schema') "
            + "AND nspname NOT LIKE 'pg_toast%' "
            + "AND nspname NOT LIKE 'pg_temp%' "
            + "ORDER BY nspname";

    private PostgresCatalogSchemaEnumerator() {}

    static List<DBDatabase> listDetails(Connection connection) {
        List<CatalogMeta> catalogs = queryCatalogs(connection);
        if (catalogs.isEmpty()) {
            return Collections.emptyList();
        }
        List<DBDatabase> result = new ArrayList<>();
        String currentCatalog = currentDatabase(connection);
        for (CatalogMeta catalog : catalogs) {
            try {
                appendSchemas(connection, currentCatalog, catalog, result);
            } catch (Exception e) {
                log.warn(
                        "Skip PostgreSQL catalog '{}' during metadata sync (no CONNECT or connect failed): {}",
                        catalog.name, e.getMessage());
            }
        }
        return result;
    }

    static List<DBObjectIdentity> listIdentities(Connection connection) {
        List<DBDatabase> details = listDetails(connection);
        List<DBObjectIdentity> identities = new ArrayList<>(details.size());
        for (DBDatabase database : details) {
            DBObjectIdentity identity = new DBObjectIdentity();
            identity.setName(database.getName());
            identity.setType(DBObjectType.DATABASE);
            identities.add(identity);
        }
        return identities;
    }

    private static void appendSchemas(Connection probeConnection, String currentCatalog, CatalogMeta catalog,
            List<DBDatabase> result) throws SQLException {
        if (StringUtils.equals(catalog.name, currentCatalog)) {
            appendFromConnection(probeConnection, catalog, result);
            return;
        }
        try (Connection catalogConnection = openCatalogConnection(probeConnection, catalog.name)) {
            appendFromConnection(catalogConnection, catalog, result);
        }
    }

    private static void appendFromConnection(Connection connection, CatalogMeta catalog, List<DBDatabase> result) {
        List<String> schemas = JdbcOperationsUtil.getJdbcOperations(connection).queryForList(LIST_SCHEMAS_SQL,
                String.class);
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
    }

    private static boolean isSystemSchema(String schema) {
        if ("pg_catalog".equals(schema) || "information_schema".equals(schema)) {
            return true;
        }
        return schema.startsWith("pg_toast") || schema.startsWith("pg_temp");
    }

    private static List<CatalogMeta> queryCatalogs(Connection connection) {
        try {
            return JdbcOperationsUtil.getJdbcOperations(connection).query(LIST_CATALOGS_SQL,
                    (rs, rowNum) -> new CatalogMeta(rs.getString("name"), rs.getString("charset"),
                            rs.getString("collation")));
        } catch (Exception e) {
            log.warn("Failed to list PostgreSQL catalogs from pg_database", e);
            return Collections.emptyList();
        }
    }

    private static String currentDatabase(Connection connection) {
        try {
            return JdbcOperationsUtil.getJdbcOperations(connection).queryForObject("SELECT current_database()",
                    String.class);
        } catch (Exception e) {
            log.debug("Failed to read current_database()", e);
            return null;
        }
    }

    private static Connection openCatalogConnection(Connection probeConnection, String catalog)
            throws SQLException {
        SchemaSyncConnectionContext.Auth auth = SchemaSyncConnectionContext.get();
        String baseUrl;
        String username;
        String password;
        if (auth != null && StringUtils.isNotBlank(auth.getJdbcUrl())) {
            baseUrl = auth.getJdbcUrl();
            username = auth.getUsername();
            password = auth.getPassword();
        } else {
            baseUrl = probeConnection.getMetaData().getURL();
            username = probeConnection.getMetaData().getUserName();
            password = "";
        }
        String catalogUrl = rewriteCatalogInJdbcUrl(baseUrl, catalog);
        Properties properties = new Properties();
        if (StringUtils.isNotBlank(username)) {
            properties.setProperty("user", username);
        }
        if (password != null) {
            properties.setProperty("password", password);
        }
        return DriverManager.getConnection(catalogUrl, properties);
    }

    /**
     * Replace the database path segment in {@code jdbc:postgresql://host:port/db?...}.
     */
    static String rewriteCatalogInJdbcUrl(String jdbcUrl, String catalog) {
        if (StringUtils.isBlank(jdbcUrl) || StringUtils.isBlank(catalog)) {
            throw new IllegalArgumentException("jdbcUrl and catalog are required");
        }
        String prefix = "jdbc:postgresql://";
        if (!jdbcUrl.startsWith(prefix)) {
            throw new IllegalArgumentException("Not a PostgreSQL JDBC URL: " + jdbcUrl);
        }
        String rest = jdbcUrl.substring(prefix.length());
        int slash = rest.indexOf('/');
        if (slash < 0) {
            throw new IllegalArgumentException("PostgreSQL JDBC URL missing catalog path: " + jdbcUrl);
        }
        String hostPort = rest.substring(0, slash);
        String afterSlash = rest.substring(slash + 1);
        int q = afterSlash.indexOf('?');
        String query = q >= 0 ? afterSlash.substring(q) : "";
        return prefix + hostPort + "/" + catalog + query;
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
