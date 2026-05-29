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

import java.util.Collection;

import javax.validation.constraints.NotNull;

import com.oceanbase.tools.dbbrowser.editor.DBTableConstraintEditor;
import com.oceanbase.tools.dbbrowser.model.DBTableConstraint;
import com.oceanbase.tools.dbbrowser.util.HiveSqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;

/**
 * Constraint editor for Apache Hive.
 * <p>
 * Hive does not support foreign key constraints. All methods throw
 * {@link UnsupportedOperationException}.
 * </p>
 */
public class HiveConstraintEditor extends DBTableConstraintEditor {

    private static final String UNSUPPORTED_MSG = "Hive does not support foreign key constraints";

    @Override
    public boolean editable() {
        return false;
    }

    @Override
    public String generateCreateObjectDDL(@NotNull DBTableConstraint constraint) {
        throw new UnsupportedOperationException(UNSUPPORTED_MSG);
    }

    @Override
    public String generateCreateDefinitionDDL(@NotNull DBTableConstraint constraint) {
        throw new UnsupportedOperationException(UNSUPPORTED_MSG);
    }

    @Override
    public String generateUpdateObjectDDL(@NotNull DBTableConstraint oldConstraint,
            @NotNull DBTableConstraint newConstraint) {
        throw new UnsupportedOperationException(UNSUPPORTED_MSG);
    }

    @Override
    public String generateUpdateObjectListDDL(Collection<DBTableConstraint> oldConstraints,
            Collection<DBTableConstraint> newConstraints) {
        throw new UnsupportedOperationException(UNSUPPORTED_MSG);
    }

    @Override
    public String generateRenameObjectDDL(@NotNull DBTableConstraint oldConstraint,
            @NotNull DBTableConstraint newConstraint) {
        throw new UnsupportedOperationException(UNSUPPORTED_MSG);
    }

    @Override
    public String generateDropObjectDDL(@NotNull DBTableConstraint constraint) {
        throw new UnsupportedOperationException(UNSUPPORTED_MSG);
    }

    @Override
    protected SqlBuilder sqlBuilder() {
        return new HiveSqlBuilder();
    }

}
