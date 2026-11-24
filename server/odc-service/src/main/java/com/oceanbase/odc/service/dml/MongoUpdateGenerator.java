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
package com.oceanbase.odc.service.dml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oceanbase.odc.common.util.StringUtils;
import com.oceanbase.odc.core.shared.exception.BadRequestException;
import com.oceanbase.odc.service.dml.model.DataModifyUnit;

import lombok.NonNull;

/**
 * Generate MongoDB shell {@code updateOne}+{@code $set} commands for result-set cell edits.
 * Whole-document / {@code _raw_json} → {@code replaceOne} is abolished (BE-S1-04/05). Unchanged
 * {@code _raw_json} may be supplied as a type hint source (AC-HF-010).
 */
public class MongoUpdateGenerator {

    private static final String RAW_JSON_COLUMN = "_raw_json";
    private static final String ID_COLUMN = "_id";
    private static final Pattern OBJECT_ID_LITERAL =
            Pattern.compile("^ObjectId\\(\\s*\"([0-9a-fA-F]{24})\"\\s*\\)$");
    private static final Pattern OBJECT_ID_HEX = Pattern.compile("^[0-9a-fA-F]{24}$");
    private static final Pattern ISO_DATE_LITERAL =
            Pattern.compile("^ISODate\\(\\s*\"([^\"]+)\"\\s*\\)$");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private MongoUpdateGenerator() {}

    /**
     * @return generated command text, or {@code null} when the row has no effective change
     */
    public static String generate(@NonNull String collectionName, @NonNull List<DataModifyUnit> units) {
        if (StringUtils.isBlank(collectionName)) {
            throw new BadRequestException("集合名不能为空");
        }
        String idRaw = null;
        Map<String, DataModifyUnit> fieldChanges = new LinkedHashMap<>();
        JsonNode rawDoc = null;

        for (DataModifyUnit unit : units) {
            if (unit == null || StringUtils.isBlank(unit.getColumnName())) {
                continue;
            }
            String columnName = unit.getColumnName();
            if (RAW_JSON_COLUMN.equals(columnName)) {
                if (hasChanged(unit)) {
                    // Scenario B: reject changed _raw_json; never emit replaceOne / whole-doc writeback.
                    throw new BadRequestException("_raw_json 列只读，不支持通过结果集编辑整文档写回");
                }
                rawDoc = parseRawJsonHint(unit.getOldData() != null ? unit.getOldData() : unit.getNewData());
                continue;
            }
            if (ID_COLUMN.equals(columnName)) {
                idRaw = unit.getOldData();
                continue;
            }
            if (!hasChanged(unit)) {
                continue;
            }
            fieldChanges.put(columnName, unit);
        }
        if (fieldChanges.isEmpty()) {
            return null;
        }
        String idLiteral = toIdLiteral(idRaw, rawDoc == null ? null : rawDoc.get(ID_COLUMN));
        if (StringUtils.isBlank(idLiteral)) {
            throw new BadRequestException("缺少 _id，无法定位要修改的文档");
        }
        return generateUpdateOne(formatCollectionRef(collectionName), idLiteral, fieldChanges, rawDoc);
    }

    private static String generateUpdateOne(String collectionRef, String idLiteral,
            Map<String, DataModifyUnit> fieldChanges, JsonNode rawDoc) {
        List<String> setEntries = new ArrayList<>();
        for (Map.Entry<String, DataModifyUnit> entry : fieldChanges.entrySet()) {
            String fieldName = entry.getKey();
            DataModifyUnit unit = entry.getValue();
            validateJsonIfNeeded(fieldName, unit.getNewData());
            JsonNode typeHint = rawDoc == null ? null : rawDoc.get(fieldName);
            setEntries.add(quoteKey(fieldName) + ": " + toBsonLiteral(unit.getNewData(), typeHint));
        }
        if (setEntries.isEmpty()) {
            return null;
        }
        // Trailing ';' is required so console SqlCommentProcessor can split multi-row scripts
        // (MySQL-style delimiter). Do not append "commit;".
        return "db." + collectionRef + ".updateOne({ _id: " + idLiteral + " }, { $set: { "
                + String.join(", ", setEntries) + " } });";
    }

    private static boolean hasChanged(DataModifyUnit unit) {
        return !StringUtils.equals(unit.getOldData(), unit.getNewData());
    }

    private static JsonNode parseRawJsonHint(String rawJson) {
        if (StringUtils.isBlank(rawJson)) {
            return null;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(rawJson.trim());
            return node != null && node.isObject() ? node : null;
        } catch (Exception ex) {
            // Type hint is best-effort; invalid unchanged raw must not block field edits.
            return null;
        }
    }

    private static void validateJsonIfNeeded(String fieldName, String newData) {
        if (newData == null) {
            return;
        }
        String trimmed = newData.trim();
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
            return;
        }
        try {
            OBJECT_MAPPER.readTree(trimmed);
        } catch (Exception ex) {
            throw new BadRequestException("字段 " + fieldName + " 的值不是合法 JSON");
        }
    }

    /**
     * Filter {@code _id} literal. Prefer type from unchanged {@code _raw_json} (AC-HF-010). Without
     * hint: only explicit {@code ObjectId("…")} / Extended JSON {@code $oid} become ObjectId; bare
     * 24-hex stays a string (AC-HF-004).
     */
    static String toIdLiteral(String rawId) {
        return toIdLiteral(rawId, null);
    }

    static String toIdLiteral(String rawId, JsonNode idHint) {
        if (idHint != null && !idHint.isNull()) {
            String fromHint = literalFromJsonNode(idHint);
            if (fromHint != null) {
                return fromHint;
            }
        }
        if (rawId == null) {
            return null;
        }
        String trimmed = rawId.trim();
        Matcher objectIdMatcher = OBJECT_ID_LITERAL.matcher(trimmed);
        if (objectIdMatcher.matches()) {
            return "ObjectId(\"" + objectIdMatcher.group(1) + "\")";
        }
        if (trimmed.startsWith("{") && trimmed.contains("$oid")) {
            try {
                JsonNode node = OBJECT_MAPPER.readTree(trimmed);
                JsonNode oid = node.get("$oid");
                if (oid != null && OBJECT_ID_HEX.matcher(oid.asText()).matches()) {
                    return "ObjectId(\"" + oid.asText() + "\")";
                }
            } catch (Exception ignore) {
                // fall through
            }
        }
        if (looksLikeNumber(trimmed)) {
            return trimmed;
        }
        return quoteString(trimmed);
    }

    /**
     * {@code $set} value literal. Without type hint: AC-HF-004 string default (no guess-upcast). With
     * hint from unchanged {@code _raw_json}: preserve original BSON/JSON type (AC-HF-010).
     */
    static String toBsonLiteral(String raw) {
        return toBsonLiteral(raw, null);
    }

    static String toBsonLiteral(String raw, JsonNode typeHint) {
        if (raw == null) {
            return "null";
        }
        if (typeHint != null && !typeHint.isNull()) {
            String typed = literalWithTypeHint(raw, typeHint);
            if (typed != null) {
                return typed;
            }
        }
        String trimmed = raw.trim();
        Matcher objectIdMatcher = OBJECT_ID_LITERAL.matcher(trimmed);
        if (objectIdMatcher.matches()) {
            return "ObjectId(\"" + objectIdMatcher.group(1) + "\")";
        }
        Matcher isoDateMatcher = ISO_DATE_LITERAL.matcher(trimmed);
        if (isoDateMatcher.matches()) {
            return "ISODate(\"" + isoDateMatcher.group(1) + "\")";
        }
        if ((trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            try {
                JsonNode node = OBJECT_MAPPER.readTree(trimmed);
                return OBJECT_MAPPER.writeValueAsString(node);
            } catch (Exception ex) {
                throw new BadRequestException("非法 JSON 字面量");
            }
        }
        return quoteString(raw);
    }

    /**
     * Emit shell literal matching the original JSON node type, using {@code raw} as the new value.
     * Extended JSON {@code $date}/{@code $oid} must be handled before {@code isObject()} so that user
     * shell literals {@code ISODate("…")}/{@code ObjectId("…")} are not rejected as illegal JSON
     * (AC-HF-014).
     */
    private static String literalWithTypeHint(String raw, JsonNode typeHint) {
        String trimmed = raw == null ? null : raw.trim();
        if (typeHint.isTextual()) {
            return quoteString(raw);
        }
        if (typeHint.isBoolean()) {
            if ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) {
                return trimmed.toLowerCase();
            }
            // edited value no longer boolean-shaped → keep string (safer than silent coercion)
            return quoteString(raw);
        }
        if (typeHint.isNumber()) {
            if (looksLikeNumber(trimmed)) {
                return trimmed;
            }
            return quoteString(raw);
        }
        // AC-HF-014: $date / $oid Extended JSON hints accept shell literals before object JSON parse
        if (typeHint.isObject()) {
            JsonNode dateHint = typeHint.get("$date");
            if (dateHint != null) {
                if (trimmed == null) {
                    return "null";
                }
                Matcher isoDateMatcher = ISO_DATE_LITERAL.matcher(trimmed);
                if (isoDateMatcher.matches()) {
                    return "ISODate(\"" + isoDateMatcher.group(1) + "\")";
                }
                if (dateHint.isTextual() && dateHint.asText().equals(trimmed)) {
                    return "ISODate(\"" + escapeJsonStringContent(trimmed) + "\")";
                }
                try {
                    JsonNode node = OBJECT_MAPPER.readTree(trimmed);
                    JsonNode nestedDate = node.get("$date");
                    if (nestedDate != null && nestedDate.isTextual()) {
                        return "ISODate(\"" + nestedDate.asText() + "\")";
                    }
                } catch (Exception ignore) {
                    // fall through to illegal JSON below when object/array path also fails
                }
                throw new BadRequestException("非法 JSON 字面量");
            }
            JsonNode oidHint = typeHint.get("$oid");
            if (oidHint != null) {
                if (trimmed == null) {
                    return "null";
                }
                Matcher objectIdMatcher = OBJECT_ID_LITERAL.matcher(trimmed);
                if (objectIdMatcher.matches()) {
                    return "ObjectId(\"" + objectIdMatcher.group(1) + "\")";
                }
                if (OBJECT_ID_HEX.matcher(trimmed).matches()) {
                    return "ObjectId(\"" + trimmed + "\")";
                }
                try {
                    JsonNode node = OBJECT_MAPPER.readTree(trimmed);
                    JsonNode nestedOid = node.get("$oid");
                    if (nestedOid != null && OBJECT_ID_HEX.matcher(nestedOid.asText()).matches()) {
                        return "ObjectId(\"" + nestedOid.asText() + "\")";
                    }
                } catch (Exception ignore) {
                    // fall through
                }
                throw new BadRequestException("非法 JSON 字面量");
            }
        }
        if (typeHint.isObject() || typeHint.isArray()) {
            try {
                JsonNode node = OBJECT_MAPPER.readTree(trimmed);
                return OBJECT_MAPPER.writeValueAsString(node);
            } catch (Exception ex) {
                throw new BadRequestException("非法 JSON 字面量");
            }
        }
        return null;
    }

    /** Render a JSON node from {@code _raw_json} as a Mongo shell literal (for {@code _id}). */
    private static String literalFromJsonNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return quoteString(node.asText());
        }
        if (node.isNumber()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return node.asBoolean() ? "true" : "false";
        }
        if (node.isObject()) {
            JsonNode oid = node.get("$oid");
            if (oid != null && oid.isTextual() && OBJECT_ID_HEX.matcher(oid.asText()).matches()) {
                return "ObjectId(\"" + oid.asText() + "\")";
            }
            JsonNode date = node.get("$date");
            if (date != null && date.isTextual()) {
                return "ISODate(\"" + date.asText() + "\")";
            }
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(node);
        } catch (Exception ex) {
            return null;
        }
    }

    private static boolean looksLikeNumber(String value) {
        if (StringUtils.isBlank(value)) {
            return false;
        }
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static String quoteKey(String key) {
        return "\"" + escapeJsonStringContent(key) + "\"";
    }

    private static String quoteString(String value) {
        return "\"" + escapeJsonStringContent(value) + "\"";
    }

    /** JSON-style escapes so newlines do not truncate the generated {@code updateOne} line. */
    private static String escapeJsonStringContent(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String formatCollectionRef(String collectionName) {
        if (collectionName.matches("^[A-Za-z_$][\\w$]*$")) {
            return collectionName;
        }
        return "getCollection(" + quoteString(collectionName) + ")";
    }
}
