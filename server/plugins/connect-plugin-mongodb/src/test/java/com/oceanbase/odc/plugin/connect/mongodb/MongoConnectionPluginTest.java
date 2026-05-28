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
package com.oceanbase.odc.plugin.connect.mongodb;

import java.util.HashMap;

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.odc.core.shared.constant.OdcConstants;
import com.oceanbase.odc.plugin.connect.model.JdbcUrlProperty;

public class MongoConnectionPluginTest {
    @Test
    public void getDialectType_returnsMongoDB() {
        Assert.assertEquals(DialectType.MONGODB, new MongoConnectionPlugin().getDialectType());
    }

    @Test
    public void generateJdbcUrl_containsSchemaAndParameters() {
        MongoConnectionExtension extension = new MongoConnectionExtension();
        HashMap<String, String> params = new HashMap<>();
        params.put("authSource", "admin");
        String actual = extension.generateJdbcUrl(new JdbcUrlProperty("127.0.0.1", 27017, "appdb", params));
        Assert.assertEquals("jdbc:mongodb://127.0.0.1:27017/appdb?authSource=admin&appName=odc-mongodb", actual);
    }

    @Test
    public void generateJdbcUrl_withoutSchemaButWithParameters_addsTrailingSlash() {
        MongoConnectionExtension extension = new MongoConnectionExtension();
        HashMap<String, String> params = new HashMap<>();
        params.put("authSource", "admin");
        String actual = extension.generateJdbcUrl(new JdbcUrlProperty("127.0.0.1", 27017, null, params));
        Assert.assertEquals("jdbc:mongodb://127.0.0.1:27017/?authSource=admin&appName=odc-mongodb", actual);
    }

    @Test
    public void getDriverClassName_returnsBridgeDriver() {
        MongoConnectionExtension extension = new MongoConnectionExtension();
        Assert.assertEquals(OdcConstants.MONGODB_DRIVER_CLASS_NAME, extension.getDriverClassName());
        Assert.assertEquals("com.oceanbase.odc.plugin.connect.mongodb.bridge.MongoJdbcDriver",
                extension.getDriverClassName());
    }
}
