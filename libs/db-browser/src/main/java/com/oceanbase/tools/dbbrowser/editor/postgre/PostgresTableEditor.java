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
package com.oceanbase.tools.dbbrowser.editor.postgre;

import java.util.Objects;

import javax.validation.constraints.NotNull;

import com.oceanbase.tools.dbbrowser.editor.DBObjectEditor;
import com.oceanbase.tools.dbbrowser.editor.DBTableEditor;
import com.oceanbase.tools.dbbrowser.model.DBTable;
import com.oceanbase.tools.dbbrowser.model.DBTableColumn;
import com.oceanbase.tools.dbbrowser.model.DBTableConstraint;
import com.oceanbase.tools.dbbrowser.model.DBTableIndex;
import com.oceanbase.tools.dbbrowser.model.DBTablePartition;
import com.oceanbase.tools.dbbrowser.util.PostgresSqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.StringUtils;

/**
 * PostgreSQL 表编辑器
 *
 * <p>
 * PostgreSQL 表 DDL 特点：
 * </p>
 * <ul>
 * <li>表注释：COMMENT ON TABLE "schema"."table" IS 'comment';</li>
 * <li>列注释：COMMENT ON COLUMN "schema"."table"."column" IS 'comment';</li>
 * <li>重命名表：ALTER TABLE "schema"."old_name" RENAME TO "new_name";</li>
 * </ul>
 *
 * @author odc
 * @since ODC_release_4.3.4
 */
public class PostgresTableEditor extends DBTableEditor {

    public PostgresTableEditor(DBObjectEditor<DBTableIndex> indexEditor,
            DBObjectEditor<DBTableColumn> columnEditor,
            DBObjectEditor<DBTableConstraint> constraintEditor,
            DBObjectEditor<DBTablePartition> partitionEditor) {
        super(indexEditor, columnEditor, constraintEditor, partitionEditor);
    }

    @Override
    protected void appendColumnComment(DBTable table, SqlBuilder sqlBuilder) {
        if (Objects.isNull(table.getColumns())) {
            return;
        }
        for (DBTableColumn column : table.getColumns()) {
            column.setSchemaName(table.getSchemaName());
            column.setTableName(table.getName());
            ((PostgresColumnEditor) columnEditor).generateColumnComment(column, sqlBuilder);
        }
    }

    @Override
    protected void appendTableComment(DBTable table, SqlBuilder sqlBuilder) {
        if (Objects.isNull(table.getTableOptions()) || StringUtils.isBlank(table.getTableOptions().getComment())) {
            return;
        }
        sqlBuilder.append("COMMENT ON TABLE ").append(getFullyQualifiedTableName(table))
                .append(" IS ").value(table.getTableOptions().getComment()).append(";\n");
    }

    @Override
    protected boolean createIndexWhenCreatingTable() {
        // PostgreSQL 索引需要在表创建后单独创建
        return false;
    }

    @Override
    protected void appendTableOptions(DBTable table, SqlBuilder sqlBuilder) {
        // PostgreSQL 表选项较少，基本不在此处理
    }

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

    @Override
    public String generateRenameObjectDDL(@NotNull DBTable oldTable, @NotNull DBTable newTable) {
        SqlBuilder sqlBuilder = sqlBuilder();
        sqlBuilder.append("ALTER TABLE ").append(getFullyQualifiedTableName(oldTable))
                .append(" RENAME TO ").identifier(newTable.getName()).append(";");
        return sqlBuilder.toString();
    }

    @Override
    protected SqlBuilder sqlBuilder() {
        return new PostgresSqlBuilder();
    }

}
