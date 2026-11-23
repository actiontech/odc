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
import org.bson.types.ObjectId;
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

    @Test
    public void mapDocuments_formatsNestedAndObjectIdWithoutDocumentNoise() {
        Document nested = new Document("city", "SH");
        Document source = new Document("_id", new ObjectId("507f1f77bcf86cd799439011"))
                .append("profile", nested)
                .append("tags", Arrays.asList("a", "b"));
        MongoTabularResult result = new MongoResultMapper().mapDocuments(Arrays.asList(source));
        int idIdx = result.getColumns().indexOf("_id");
        int profileIdx = result.getColumns().indexOf("profile");
        int tagsIdx = result.getColumns().indexOf("tags");
        Assert.assertEquals("ObjectId(\"507f1f77bcf86cd799439011\")", result.getRows().get(0).get(idIdx));
        String profile = String.valueOf(result.getRows().get(0).get(profileIdx));
        String tags = String.valueOf(result.getRows().get(0).get(tagsIdx));
        Assert.assertFalse(profile.contains("Document{{"));
        Assert.assertTrue(profile.startsWith("{"));
        Assert.assertTrue(tags.startsWith("["));
    }

    @Test
    public void mapWriteResult_formatsAckWithoutRawJson() {
        Document doc = new Document("acknowledged", true)
                .append("insertedId", new ObjectId("507f1f77bcf86cd799439011"));
        MongoTabularResult result = new MongoResultMapper().mapWriteResult(doc);
        Assert.assertFalse(result.getColumns().contains("_raw_json"));
        Assert.assertEquals(Arrays.asList("acknowledged", "insertedId"), result.getColumns());
        Assert.assertEquals(true, result.getRows().get(0).get(0));
        Assert.assertEquals("ObjectId(\"507f1f77bcf86cd799439011\")", result.getRows().get(0).get(1));
    }
}
