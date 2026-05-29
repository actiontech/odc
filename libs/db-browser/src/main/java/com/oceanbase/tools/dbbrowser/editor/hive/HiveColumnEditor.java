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
package com.oceanbase.tools.dbbrowser.editor.hive;

import java.util.Arrays;
import java.util.List;

import javax.validation.constraints.NotNull;

import com.oceanbase.tools.dbbrowser.editor.DBTableColumnEditor;
import com.oceanbase.tools.dbbrowser.model.DBTableColumn;
import com.oceanbase.tools.dbbrowser.util.HiveSqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.StringUtils;

/**
 * Column editor for Apache Hive.
 * <p>
 * Hive data types (STRING, BIGINT, ARRAY&lt;STRING&gt;, MAP&lt;STRING,INT&gt;, etc.) do not use
 * precision/scale -- they are output as literal type names.
 * </p>
 * <p>
 * Hive does not support DROP COLUMN; attempting to drop a column will throw
 * {@link UnsupportedOperationException}.
 * </p>
 */
public class HiveColumnEditor extends DBTableColumnEditor {

    @Override
    protected SqlBuilder sqlBuilder() {
        return new HiveSqlBuilder();
    }

    @Override
    protected boolean appendColumnKeyWord() {
        return false;
    }

    @Override
    protected List<DBColumnModifier> getSupportColumnModifiers() {
        return Arrays.asList(new HiveDataTypeModifier(), new HiveCommentModifier());
    }

    /**
     * Generate ADD COLUMNS DDL.
     * <p>
     * Format: {@code ALTER TABLE `db`.`table` ADD COLUMNS (`col` type COMMENT 'comment')}
     * </p>
     * <p>
     * Note: Hive uses {@code ADD COLUMNS} (with 'S') instead of {@code ADD COLUMN}.
     * </p>
     */
    @Override
    public String generateCreateObjectDDL(@NotNull DBTableColumn column) {
        SqlBuilder sqlBuilder = sqlBuilder();
        sqlBuilder.append("ALTER TABLE ").append(getFullyQualifiedTableName(column))
                .append(" ADD COLUMNS (");
        appendColumnDefinition(column, sqlBuilder);
        sqlBuilder.append(");\n");
        return sqlBuilder.toString();
    }

    /**
     * Generate CHANGE COLUMN DDL.
     * <p>
     * Format: {@code ALTER TABLE `db`.`table` CHANGE COLUMN `old_name` `new_name` type COMMENT
     * 'comment'}
     * </p>
     */
    @Override
    public String generateUpdateObjectDDL(@NotNull DBTableColumn oldColumn,
            @NotNull DBTableColumn newColumn) {
        SqlBuilder sqlBuilder = sqlBuilder();
        sqlBuilder.append("ALTER TABLE ").append(getFullyQualifiedTableName(oldColumn))
                .append(" CHANGE COLUMN ").identifier(oldColumn.getName()).append(" ");
        appendColumnDefinition(newColumn, sqlBuilder);
        sqlBuilder.append(";\n");
        return sqlBuilder.toString();
    }

    /**
     * Hive does not support DROP COLUMN.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public String generateDropObjectDDL(@NotNull DBTableColumn column) {
        throw new UnsupportedOperationException("Hive does not support DROP COLUMN");
    }

    @Override
    protected void generateColumnComment(DBTableColumn column, SqlBuilder sqlBuilder) {
        // Hive column comments are inline (handled by HiveCommentModifier), no separate DDL needed
    }

    /**
     * Hive data type modifier: outputs the type name as-is (no precision/scale).
     */
    protected static class HiveDataTypeModifier implements DBColumnModifier {
        @Override
        public void appendModifier(DBTableColumn column, SqlBuilder sqlBuilder) {
            sqlBuilder.space().append(column.getTypeName());
        }
    }

    /**
     * Hive comment modifier: appends {@code COMMENT 'xxx'} if comment is non-blank.
     */
    protected static class HiveCommentModifier implements DBColumnModifier {
        @Override
        public void appendModifier(DBTableColumn column, SqlBuilder sqlBuilder) {
            if (StringUtils.isNotBlank(column.getComment())) {
                sqlBuilder.append(" COMMENT ").value(column.getComment());
            }
        }
    }

}
