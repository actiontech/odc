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

import java.util.List;

import org.bson.Document;
import org.bson.conversions.Bson;

import lombok.Getter;

@Getter
public class MongoParsedCommand {
    public enum Type {
        FIND,
        AGGREGATE,
        INSERT_ONE,
        INSERT_MANY,
        UPDATE_ONE,
        UPDATE_MANY,
        DELETE_ONE,
        DELETE_MANY,
        RUN_COMMAND
    }

    private final Type type;
    private final String collection;
    private final Document document;
    private final List<Document> documents;
    private final List<Bson> pipeline;
    private final List<String> rawArgs;

    private MongoParsedCommand(Type type, String collection, Document document, List<Document> documents,
            List<Bson> pipeline, List<String> rawArgs) {
        this.type = type;
        this.collection = collection;
        this.document = document;
        this.documents = documents;
        this.pipeline = pipeline;
        this.rawArgs = rawArgs;
    }

    public static MongoParsedCommand find(String collection, Document filter) {
        return new MongoParsedCommand(Type.FIND, collection, filter, null, null, null);
    }

    public static MongoParsedCommand aggregate(String collection, List<Bson> pipeline) {
        return new MongoParsedCommand(Type.AGGREGATE, collection, null, null, pipeline, null);
    }

    public static MongoParsedCommand insertOne(String collection, Document document) {
        return new MongoParsedCommand(Type.INSERT_ONE, collection, document, null, null, null);
    }

    public static MongoParsedCommand insertMany(String collection, List<Document> documents) {
        return new MongoParsedCommand(Type.INSERT_MANY, collection, null, documents, null, null);
    }

    public static MongoParsedCommand update(String collection, String method, List<String> args) {
        return new MongoParsedCommand("updateMany".equals(method) ? Type.UPDATE_MANY : Type.UPDATE_ONE,
                collection, null, null, null, args);
    }

    public static MongoParsedCommand delete(String collection, String method, Document filter) {
        return new MongoParsedCommand("deleteMany".equals(method) ? Type.DELETE_MANY : Type.DELETE_ONE,
                collection, filter, null, null, null);
    }

    public static MongoParsedCommand runCommand(Document document) {
        return new MongoParsedCommand(Type.RUN_COMMAND, null, document, null, null, null);
    }
}
