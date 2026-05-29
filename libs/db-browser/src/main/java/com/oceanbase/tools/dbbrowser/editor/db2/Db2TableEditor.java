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

import java.util.Objects;

import javax.validation.constraints.NotNull;

import com.oceanbase.tools.dbbrowser.editor.DBObjectEditor;
import com.oceanbase.tools.dbbrowser.editor.DBTableEditor;
import com.oceanbase.tools.dbbrowser.model.DBTable;
import com.oceanbase.tools.dbbrowser.model.DBTableColumn;
import com.oceanbase.tools.dbbrowser.model.DBTableConstraint;
import com.oceanbase.tools.dbbrowser.model.DBTableIndex;
import com.oceanbase.tools.dbbrowser.model.DBTablePartition;
import com.oceanbase.tools.dbbrowser.util.Db2SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.StringUtils;

import lombok.NonNull;

/**
 * DB2 LUW table editor (fix_report_20260529_100416 Bug-2, Issue dms-ee#839).
 *
 * <p>
 * Implements the {@link DBTableEditor} contract using DB2 LUW grammar:
 *
 * <pre>
 * {@code
 * CREATE TABLE "S"."T" (
 *     "ID"   BIGINT NOT NULL,
 *     "NAME" VARCHAR(100),
 *     PRIMARY KEY ("ID")
 * );
 * COMMENT ON TABLE "S"."T" IS '...';
 * COMMENT ON COLUMN "S"."T"."ID" IS '...';
 *
 * -- CREATE INDEX runs as a separate statement (createIndexWhenCreatingTable() = false)
 * CREATE INDEX "S"."idx_t_name" ON "S"."T" ("NAME");
 * }
 * </pre>
 *
 * <p>
 * The parent class' {@link DBTableEditor#generateUpdateObjectDDL} dispatch fans out to the column /
 * index / constraint editors via {@code generateUpdateObjectListDDL}, so wiring this editor to
 * {@link Db2ColumnEditor}, {@link Db2IndexEditor}, {@link Db2ConstraintEditor} is sufficient — we
 * only override the small set of dialect-specific hooks (rename / comment / table-option emission).
 *
 * @since ODC_release_4.3.4 (Issue dms-ee#839, fix_report_20260529_100416)
 */
public class Db2TableEditor extends DBTableEditor {

    public Db2TableEditor(DBObjectEditor<DBTableIndex> indexEditor,
            DBObjectEditor<DBTableColumn> columnEditor,
            DBObjectEditor<DBTableConstraint> constraintEditor,
            DBObjectEditor<DBTablePartition> partitionEditor) {
        super(indexEditor, columnEditor, constraintEditor, partitionEditor);
    }

    @Override
    protected SqlBuilder sqlBuilder() {
        return new Db2SqlBuilder();
    }

    /**
     * DB2 cannot embed CREATE INDEX inside CREATE TABLE; indexes are emitted as separate statements
     * after table creation. The parent CREATE TABLE pipeline already handles this when this returns
     * false.
     */
    @Override
    protected boolean createIndexWhenCreatingTable() {
        return false;
    }

    @Override
    protected void appendColumnComment(DBTable table, SqlBuilder sqlBuilder) {
        if (Objects.isNull(table.getColumns())) {
            return;
        }
        for (DBTableColumn column : table.getColumns()) {
            column.setSchemaName(table.getSchemaName());
            column.setTableName(table.getName());
            if (columnEditor instanceof Db2ColumnEditor) {
                // Reuse the column editor's COMMENT ON COLUMN emission (package-private accessor).
                String fragment = ((Db2ColumnEditor) columnEditor).generateUpdateObjectDDL(
                        emptyColumn(column), column);
                // generateUpdateObjectDDL emits ALTER TABLE first when names differ — for a freshly
                // CREATEd table the column already has its real name so the rename branch is skipped.
                // Strip everything but COMMENT ON COLUMN lines.
                for (String line : fragment.split("\\r?\\n")) {
                    if (line.startsWith("COMMENT ON COLUMN")) {
                        sqlBuilder.append(line).line();
                    }
                }
            }
        }
    }

    @Override
    protected void appendTableComment(DBTable table, SqlBuilder sqlBuilder) {
        if (Objects.isNull(table.getTableOptions())
                || StringUtils.isBlank(table.getTableOptions().getComment())) {
            return;
        }
        sqlBuilder.append("COMMENT ON TABLE ").append(getFullyQualifiedTableName(table))
                .append(" IS ").value(table.getTableOptions().getComment()).append(";\n");
    }

    @Override
    protected void appendTableOptions(DBTable table, SqlBuilder sqlBuilder) {
        // DB2 has no MySQL-style table options block (CHARSET / COLLATE / ENGINE). Comments are
        // applied via the COMMENT ON TABLE statement appended afterwards by appendTableComment().
    }

    @Override
    public void generateUpdateTableOptionDDL(@NonNull DBTable oldTable, @NonNull DBTable newTable,
            @NonNull SqlBuilder sqlBuilder) {
        if (Objects.isNull(oldTable.getTableOptions()) || Objects.isNull(newTable.getTableOptions())) {
            return;
        }
        String oldComment = oldTable.getTableOptions().getComment();
        String newComment = newTable.getTableOptions().getComment();
        if (!Objects.equals(oldComment, newComment)) {
            sqlBuilder.append("COMMENT ON TABLE ").append(getFullyQualifiedTableName(newTable))
                    .append(" IS ").value(StringUtils.isBlank(newComment) ? "" : newComment).append(";\n");
        }
    }

    @Override
    public String generateRenameObjectDDL(@NotNull DBTable oldTable, @NotNull DBTable newTable) {
        // DB2 supports RENAME TABLE within the same schema. Cross-schema rename requires
        // ADMIN_MOVE_TABLE which is out of scope for the workbench editor.
        SqlBuilder sqlBuilder = sqlBuilder();
        sqlBuilder.append("RENAME TABLE ").append(getFullyQualifiedTableName(oldTable))
                .append(" TO ").identifier(newTable.getName());
        return sqlBuilder.toString();
    }

    /**
     * Build a column with the same identity (schema / table / name) but no other attributes — used as
     * the "old" sentinel when reusing {@link Db2ColumnEditor#generateUpdateObjectDDL} purely to obtain
     * the COMMENT ON COLUMN fragment for a freshly added column. Returning a stripped clone avoids
     * mutating the caller's instance.
     */
    private static DBTableColumn emptyColumn(DBTableColumn template) {
        DBTableColumn empty = new DBTableColumn();
        empty.setSchemaName(template.getSchemaName());
        empty.setTableName(template.getTableName());
        empty.setName(template.getName());
        return empty;
    }
}
