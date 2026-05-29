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

import javax.validation.constraints.NotNull;

import com.oceanbase.tools.dbbrowser.editor.DBTableConstraintEditor;
import com.oceanbase.tools.dbbrowser.model.DBTableConstraint;
import com.oceanbase.tools.dbbrowser.util.HanaSqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.StringUtils;

/**
 * Constraint editor for SAP HANA database.
 * <p>
 * HANA constraint DDL syntax:
 * <ul>
 * <li>ADD:
 * {@code ALTER TABLE "schema"."table" ADD CONSTRAINT "name" PRIMARY KEY ("col1", "col2")}</li>
 * <li>ADD: {@code ALTER TABLE "schema"."table" ADD CONSTRAINT "name" UNIQUE ("col1")}</li>
 * <li>ADD FK: {@code ALTER TABLE "schema"."table" ADD CONSTRAINT "name" FOREIGN KEY ("col")
 *            REFERENCES "ref_schema"."ref_table" ("ref_col")}</li>
 * <li>DROP: {@code ALTER TABLE "schema"."table" DROP CONSTRAINT "constraint_name"}</li>
 * </ul>
 * Note: HANA does not support renaming constraints directly.
 *
 * @since ODC_release_4.3.4
 */
public class HanaConstraintEditor extends DBTableConstraintEditor {

    @Override
    protected SqlBuilder sqlBuilder() {
        return new HanaSqlBuilder();
    }

    /**
     * Generate DROP CONSTRAINT DDL for HANA. Format: ALTER TABLE "schema"."table" DROP CONSTRAINT
     * "constraint_name"
     */
    @Override
    public String generateDropObjectDDL(@NotNull DBTableConstraint constraint) {
        SqlBuilder sqlBuilder = sqlBuilder();
        sqlBuilder.append("ALTER TABLE ").append(getFullyQualifiedTableName(constraint))
                .append(" DROP CONSTRAINT ").identifier(constraint.getName());
        return sqlBuilder.toString().trim() + ";\n";
    }

    /**
     * HANA does not support renaming constraints directly; throw UnsupportedOperationException.
     */
    @Override
    public String generateRenameObjectDDL(@NotNull DBTableConstraint oldConstraint,
            @NotNull DBTableConstraint newConstraint) {
        throw new UnsupportedOperationException("HANA does not support constraint renaming");
    }

    @Override
    protected String getFullyQualifiedTableName(@NotNull DBTableConstraint constraint) {
        SqlBuilder sqlBuilder = sqlBuilder();
        if (StringUtils.isNotEmpty(constraint.getSchemaName())) {
            sqlBuilder.identifier(constraint.getSchemaName()).append(".");
        }
        if (StringUtils.isNotEmpty(constraint.getTableName())) {
            sqlBuilder.identifier(constraint.getTableName());
        }
        return sqlBuilder.toString();
    }
}
