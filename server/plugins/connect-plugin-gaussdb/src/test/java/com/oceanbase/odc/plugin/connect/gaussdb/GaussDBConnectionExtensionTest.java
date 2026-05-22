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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oceanbase.odc.plugin.connect.model.JdbcUrlProperty;

/**
 * Unit tests for {@link GaussDBConnectionExtension}.
 * <p>
 * Map-case style. Covers compat_risks CR-1 (driver class constant indirection) and CR-17
 * (opengauss-jdbc isolated to plugin classpath): the test asserts the well-known constant string
 * but never touches a real {@code
 * org.opengauss.Driver} instance, so the suite runs even when the driver jar is not on the surefire
 * classpath.
 */
public class GaussDBConnectionExtensionTest {

    private GaussDBConnectionExtension extension;

    @Before
    public void setUp() {
        this.extension = new GaussDBConnectionExtension();
    }

    @Test
    public void testGetDriverClassName_returns_org_opengauss_Driver() {
        Assert.assertEquals("org.opengauss.Driver", extension.getDriverClassName());
    }

    @Test
    public void testGenerateJdbcUrl_prefix_is_jdbc_opengauss() {
        JdbcUrlProperty props = new JdbcUrlProperty(
                "h", 5432, null, null, null, null, "db");
        String url = extension.generateJdbcUrl(props);
        Assert.assertTrue("URL must start with jdbc:opengauss:// but was: " + url,
                url.startsWith("jdbc:opengauss://"));
        // Defence in depth: must NOT accidentally fall back to the PG prefix
        // (which would route to org.postgresql.Driver via the OBConsole stack
        // and cause SASL auth failures against GaussDB).
        Assert.assertFalse("URL must not use the postgresql prefix: " + url,
                url.startsWith("jdbc:postgresql://"));
    }

    @Test
    public void testGenerateJdbcUrl_includes_currentSchema_param() {
        JdbcUrlProperty props = new JdbcUrlProperty(
                "h", 5432, "myschema", null, null, null, "db");
        String url = extension.generateJdbcUrl(props);
        Assert.assertTrue("expected currentSchema query param in: " + url,
                url.contains("?currentSchema=myschema"));
    }

    @Test
    public void testGenerateJdbcUrl_no_currentSchema_when_schema_blank() {
        JdbcUrlProperty props = new JdbcUrlProperty(
                "h", 5432, "", null, null, null, "db");
        String url = extension.generateJdbcUrl(props);
        Assert.assertFalse("blank schema must not append currentSchema: " + url,
                url.contains("?currentSchema="));
    }

    @Test
    public void testGenerateJdbcUrl_host_port_catalog_well_formed() {
        JdbcUrlProperty props = new JdbcUrlProperty(
                "example.com", 5433, null, null, null, null, "prod_db");
        String url = extension.generateJdbcUrl(props);
        Assert.assertEquals("jdbc:opengauss://example.com:5433/prod_db", url);
    }

    /**
     * Regression for Task-003-FIX: DMS-managed GaussDB / openGauss data sources do not surface a
     * separate catalog input, so {@code ConnectionConfig.catalogName} is persisted as {@code NULL} and
     * arrives blank in {@link JdbcUrlProperty#getCatalogName()}. The previous implementation called
     * {@code Validate.notEmpty(catalogName, "catalog name can not be null")} and crashed the periodic
     * schema-sync loop, completely blocking the ODC workbench main path. The plugin must fall back to
     * the GaussDB-family bootstrap database ({@code postgres}) rather than throw — see CR-1, REQ-1 ~
     * REQ-6 downstream blockage.
     */
    @Test
    public void testGenerateJdbcUrl_null_catalog_falls_back_to_postgres_database() {
        JdbcUrlProperty props = new JdbcUrlProperty(
                "h", 5432, null, null, null, null, null);
        String url = extension.generateJdbcUrl(props);
        Assert.assertEquals("jdbc:opengauss://h:5432/postgres", url);
    }

    @Test
    public void testGenerateJdbcUrl_blank_catalog_falls_back_to_postgres_database() {
        JdbcUrlProperty props = new JdbcUrlProperty(
                "h", 5432, null, null, null, null, "");
        String url = extension.generateJdbcUrl(props);
        Assert.assertEquals("jdbc:opengauss://h:5432/postgres", url);
    }

    @Test
    public void testGenerateJdbcUrl_null_catalog_with_schema_keeps_postgres_database_and_appends_schema() {
        // Schema "public" must NOT be promoted to the catalog position — that would
        // resolve to a non-existent database. Catalog falls back to "postgres", schema
        // is still appended as ?currentSchema=public.
        JdbcUrlProperty props = new JdbcUrlProperty(
                "h", 5432, "public", null, null, null, null);
        String url = extension.generateJdbcUrl(props);
        Assert.assertEquals("jdbc:opengauss://h:5432/postgres?currentSchema=public", url);
    }

    @Test
    public void testGenerateJdbcUrl_explicit_catalog_postgres_is_preserved() {
        JdbcUrlProperty props = new JdbcUrlProperty(
                "h", 5432, "public", null, null, null, "postgres");
        String url = extension.generateJdbcUrl(props);
        Assert.assertEquals("jdbc:opengauss://h:5432/postgres?currentSchema=public", url);
    }

    @Test
    public void testGenerateJdbcUrl_explicit_custom_catalog_is_preserved() {
        JdbcUrlProperty props = new JdbcUrlProperty(
                "h", 5432, "myschema", null, null, null, "my_business_db");
        String url = extension.generateJdbcUrl(props);
        Assert.assertEquals("jdbc:opengauss://h:5432/my_business_db?currentSchema=myschema", url);
    }
}
