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
package com.oceanbase.tools.dbbrowser.schema.hive;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.oceanbase.tools.dbbrowser.model.DBTableColumn;

/**
 * Utility class for Apache Hive schema operations.
 * <p>
 * Provides SQL command constants used by {@link HiveSchemaAccessor} and a state-machine parser for
 * the output of {@code DESCRIBE FORMATTED 
 * <table>
 * }.
 * </p>
 * <p>
 * Target Hive version: 4.2.0. The parser uses defensive programming (trim, null-safe) to tolerate
 * minor format variations across Hive versions.
 * </p>
 *
 * @since ODC_release_4.3.4
 */
public class HiveSchemaUtil {

    // =================== SQL constants ===================

    public static final String SHOW_DATABASES = "SHOW DATABASES";
    public static final String SHOW_TABLES_IN = "SHOW TABLES IN ";
    public static final String SHOW_VIEWS_IN = "SHOW VIEWS IN ";
    public static final String DESCRIBE = "DESCRIBE ";
    public static final String DESCRIBE_FORMATTED = "DESCRIBE FORMATTED ";
    public static final String SHOW_CREATE_TABLE = "SHOW CREATE TABLE ";
    public static final String SHOW_CREATE_VIEW = "SHOW CREATE VIEW ";
    public static final String SHOW_PARTITIONS = "SHOW PARTITIONS ";

    // =================== DESCRIBE FORMATTED parser ===================

    /**
     * States of the DESCRIBE FORMATTED output parser.
     */
    private enum ParseState {
        /** Regular column definitions (first section) */
        COLUMNS,
        /** Partition column definitions (after "# Partition Information") */
        PARTITION_INFO,
        /** Detailed table information key-value pairs */
        TABLE_INFO,
        /** Table Parameters sub-section (indented key-value pairs under TABLE_INFO) */
        TABLE_PARAMETERS,
        /** Storage Information key-value pairs */
        STORAGE_INFO,
        /** Storage Desc Params sub-section (indented key-value pairs under STORAGE_INFO) */
        STORAGE_DESC_PARAMS
    }

    /**
     * Structured result of parsing {@code DESCRIBE FORMATTED} output.
     */
    public static class HiveTableMetadata {
        private final List<DBTableColumn> columns = new ArrayList<>();
        private final List<DBTableColumn> partitionColumns = new ArrayList<>();
        private final Map<String, String> tableProperties = new LinkedHashMap<>();
        private final Map<String, String> tableParameters = new LinkedHashMap<>();
        private final Map<String, String> storageProperties = new LinkedHashMap<>();
        private final Map<String, String> storageDescParams = new LinkedHashMap<>();

        public List<DBTableColumn> getColumns() {
            return columns;
        }

        public List<DBTableColumn> getPartitionColumns() {
            return partitionColumns;
        }

        public Map<String, String> getTableProperties() {
            return tableProperties;
        }

        public Map<String, String> getTableParameters() {
            return tableParameters;
        }

        public Map<String, String> getStorageProperties() {
            return storageProperties;
        }

        public Map<String, String> getStorageDescParams() {
            return storageDescParams;
        }
    }

    /**
     * Represents a single row from the {@code DESCRIBE FORMATTED} ResultSet. Each row has three
     * columns: {@code col_name}, {@code data_type}, and {@code comment}.
     */
    public static class DescribeRow {
        private final String colName;
        private final String dataType;
        private final String comment;

        public DescribeRow(String colName, String dataType, String comment) {
            this.colName = colName;
            this.dataType = dataType;
            this.comment = comment;
        }

        public String getColName() {
            return colName;
        }

        public String getDataType() {
            return dataType;
        }

        public String getComment() {
            return comment;
        }
    }

    /**
     * Parses a list of rows from {@code DESCRIBE FORMATTED 
     * <table>
     * } output into structured {@link HiveTableMetadata}.
     * <p>
     * The parser is a state machine that transitions between sections based on header markers:
     * <ul>
     * <li>Initial state: COLUMNS</li>
     * <li>{@code # Partition Information} &rarr; PARTITION_INFO</li>
     * <li>{@code # Detailed Table Information} &rarr; TABLE_INFO</li>
     * <li>{@code Table Parameters:} within TABLE_INFO &rarr; TABLE_PARAMETERS</li>
     * <li>{@code # Storage Information} &rarr; STORAGE_INFO</li>
     * <li>{@code Storage Desc Params:} within STORAGE_INFO &rarr; STORAGE_DESC_PARAMS</li>
     * </ul>
     *
     * @param rows the rows from DESCRIBE FORMATTED output
     * @return parsed metadata
     */
    public static HiveTableMetadata parseDescribeFormatted(List<DescribeRow> rows) {
        HiveTableMetadata metadata = new HiveTableMetadata();
        ParseState state = ParseState.COLUMNS;
        int ordinalPosition = 1;
        int partitionOrdinalPosition = 1;

        for (int i = 0; i < rows.size(); i++) {
            DescribeRow row = rows.get(i);
            String colName = row.getColName() != null ? row.getColName() : "";
            String dataType = row.getDataType();
            String comment = row.getComment();

            String trimmedColName = colName.trim();

            // Skip empty rows (both col_name and data_type are blank)
            if (trimmedColName.isEmpty() && isBlank(dataType)) {
                continue;
            }

            // Detect section headers and transition state
            if (trimmedColName.startsWith("# Partition Information")) {
                state = ParseState.PARTITION_INFO;
                continue;
            }
            if (trimmedColName.startsWith("# Detailed Table Information")) {
                state = ParseState.TABLE_INFO;
                continue;
            }
            if (trimmedColName.startsWith("# Storage Information")) {
                state = ParseState.STORAGE_INFO;
                continue;
            }
            // Skip other comment/header lines (e.g. "# col_name", "# data_type", "# comment")
            if (trimmedColName.startsWith("#")) {
                continue;
            }

            switch (state) {
                case COLUMNS: {
                    DBTableColumn column = buildColumn(trimmedColName, dataType, comment,
                            ordinalPosition++);
                    metadata.getColumns().add(column);
                    break;
                }
                case PARTITION_INFO: {
                    DBTableColumn column = buildColumn(trimmedColName, dataType, comment,
                            partitionOrdinalPosition++);
                    metadata.getPartitionColumns().add(column);
                    break;
                }
                case TABLE_INFO: {
                    if (trimmedColName.endsWith(":")) {
                        String key = trimmedColName.substring(0, trimmedColName.length() - 1).trim();
                        if ("Table Parameters".equals(key)) {
                            state = ParseState.TABLE_PARAMETERS;
                        } else {
                            metadata.getTableProperties().put(key,
                                    dataType != null ? dataType.trim() : null);
                        }
                    } else {
                        metadata.getTableProperties().put(trimmedColName,
                                dataType != null ? dataType.trim() : null);
                    }
                    break;
                }
                case TABLE_PARAMETERS: {
                    // Table Parameters rows are indented (start with spaces in col_name)
                    if (colName.startsWith("  ") || colName.startsWith("\t")) {
                        metadata.getTableParameters().put(trimmedColName,
                                dataType != null ? dataType.trim() : null);
                    } else {
                        // No longer indented -- fall back to TABLE_INFO and re-process this row
                        state = ParseState.TABLE_INFO;
                        i--; // re-process current row in TABLE_INFO state
                    }
                    break;
                }
                case STORAGE_INFO: {
                    if (trimmedColName.endsWith(":")) {
                        String key = trimmedColName.substring(0, trimmedColName.length() - 1).trim();
                        if ("Storage Desc Params".equals(key)) {
                            state = ParseState.STORAGE_DESC_PARAMS;
                        } else {
                            metadata.getStorageProperties().put(key,
                                    dataType != null ? dataType.trim() : null);
                        }
                    } else {
                        metadata.getStorageProperties().put(trimmedColName,
                                dataType != null ? dataType.trim() : null);
                    }
                    break;
                }
                case STORAGE_DESC_PARAMS: {
                    metadata.getStorageDescParams().put(trimmedColName,
                            dataType != null ? dataType.trim() : null);
                    break;
                }
                default:
                    break;
            }
        }

        return metadata;
    }

    /**
     * Builds a {@link DBTableColumn} from DESCRIBE output fields.
     */
    private static DBTableColumn buildColumn(String name, String dataType, String comment,
            int ordinalPosition) {
        DBTableColumn column = new DBTableColumn();
        column.setName(name);
        if (dataType != null) {
            String trimmedType = dataType.trim();
            column.setTypeName(trimmedType);
            column.setFullTypeName(trimmedType);
        }
        if (comment != null && !comment.trim().isEmpty()) {
            column.setComment(comment.trim());
        }
        column.setOrdinalPosition(ordinalPosition);
        return column;
    }

    /**
     * Null-safe blank check (avoids dependency on external StringUtils for simple check).
     */
    private static boolean isBlank(String s) {
        if (s == null) {
            return true;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
