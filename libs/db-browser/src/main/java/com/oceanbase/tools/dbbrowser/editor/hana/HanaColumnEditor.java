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
package com.oceanbase.tools.dbbrowser.editor.hana;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javax.validation.constraints.NotNull;

import com.oceanbase.tools.dbbrowser.editor.DBTableColumnEditor;
import com.oceanbase.tools.dbbrowser.model.DBTableColumn;
import com.oceanbase.tools.dbbrowser.util.HanaSqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.StringUtils;

/**
 * Column editor for SAP HANA database.
 * <p>
 * HANA column DDL differences from other databases:
 * <ul>
 * <li>ADD column:
 * {@code ALTER TABLE "schema"."table" ADD ("col" TYPE [NOT NULL] [DEFAULT ...])}</li>
 * <li>ALTER column:
 * {@code ALTER TABLE "schema"."table" ALTER ("col" TYPE [NOT NULL] [DEFAULT ...])}</li>
 * <li>DROP column: {@code ALTER TABLE "schema"."table" DROP ("col")}</li>
 * <li>RENAME column: {@code RENAME COLUMN "schema"."table"."old_col" TO "new_col"}</li>
 * <li>Column comment: {@code COMMENT ON COLUMN "schema"."table"."col" IS 'comment'}</li>
 * </ul>
 * Note: HANA requires parentheses around column definitions in ALTER TABLE statements.
 *
 * @since ODC_release_4.3.4
 */
public class HanaColumnEditor extends DBTableColumnEditor {

    @Override
    protected SqlBuilder sqlBuilder() {
        return new HanaSqlBuilder();
    }

    @Override
    protected boolean appendColumnKeyWord() {
        return false;
    }

    @Override
    protected List<DBColumnModifier> getSupportColumnModifiers() {
        return Arrays.asList(new HanaDataTypeModifier(),
                new HanaNullNotNullModifier(),
                new HanaDefaultValueModifier());
    }

    /**
     * Generate ADD column DDL for HANA. Format: ALTER TABLE "schema"."table" ADD ("col" TYPE [NOT NULL]
     * [DEFAULT ...])
     */
    @Override
    public String generateCreateObjectDDL(@NotNull DBTableColumn column) {
        SqlBuilder sqlBuilder = sqlBuilder();
        sqlBuilder.append("ALTER TABLE ").append(getFullyQualifiedTableName(column))
                .append(" ADD (");
        appendColumnDefinition(column, sqlBuilder);
        sqlBuilder.append(");\n");
        generateColumnComment(column, sqlBuilder);
        return sqlBuilder.toString();
    }

    /**
     * Generate RENAME COLUMN DDL for HANA. Format: RENAME COLUMN "schema"."table"."old_col" TO
     * "new_col"
     */
    @Override
    public String generateRenameObjectDDL(@NotNull DBTableColumn oldColumn,
            @NotNull DBTableColumn newColumn) {
        SqlBuilder sqlBuilder = sqlBuilder();
        sqlBuilder.append("RENAME COLUMN ")
                .append(getFullyQualifiedTableName(oldColumn))
                .append(".").identifier(oldColumn.getName())
                .append(" TO ").identifier(newColumn.getName());
        return sqlBuilder.toString();
    }

    /**
     * Generate UPDATE column DDL for HANA. Handles: rename, type/nullable change, comment change,
     * default value change. Format: ALTER TABLE "schema"."table" ALTER ("col" TYPE [NOT NULL] [DEFAULT
     * ...])
     */
    @Override
    public String generateUpdateObjectDDL(@NotNull DBTableColumn oldColumn,
            @NotNull DBTableColumn newColumn) {
        SqlBuilder sqlBuilder = sqlBuilder();

        // Step 1: Handle column rename
        if (!StringUtils.equals(oldColumn.getName(), newColumn.getName())) {
            sqlBuilder.append(generateRenameObjectDDL(oldColumn, newColumn)).append(";\n");
        }

        // Step 2: Handle column definition change (type, precision, nullable, default)
        if (!isColumnDefinitionEqual(oldColumn, newColumn)) {
            sqlBuilder.append("ALTER TABLE ").append(getFullyQualifiedTableName(oldColumn))
                    .append(" ALTER (").identifier(newColumn.getName());
            // Append data type
            new HanaDataTypeModifier().appendModifier(newColumn, sqlBuilder);
            // Append nullable
            new HanaNullNotNullModifier().appendModifier(newColumn, sqlBuilder);
            // Append default value if changed
            if (!Objects.equals(oldColumn.getDefaultValue(), newColumn.getDefaultValue())) {
                new HanaDefaultValueModifier().appendModifier(newColumn, sqlBuilder);
            }
            sqlBuilder.append(");\n");
        } else if (!Objects.equals(oldColumn.getDefaultValue(), newColumn.getDefaultValue())) {
            // Only default value changed, still need ALTER
            sqlBuilder.append("ALTER TABLE ").append(getFullyQualifiedTableName(oldColumn))
                    .append(" ALTER (").identifier(newColumn.getName());
            new HanaDataTypeModifier().appendModifier(newColumn, sqlBuilder);
            new HanaNullNotNullModifier().appendModifier(newColumn, sqlBuilder);
            new HanaDefaultValueModifier().appendModifier(newColumn, sqlBuilder);
            sqlBuilder.append(");\n");
        }

        // Step 3: Handle comment change
        if (!Objects.equals(oldColumn.getComment(), newColumn.getComment())) {
            generateColumnComment(newColumn, sqlBuilder);
        }

        return sqlBuilder.toString();
    }

    /**
     * Generate DROP column DDL for HANA. Format: ALTER TABLE "schema"."table" DROP ("col")
     */
    @Override
    public String generateDropObjectDDL(@NotNull DBTableColumn column) {
        SqlBuilder sqlBuilder = sqlBuilder();
        sqlBuilder.append("ALTER TABLE ").append(getFullyQualifiedTableName(column))
                .append(" DROP (").identifier(column.getName()).append(");\n");
        return sqlBuilder.toString();
    }

    /**
     * Generate COMMENT ON COLUMN for HANA. Format: COMMENT ON COLUMN "schema"."table"."col" IS
     * 'comment'
     */
    @Override
    protected void generateColumnComment(DBTableColumn column, SqlBuilder sqlBuilder) {
        if (StringUtils.isBlank(column.getComment())) {
            return;
        }
        sqlBuilder.append("COMMENT ON COLUMN ")
                .append(getFullyQualifiedTableName(column))
                .append(".").identifier(column.getName())
                .append(" IS ").value(column.getComment()).append(";\n");
    }

    @Override
    protected String getFullyQualifiedTableName(@NotNull DBTableColumn column) {
        SqlBuilder sqlBuilder = sqlBuilder();
        if (StringUtils.isNotEmpty(column.getSchemaName())) {
            sqlBuilder.identifier(column.getSchemaName()).append(".");
        }
        if (StringUtils.isNotEmpty(column.getTableName())) {
            sqlBuilder.identifier(column.getTableName());
        }
        return sqlBuilder.toString();
    }

    private boolean isColumnDefinitionEqual(DBTableColumn oldColumn, DBTableColumn newColumn) {
        if (!Objects.equals(oldColumn.getTypeName(), newColumn.getTypeName())) {
            return false;
        }
        if (!Objects.equals(oldColumn.getNullable(), newColumn.getNullable())) {
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
     * HANA data type modifier. Formats type with precision and scale. Examples: NVARCHAR(100),
     * DECIMAL(10,2), INTEGER
     */
    protected static class HanaDataTypeModifier implements DBColumnModifier {
        @Override
        public void appendModifier(DBTableColumn column, SqlBuilder sqlBuilder) {
            String typeName = column.getTypeName();
            Long precision = column.getPrecision();
            Integer scale = column.getScale();
            sqlBuilder.space().append(typeName);
            if (Objects.isNull(scale)) {
                if (Objects.nonNull(precision)) {
                    sqlBuilder.append("(").append(String.valueOf(precision)).append(")");
                }
            } else {
                if (Objects.isNull(precision)) {
                    sqlBuilder.append("(").append(String.valueOf(scale)).append(")");
                } else {
                    sqlBuilder.append("(").append(String.valueOf(precision))
                            .append(",").append(String.valueOf(scale)).append(")");
                }
            }
        }
    }

    /**
     * HANA NULL/NOT NULL modifier.
     */
    protected static class HanaNullNotNullModifier implements DBColumnModifier {
        @Override
        public void appendModifier(DBTableColumn column, SqlBuilder sqlBuilder) {
            sqlBuilder.append(column.getNullable() ? " NULL" : " NOT NULL");
        }
    }

    /**
     * HANA DEFAULT value modifier.
     */
    protected static class HanaDefaultValueModifier implements DBColumnModifier {
        @Override
        public void appendModifier(DBTableColumn column, SqlBuilder sqlBuilder) {
            String defaultValue = column.getDefaultValue();
            if (StringUtils.isNotEmpty(defaultValue)) {
                sqlBuilder.append(" DEFAULT ").append(defaultValue);
            }
        }
    }
}
