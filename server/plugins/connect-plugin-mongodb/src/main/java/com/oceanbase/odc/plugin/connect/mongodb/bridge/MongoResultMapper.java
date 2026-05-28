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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.bson.Document;
import org.bson.types.ObjectId;

public class MongoResultMapper {
    public MongoTabularResult mapDocuments(List<Document> documents) {
        Set<String> columns = new LinkedHashSet<>();
        columns.add("_raw_json");
        for (Document document : documents) {
            columns.addAll(document.keySet());
        }
        List<List<Object>> rows = new ArrayList<>();
        for (Document document : documents) {
            List<Object> row = new ArrayList<>();
            for (String column : columns) {
                row.add("_raw_json".equals(column) ? document.toJson() : document.get(column));
            }
            rows.add(row);
        }
        return new MongoTabularResult(new ArrayList<>(columns), rows);
    }

    public MongoTabularResult mapSingleDocument(Document document) {
        List<Document> list = new ArrayList<>();
        list.add(document == null ? new Document() : document);
        return mapDocuments(list);
    }

    /**
     * Map MongoDB write command acknowledgement (insert/update/delete) without {@code _raw_json}.
     */
    public MongoTabularResult mapWriteResult(Document document) {
        Document payload = document == null ? new Document() : document;
        List<String> columns = new ArrayList<>(payload.keySet());
        List<Object> row = new ArrayList<>();
        for (String column : columns) {
            row.add(formatWriteValue(payload.get(column)));
        }
        List<List<Object>> rows = new ArrayList<>();
        rows.add(row);
        return new MongoTabularResult(columns, rows);
    }

    private Object formatWriteValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof ObjectId) {
            return "ObjectId(\"" + value + "\")";
        }
        if (value instanceof Document) {
            return ((Document) value).toJson();
        }
        return value;
    }
}
