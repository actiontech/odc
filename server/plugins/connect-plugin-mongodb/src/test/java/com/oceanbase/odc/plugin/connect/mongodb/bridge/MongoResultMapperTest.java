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
package com.oceanbase.odc.plugin.connect.mongodb.bridge;

import java.util.Arrays;

import org.bson.Document;
import org.junit.Assert;
import org.junit.Test;

public class MongoResultMapperTest {
    @Test
    public void mapDocuments_keepsRawJsonAndFields() {
        MongoTabularResult result = new MongoResultMapper().mapDocuments(Arrays.asList(
                new Document("name", "alice").append("age", 18)));
        Assert.assertTrue(result.getColumns().contains("_raw_json"));
        Assert.assertTrue(result.getColumns().contains("name"));
        Assert.assertEquals(1, result.getRows().size());
    }
}
