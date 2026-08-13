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
 * Unit tests for {@link DruidDataSourceFactory#resolveValidationQuery(DialectType)}.
 * <p>
 * Pins the Task-004-FIX-2 follow-on fix: the Druid pool {@code validationQuery} for any PG-family
 * dialect (POSTGRESQL + GAUSSDB) must be the portable {@code "select 1"}, not the Oracle-style
 * {@code "select 1 from dual"}. Open-source openGauss rejects the latter with
 * {@code ERROR: relation "dual" does not exist on gaussdb (SQLSTATE 42P01)}, which crashes the
 * Druid CreateConnectionThread and surfaces as {@code CannotGetJdbcConnectionException} on the
 * first BACKEND_DS_KEY lookup ({@code DBTableService.listTables}); GaussDB commercial happens to
 * honour {@code DUAL} as a vendor extension but we still want a single portable validation query
 * for the whole PG family.
 * <p>
 * Also covers B-24 — DB2 validation query must be {@code "select 1 from SYSIBM.SYSDUMMY1"} (DB2
 * enforces a FROM clause, design.md §2.5).
 */
public class DruidDataSourceFactoryTest {

    @Test
    public void testResolveValidationQuery_gaussdb_uses_select_1() {
        // Critical: GAUSSDB must NOT fall back to "select 1 from dual" anymore. openGauss
        // rejects it and previously broke DBTableService.listTables(test_odc_opengauss).
        Assert.assertEquals("select 1",
                DruidDataSourceFactory.resolveValidationQuery(DialectType.GAUSSDB));
    }

    @Test
    public void testResolveValidationQuery_postgresql_uses_select_1() {
        // PG regression pin (KF-3): zero PG behaviour change.
        Assert.assertEquals("select 1",
                DruidDataSourceFactory.resolveValidationQuery(DialectType.POSTGRESQL));
    }

    @Test
    public void testResolveValidationQuery_mysql_uses_select_1() {
        Assert.assertEquals("select 1",
                DruidDataSourceFactory.resolveValidationQuery(DialectType.MYSQL));
    }

    @Test
    public void testResolveValidationQuery_ob_mysql_uses_select_1() {
        Assert.assertEquals("select 1",
                DruidDataSourceFactory.resolveValidationQuery(DialectType.OB_MYSQL));
    }

    @Test
    public void testResolveValidationQuery_tidb_uses_select_1() {
        Assert.assertEquals("select 1",
                DruidDataSourceFactory.resolveValidationQuery(DialectType.TIDB));
    }

    @Test
    public void testResolveValidationQuery_gbase8a_uses_select_1() {
        // S3 / AC-3: GBase-8a keep-alive must not fall back to "select 1 from dual".
        Assert.assertEquals("select 1",
                DruidDataSourceFactory.resolveValidationQuery(DialectType.GBASE_8A));
    }

    @Test
    public void testResolveValidationQuery_doris_uses_select_1() {
        Assert.assertEquals("select 1",
                DruidDataSourceFactory.resolveValidationQuery(DialectType.DORIS));
    }

    @Test
    public void testResolveValidationQuery_sqlserver_uses_select_1() {
        Assert.assertEquals("select 1",
                DruidDataSourceFactory.resolveValidationQuery(DialectType.SQL_SERVER));
    }

    @Test
    public void testResolveValidationQuery_db2_uses_sysibm_sysdummy1() {
        Assert.assertEquals("select 1 from SYSIBM.SYSDUMMY1",
                DruidDataSourceFactory.resolveValidationQuery(DialectType.DB2));
    }

    @Test
    public void testResolveValidationQuery_hana_uses_dummy() {
        Assert.assertEquals("select 1 from DUMMY",
                DruidDataSourceFactory.resolveValidationQuery(DialectType.HANA));
    }

    @Test
    public void testResolveValidationQuery_ob_oracle_uses_select_1_from_dual() {
        // Oracle / OB_ORACLE legitimately support DUAL; pin them.
        Assert.assertEquals("select 1 from dual",
                DruidDataSourceFactory.resolveValidationQuery(DialectType.OB_ORACLE));
    }

    @Test
    public void testResolveValidationQuery_oracle_uses_select_1_from_dual() {
        Assert.assertEquals("select 1 from dual",
                DruidDataSourceFactory.resolveValidationQuery(DialectType.ORACLE));
    }

    @Test
    public void testResolveValidationQuery_dm_uses_select_1_from_dual() {
        Assert.assertEquals("select 1 from dual",
                DruidDataSourceFactory.resolveValidationQuery(DialectType.DM));
    }

    @Test
    public void testResolveValidationQuery_nullDialect_defaultsToOracleStyle() {
        Assert.assertEquals("select 1 from dual",
                DruidDataSourceFactory.resolveValidationQuery(null));
    }
}
