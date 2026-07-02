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
package com.oceanbase.odc.service.session.factory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.odc.core.shared.constant.ConnectType;
import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.odc.service.connection.model.ConnectionConfig;

import sun.misc.Unsafe;

/**
 * Unit tests for {@link OBConsoleDataSourceFactory}, including
 * {@link OBConsoleDataSourceFactory#resolveEffectiveCatalogName(DialectType, String, String)}.
 *
 * <p>
 * 覆盖 issue #850 中 PG 数据源 {@code catalog name can not be null} 阻塞性 BUG 的修复路径：上游（DMS）创建 PG 数据源时通常只传
 * {@code default_schema=public} 而不传 {@code catalog_name}，导致 ODC 后端
 * {@code DatabaseService.syncDataSourceSchemas} 100% 失败、前端资源树无法展开。修复方案在 PG 类型 + catalog 为空时 统一兜底到
 * PG 内置默认数据库 {@code postgres}；不再以 defaultSchema 推断 catalog（schema 不是 database， 强行使用会触发
 * {@code FATAL: database "<schema>" does not exist}）。
 */
public class OBConsoleDataSourceFactoryTest {

    private ConnectionConfig newConfig(DialectType dialectType, String defaultSchema) {
        ConnectionConfig config = new ConnectionConfig();
        config.setType(ConnectType.from(dialectType));
        config.setDefaultSchema(defaultSchema);
        return config;
    }

    @Test
    public void testGetDefaultSchema_GAUSSDB_returns_public_when_empty() {
        ConnectionConfig config = newConfig(DialectType.GAUSSDB, "");
        Assert.assertEquals("public", OBConsoleDataSourceFactory.getDefaultSchema(config));
    }

    @Test
    public void testGetDefaultSchema_GAUSSDB_returns_public_when_null() {
        ConnectionConfig config = newConfig(DialectType.GAUSSDB, null);
        Assert.assertEquals("public", OBConsoleDataSourceFactory.getDefaultSchema(config));
    }

    @Test
    public void testGetDefaultSchema_GAUSSDB_uses_user_input_when_provided() {
        ConnectionConfig config = newConfig(DialectType.GAUSSDB, "myschema");
        Assert.assertEquals("myschema", OBConsoleDataSourceFactory.getDefaultSchema(config));
    }

    @Test
    public void testGetDefaultSchema_POSTGRESQL_Unchanged() {
        // PG 0 regression - empty defaultSchema must still fall back to "public"
        // and an explicit defaultSchema must round-trip unchanged.
        ConnectionConfig empty = newConfig(DialectType.POSTGRESQL, "");
        Assert.assertEquals("public", OBConsoleDataSourceFactory.getDefaultSchema(empty));
        ConnectionConfig custom = newConfig(DialectType.POSTGRESQL, "tenant1");
        Assert.assertEquals("tenant1", OBConsoleDataSourceFactory.getDefaultSchema(custom));
    }

    @Test
    public void testGetDefaultSchema_OB_MYSQL_Unchanged() {
        // OB_MYSQL: empty defaultSchema returns null (legacy behaviour to omit
        // the database in the JDBC URL). Adding GAUSSDB must not perturb this.
        ConnectionConfig empty = newConfig(DialectType.OB_MYSQL, "");
        Assert.assertNull(OBConsoleDataSourceFactory.getDefaultSchema(empty));
        ConnectionConfig custom = newConfig(DialectType.OB_MYSQL, "tenant1");
        Assert.assertEquals("tenant1", OBConsoleDataSourceFactory.getDefaultSchema(custom));
    }

    @Test
    public void testGetSchema_GAUSSDB_returns_schema_unquoted() {
        // GaussDB inherits the PG identifier-quoting policy: unquoted schema
        // for routing purposes (Oracle-style double quoting is not applied).
        Assert.assertEquals("public",
                OBConsoleDataSourceFactory.getSchema("public", DialectType.GAUSSDB));
        Assert.assertEquals("MyMixedCase",
                OBConsoleDataSourceFactory.getSchema("MyMixedCase", DialectType.GAUSSDB));
    }

    @Test
    public void testGetSchema_POSTGRESQL_Unchanged() {
        Assert.assertEquals("public",
                OBConsoleDataSourceFactory.getSchema("public", DialectType.POSTGRESQL));
    }

    @Test
    public void testGetSchema_OB_ORACLE_StillQuotes() {
        // Defence in depth - the Oracle branch is unaffected.
        Assert.assertEquals("\"public\"",
                OBConsoleDataSourceFactory.getSchema("public", DialectType.OB_ORACLE));
    }

    @Test
    public void getKeepAliveSql_mongodb_returnsPingCommand() throws Exception {
        Method method = OBConsoleDataSourceFactory.class.getDeclaredMethod("getKeepAliveSql", DialectType.class);
        method.setAccessible(true);

        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        OBConsoleDataSourceFactory factory = (OBConsoleDataSourceFactory) unsafe.allocateInstance(
                OBConsoleDataSourceFactory.class);
        String keepAliveSql = (String) method.invoke(factory, DialectType.MONGODB);

        Assert.assertEquals("db.runCommand({ ping: 1 })", keepAliveSql);
    }

    @Test
    public void testResolveEffectiveCatalogName_ExplicitCatalog_PostgreSQL_returnsAsIs() {
        Assert.assertEquals("mydb",
                OBConsoleDataSourceFactory.resolveEffectiveCatalogName(DialectType.POSTGRESQL, "mydb", "public"));
    }

    @Test
    public void testResolveEffectiveCatalogName_ExplicitCatalog_MySQL_returnsAsIs() {
        Assert.assertEquals("mydb",
                OBConsoleDataSourceFactory.resolveEffectiveCatalogName(DialectType.MYSQL, "mydb",
                        "information_schema"));
    }

    @Test
    public void testResolveEffectiveCatalogName_PG_nullCatalog_publicSchema_fallsBackToPostgresDb() {
        // 复现 issue #850 现场：DMS 创建 PG 数据源仅传 default_schema=public，catalog 为 null
        Assert.assertEquals("postgres",
                OBConsoleDataSourceFactory.resolveEffectiveCatalogName(DialectType.POSTGRESQL, null, "public"));
    }

    @Test
    public void testResolveEffectiveCatalogName_PG_nullCatalog_publicSchemaCaseInsensitive_fallsBackToPostgresDb() {
        Assert.assertEquals("postgres",
                OBConsoleDataSourceFactory.resolveEffectiveCatalogName(DialectType.POSTGRESQL, null, "Public"));
        Assert.assertEquals("postgres",
                OBConsoleDataSourceFactory.resolveEffectiveCatalogName(DialectType.POSTGRESQL, null, "PUBLIC"));
    }

    @Test
    public void testResolveEffectiveCatalogName_PG_emptyCatalog_publicSchema_fallsBackToPostgresDb() {
        Assert.assertEquals("postgres",
                OBConsoleDataSourceFactory.resolveEffectiveCatalogName(DialectType.POSTGRESQL, "", "public"));
        Assert.assertEquals("postgres",
                OBConsoleDataSourceFactory.resolveEffectiveCatalogName(DialectType.POSTGRESQL, "  ", "public"));
    }

    @Test
    public void testResolveEffectiveCatalogName_PG_nullCatalog_customSchema_fallsBackToPostgresDb() {
        // 即便用户填了"看似 database 名"的 defaultSchema（如 testdb / appdb），也不能再据此推断 catalog——
        // 因为它可能是真实存在的 PG schema（如 schema_a），用作 catalog 会触发
        // FATAL: database "<schema>" does not exist。统一兜底到 postgres 内置库。
        Assert.assertEquals("postgres",
                OBConsoleDataSourceFactory.resolveEffectiveCatalogName(DialectType.POSTGRESQL, null, "testdb"));
        Assert.assertEquals("postgres",
                OBConsoleDataSourceFactory.resolveEffectiveCatalogName(DialectType.POSTGRESQL, "", "appdb"));
    }

    @Test
    public void testResolveEffectiveCatalogName_PG_nullCatalog_nullSchema_fallsBackToPostgresDb() {
        Assert.assertEquals("postgres",
                OBConsoleDataSourceFactory.resolveEffectiveCatalogName(DialectType.POSTGRESQL, null, null));
        Assert.assertEquals("postgres",
                OBConsoleDataSourceFactory.resolveEffectiveCatalogName(DialectType.POSTGRESQL, "", ""));
    }

    @Test
    public void testResolveEffectiveCatalogName_MySQL_nullCatalog_doesNotFallback() {
        // 不能影响其他数据源类型——MySQL 不强校验 catalog
        Assert.assertNull(
                OBConsoleDataSourceFactory.resolveEffectiveCatalogName(DialectType.MYSQL, null, "information_schema"));
    }

    @Test
    public void testResolveEffectiveCatalogName_Oracle_nullCatalog_doesNotFallback() {
        Assert.assertNull(
                OBConsoleDataSourceFactory.resolveEffectiveCatalogName(DialectType.ORACLE, null, "ORCL"));
    }

    @Test
    public void testResolveEffectiveCatalogName_OBMySQL_nullCatalog_doesNotFallback() {
        Assert.assertNull(
                OBConsoleDataSourceFactory.resolveEffectiveCatalogName(DialectType.OB_MYSQL, null, "test"));
    }

    @Test
    public void testResolveEffectiveCatalogName_SqlServer_nullCatalog_doesNotFallback() {
        Assert.assertNull(
                OBConsoleDataSourceFactory.resolveEffectiveCatalogName(DialectType.SQL_SERVER, null, "master"));
    }

    @Test
    public void testResolveEffectiveCatalogName_SqlServer_databaseDotSchema_extractsCatalog() {
        Assert.assertEquals("TestDB",
                OBConsoleDataSourceFactory.resolveEffectiveCatalogName(DialectType.SQL_SERVER, null, "TestDB.dbo"));
    }

    @Test
    public void testResolveSqlServerCatalogAndSchema_splitsDatabaseDotSchema() {
        String[] parts = OBConsoleDataSourceFactory.resolveSqlServerCatalogAndSchema(null, "TestDB.dbo");
        Assert.assertEquals("TestDB", parts[0]);
        Assert.assertEquals("dbo", parts[1]);
    }

    @Test
    public void testResolveSqlServerCatalogAndSchema_plainSchema_unchanged() {
        String[] parts = OBConsoleDataSourceFactory.resolveSqlServerCatalogAndSchema(null, "dbo");
        Assert.assertNull(parts[0]);
        Assert.assertEquals("dbo", parts[1]);
    }

    @Test
    public void testGetDefaultSchema_SqlServer_databaseDotSchema_returnsSchemaOnly() {
        ConnectionConfig config = newConfig(DialectType.SQL_SERVER, "TestDB.dbo");
        Assert.assertEquals("dbo", OBConsoleDataSourceFactory.getDefaultSchema(config));
    }

    @Test
    public void testGetJdbcUrl_SqlServer_databaseDotSchema_usesCatalogAndSchema() {
        ConnectionConfig config = new ConnectionConfig();
        config.setType(ConnectType.from(DialectType.SQL_SERVER));
        config.setDefaultSchema("TestDB.dbo");
        config.setHost("127.0.0.1");
        config.setPort(1433);
        config.setUsername("sa");
        config.setPassword("pwd");
        OBConsoleDataSourceFactory factory = new OBConsoleDataSourceFactory(config, true);
        String jdbcUrl = factory.getJdbcUrl();
        Assert.assertTrue(jdbcUrl.contains("databaseName=TestDB"));
        Assert.assertTrue(jdbcUrl.contains("currentSchema=dbo"));
        Assert.assertFalse(jdbcUrl.contains("currentSchema=TestDB.dbo"));
    }

    @Test
    public void testResolveEffectiveCatalogName_NullDialect_emptyCatalog_returnsEmpty() {
        Assert.assertNull(
                OBConsoleDataSourceFactory.resolveEffectiveCatalogName(null, null, "any"));
    }
}
