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

import java.util.Objects;

import javax.validation.constraints.NotNull;

import com.oceanbase.tools.dbbrowser.editor.DBObjectEditor;
import com.oceanbase.tools.dbbrowser.editor.DBTableEditor;
import com.oceanbase.tools.dbbrowser.model.DBTable;
import com.oceanbase.tools.dbbrowser.model.DBTableColumn;
import com.oceanbase.tools.dbbrowser.model.DBTableConstraint;
import com.oceanbase.tools.dbbrowser.model.DBTableIndex;
import com.oceanbase.tools.dbbrowser.model.DBTablePartition;
import com.oceanbase.tools.dbbrowser.util.HanaSqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.StringUtils;

/**
 * Table editor for SAP HANA database.
 * <p>
 * HANA table DDL syntax:
 * <ul>
 * <li>CREATE: {@code CREATE COLUMN TABLE "schema"."table" (col_definitions...)}</li>
 * <li>RENAME: {@code RENAME TABLE "schema"."old_name" TO "schema"."new_name"}</li>
 * <li>DROP: {@code DROP TABLE "schema"."table"}</li>
 * <li>Comment: {@code COMMENT ON TABLE "schema"."table" IS 'comment'}</li>
 * </ul>
 * This editor integrates HanaColumnEditor, HanaIndexEditor, and HanaConstraintEditor
 * to generate complete table DDL statements.
 * <p>
 * Important: {@code generateUpdateObjectDDL} is overridden to directly control the
 * change processing flow, avoiding the parent class's default traversal logic which
 * may produce incompatible SQL for HANA.
 *
 * @since ODC_release_4.3.4
 */
public class HanaTableEditor extends DBTableEditor {

    public HanaTableEditor(DBObjectEditor<DBTableIndex> indexEditor,
            DBObjectEditor<DBTableColumn> columnEditor,
            DBObjectEditor<DBTableConstraint> constraintEditor,
            DBObjectEditor<DBTablePartition> partitionEditor) {
        super(indexEditor, columnEditor, constraintEditor, partitionEditor);
    }

    @Override
    protected SqlBuilder sqlBuilder() {
        return new HanaSqlBuilder();
    }

    @Override
    protected String getFullyQualifiedTableName(@NotNull DBTable table) {
        // HANA two-level structure: "schema"."table"
        SqlBuilder sb = sqlBuilder();
        sb.schemaPrefixIfNotBlank(table.getSchemaName());
        sb.identifier(table.getName());
        return sb.toString();
    }

    @Override
    protected void appendTableOptions(DBTable table, SqlBuilder sqlBuilder) {
        // HANA uses CREATE COLUMN TABLE by default; no extra table options needed
    }

    @Override
    protected boolean createIndexWhenCreatingTable() {
        // HANA indexes are created separately, not inline in CREATE TABLE
        return false;
    }

    /**
     * Generate RENAME TABLE DDL for HANA.
     * Format: RENAME TABLE "schema"."old_name" TO "schema"."new_name"
     */
    @Override
    public String generateRenameObjectDDL(@NotNull DBTable oldTable, @NotNull DBTable newTable) {
        SqlBuilder sb = sqlBuilder();
        sb.append("RENAME TABLE ");
        sb.schemaPrefixIfNotBlank(oldTable.getSchemaName());
        sb.identifier(oldTable.getName());
        sb.append(" TO ");
        sb.schemaPrefixIfNotBlank(newTable.getSchemaName());
        sb.identifier(newTable.getName());
        return sb.toString();
    }

    /**
     * Generate COMMENT ON COLUMN for each column with a comment.
     * Format: COMMENT ON COLUMN "schema"."table"."column" IS 'comment'
     */
    @Override
    protected void appendColumnComment(DBTable table, SqlBuilder sqlBuilder) {
        if (Objects.isNull(table.getColumns())) {
            return;
        }
        for (DBTableColumn column : table.getColumns()) {
            if (StringUtils.isNotBlank(column.getComment())) {
                sqlBuilder.append("COMMENT ON COLUMN ")
                        .identifier(table.getSchemaName())
                        .append(".").identifier(table.getName())
                        .append(".").identifier(column.getName())
                        .append(" IS ").value(column.getComment()).append(";\n");
            }
        }
    }

    /**
     * Generate COMMENT ON TABLE for HANA.
     * Format: COMMENT ON TABLE "schema"."table" IS 'comment'
     */
    @Override
    protected void appendTableComment(DBTable table, SqlBuilder sqlBuilder) {
        if (Objects.isNull(table.getTableOptions())
                || StringUtils.isBlank(table.getTableOptions().getComment())) {
            return;
        }
        sqlBuilder.append("COMMENT ON TABLE ");
        sqlBuilder.schemaPrefixIfNotBlank(table.getSchemaName());
        sqlBuilder.identifier(table.getName());
        sqlBuilder.append(" IS ").value(table.getTableOptions().getComment()).append(";\n");
    }

    /**
     * Handle table option changes (currently only table comment).
     */
    @Override
    public void generateUpdateTableOptionDDL(DBTable oldTable, DBTable newTable, SqlBuilder sqlBuilder) {
        String oldComment =
                Objects.nonNull(oldTable.getTableOptions()) ? oldTable.getTableOptions().getComment() : null;
        String newComment =
                Objects.nonNull(newTable.getTableOptions()) ? newTable.getTableOptions().getComment() : null;
        if (!Objects.equals(oldComment, newComment)) {
            appendTableComment(newTable, sqlBuilder);
        }
    }

    /**
     * Override generateUpdateObjectDDL to directly control the change processing flow.
     * This avoids the parent class's default traversal logic which may produce incompatible
     * SQL for HANA (e.g., partition operations and column group operations that HANA doesn't support).
     */
    @Override
    public String generateUpdateObjectDDL(@NotNull DBTable oldTable, @NotNull DBTable newTable) {
        SqlBuilder sqlBuilder = sqlBuilder();

        // Step 1: Handle table rename
        if (!StringUtils.equals(oldTable.getName(), newTable.getName())) {
            sqlBuilder.append(generateRenameObjectDDL(oldTable, newTable));
            sqlBuilder.append(";\n");
        }

        // Step 2: Handle table options (comment) change
        generateUpdateTableOptionDDL(oldTable, newTable, sqlBuilder);

        // Step 3: Fill schema and table name into sub-objects
        fillSchemaAndTableName(oldTable);
        fillSchemaAndTableName(newTable);

        // Step 4: Handle column changes via HanaColumnEditor
        sqlBuilder.append(columnEditor.generateUpdateObjectListDDL(
                oldTable.getColumns(), newTable.getColumns()));

        // Step 5: Handle index changes via HanaIndexEditor
        sqlBuilder.append(indexEditor.generateUpdateObjectListDDL(
                oldTable.getIndexes(), newTable.getIndexes()));

        // Step 6: Handle constraint changes via HanaConstraintEditor
        sqlBuilder.append(constraintEditor.generateUpdateObjectListDDL(
                oldTable.getConstraints(), newTable.getConstraints()));

        // Note: HANA does not support partition editing or column groups,
        // so we skip partitionEditor and columnGroup handling

        return sqlBuilder.toString();
    }

    /**
     * Fill schema name and table name into all sub-objects (columns, indexes, constraints).
     */
    private void fillSchemaAndTableName(DBTable table) {
        if (table.getColumns() != null) {
            table.getColumns().forEach(col -> {
                col.setSchemaName(table.getSchemaName());
                col.setTableName(table.getName());
            });
        }
        if (table.getIndexes() != null) {
            table.getIndexes().forEach(idx -> {
                idx.setSchemaName(table.getSchemaName());
                idx.setTableName(table.getName());
            });
        }
        if (table.getConstraints() != null) {
            table.getConstraints().forEach(cst -> {
                cst.setSchemaName(table.getSchemaName());
                cst.setTableName(table.getName());
            });
        }
    }
}
