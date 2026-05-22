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
 * Unit tests for {@link OBConsoleDataSourceFactory#getSchema(String, DialectType)} and
 * {@link OBConsoleDataSourceFactory#getDefaultSchema(ConnectionConfig)}.
 * <p>
 * Covers compat_risks CR-4d (GAUSSDB default schema fallback) and pins the POSTGRESQL / OB_MYSQL
 * paths as regression baselines.
 */
public class OBConsoleDataSourceFactoryTest {

    private ConnectionConfig newConfig(DialectType dialectType, String defaultSchema) {
        ConnectionConfig config = new ConnectionConfig();
        // ConnectionConfig#getDialectType() is derived from #type; populate via setType(ConnectType).
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
}
