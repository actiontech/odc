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
package com.oceanbase.odc.plugin.connect.hive;

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.odc.core.shared.constant.DialectType;

/**
 * Pinning test for the pf4j plugin entry. If this assertion ever flips, ODC plugin manager will
 * silently rebind every Hive extension to the wrong dialect at startup (compat-RISK R-4.2 entry
 * point stability).
 */
public class HiveConnectionPluginTest {

    @Test
    public void getDialectType_returnsHive() {
        Assert.assertEquals(DialectType.HIVE, new HiveConnectionPlugin().getDialectType());
    }
}
