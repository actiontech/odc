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
package com.oceanbase.odc.service.feature;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.odc.core.shared.constant.ConnectType;

public class AllFeaturesTest {

    @Test
    public void getByConnectType_tidb_returnsSameAsMysql() {
        Features mysqlFeatures = AllFeatures.getByConnectType(ConnectType.MYSQL);
        Features tidbFeatures = AllFeatures.getByConnectType(ConnectType.TIDB);
        Assert.assertNotNull(tidbFeatures);
        Assert.assertSame(mysqlFeatures, tidbFeatures);
    }

    @Test
    public void getByConnectType_variousTypes_returnsNonNull() {
        Map<ConnectType, Class<? extends Features>> cases = new LinkedHashMap<>();
        cases.put(ConnectType.OB_MYSQL, OBMySQLFeatures.class);
        cases.put(ConnectType.MYSQL, MySQLFeatures.class);
        cases.put(ConnectType.TIDB, MySQLFeatures.class);
        cases.put(ConnectType.ODP_SHARDING_OB_MYSQL, ODPShardingFeatures.class);

        for (Map.Entry<ConnectType, Class<? extends Features>> entry : cases.entrySet()) {
            Features features = AllFeatures.getByConnectType(entry.getKey());
            Assert.assertNotNull("Features should not be null for " + entry.getKey(), features);
            Assert.assertEquals("Features type mismatch for " + entry.getKey(),
                    entry.getValue(), features.getClass());
        }
    }
}
