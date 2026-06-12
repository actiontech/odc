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

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.tools.dbbrowser.model.DBTableColumn;

public class RedisColumnExtensionTest {
    @Test
    public void columns_whenRedisVirtualTable_thenExposeStableMetadata() {
        List<DBTableColumn> columns = new RedisColumnExtension().columns("0", "user");
        Assert.assertEquals(4, columns.size());
        Assert.assertEquals("key_name", columns.get(0).getName());
        Assert.assertEquals("VARCHAR", columns.get(0).getTypeName());
        Assert.assertEquals("value_preview", columns.get(2).getName());
    }
}
