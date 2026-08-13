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
package com.oceanbase.odc.service.db.browser;

import java.lang.reflect.Method;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcOperations;

import com.oceanbase.odc.core.shared.constant.ConnectType;
import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;
import com.oceanbase.tools.dbbrowser.schema.postgre.PostgresSchemaAccessor;

/**
 * Unit tests for {@link DBSchemaAccessors}.
 * <p>
 * Covers the Task-004-FIX-2 follow-on fix: db-browser:1.2.3's
 * {@code AbstractDBBrowserFactory#create} switch table only recognises a fixed set of 10 dialect
 * strings (ORACLE / MYSQL / DORIS / TIDB / OB_ORACLE / OB_MYSQL / ODP_SHARDING_OB_MYSQL /
 * POSTGRESQL / SQL_SERVER / DM); a raw {@code "GAUSSDB"} would otherwise hit the {@code default:}
 * arm and throw {@code IllegalStateException: "Not supported for the type, GAUSSDB"}, surfacing as
 * HTTP 500 on {@code POST /api/v2/datasource/databases/{id}/sessions} during
 * {@code DatasourceColumnAccessor.<init>} for any GaussDB / openGauss workbench session.
 */
public class DBSchemaAccessorsTest {

    private static String invokeToDbBrowserType(DialectType dialectType) throws Exception {
        Method m = DBSchemaAccessors.class.getDeclaredMethod("toDbBrowserType", DialectType.class);
        m.setAccessible(true);
        return (String) m.invoke(null, dialectType);
    }

    @Test
    public void testToDbBrowserType_gaussdb_routes_to_postgresql() throws Exception {
        // Without this routing the switch table in AbstractDBBrowserFactory.java:54 would throw
        // IllegalStateException because db-browser:1.2.3 does not know "GAUSSDB".
        Assert.assertEquals("POSTGRESQL", invokeToDbBrowserType(DialectType.GAUSSDB));
    }

    @Test
    public void testToDbBrowserType_gbase8a_routes_to_mysql() throws Exception {
        // S3 / AC-3: session open requires SchemaAccessor; db-browser has no GBASE_8A case.
        Assert.assertEquals("MYSQL", invokeToDbBrowserType(DialectType.GBASE_8A));
    }

    @Test
    public void testToDbBrowserType_postgresql_unchanged() throws Exception {
        // PG must keep its own POSTGRESQL routing untouched (KF-3 zero PG regression).
        Assert.assertEquals("POSTGRESQL", invokeToDbBrowserType(DialectType.POSTGRESQL));
    }

    @Test
    public void testToDbBrowserType_obmysql_unchanged() throws Exception {
        // Existing OB / MySQL paths must not change.
        Assert.assertEquals("OB_MYSQL", invokeToDbBrowserType(DialectType.OB_MYSQL));
    }

    @Test
    public void testToDbBrowserType_oracle_unchanged() throws Exception {
        Assert.assertEquals("ORACLE", invokeToDbBrowserType(DialectType.ORACLE));
    }

    @Test
    public void testToDbBrowserType_kingbase_routes_to_oracle() throws Exception {
        Assert.assertEquals("ORACLE", invokeToDbBrowserType(DialectType.KINGBASE));
    }

    @Test
    public void testCreate_gaussdb_returnsPostgresSchemaAccessor() {
        JdbcOperations jdbc = Mockito.mock(JdbcOperations.class);
        DBSchemaAccessor accessor =
                DBSchemaAccessors.create(jdbc, null, ConnectType.GAUSSDB, "9.2.4", null);
        // Returning a PostgresSchemaAccessor rather than throwing IllegalStateException is the
        // entire fix: lets ODC create a ConnectionSession against GaussDB / openGauss and unblocks
        // the workbench tables view.
        Assert.assertTrue(
                "expected PostgresSchemaAccessor for GAUSSDB, got " + accessor.getClass().getName(),
                accessor instanceof PostgresSchemaAccessor);
    }

    @Test
    public void testCreate_postgresql_returnsPostgresSchemaAccessor_noRegression() {
        JdbcOperations jdbc = Mockito.mock(JdbcOperations.class);
        DBSchemaAccessor accessor =
                DBSchemaAccessors.create(jdbc, null, ConnectType.POSTGRESQL, "14.20", null);
        Assert.assertTrue(accessor instanceof PostgresSchemaAccessor);
    }
}
