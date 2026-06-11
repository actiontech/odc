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
package com.oceanbase.odc.plugin.schema.redis;

import org.junit.Assert;
import org.junit.Test;

public class RedisTableExtensionTest {
    @Test
    public void groupName_prefixedKey() {
        Assert.assertEquals("codex", RedisTableExtension.groupName("codex:test:string"));
    }

    @Test
    public void groupName_plainKey() {
        Assert.assertEquals("keys", RedisTableExtension.groupName("codex"));
    }
}
