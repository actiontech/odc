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

import org.junit.Assert;
import org.junit.Test;

public class ConnectTypeTest {

    @Test
    public void isODPSharding_ODP_SHARDING_OB_MYSQL_ReturnTrue() {
        boolean isODPSharding = ConnectType.ODP_SHARDING_OB_MYSQL.isODPSharding();
        Assert.assertTrue(isODPSharding);
    }

    @Test
    public void isODPSharding_ODP_SHARDING_OB_ORACLE_ReturnTrue() {
        boolean isODPSharding = ConnectType.ODP_SHARDING_OB_ORACLE.isODPSharding();
        Assert.assertTrue(isODPSharding);
    }

    @Test
    public void isODPSharding_OB_MYSQL_ReturnFalse() {
        boolean isODPSharding = ConnectType.OB_MYSQL.isODPSharding();
        Assert.assertFalse(isODPSharding);
    }

    @Test
    public void getDialectType_TIDB_ReturnDialectTypeTIDB() {
        Assert.assertEquals(DialectType.TIDB, ConnectType.TIDB.getDialectType());
    }

    @Test
    public void from_DialectTypeTIDB_ReturnConnectTypeTIDB() {
        Assert.assertEquals(ConnectType.TIDB, ConnectType.from(DialectType.TIDB));
    }

    @Test
    public void isODPSharding_TIDB_ReturnFalse() {
        Assert.assertFalse(ConnectType.TIDB.isODPSharding());
    }

    @Test
    public void isFileSystem_TIDB_ReturnFalse() {
        Assert.assertFalse(ConnectType.TIDB.isFileSystem());
    }

    @Test
    public void isCloud_TIDB_ReturnFalse() {
        Assert.assertFalse(ConnectType.TIDB.isCloud());
    }

    @Test
    public void getDialectType_DB2_ReturnDialectTypeDB2() {
        Assert.assertEquals(DialectType.DB2, ConnectType.DB2.getDialectType());
    }

    @Test
    public void from_DialectTypeDB2_ReturnConnectTypeDB2() {
        Assert.assertEquals(ConnectType.DB2, ConnectType.from(DialectType.DB2));
    }

    @Test
    public void isCloud_DB2_ReturnFalse() {
        Assert.assertFalse(ConnectType.DB2.isCloud());
    }
}
