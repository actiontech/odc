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

import javax.validation.constraints.NotNull;

import com.oceanbase.tools.dbbrowser.editor.DBTableConstraintEditor;
import com.oceanbase.tools.dbbrowser.model.DBConstraintType;
import com.oceanbase.tools.dbbrowser.model.DBTableConstraint;
import com.oceanbase.tools.dbbrowser.util.Db2SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.StringUtils;

/**
 * DB2 LUW constraint editor (fix_report_20260529_100416 Bug-2, Issue dms-ee#839).
 *
 * <p>
 * DB2 ADD CONSTRAINT uses standard SQL/ANSI syntax:
 *
 * <ul>
 * <li>{@code ALTER TABLE "S"."T" ADD CONSTRAINT "pk" PRIMARY KEY ("col");}</li>
 * <li>{@code ALTER TABLE "S"."T" ADD CONSTRAINT "uq" UNIQUE ("col");}</li>
 * <li>{@code ALTER TABLE "S"."T" ADD CONSTRAINT "fk" FOREIGN KEY ("c") REFERENCES "S2"."T2" ("c");}</li>
 * <li>{@code ALTER TABLE "S"."T" ADD CONSTRAINT "ck" CHECK (col > 0);}</li>
 * <li>{@code ALTER TABLE "S"."T" DROP CONSTRAINT "name";} (for non-PK) or
 * {@code DROP PRIMARY KEY;}</li>
 * </ul>
 *
 * <p>
 * Inheriting {@link DBTableConstraintEditor#generateCreateObjectDDL} produces a usable form; the
 * override here normalises DROP for PRIMARY KEY and switches the SQL builder to
 * {@link Db2SqlBuilder} for double-quoted identifiers.
 *
 * @since ODC_release_4.3.4 (Issue dms-ee#839, fix_report_20260529_100416)
 */
public class Db2ConstraintEditor extends DBTableConstraintEditor {

    @Override
    protected SqlBuilder sqlBuilder() {
        return new Db2SqlBuilder();
    }

    /**
     * DB2 has no in-place rename for table constraints (DROP + ADD is the documented workflow); the
     * editor emits a DROP / re-ADD pair so the round-trip works inside the table designer.
     */
    @Override
    public String generateRenameObjectDDL(@NotNull DBTableConstraint oldConstraint,
            @NotNull DBTableConstraint newConstraint) {
        SqlBuilder sqlBuilder = sqlBuilder();
        sqlBuilder.append(generateDropObjectDDL(oldConstraint));
        sqlBuilder.append(generateCreateObjectDDL(newConstraint));
        return sqlBuilder.toString();
    }

    @Override
    public String generateDropObjectDDL(@NotNull DBTableConstraint constraint) {
        SqlBuilder sqlBuilder = sqlBuilder();
        sqlBuilder.append("ALTER TABLE ").append(getFullyQualifiedTableName(constraint));
        if (constraint.getType() == DBConstraintType.PRIMARY_KEY) {
            // DB2 uses DROP PRIMARY KEY; ALTER ... DROP CONSTRAINT also works on 11.5+
            // but DROP PRIMARY KEY is the documented, version-independent form.
            sqlBuilder.append(" DROP PRIMARY KEY;\n");
        } else if (StringUtils.isNotBlank(constraint.getName())) {
            sqlBuilder.append(" DROP CONSTRAINT ").identifier(constraint.getName()).append(";\n");
        } else {
            // Defensive fall-back: anonymous non-PK constraint is rare in DB2 because
            // SYSCAT.TABCONST always materialises an auto-generated SQLxxxxxxxx name.
            sqlBuilder.append(";\n");
        }
        return sqlBuilder.toString();
    }
}
