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
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bson.Document;
import org.bson.conversions.Bson;

public class MongoCommandParser {
    private static final Pattern COMMAND =
            Pattern.compile("^db(?:\\.([a-zA-Z0-9_\\-]+))?\\.([a-zA-Z]+)\\((.*)\\)\\s*;?$", Pattern.DOTALL);
    private static final Pattern KEEP_ALIVE =
            Pattern.compile("^select\\s+1(?:\\s+from\\s+dual)?\\s*;?$", Pattern.CASE_INSENSITIVE);

    public MongoParsedCommand parse(String sql) {
        String normalized = normalize(sql);
        if (KEEP_ALIVE.matcher(normalized).matches()) {
            return MongoParsedCommand.runCommand(new Document("ping", 1));
        }
        Matcher matcher = COMMAND.matcher(normalized);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unsupported MongoDB command");
        }
        String collection = matcher.group(1);
        String method = matcher.group(2);
        String args = matcher.group(3) == null ? "" : matcher.group(3).trim();
        if ("runCommand".equals(method) || "adminCommand".equals(method)) {
            return MongoParsedCommand.runCommand(parseDocument(args));
        }
        if (collection == null) {
            throw new IllegalArgumentException("Collection name is required for MongoDB command");
        }
        switch (method) {
            case "find":
                return MongoParsedCommand.find(collection, args.isEmpty() ? new Document() : parseDocument(args));
            case "aggregate":
                return MongoParsedCommand.aggregate(collection, parsePipeline(args));
            case "insertOne":
                return MongoParsedCommand.insertOne(collection, parseDocument(args));
            case "insertMany":
                return MongoParsedCommand.insertMany(collection, parseDocumentList(args));
            case "updateOne":
            case "updateMany":
                return MongoParsedCommand.update(collection, method, splitTopLevelArgs(args));
            case "deleteOne":
            case "deleteMany":
                return MongoParsedCommand.delete(collection, method,
                        args.isEmpty() ? new Document() : parseDocument(args));
            default:
                throw new IllegalArgumentException("Unsupported MongoDB command: " + method);
        }
    }

    private Document parseDocument(String raw) {
        String json = toJson(raw.trim());
        return Document.parse(json);
    }

    private List<Bson> parsePipeline(String raw) {
        String json = toJson(raw.trim());
        List<Object> values = Document.parse("{\"pipeline\":" + json + "}").getList("pipeline", Object.class);
        List<Bson> pipeline = new ArrayList<>();
        for (Object value : values) {
            pipeline.add(asDocument(value));
        }
        return pipeline;
    }

    private List<Document> parseDocumentList(String raw) {
        String json = toJson(raw.trim());
        List<Object> values = Document.parse("{\"items\":" + json + "}").getList("items", Object.class);
        List<Document> documents = new ArrayList<>();
        for (Object value : values) {
            documents.add(asDocument(value));
        }
        return documents;
    }

    private Document asDocument(Object value) {
        if (value == null) {
            return new Document();
        }
        if (value instanceof Document) {
            return (Document) value;
        }
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            return new Document(map);
        }
        return Document.parse(String.valueOf(value));
    }

    private List<String> splitTopLevelArgs(String args) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < args.length(); i++) {
            char current = args.charAt(i);
            if (current == '{' || current == '[' || current == '(') {
                depth++;
            } else if (current == '}' || current == ']' || current == ')') {
                depth--;
            } else if (current == ',' && depth == 0) {
                result.add(args.substring(start, i).trim());
                start = i + 1;
            }
        }
        result.add(args.substring(start).trim());
        return result;
    }

    private String toJson(String value) {
        String json = value.replace('\'', '"');
        json = json.replaceAll("([\\{,]\\s*)([A-Za-z_\\$][A-Za-z0-9_\\$]*)\\s*:", "$1\"$2\":");
        return json;
    }

    private String normalize(String sql) {
        String normalized = sql == null ? "" : sql.trim();
        while (normalized.startsWith("/*")) {
            int end = normalized.indexOf("*/");
            if (end < 0) {
                break;
            }
            normalized = normalized.substring(end + 2).trim();
        }
        while (normalized.startsWith("--") || normalized.startsWith("#")) {
            int end = normalized.indexOf('\n');
            if (end < 0) {
                return "";
            }
            normalized = normalized.substring(end + 1).trim();
        }
        while (normalized.endsWith("*/")) {
            int begin = normalized.lastIndexOf("/*");
            if (begin < 0) {
                break;
            }
            normalized = normalized.substring(0, begin).trim();
        }
        while (normalized.contains("\n")) {
            int lineComment = Math.max(normalized.lastIndexOf("--"), normalized.lastIndexOf("#"));
            if (lineComment < 0) {
                break;
            }
            int nextLine = normalized.indexOf('\n', lineComment);
            if (nextLine >= 0 && nextLine == normalized.length() - 1) {
                normalized = normalized.substring(0, lineComment).trim();
                continue;
            }
            break;
        }
        return normalized;
    }
}
