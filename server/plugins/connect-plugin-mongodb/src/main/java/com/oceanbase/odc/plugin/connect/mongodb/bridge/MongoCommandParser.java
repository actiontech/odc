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

import java.time.Instant;
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
    private static final Pattern NEW_DATE =
            Pattern.compile("new\\s+Date\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern ISO_DATE =
            Pattern.compile("ISODate\\s*\\(\\s*\"([^\"]+)\"\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_NOW =
            Pattern.compile("Date\\.now\\(\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_STRING_ARG =
            Pattern.compile("^\"([^\"]+)\"$");
    private static final Pattern NUMERIC_EXPRESSION =
            Pattern.compile("[0-9+\\-*/().]+");

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
        json = replaceJavaScriptDates(json);
        json = json.replaceAll("([\\{,]\\s*)([A-Za-z_\\$][A-Za-z0-9_\\$]*)\\s*:", "$1\"$2\":");
        return json;
    }

    private String replaceJavaScriptDates(String input) {
        String replacedIsoDate = ISO_DATE.matcher(input).replaceAll("{\"$date\":\"$1\"}");
        StringBuilder result = new StringBuilder();
        int index = 0;
        Matcher matcher = NEW_DATE.matcher(replacedIsoDate);
        while (matcher.find()) {
            result.append(replacedIsoDate, index, matcher.start());
            int openParen = matcher.end() - 1;
            int closeParen = findMatchingParen(replacedIsoDate, openParen);
            String argument = replacedIsoDate.substring(matcher.end(), closeParen).trim();
            result.append(toExtendedJsonDate(argument));
            index = closeParen + 1;
        }
        result.append(replacedIsoDate.substring(index));
        return result.toString();
    }

    private int findMatchingParen(String input, int openParen) {
        int depth = 0;
        for (int i = openParen; i < input.length(); i++) {
            char current = input.charAt(i);
            if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        throw new IllegalArgumentException("Unbalanced parentheses in MongoDB command");
    }

    private String toExtendedJsonDate(String argument) {
        long epochMillis;
        if (argument.isEmpty()) {
            epochMillis = System.currentTimeMillis();
        } else {
            Matcher stringMatcher = DATE_STRING_ARG.matcher(argument);
            if (stringMatcher.matches()) {
                epochMillis = Instant.parse(stringMatcher.group(1)).toEpochMilli();
            } else {
                epochMillis = evaluateMillisExpression(argument);
            }
        }
        return "{\"$date\":\"" + Instant.ofEpochMilli(epochMillis).toString() + "\"}";
    }

    private long evaluateMillisExpression(String expression) {
        String normalized = DATE_NOW.matcher(expression.trim()).replaceAll(Long.toString(System.currentTimeMillis()));
        normalized = normalized.replaceAll("\\s+", "");
        if (!NUMERIC_EXPRESSION.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Unsupported date expression: " + expression);
        }
        return (long) evaluateArithmetic(normalized);
    }

    private double evaluateArithmetic(String expression) {
        return new ArithmeticExpression(expression).evaluate();
    }

    private static final class ArithmeticExpression {
        private final String expression;
        private int index;

        private ArithmeticExpression(String expression) {
            this.expression = expression;
            this.index = 0;
        }

        private double evaluate() {
            double value = parseExpression();
            if (index < expression.length()) {
                throw new IllegalArgumentException("Unexpected token at index " + index);
            }
            return value;
        }

        private double parseExpression() {
            double value = parseTerm();
            while (index < expression.length()) {
                char operator = expression.charAt(index);
                if (operator == '+') {
                    index++;
                    value += parseTerm();
                } else if (operator == '-') {
                    index++;
                    value -= parseTerm();
                } else {
                    break;
                }
            }
            return value;
        }

        private double parseTerm() {
            double value = parseFactor();
            while (index < expression.length()) {
                char operator = expression.charAt(index);
                if (operator == '*') {
                    index++;
                    value *= parseFactor();
                } else if (operator == '/') {
                    index++;
                    value /= parseFactor();
                } else {
                    break;
                }
            }
            return value;
        }

        private double parseFactor() {
            if (expression.charAt(index) == '+') {
                index++;
            } else if (expression.charAt(index) == '-') {
                index++;
                return -parseFactor();
            }
            if (expression.charAt(index) == '(') {
                index++;
                double value = parseExpression();
                if (index >= expression.length() || expression.charAt(index) != ')') {
                    throw new IllegalArgumentException("Missing closing parenthesis");
                }
                index++;
                return value;
            }
            int start = index;
            while (index < expression.length()
                    && (Character.isDigit(expression.charAt(index)) || expression.charAt(index) == '.')) {
                index++;
            }
            if (start == index) {
                throw new IllegalArgumentException("Expected number at index " + index);
            }
            return Double.parseDouble(expression.substring(start, index));
        }
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
