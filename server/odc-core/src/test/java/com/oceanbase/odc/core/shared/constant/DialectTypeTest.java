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
package com.oceanbase.odc.core.shared.constant;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class DialectTypeTest {

    @Test
    public void isDm_DM_ReturnTrue() {
        Assert.assertTrue(DialectType.DM.isDm());
    }

    @Test
    public void isDm_OtherTypes_ReturnFalse() {
        Map<DialectType, Boolean> testCases = new LinkedHashMap<>();
        testCases.put(DialectType.MYSQL, false);
        testCases.put(DialectType.ORACLE, false);
        testCases.put(DialectType.OB_MYSQL, false);
        testCases.put(DialectType.OB_ORACLE, false);
        testCases.put(DialectType.SQL_SERVER, false);
        testCases.put(DialectType.POSTGRESQL, false);
        testCases.put(DialectType.DORIS, false);
        testCases.put(DialectType.UNKNOWN, false);

        for (Map.Entry<DialectType, Boolean> entry : testCases.entrySet()) {
            Assert.assertEquals("isDm() should return false for " + entry.getKey(),
                    entry.getValue().booleanValue(), entry.getKey().isDm());
        }
    }

    @Test
    public void getDBBrowserDialectTypeName_DM_ReturnDM() {
        Assert.assertEquals("DM", DialectType.DM.getDBBrowserDialectTypeName());
    }

    @Test
    public void fromValue_DM_ReturnDialectTypeDM() {
        Assert.assertEquals(DialectType.DM, DialectType.fromValue("DM"));
    }

    @Test
    public void fromValue_Null_ReturnNull() {
        Assert.assertNull(DialectType.fromValue(null));
    }

    @Test
    public void fromValue_Empty_ReturnNull() {
        Assert.assertNull(DialectType.fromValue(""));
    }

    @Test(expected = IllegalArgumentException.class)
    public void fromValue_InvalidValue_ThrowException() {
        DialectType.fromValue("INVALID_TYPE");
    }

    @Test
    public void isDm_AllDialectTypes_OnlyDmReturnsTrue() {
        for (DialectType type : DialectType.values()) {
            if (type == DialectType.DM) {
                Assert.assertTrue("isDm() should return true for DM", type.isDm());
            } else {
                Assert.assertFalse("isDm() should return false for " + type, type.isDm());
            }
        }
    }

    @Test
    public void isSqlServer_DM_ReturnFalse() {
        Assert.assertFalse(DialectType.DM.isSqlServer());
    }

    @Test
    public void isOracle_DM_ReturnFalse() {
        Assert.assertFalse(DialectType.DM.isOracle());
    }

    @Test
    public void isMysql_DM_ReturnFalse() {
        Assert.assertFalse(DialectType.DM.isMysql());
    }

    @Test
    public void isOceanbase_DM_ReturnFalse() {
        Assert.assertFalse(DialectType.DM.isOceanbase());
    }

    @Test
    public void isPostgreSql_DM_ReturnFalse() {
        Assert.assertFalse(DialectType.DM.isPostgreSql());
    }

    @Test
    public void isDoris_DM_ReturnFalse() {
        Assert.assertFalse(DialectType.DM.isDoris());
    }

    @Test
    public void valueOf_DM_Success() {
        DialectType dm = DialectType.valueOf("DM");
        Assert.assertEquals(DialectType.DM, dm);
    }

    @Test
    public void name_DM_ReturnDM() {
        Assert.assertEquals("DM", DialectType.DM.name());
    }

    @Test
    public void isTidb_TIDB_ReturnTrue() {
        Assert.assertTrue(DialectType.TIDB.isTidb());
    }

    @Test
    public void isTidb_OtherTypes_ReturnFalse() {
        Map<DialectType, Boolean> testCases = new LinkedHashMap<>();
        testCases.put(DialectType.MYSQL, false);
        testCases.put(DialectType.ORACLE, false);
        testCases.put(DialectType.OB_MYSQL, false);
        testCases.put(DialectType.OB_ORACLE, false);
        testCases.put(DialectType.SQL_SERVER, false);
        testCases.put(DialectType.POSTGRESQL, false);
        testCases.put(DialectType.DORIS, false);
        testCases.put(DialectType.DM, false);
        testCases.put(DialectType.UNKNOWN, false);

        for (Map.Entry<DialectType, Boolean> entry : testCases.entrySet()) {
            Assert.assertEquals("isTidb() should return false for " + entry.getKey(),
                    entry.getValue().booleanValue(), entry.getKey().isTidb());
        }
    }

    @Test
    public void isMysql_TIDB_ReturnFalse() {
        Assert.assertFalse(DialectType.TIDB.isMysql());
    }

    @Test
    public void isDoris_TIDB_ReturnFalse() {
        Assert.assertFalse(DialectType.TIDB.isDoris());
    }

    @Test
    public void isOracle_TIDB_ReturnFalse() {
        Assert.assertFalse(DialectType.TIDB.isOracle());
    }

    @Test
    public void isOceanbase_TIDB_ReturnFalse() {
        Assert.assertFalse(DialectType.TIDB.isOceanbase());
    }

    @Test
    public void isSqlServer_TIDB_ReturnFalse() {
        Assert.assertFalse(DialectType.TIDB.isSqlServer());
    }

    @Test
    public void isDm_TIDB_ReturnFalse() {
        Assert.assertFalse(DialectType.TIDB.isDm());
    }

    @Test
    public void isPostgreSql_TIDB_ReturnFalse() {
        Assert.assertFalse(DialectType.TIDB.isPostgreSql());
    }

    @Test
    public void fromValue_TIDB_ReturnDialectTypeTIDB() {
        Assert.assertEquals(DialectType.TIDB, DialectType.fromValue("TIDB"));
    }

    @Test
    public void getDBBrowserDialectTypeName_TIDB_ReturnTIDB() {
        Assert.assertEquals("TIDB", DialectType.TIDB.getDBBrowserDialectTypeName());
    }

    @Test
    public void isTidb_AllDialectTypes_OnlyTidbReturnsTrue() {
        for (DialectType type : DialectType.values()) {
            if (type == DialectType.TIDB) {
                Assert.assertTrue("isTidb() should return true for TIDB", type.isTidb());
            } else {
                Assert.assertFalse("isTidb() should return false for " + type, type.isTidb());
            }
        }
    }

    // ===== GaussDB (Issue #865) =====

    @Test
    public void valueOf_GAUSSDB_Success() {
        Assert.assertEquals(DialectType.GAUSSDB, DialectType.valueOf("GAUSSDB"));
    }

    @Test
    public void fromValue_GAUSSDB_ReturnDialectTypeGAUSSDB() {
        Assert.assertEquals(DialectType.GAUSSDB, DialectType.fromValue("GAUSSDB"));
    }

    @Test
    public void name_GAUSSDB_ReturnGAUSSDB() {
        Assert.assertEquals("GAUSSDB", DialectType.GAUSSDB.name());
    }

    @Test
    public void getDBBrowserDialectTypeName_GAUSSDB_ReturnGAUSSDB() {
        Assert.assertEquals("GAUSSDB", DialectType.GAUSSDB.getDBBrowserDialectTypeName());
    }

    @Test
    public void isGaussDB_GAUSSDB_ReturnTrue() {
        Assert.assertTrue(DialectType.GAUSSDB.isGaussDB());
    }

    @Test
    public void isGaussDB_OtherTypes_ReturnFalse() {
        Map<DialectType, Boolean> testCases = new LinkedHashMap<>();
        testCases.put(DialectType.MYSQL, false);
        testCases.put(DialectType.ORACLE, false);
        testCases.put(DialectType.OB_MYSQL, false);
        testCases.put(DialectType.OB_ORACLE, false);
        testCases.put(DialectType.SQL_SERVER, false);
        testCases.put(DialectType.POSTGRESQL, false);
        testCases.put(DialectType.DORIS, false);
        testCases.put(DialectType.TIDB, false);
        testCases.put(DialectType.DM, false);
        testCases.put(DialectType.UNKNOWN, false);

        for (Map.Entry<DialectType, Boolean> entry : testCases.entrySet()) {
            Assert.assertEquals("isGaussDB() should return false for " + entry.getKey(),
                    entry.getValue().booleanValue(), entry.getKey().isGaussDB());
        }
    }

    @Test
    public void isPgFamily_GAUSSDB_ReturnTrue() {
        Assert.assertTrue(DialectType.GAUSSDB.isPgFamily());
    }

    @Test
    public void isPgFamily_POSTGRESQL_ReturnTrue() {
        Assert.assertTrue(DialectType.POSTGRESQL.isPgFamily());
    }

    @Test
    public void isPgFamily_NonPgFamily_ReturnFalse() {
        Map<DialectType, Boolean> testCases = new LinkedHashMap<>();
        testCases.put(DialectType.MYSQL, false);
        testCases.put(DialectType.ORACLE, false);
        testCases.put(DialectType.OB_MYSQL, false);
        testCases.put(DialectType.OB_ORACLE, false);
        testCases.put(DialectType.SQL_SERVER, false);
        testCases.put(DialectType.DORIS, false);
        testCases.put(DialectType.TIDB, false);
        testCases.put(DialectType.DM, false);
        testCases.put(DialectType.UNKNOWN, false);

        for (Map.Entry<DialectType, Boolean> entry : testCases.entrySet()) {
            Assert.assertEquals("isPgFamily() should return false for " + entry.getKey(),
                    entry.getValue().booleanValue(), entry.getKey().isPgFamily());
        }
    }

    @Test
    public void isPostgreSql_GAUSSDB_ReturnFalse() {
        // GAUSSDB intentionally NOT classified as PostgreSQL to keep the PG-only business path
        // untouched (zero PG regression). Use isPgFamily() for protocol-family-wide checks.
        Assert.assertFalse(DialectType.GAUSSDB.isPostgreSql());
    }

    @Test
    public void isPostgreSql_POSTGRESQL_ReturnTrue() {
        Assert.assertTrue(DialectType.POSTGRESQL.isPostgreSql());
    }

    @Test
    public void isMysql_GAUSSDB_ReturnFalse() {
        Assert.assertFalse(DialectType.GAUSSDB.isMysql());
    }

    @Test
    public void isOracle_GAUSSDB_ReturnFalse() {
        Assert.assertFalse(DialectType.GAUSSDB.isOracle());
    }

    @Test
    public void isOceanbase_GAUSSDB_ReturnFalse() {
        Assert.assertFalse(DialectType.GAUSSDB.isOceanbase());
    }

    @Test
    public void valueOf_LegacyValues_StillWork() {
        // Regression: pre-existing enum values must remain deserializable after GAUSSDB is added.
        String[] legacy = {"MYSQL", "OB_MYSQL", "OB_ORACLE", "ORACLE",
                "ODP_SHARDING_OB_MYSQL", "POSTGRESQL", "SQL_SERVER", "DM",
                "DORIS", "TIDB", "FILE_SYSTEM", "UNKNOWN"};
        for (String name : legacy) {
            DialectType type = DialectType.valueOf(name);
            Assert.assertNotNull("valueOf(" + name + ") must not return null", type);
            Assert.assertEquals(name, type.name());
        }
    }
}
