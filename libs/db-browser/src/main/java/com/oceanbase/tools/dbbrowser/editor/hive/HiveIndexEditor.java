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

import com.oceanbase.tools.dbbrowser.editor.DBTableIndexEditor;
import com.oceanbase.tools.dbbrowser.model.DBTableIndex;
import com.oceanbase.tools.dbbrowser.util.HiveSqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;

/**
 * Index editor for Apache Hive.
 * <p>
 * Hive 3.0+ has deprecated the index mechanism. All methods throw
 * {@link UnsupportedOperationException}.
 * </p>
 */
public class HiveIndexEditor extends DBTableIndexEditor {

    private static final String UNSUPPORTED_MSG = "Hive does not support indexes (deprecated since Hive 3.0)";

    @Override
    public boolean editable() {
        return false;
    }

    @Override
    public String generateCreateObjectDDL(@NotNull DBTableIndex index) {
        throw new UnsupportedOperationException(UNSUPPORTED_MSG);
    }

    @Override
    public String generateCreateDefinitionDDL(@NotNull DBTableIndex index) {
        throw new UnsupportedOperationException(UNSUPPORTED_MSG);
    }

    @Override
    public String generateUpdateObjectDDL(@NotNull DBTableIndex oldIndex, @NotNull DBTableIndex newIndex) {
        throw new UnsupportedOperationException(UNSUPPORTED_MSG);
    }

    @Override
    public String generateUpdateObjectListDDL(Collection<DBTableIndex> oldIndexes,
            Collection<DBTableIndex> newIndexes) {
        throw new UnsupportedOperationException(UNSUPPORTED_MSG);
    }

    @Override
    public String generateRenameObjectDDL(@NotNull DBTableIndex oldIndex, @NotNull DBTableIndex newIndex) {
        throw new UnsupportedOperationException(UNSUPPORTED_MSG);
    }

    @Override
    public String generateDropObjectDDL(@NotNull DBTableIndex index) {
        throw new UnsupportedOperationException(UNSUPPORTED_MSG);
    }

    @Override
    protected void appendIndexColumnModifiers(DBTableIndex index, SqlBuilder sqlBuilder) {
        throw new UnsupportedOperationException(UNSUPPORTED_MSG);
    }

    @Override
    protected void appendIndexOptions(DBTableIndex index, SqlBuilder sqlBuilder) {
        throw new UnsupportedOperationException(UNSUPPORTED_MSG);
    }

    @Override
    protected SqlBuilder sqlBuilder() {
        return new HiveSqlBuilder();
    }

}
