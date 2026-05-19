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
package com.oceanbase.odc.service.connection;

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.odc.core.shared.constant.ConnectType;
import com.oceanbase.odc.service.connection.model.ConnectionConfig;

/**
 * Mock-only unit tests for the DB2 datasource adapter introduced by fix-F. Verifies that the DMS-EE
 * convention "carry the DB2 database name via {@code defaultSchema}" is normalised into
 * {@code catalogName} before persistence, and that the adapter is a no-op for other dialects /
 * already-populated rows. No real JDBC, no Spring context — pure POJO checks.
 *
 * @author actiontech-zihan
 * @since 4.3.4
 */
public class ConnectionServiceDb2AdapterTest {

    @Test
    public void adapt_movesDefaultSchemaToCatalog_forDb2() {
        ConnectionConfig config = new ConnectionConfig();
        config.setType(ConnectType.DB2);
        config.setUsername("db2inst1");
        config.setDefaultSchema("testdb");

        ConnectionService.adaptDb2DatabaseToCatalog(config);

        Assert.assertEquals("testdb", config.getCatalogName());
        // Raw field should still hold the original input; the downstream
        // isDefaultSchemaRequired() block in innerCreate/updateConnectionConfig clears it.
        Assert.assertEquals("testdb", config.getRawDefaultSchema());
    }

    @Test
    public void adapt_preservesExplicitCatalog_forDb2() {
        ConnectionConfig config = new ConnectionConfig();
        config.setType(ConnectType.DB2);
        config.setUsername("db2inst1");
        config.setDefaultSchema("testdb");
        config.setCatalogName("EXPLICIT_DB");

        ConnectionService.adaptDb2DatabaseToCatalog(config);

        Assert.assertEquals("EXPLICIT_DB", config.getCatalogName());
    }

    @Test
    public void adapt_isNoop_forMysql() {
        ConnectionConfig config = new ConnectionConfig();
        config.setType(ConnectType.MYSQL);
        config.setUsername("root");
        config.setDefaultSchema("testdb");

        ConnectionService.adaptDb2DatabaseToCatalog(config);

        Assert.assertNull(config.getCatalogName());
        Assert.assertEquals("testdb", config.getRawDefaultSchema());
    }

    @Test
    public void adapt_isNoop_whenDefaultSchemaBlank() {
        ConnectionConfig config = new ConnectionConfig();
        config.setType(ConnectType.DB2);
        config.setUsername("db2inst1");
        config.setDefaultSchema(null);

        ConnectionService.adaptDb2DatabaseToCatalog(config);

        Assert.assertNull(config.getCatalogName());
    }

    @Test
    public void adapt_isNoop_forNullConfig() {
        // exercise the null-guard branch; tolerates being called from a guarded caller
        ConnectionService.adaptDb2DatabaseToCatalog(null);
    }

    @Test
    public void rawDefaultSchema_returnsRawField_evenForDb2() {
        ConnectionConfig config = new ConnectionConfig();
        config.setType(ConnectType.DB2);
        config.setUsername("db2inst1");
        // raw is null; getDefaultSchema() falls back to user.toUpperCase() = "DB2INST1",
        // but the raw getter must return null so the adapter can tell apart the two cases.
        Assert.assertNull(config.getRawDefaultSchema());
        Assert.assertEquals("DB2INST1", config.getDefaultSchema());
    }
}
