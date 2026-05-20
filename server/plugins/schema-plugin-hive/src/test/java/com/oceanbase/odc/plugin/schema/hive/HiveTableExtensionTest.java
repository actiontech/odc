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
package com.oceanbase.odc.plugin.schema.hive;

import java.sql.Connection;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.oceanbase.tools.dbbrowser.model.DBObjectType;
import com.oceanbase.tools.dbbrowser.model.DBTable;

/**
 * Unit tests for {@link HiveTableExtension}. Concentrates on the read-only backstop contract
 * (design.md §2.3 decision 1 / R-4.1 / R-4.2) and the deterministic helpers ({@code
 * resolveTableType} / {@code quote}). Live JDBC paths (DESCRIBE FORMATTED, SHOW TABLES) require a
 * real Hive Server and are covered by integration tests in {@code skills/db-validation}.
 */
public class HiveTableExtensionTest {

    private final HiveTableExtension extension = new HiveTableExtension();

    @Test
    public void drop_throwsUnsupportedOperationException() {
        Connection conn = Mockito.mock(Connection.class);
        try {
            extension.drop(conn, "default", "t1");
            Assert.fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            Assert.assertTrue(
                    "message should reference design contract, got: " + e.getMessage(),
                    e.getMessage() != null && e.getMessage().contains("design 2.3 decision 1"));
        }
        Mockito.verifyNoInteractions(conn);
    }

    @Test
    public void generateCreateDDL_throwsUnsupportedOperationException() {
        Connection conn = Mockito.mock(Connection.class);
        DBTable t = new DBTable();
        try {
            extension.generateCreateDDL(conn, t);
            Assert.fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            Assert.assertNotNull(e.getMessage());
            Assert.assertTrue(e.getMessage().toLowerCase().contains("not supported"));
        }
    }

    @Test
    public void generateUpdateDDL_throwsUnsupportedOperationException() {
        Connection conn = Mockito.mock(Connection.class);
        DBTable oldT = new DBTable();
        DBTable newT = new DBTable();
        try {
            extension.generateUpdateDDL(conn, oldT, newT);
            Assert.fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            Assert.assertNotNull(e.getMessage());
            Assert.assertTrue(e.getMessage().toLowerCase().contains("not supported"));
        }
    }

    @Test
    public void syncExternalTableFiles_throwsUnsupportedOperationException() {
        Connection conn = Mockito.mock(Connection.class);
        try {
            extension.syncExternalTableFiles(conn, "default", "t1");
            Assert.fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            Assert.assertNotNull(e.getMessage());
            Assert.assertTrue(e.getMessage().toLowerCase().contains("not supported"));
        }
    }

    @Test
    public void resolveTableType_mapsExternalAndViewAndDefault() {
        // null and unknown both fall through to TABLE — defensive default per design §4.1.5
        Assert.assertEquals(DBObjectType.TABLE, HiveTableExtension.resolveTableType(null));
        Assert.assertEquals(DBObjectType.TABLE, HiveTableExtension.resolveTableType("MANAGED_TABLE"));
        // EXTERNAL is detected via substring match (case-insensitive)
        Assert.assertEquals(DBObjectType.EXTERNAL_TABLE,
                HiveTableExtension.resolveTableType("EXTERNAL_TABLE"));
        Assert.assertEquals(DBObjectType.EXTERNAL_TABLE,
                HiveTableExtension.resolveTableType("external_table"));
        // VIRTUAL_VIEW maps to VIEW; whitespace tolerated
        Assert.assertEquals(DBObjectType.VIEW,
                HiveTableExtension.resolveTableType("  VIRTUAL_VIEW  "));
    }

    @Test
    public void quote_wrapsIdentifierWithBackticksAndDoublesEmbedded() {
        Assert.assertEquals("`default`", HiveTableExtension.quote("default"));
        // embedded backticks are escaped by doubling per Hive grammar
        Assert.assertEquals("`weird``name`", HiveTableExtension.quote("weird`name"));
    }

    @Test(expected = NullPointerException.class)
    public void quote_nullIdentifier_throws() {
        HiveTableExtension.quote(null);
    }
}
