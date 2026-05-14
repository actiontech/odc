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
package com.oceanbase.tools.dbbrowser.editor.db2;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.oceanbase.tools.dbbrowser.editor.DBTableColumnEditor;
import com.oceanbase.tools.dbbrowser.model.DBTableColumn;
import com.oceanbase.tools.dbbrowser.util.DB2SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.StringUtils;

/**
 * DB2 (LUW 11.5 / 12.x) column editor. Implements only the methods needed by
 * {@link com.oceanbase.tools.dbbrowser.editor.db2.DB2TableEditor} during MVP table view / row
 * editing flows (design.md §3.5.3). Schema-level CREATE / ALTER COLUMN DDL generation is available;
 * advanced DB2 column features (XML types, ROW BEGIN / END TS, IDENTITY, security label) are
 * intentionally not emitted because the workbench MVP does not surface them.
 */
public class DB2ColumnEditor extends DBTableColumnEditor {

    @Override
    protected SqlBuilder sqlBuilder() {
        return new DB2SqlBuilder();
    }

    /**
     * DB2 ALTER TABLE ADD does not need the {@code COLUMN} keyword (it is allowed but optional);
     * keeping it omitted aligns with the IBM Db2 LUW {@code CREATE TABLE} reference syntax and is
     * consistent with how the existing SqlServer editor emits ALTER TABLE.
     */
    @Override
    protected boolean appendColumnKeyWord() {
        return false;
    }

    @Override
    protected List<DBColumnModifier> getSupportColumnModifiers() {
        return Arrays.asList(new DB2DataTypeModifier(),
                new NullNotNullModifier(),
                new DB2DefaultValueModifier());
    }

    /**
     * DB2 COMMENT ON COLUMN syntax:
     *
     * <pre>
     *     COMMENT ON COLUMN "schema"."table"."column" IS 'comment';
     * </pre>
     */
    @Override
    protected void generateColumnComment(DBTableColumn column, SqlBuilder sqlBuilder) {
        if (StringUtils.isBlank(column.getComment())) {
            return;
        }
        sqlBuilder.append("COMMENT ON COLUMN ");
        if (StringUtils.isNotBlank(column.getSchemaName())) {
            sqlBuilder.identifier(column.getSchemaName()).append(".");
        }
        if (StringUtils.isNotBlank(column.getTableName())) {
            sqlBuilder.identifier(column.getTableName()).append(".");
        }
        sqlBuilder.identifier(column.getName())
                .append(" IS ").value(column.getComment()).append(";").line();
    }

    /**
     * DB2 default value modifier. DB2 expects the literal expression as-is in {@code DEFAULT
     * expr}.
     */
    protected static class DB2DefaultValueModifier implements DBColumnModifier {
        @Override
        public void appendModifier(DBTableColumn column, SqlBuilder sqlBuilder) {
            String defaultValue = column.getDefaultValue();
            if (StringUtils.isNotEmpty(defaultValue)) {
                sqlBuilder.append(" DEFAULT ").append(defaultValue);
            }
        }
    }

    /**
     * DB2 data type formatter. DB2 type names come directly from {@code SYSCAT.COLUMNS.TYPENAME}
     * (already upper case, e.g. {@code INTEGER}, {@code VARCHAR}, {@code DECIMAL}, {@code TIMESTAMP},
     * {@code DECFLOAT}). We respect SYSCAT-reported precision / scale:
     *
     * <ul>
     * <li>{@code CHAR / VARCHAR / GRAPHIC / VARGRAPHIC / BLOB / CLOB / DBCLOB}: append
     * {@code (length)}</li>
     * <li>{@code DECIMAL / NUMERIC}: append {@code (precision, scale)} or {@code (precision)} if scale
     * is null/0</li>
     * <li>{@code TIMESTAMP / TIME}: append {@code (precision)} only when present (fractional
     * seconds)</li>
     * <li>{@code INTEGER / BIGINT / SMALLINT / DOUBLE / REAL / DATE / XML / DECFLOAT}: no parens</li>
     * </ul>
     */
    protected static class DB2DataTypeModifier implements DBColumnModifier {
        @Override
        public void appendModifier(DBTableColumn column, SqlBuilder sqlBuilder) {
            String typeName = column.getTypeName();
            if (StringUtils.isBlank(typeName)) {
                return;
            }
            Long precision = column.getPrecision();
            Integer scale = column.getScale();
            sqlBuilder.space().append(typeName);
            String upper = typeName.toUpperCase();
            if (needsLength(upper)) {
                if (precision != null) {
                    sqlBuilder.append("(").append(String.valueOf(precision)).append(")");
                }
            } else if (isDecimalLike(upper)) {
                if (precision != null && scale != null && scale > 0) {
                    sqlBuilder.append("(").append(String.valueOf(precision))
                            .append(",").append(String.valueOf(scale)).append(")");
                } else if (precision != null) {
                    sqlBuilder.append("(").append(String.valueOf(precision)).append(")");
                }
            } else if (isFractionalSecondsType(upper)) {
                if (Objects.nonNull(scale) && scale > 0) {
                    sqlBuilder.append("(").append(String.valueOf(scale)).append(")");
                }
            }
        }

        private static boolean needsLength(String upper) {
            return "CHAR".equals(upper) || "CHARACTER".equals(upper)
                    || "VARCHAR".equals(upper) || "CHARACTER VARYING".equals(upper)
                    || "GRAPHIC".equals(upper) || "VARGRAPHIC".equals(upper)
                    || "BLOB".equals(upper) || "CLOB".equals(upper) || "DBCLOB".equals(upper);
        }

        private static boolean isDecimalLike(String upper) {
            return "DECIMAL".equals(upper) || "NUMERIC".equals(upper) || "DEC".equals(upper);
        }

        private static boolean isFractionalSecondsType(String upper) {
            return "TIMESTAMP".equals(upper) || "TIME".equals(upper);
        }
    }
}
