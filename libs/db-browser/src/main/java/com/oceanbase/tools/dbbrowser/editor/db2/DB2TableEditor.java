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
import com.oceanbase.tools.dbbrowser.util.DB2SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.StringUtils;

/**
 * DB2 (LUW 11.5 / 12.x) table editor. MVP scope (see design.md §3.5.2):
 *
 * <ul>
 * <li>CREATE TABLE skeleton (delegates column generation to {@link DB2ColumnEditor}).</li>
 * <li>UPDATE / ALTER TABLE COLUMN flows the table-view / row-edit UI requires (column add / modify
 * / drop / rename via the column editor; table comment update via {@code COMMENT ON
 * TABLE}).</li>
 * <li>RENAME TABLE: {@code RENAME TABLE "schema"."old" TO "new"}.</li>
 * </ul>
 *
 * <p>
 * Out of MVP scope (will surface {@link UnsupportedOperationException} via the corresponding
 * factories which already throw "Not supported for DB2 yet"): index / partition / constraint /
 * stats edition. See {@code AbstractDBBrowserFactoryTest} and compat_risks.md compat-RISK-7.
 */
public class DB2TableEditor extends DBTableEditor {

    public DB2TableEditor(DBObjectEditor<DBTableIndex> indexEditor,
            DBObjectEditor<DBTableColumn> columnEditor,
            DBObjectEditor<DBTableConstraint> constraintEditor,
            DBObjectEditor<DBTablePartition> partitionEditor) {
        super(indexEditor, columnEditor, constraintEditor, partitionEditor);
    }

    /**
     * Override the parent's CREATE TABLE flow to avoid touching the optional index / constraint /
     * partition editors (which the DB2 factory wires as {@code null} in MVP; see design.md §3.5.2).
     * Only the column editor is needed for the table view / row edit flows.
     */
    @Override
    public String generateCreateObjectDDL(@NotNull DBTable table) {
        SqlBuilder sqlBuilder = sqlBuilder();
        sqlBuilder.append("CREATE TABLE ");
        if (StringUtils.isNotBlank(table.getSchemaName())) {
            sqlBuilder.identifier(table.getSchemaName()).append(".");
        }
        sqlBuilder.identifier(table.getName()).append(" (").line();
        boolean isFirst = true;
        if (table.getColumns() != null) {
            for (DBTableColumn column : table.getColumns()) {
                if (!isFirst) {
                    sqlBuilder.append(",").line();
                }
                isFirst = false;
                column.setSchemaName(table.getSchemaName());
                column.setTableName(table.getName());
                sqlBuilder.append(columnEditor.generateCreateDefinitionDDL(column));
            }
        }
        sqlBuilder.line().append(");").line();
        appendTableComment(table, sqlBuilder);
        appendColumnComment(table, sqlBuilder);
        return sqlBuilder.toString();
    }

    @Override
    protected SqlBuilder sqlBuilder() {
        return new DB2SqlBuilder();
    }

    /**
     * Db2 table column comments are emitted by {@link DB2ColumnEditor#generateColumnComment}. During
     * CREATE TABLE we emit them after the table definition by delegating to the column editor (same
     * pattern as SqlServer).
     */
    @Override
    protected void appendColumnComment(DBTable table, SqlBuilder sqlBuilder) {
        if (Objects.isNull(table.getColumns())) {
            return;
        }
        for (DBTableColumn column : table.getColumns()) {
            column.setSchemaName(table.getSchemaName());
            column.setTableName(table.getName());
            if (StringUtils.isBlank(column.getComment())) {
                continue;
            }
            sqlBuilder.append("COMMENT ON COLUMN ");
            if (StringUtils.isNotBlank(table.getSchemaName())) {
                sqlBuilder.identifier(table.getSchemaName()).append(".");
            }
            sqlBuilder.identifier(table.getName()).append(".").identifier(column.getName())
                    .append(" IS ").value(column.getComment()).append(";").line();
        }
    }

    /**
     * DB2 table-level COMMENT ON TABLE syntax:
     *
     * <pre>
     *     COMMENT ON TABLE "schema"."table" IS 'comment';
     * </pre>
     */
    @Override
    protected void appendTableComment(DBTable table, SqlBuilder sqlBuilder) {
        if (Objects.isNull(table.getTableOptions())
                || StringUtils.isBlank(table.getTableOptions().getComment())) {
            return;
        }
        sqlBuilder.append("COMMENT ON TABLE ");
        if (StringUtils.isNotBlank(table.getSchemaName())) {
            sqlBuilder.identifier(table.getSchemaName()).append(".");
        }
        sqlBuilder.identifier(table.getName())
                .append(" IS ").value(table.getTableOptions().getComment()).append(";").line();
    }

    @Override
    protected boolean createIndexWhenCreatingTable() {
        // DB2 indexes must be created as separate CREATE INDEX statements after CREATE TABLE.
        // Index emission itself is delegated to the index editor; MVP factory returns a
        // placeholder so the parent loop is effectively no-op.
        return false;
    }

    @Override
    protected void appendTableOptions(DBTable table, SqlBuilder sqlBuilder) {
        // MVP: no IN TABLESPACE / DISTRIBUTE BY / etc. emitted.
    }

    @Override
    public void generateUpdateTableOptionDDL(DBTable oldTable, DBTable newTable, SqlBuilder sqlBuilder) {
        String oldComment = Objects.nonNull(oldTable.getTableOptions())
                ? oldTable.getTableOptions().getComment()
                : null;
        String newComment = Objects.nonNull(newTable.getTableOptions())
                ? newTable.getTableOptions().getComment()
                : null;
        if (!Objects.equals(oldComment, newComment)) {
            appendTableComment(newTable, sqlBuilder);
        }
    }

    @Override
    public String generateRenameObjectDDL(@NotNull DBTable oldTable, @NotNull DBTable newTable) {
        SqlBuilder sqlBuilder = sqlBuilder();
        sqlBuilder.append("RENAME TABLE ");
        if (StringUtils.isNotBlank(oldTable.getSchemaName())) {
            sqlBuilder.identifier(oldTable.getSchemaName()).append(".");
        }
        sqlBuilder.identifier(oldTable.getName())
                .append(" TO ").identifier(newTable.getName());
        return sqlBuilder.toString();
    }
}
