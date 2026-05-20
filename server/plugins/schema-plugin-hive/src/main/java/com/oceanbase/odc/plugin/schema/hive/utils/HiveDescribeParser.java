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
package com.oceanbase.odc.plugin.schema.hive.utils;

import java.util.ArrayList;
import java.util.List;

import com.oceanbase.tools.dbbrowser.model.DBTableColumn;

/**
 * Parses the row-oriented text returned by Hive's {@code DESCRIBE [FORMATTED|EXTENDED] table}
 * command. The JDBC driver returns three columns:
 *
 * <pre>
 *   col_name        data_type        comment
 * </pre>
 *
 * <p>
 * The output is divided into 3 logical sections separated by blank rows or section header rows such
 * as {@code # Partition Information} / {@code # Detailed Table Information}:
 * <ol>
 * <li><b>Regular columns</b> (top, before any "#" section header)</li>
 * <li><b>Partition columns</b> (after {@code # Partition Information})</li>
 * <li><b>Detailed metadata</b> (after {@code # Detailed Table Information} / {@code # Storage
 *       Information}) – key/value pairs we surface as table comment + table type.</li>
 * </ol>
 *
 * <p>
 * This parser intentionally takes a list of {@link Row} so it can be unit-tested without a JDBC
 * dependency.
 *
 * @since ODC_release_4.3.4
 */
public final class HiveDescribeParser {

    /** Marker row whose {@code colName} begins with this prefix is a Hive section header. */
    private static final String SECTION_PREFIX = "#";

    /** Header introducing partition column rows. */
    private static final String PARTITION_HEADER = "# Partition Information";

    /** Header introducing detailed table information rows. */
    private static final String DETAILED_HEADER = "# Detailed Table Information";

    private HiveDescribeParser() {}

    /**
     * Parse the rows of {@code DESCRIBE FORMATTED} into a structured result. Rows that are blank or are
     * section headers serve only to switch sections – they never produce columns.
     */
    public static ParsedDescribe parse(List<Row> rows) {
        ParsedDescribe result = new ParsedDescribe();
        if (rows == null || rows.isEmpty()) {
            return result;
        }
        Section section = Section.COLUMNS;
        for (Row r : rows) {
            if (r == null) {
                continue;
            }
            String col = r.colName == null ? "" : r.colName.trim();
            if (col.isEmpty()) {
                continue;
            }
            if (col.startsWith(SECTION_PREFIX)) {
                section = classify(col);
                continue;
            }
            switch (section) {
                case COLUMNS:
                    result.columns.add(toColumn(r, false));
                    break;
                case PARTITION_COLUMNS:
                    result.partitionColumns.add(toColumn(r, true));
                    break;
                case DETAILED:
                    captureDetailed(r, result);
                    break;
                default:
                    break;
            }
        }
        return result;
    }

    private static Section classify(String header) {
        String h = header.trim();
        if (h.startsWith(PARTITION_HEADER)) {
            return Section.PARTITION_COLUMNS;
        }
        if (h.startsWith(DETAILED_HEADER)) {
            return Section.DETAILED;
        }
        return Section.OTHER;
    }

    private static DBTableColumn toColumn(Row r, boolean partition) {
        DBTableColumn col = new DBTableColumn();
        col.setName(r.colName == null ? null : r.colName.trim());
        HiveTypeMapper.ParsedType pt = HiveTypeMapper.parse(r.dataType);
        col.setTypeName(pt.typeName);
        col.setFullTypeName(pt.fullTypeName);
        col.setPrecision(pt.precision);
        col.setMaxLength(pt.maxLength);
        col.setScale(pt.scale);
        col.setComment(r.comment);
        col.setNullable(Boolean.TRUE);
        if (partition) {
            col.setExtraInfo("PARTITION");
        }
        return col;
    }

    private static void captureDetailed(Row r, ParsedDescribe result) {
        // Detailed-section rows look like "key:" / value / null; we surface the few we care about
        String key = r.colName == null ? "" : r.colName.trim();
        String val = r.dataType == null ? "" : r.dataType.trim();
        if (key.equalsIgnoreCase("Table Type:")) {
            result.tableType = val;
        } else if (key.equalsIgnoreCase("Owner:")) {
            result.owner = val;
        } else if (key.equalsIgnoreCase("comment")) {
            // Detailed Table Information emits comment under "Table Parameters:" sub-block; for
            // simplicity the caller may also fill the comment from a separate query.
            result.comment = val;
        }
    }

    /** Single row of a Hive DESCRIBE result set. */
    public static final class Row {
        public final String colName;
        public final String dataType;
        public final String comment;

        public Row(String colName, String dataType, String comment) {
            this.colName = colName;
            this.dataType = dataType;
            this.comment = comment;
        }
    }

    /** Structured result of a parsed DESCRIBE FORMATTED output. */
    public static final class ParsedDescribe {
        public final List<DBTableColumn> columns = new ArrayList<>();
        public final List<DBTableColumn> partitionColumns = new ArrayList<>();
        public String tableType;
        public String owner;
        public String comment;
    }

    private enum Section {
        COLUMNS, PARTITION_COLUMNS, DETAILED, OTHER
    }
}
