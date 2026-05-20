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

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.odc.plugin.schema.api.BaseSchemaPlugin;

/**
 * Pinning tests for the pf4j plugin entry. If these assertions ever flip, ODC plugin manager will
 * silently rebind every Hive schema extension to the wrong dialect at startup (compat-RISK R-4.1
 * read-only gate + R-4.2 extension entry point stability).
 */
public class HiveSchemaPluginTest {

    @Test
    public void getDialectType_returnsHive() {
        Assert.assertEquals(DialectType.HIVE, new HiveSchemaPlugin().getDialectType());
    }

    @Test
    public void plugin_extendsBaseSchemaPlugin_so_pf4j_canBind() {
        // pf4j discovers schema plugins via class hierarchy; if the parent ever shifts the
        // plugin loader will not pick HIVE up.
        Assert.assertTrue(new HiveSchemaPlugin() instanceof BaseSchemaPlugin);
    }
}
