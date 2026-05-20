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

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.odc.core.shared.constant.DialectType;

/**
 * {@link OBConsoleDataSourceFactory#resolveEffectiveCatalogName(DialectType, String, String)} 单元测试。
 *
 * <p>
 * 覆盖 issue #850 中 PG 数据源 {@code catalog name can not be null} 阻塞性 BUG 的修复路径：上游（DMS）创建 PG 数据源时通常只传
 * {@code default_schema=public} 而不传 {@code catalog_name}，导致 ODC 后端
 * {@code DatabaseService.syncDataSourceSchemas} 100% 失败、前端资源树无法展开。修复方案在 PG 类型 + catalog 为空时
 * 走如下兜底：defaultSchema 非空且不等于 PG 内置 schema {@code public} → defaultSchema；否则 → PG 内置默认数据库
 * {@code postgres}。
 */
public class OBConsoleDataSourceFactoryTest {

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
    public void testResolveEffectiveCatalogName_PG_nullCatalog_customSchema_usesSchemaAsCatalog() {
        // 兼容用户在 default_schema 字段中实际填了 database 名的场景
        Assert.assertEquals("testdb",
                OBConsoleDataSourceFactory.resolveEffectiveCatalogName(DialectType.POSTGRESQL, null, "testdb"));
        Assert.assertEquals("appdb",
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
    public void testResolveEffectiveCatalogName_NullDialect_emptyCatalog_returnsEmpty() {
        Assert.assertNull(
                OBConsoleDataSourceFactory.resolveEffectiveCatalogName(null, null, "any"));
    }
}
