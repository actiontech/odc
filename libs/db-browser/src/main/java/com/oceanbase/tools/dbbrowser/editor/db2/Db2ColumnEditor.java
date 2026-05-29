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

import javax.validation.constraints.NotNull;

import com.oceanbase.tools.dbbrowser.editor.DBTableColumnEditor;
import com.oceanbase.tools.dbbrowser.model.DBTableColumn;
import com.oceanbase.tools.dbbrowser.util.Db2SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.StringUtils;

/**
 * DB2 LUW column editor (fix_report_20260529_100416 Bug-2, Issue dms-ee#839).
 *
 * <p>
 * Generates DDL fragments that satisfy DB2's ALTER TABLE column-action grammar:
 *
 * <ul>
 * <li>ADD: {@code ALTER TABLE "S"."T" ADD COLUMN "C" VARCHAR(50) NOT NULL DEFAULT '';}</li>
 * <li>ALTER type: {@code ALTER TABLE "S"."T" ALTER COLUMN "C" SET DATA TYPE VARCHAR(100);}</li>
 * <li>ALTER default: {@code ALTER TABLE "S"."T" ALTER COLUMN "C" SET DEFAULT 'v';} /
 * {@code ... DROP DEFAULT;}</li>
 * <li>ALTER nullability: {@code ALTER TABLE "S"."T" ALTER COLUMN "C" SET NOT NULL;} /
 * {@code ... DROP NOT NULL;}</li>
 * <li>DROP: {@code ALTER TABLE "S"."T" DROP COLUMN "C";}</li>
 * <li>RENAME (DB2 11.1+): {@code ALTER TABLE "S"."T" RENAME COLUMN "OLD" TO "NEW";}</li>
 * </ul>
 *
 * <p>
 * Compared to MySQL {@code ALTER TABLE ... MODIFY COLUMN col type ...} (which restates the whole
 * column definition) DB2 requires per-attribute sub-actions. We override
 * {@link #generateUpdateObjectDDL} to emit one statement per changed attribute instead of falling
 * through to the parent class' MODIFY-style aggregation, which DB2 rejects with SQLCODE=-104.
 *
 * @since ODC_release_4.3.4 (Issue dms-ee#839, fix_report_20260529_100416)
 */
public class Db2ColumnEditor extends DBTableColumnEditor {

    @Override
    protected SqlBuilder sqlBuilder() {
        return new Db2SqlBuilder();
    }

    /**
     * DB2 ALTER TABLE ADD requires the COLUMN keyword for clarity (the standalone
     * {@code ADD <column-def>} form is parsed as a table-level constraint candidate first).
     */
    @Override
    protected boolean appendColumnKeyWord() {
        return true;
    }

    @Override
    protected List<DBColumnModifier> getSupportColumnModifiers() {
        return Arrays.asList(
                new Db2DataTypeModifier(),
                new Db2NullNotNullModifier(),
                new Db2DefaultModifier());
    }

    @Override
    public String generateRenameObjectDDL(@NotNull DBTableColumn oldColumn,
            @NotNull DBTableColumn newColumn) {
        SqlBuilder sqlBuilder = sqlBuilder();
        sqlBuilder.append("ALTER TABLE ").append(getFullyQualifiedTableName(oldColumn))
                .append(" RENAME COLUMN ").identifier(oldColumn.getName()).append(" TO ")
                .identifier(newColumn.getName()).append(";\n");
        return sqlBuilder.toString();
    }

    @Override
    public String generateDropObjectDDL(@NotNull DBTableColumn column) {
        SqlBuilder sqlBuilder = sqlBuilder();
        sqlBuilder.append("ALTER TABLE ").append(getFullyQualifiedTableName(column))
                .append(" DROP COLUMN ").identifier(column.getName()).append(";\n");
        return sqlBuilder.toString();
    }

    /**
     * Override the parent's "ALTER TABLE ... MODIFY <full column def>" path because DB2 only accepts
     * per-attribute sub-actions under {@code ALTER COLUMN}. Emit one statement per changed attribute:
     * SET DATA TYPE / SET (DROP) DEFAULT / SET NOT NULL / DROP NOT NULL. Comment changes are handled
     * via {@link #generateColumnComment(DBTableColumn, SqlBuilder)} just like the SQL Server / Oracle
     * editors do.
     */
    @Override
    public String generateUpdateObjectDDL(@NotNull DBTableColumn oldColumn,
            @NotNull DBTableColumn newColumn) {
        SqlBuilder sqlBuilder = sqlBuilder();

        // 1. RENAME COLUMN (DB2 11.1+). The rename runs first so subsequent ALTER statements can
        // refer to the new column name without ambiguity.
        if (!StringUtils.equals(oldColumn.getName(), newColumn.getName())) {
            sqlBuilder.append(generateRenameObjectDDL(oldColumn, newColumn));
        }

        // 2. Data-type change → SET DATA TYPE.
        if (!isDataTypeEqual(oldColumn, newColumn)) {
            sqlBuilder.append("ALTER TABLE ").append(getFullyQualifiedTableName(newColumn))
                    .append(" ALTER COLUMN ").identifier(newColumn.getName())
                    .append(" SET DATA TYPE");
            new Db2DataTypeModifier().appendModifier(newColumn, sqlBuilder);
            sqlBuilder.append(";\n");
        }

        // 3. Nullability change → SET NOT NULL / DROP NOT NULL.
        if (!Objects.equals(oldColumn.getNullable(), newColumn.getNullable())) {
            if (Boolean.FALSE.equals(newColumn.getNullable())) {
                sqlBuilder.append("ALTER TABLE ").append(getFullyQualifiedTableName(newColumn))
                        .append(" ALTER COLUMN ").identifier(newColumn.getName())
                        .append(" SET NOT NULL;\n");
            } else {
                sqlBuilder.append("ALTER TABLE ").append(getFullyQualifiedTableName(newColumn))
                        .append(" ALTER COLUMN ").identifier(newColumn.getName())
                        .append(" DROP NOT NULL;\n");
            }
        }

        // 4. Default value change → SET DEFAULT / DROP DEFAULT.
        if (!StringUtils.equals(oldColumn.getDefaultValue(), newColumn.getDefaultValue())) {
            if (StringUtils.isNotBlank(newColumn.getDefaultValue())) {
                sqlBuilder.append("ALTER TABLE ").append(getFullyQualifiedTableName(newColumn))
                        .append(" ALTER COLUMN ").identifier(newColumn.getName())
                        .append(" SET DEFAULT ").append(newColumn.getDefaultValue()).append(";\n");
            } else {
                sqlBuilder.append("ALTER TABLE ").append(getFullyQualifiedTableName(newColumn))
                        .append(" ALTER COLUMN ").identifier(newColumn.getName())
                        .append(" DROP DEFAULT;\n");
            }
        }

        // 5. Comment change → COMMENT ON COLUMN.
        if (!Objects.equals(oldColumn.getComment(), newColumn.getComment())) {
            generateColumnComment(newColumn, sqlBuilder);
        }

        return sqlBuilder.toString();
    }

    @Override
    protected void generateColumnComment(DBTableColumn column, SqlBuilder sqlBuilder) {
        if (StringUtils.isBlank(column.getComment())) {
            return;
        }
        // DB2 LUW uses COMMENT ON COLUMN schema.table.column IS '...';
        sqlBuilder.append("COMMENT ON COLUMN ").append(getFullyQualifiedTableName(column))
                .append(".").identifier(column.getName())
                .append(" IS ").value(column.getComment()).append(";\n");
    }

    private boolean isDataTypeEqual(DBTableColumn oldColumn, DBTableColumn newColumn) {
        if (!StringUtils.equalsIgnoreCase(oldColumn.getTypeName(), newColumn.getTypeName())) {
            return false;
        }
        if (!Objects.equals(oldColumn.getPrecision(), newColumn.getPrecision())) {
            return false;
        }
        if (!Objects.equals(oldColumn.getScale(), newColumn.getScale())) {
            return false;
        }
        return true;
    }

    /**
     * DB2 data-type fragment. Precision/scale are emitted only for types that accept them
     * (VARCHAR/CHAR/DECIMAL/...). Integer types such as SMALLINT/INTEGER/BIGINT have no length so we
     * skip them even if precision is non-null in the {@link DBTableColumn} model.
     */
    protected static class Db2DataTypeModifier implements DBColumnModifier {

        @Override
        public void appendModifier(DBTableColumn column, SqlBuilder sqlBuilder) {
            String typeName = column.getTypeName();
            Long precision = column.getPrecision();
            Integer scale = column.getScale();
            sqlBuilder.space().append(typeName);
            if (!supportsPrecision(typeName)) {
                return;
            }
            if (Objects.nonNull(scale)) {
                if (Objects.nonNull(precision)) {
                    sqlBuilder.append("(").append(String.valueOf(precision))
                            .append(",").append(String.valueOf(scale)).append(")");
                } else {
                    sqlBuilder.append("(").append(String.valueOf(scale)).append(")");
                }
            } else if (Objects.nonNull(precision)) {
                sqlBuilder.append("(").append(String.valueOf(precision)).append(")");
            }
        }

        private boolean supportsPrecision(String typeName) {
            if (StringUtils.isBlank(typeName)) {
                return false;
            }
            String upper = typeName.toUpperCase();
            return upper.equals("VARCHAR") || upper.equals("CHAR") || upper.equals("CHARACTER")
                    || upper.equals("VARGRAPHIC") || upper.equals("GRAPHIC")
                    || upper.equals("DECIMAL") || upper.equals("NUMERIC")
                    || upper.equals("BLOB") || upper.equals("CLOB") || upper.equals("DBCLOB")
                    || upper.equals("BINARY") || upper.equals("VARBINARY")
                    || upper.equals("TIMESTAMP") || upper.equals("DECFLOAT");
        }
    }

    /**
     * DB2 nullability is emitted as a column attribute during CREATE / ADD COLUMN. SET / DROP NOT NULL
     * is handled separately during ALTER.
     */
    protected static class Db2NullNotNullModifier implements DBColumnModifier {
        @Override
        public void appendModifier(DBTableColumn column, SqlBuilder sqlBuilder) {
            if (column.getNullable() == null) {
                return;
            }
            sqlBuilder.append(column.getNullable() ? "" : " NOT NULL");
        }
    }

    /**
     * DB2 DEFAULT expression. Verbatim emission — same approach as
     * {@link Db2SqlBuilder#defaultValue(String)}.
     */
    protected static class Db2DefaultModifier implements DBColumnModifier {
        @Override
        public void appendModifier(DBTableColumn column, SqlBuilder sqlBuilder) {
            String defaultValue = column.getDefaultValue();
            if (StringUtils.isNotBlank(defaultValue)) {
                sqlBuilder.append(" DEFAULT ").append(defaultValue);
            }
        }
    }
}
