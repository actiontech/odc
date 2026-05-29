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

import java.util.List;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import com.oceanbase.tools.dbbrowser.editor.DBTableIndexEditor;
import com.oceanbase.tools.dbbrowser.model.DBTableIndex;
import com.oceanbase.tools.dbbrowser.util.HanaSqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.StringUtils;

/**
 * Index editor for SAP HANA database.
 * <p>
 * HANA index DDL syntax:
 * <ul>
 * <li>CREATE: {@code CREATE [UNIQUE] INDEX "idx_name" ON "schema"."table" ("col1", "col2")}</li>
 * <li>DROP: {@code DROP INDEX "schema"."idx_name"}</li>
 * </ul>
 * Note: HANA does not support index renaming; a DROP + CREATE is needed instead.
 *
 * @since ODC_release_4.3.4
 */
public class HanaIndexEditor extends DBTableIndexEditor {

    @Override
    protected SqlBuilder sqlBuilder() {
        return new HanaSqlBuilder();
    }

    @Override
    public boolean editable() {
        return true;
    }

    /**
     * Generate CREATE INDEX DDL for HANA.
     * Format: CREATE [UNIQUE] INDEX "idx_name" ON "schema"."table" ("col1", "col2")
     */
    @Override
    public String generateCreateObjectDDL(@NotNull DBTableIndex index) {
        SqlBuilder sqlBuilder = sqlBuilder();
        sqlBuilder.append("CREATE ");
        if (index.getType() != null
                && "UNIQUE".equalsIgnoreCase(index.getType().getValue())) {
            sqlBuilder.append("UNIQUE ");
        }
        sqlBuilder.append("INDEX ").identifier(index.getName())
                .append(" ON ").append(getFullyQualifiedTableName(index))
                .append(" (");
        List<String> columnNames = index.getColumnNames().stream()
                .map(StringUtils::quoteOracleIdentifier)
                .collect(Collectors.toList());
        sqlBuilder.append(String.join(", ", columnNames)).append(")");
        return sqlBuilder.toString().trim() + ";\n";
    }

    /**
     * Generate DROP INDEX DDL for HANA.
     * Format: DROP INDEX "schema"."idx_name"
     * <p>
     * Note: In HANA, DROP INDEX uses schema-qualified index name, not table-qualified.
     */
    @Override
    public String generateDropObjectDDL(@NotNull DBTableIndex index) {
        SqlBuilder sqlBuilder = sqlBuilder();
        sqlBuilder.append("DROP INDEX ");
        if (StringUtils.isNotEmpty(index.getSchemaName())) {
            sqlBuilder.identifier(index.getSchemaName()).append(".");
        }
        sqlBuilder.identifier(index.getName());
        return sqlBuilder.toString().trim() + ";\n";
    }

    @Override
    protected void appendIndexColumnModifiers(DBTableIndex index, SqlBuilder sqlBuilder) {
        // HANA does not have special modifiers for index columns
    }

    @Override
    protected void appendIndexOptions(DBTableIndex index, SqlBuilder sqlBuilder) {
        // HANA does not have additional index options in standard DDL
    }

    @Override
    protected String getFullyQualifiedTableName(@NotNull DBTableIndex index) {
        SqlBuilder sqlBuilder = sqlBuilder();
        if (StringUtils.isNotEmpty(index.getSchemaName())) {
            sqlBuilder.identifier(index.getSchemaName()).append(".");
        }
        if (StringUtils.isNotEmpty(index.getTableName())) {
            sqlBuilder.identifier(index.getTableName());
        }
        return sqlBuilder.toString();
    }
}
