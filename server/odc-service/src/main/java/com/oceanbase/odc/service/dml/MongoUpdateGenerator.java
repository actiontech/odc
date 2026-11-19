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
 * Whole-document / {@code _raw_json} → {@code replaceOne} is abolished (BE-S1-04/05).
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
        String idLiteral = null;
        Map<String, DataModifyUnit> fieldChanges = new LinkedHashMap<>();

        for (DataModifyUnit unit : units) {
            if (unit == null || StringUtils.isBlank(unit.getColumnName())) {
                continue;
            }
            String columnName = unit.getColumnName();
            if (ID_COLUMN.equals(columnName)) {
                idLiteral = toIdLiteral(unit.getOldData());
                continue;
            }
            if (!hasChanged(unit)) {
                continue;
            }
            if (RAW_JSON_COLUMN.equals(columnName)) {
                // Scenario B: reject _raw_json units; never emit replaceOne / whole-doc writeback.
                throw new BadRequestException("_raw_json 列只读，不支持通过结果集编辑整文档写回");
            }
            fieldChanges.put(columnName, unit);
        }
        if (fieldChanges.isEmpty()) {
            return null;
        }
        if (StringUtils.isBlank(idLiteral)) {
            throw new BadRequestException("缺少 _id，无法定位要修改的文档");
        }
        return generateUpdateOne(formatCollectionRef(collectionName), idLiteral, fieldChanges);
    }

    private static String generateUpdateOne(String collectionRef, String idLiteral,
            Map<String, DataModifyUnit> fieldChanges) {
        List<String> setEntries = new ArrayList<>();
        for (Map.Entry<String, DataModifyUnit> entry : fieldChanges.entrySet()) {
            String fieldName = entry.getKey();
            DataModifyUnit unit = entry.getValue();
            validateJsonIfNeeded(fieldName, unit.getNewData());
            setEntries.add(quoteKey(fieldName) + ": " + toBsonLiteral(unit.getNewData()));
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

    static String toIdLiteral(String rawId) {
        if (rawId == null) {
            return null;
        }
        String trimmed = rawId.trim();
        Matcher objectIdMatcher = OBJECT_ID_LITERAL.matcher(trimmed);
        if (objectIdMatcher.matches()) {
            return "ObjectId(\"" + objectIdMatcher.group(1) + "\")";
        }
        if (OBJECT_ID_HEX.matcher(trimmed).matches()) {
            return "ObjectId(\"" + trimmed + "\")";
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

    static String toBsonLiteral(String raw) {
        if (raw == null) {
            return "null";
        }
        String trimmed = raw.trim();
        if ("null".equalsIgnoreCase(trimmed)) {
            return "null";
        }
        Matcher objectIdMatcher = OBJECT_ID_LITERAL.matcher(trimmed);
        if (objectIdMatcher.matches()) {
            return "ObjectId(\"" + objectIdMatcher.group(1) + "\")";
        }
        Matcher isoDateMatcher = ISO_DATE_LITERAL.matcher(trimmed);
        if (isoDateMatcher.matches()) {
            return "ISODate(\"" + isoDateMatcher.group(1) + "\")";
        }
        if (looksLikeNumber(trimmed) || "true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) {
            return trimmed;
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
        return quoteString(trimmed);
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
        return "\"" + key.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String quoteString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String formatCollectionRef(String collectionName) {
        if (collectionName.matches("^[A-Za-z_$][\\w$]*$")) {
            return collectionName;
        }
        return "getCollection(" + quoteString(collectionName) + ")";
    }
}
