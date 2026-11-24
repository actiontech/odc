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

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.odc.core.shared.constant.ConnectType;
import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.odc.service.connection.model.ConnectionConfig;

/**
 * AC-BE-005 / AC-UT-BE-001: Path-A multi-catalog sync lister is POSTGRESQL-only. GaussDB must not
 * enter {@link PostgresCatalogSchemaDatabaseLister}.
 */
public class PostgresCatalogSchemaDatabaseListerTest {

    @Test
    public void list_rejectsGaussDB_beforeAnyJdbc() {
        ConnectionConfig config = new ConnectionConfig();
        config.setType(ConnectType.GAUSSDB);
        Assert.assertEquals(DialectType.GAUSSDB, config.getDialectType());

        try {
            PostgresCatalogSchemaDatabaseLister.list(config);
            Assert.fail("GAUSSDB must not enter PostgresCatalogSchemaDatabaseLister");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("POSTGRESQL"));
        }
    }

    @Test
    public void list_rejectsNullConfig() {
        try {
            PostgresCatalogSchemaDatabaseLister.list(null);
            Assert.fail("null config must be rejected");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("POSTGRESQL"));
        }
    }

    @Test
    public void list_rejectsNonPostgresDialect_obMysql() {
        ConnectionConfig config = new ConnectionConfig();
        config.setType(ConnectType.OB_MYSQL);
        try {
            PostgresCatalogSchemaDatabaseLister.list(config);
            Assert.fail("non-POSTGRESQL dialect must be rejected");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("POSTGRESQL"));
        }
    }

    @Test
    public void assertSyncListNotSilentlyEmpty_allCatalogsFailed_throws() {
        try {
            PostgresCatalogSchemaDatabaseLister.assertSyncListNotSilentlyEmpty(3, 0, 3);
            Assert.fail("all-failed sync must abort");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("All PostgreSQL catalogs failed"));
        }
    }

    @Test
    public void assertSyncListNotSilentlyEmpty_partialSkip_allowsEmptySchemas() {
        // One catalog connected but yielded no user schemas; two failed — not "all failed".
        PostgresCatalogSchemaDatabaseLister.assertSyncListNotSilentlyEmpty(3, 0, 2);
    }

    @Test
    public void assertSyncListNotSilentlyEmpty_success_noop() {
        PostgresCatalogSchemaDatabaseLister.assertSyncListNotSilentlyEmpty(2, 5, 0);
    }

    @Test
    public void assertSyncListNotSilentlyEmpty_noCatalogs_noop() {
        PostgresCatalogSchemaDatabaseLister.assertSyncListNotSilentlyEmpty(0, 0, 0);
    }
}
