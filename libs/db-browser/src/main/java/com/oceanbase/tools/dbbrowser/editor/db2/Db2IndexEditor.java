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

import java.util.List;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import com.oceanbase.tools.dbbrowser.editor.DBTableIndexEditor;
import com.oceanbase.tools.dbbrowser.model.DBIndexType;
import com.oceanbase.tools.dbbrowser.model.DBTableIndex;
import com.oceanbase.tools.dbbrowser.util.Db2SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.StringUtils;

/**
 * DB2 LUW index editor (fix_report_20260529_100416 Bug-2, Issue dms-ee#839).
 *
 * <p>
 * DB2 indexes live in a schema rather than under their table:
 * {@code CREATE [UNIQUE] INDEX "schema"."idx" ON "schema"."table" (col1, col2);}
 * {@code DROP INDEX "schema"."idx";} — we therefore override {@link #generateCreateObjectDDL} and
 * {@link #generateDropObjectDDL} instead of inheriting the MySQL
 * {@code ALTER TABLE ... ADD / DROP INDEX} form which DB2 rejects.
 *
 * @since ODC_release_4.3.4 (Issue dms-ee#839, fix_report_20260529_100416)
 */
public class Db2IndexEditor extends DBTableIndexEditor {

    @Override
    protected SqlBuilder sqlBuilder() {
        return new Db2SqlBuilder();
    }

    @Override
    public boolean editable() {
        return true;
    }

    @Override
    public String generateCreateObjectDDL(@NotNull DBTableIndex index) {
        SqlBuilder sqlBuilder = sqlBuilder();
        sqlBuilder.append("CREATE ");
        if (index.getType() == DBIndexType.UNIQUE) {
            sqlBuilder.append("UNIQUE ");
        }
        sqlBuilder.append("INDEX ").append(getFullyQualifiedIndexName(index))
                .append(" ON ").append(getFullyQualifiedTableName(index))
                .append(" (");
        List<String> quotedColumns = index.getColumnNames().stream()
                .map(StringUtils::quoteOracleIdentifier)
                .collect(Collectors.toList());
        sqlBuilder.append(String.join(", ", quotedColumns));
        sqlBuilder.append(");\n");
        return sqlBuilder.toString();
    }

    @Override
    public String generateDropObjectDDL(@NotNull DBTableIndex index) {
        SqlBuilder sqlBuilder = sqlBuilder();
        sqlBuilder.append("DROP INDEX ").append(getFullyQualifiedIndexName(index)).append(";\n");
        return sqlBuilder.toString();
    }

    /**
     * DB2 has no {@code ALTER INDEX RENAME} for plain indexes — recreate the index.
     */
    @Override
    public String generateRenameObjectDDL(@NotNull DBTableIndex oldIndex, @NotNull DBTableIndex newIndex) {
        SqlBuilder sqlBuilder = sqlBuilder();
        sqlBuilder.append(generateDropObjectDDL(oldIndex));
        sqlBuilder.append(generateCreateObjectDDL(newIndex));
        return sqlBuilder.toString();
    }

    @Override
    protected void appendIndexColumnModifiers(DBTableIndex index, SqlBuilder sqlBuilder) {
        // DB2 does not allow per-column modifiers (no MySQL-style index length) in CREATE INDEX.
    }

    @Override
    protected void appendIndexOptions(DBTableIndex index, SqlBuilder sqlBuilder) {
        // DB2 index options (CLUSTER / INCLUDE / PCTFREE) are out of scope for the workbench editor.
    }

    /**
     * DB2 index objects live in a schema (just like tables). Build the {@code "schema"."index"}
     * qualifier so DROP INDEX / CREATE INDEX address the correct namespace.
     */
    private String getFullyQualifiedIndexName(@NotNull DBTableIndex index) {
        SqlBuilder sqlBuilder = sqlBuilder();
        if (StringUtils.isNotEmpty(index.getSchemaName())) {
            sqlBuilder.identifier(index.getSchemaName()).append(".");
        }
        sqlBuilder.identifier(index.getName());
        return sqlBuilder.toString();
    }
}
