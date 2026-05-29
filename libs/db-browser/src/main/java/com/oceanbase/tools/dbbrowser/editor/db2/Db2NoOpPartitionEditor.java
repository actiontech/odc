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

import javax.validation.constraints.NotNull;

import com.oceanbase.tools.dbbrowser.editor.DBTablePartitionEditor;
import com.oceanbase.tools.dbbrowser.model.DBTablePartition;
import com.oceanbase.tools.dbbrowser.model.DBTablePartitionDefinition;
import com.oceanbase.tools.dbbrowser.model.DBTablePartitionOption;
import com.oceanbase.tools.dbbrowser.util.Db2SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;

import lombok.NonNull;

/**
 * No-op partition editor for DB2 LUW (fix_report_20260529_100416 Bug-2, Issue dms-ee#839).
 *
 * <p>
 * DB2 range / hash partitioning is intentionally out of scope per {@code expand_odc_db2.md} §14.
 * {@link Db2TableEditor} still needs a non-null partition editor so the parent
 * {@link com.oceanbase.tools.dbbrowser.editor.DBTableEditor#generateUpdateObjectDDL} pipeline can
 * call {@code partitionEditor.generateUpdateObjectDDL(null, null)} without NPE. All DDL-emitting
 * methods return empty strings so the workbench produces a clean ALTER COLUMN / ALTER INDEX flow
 * without trailing comments.
 *
 * @since ODC_release_4.3.4 (Issue dms-ee#839, fix_report_20260529_100416)
 */
public class Db2NoOpPartitionEditor extends DBTablePartitionEditor {

    @Override
    public boolean editable() {
        return false;
    }

    @Override
    protected SqlBuilder sqlBuilder() {
        return new Db2SqlBuilder();
    }

    @Override
    public String generateCreateObjectDDL(@NotNull DBTablePartition partition) {
        return "";
    }

    @Override
    public String generateCreateDefinitionDDL(@NotNull DBTablePartition partition) {
        return "";
    }

    @Override
    public String generateDropObjectDDL(@NotNull DBTablePartition partition) {
        return "";
    }

    @Override
    public String generateUpdateObjectDDL(DBTablePartition oldPartition, DBTablePartition newPartition) {
        return "";
    }

    @Override
    public String generateAddPartitionDefinitionDDL(@NotNull DBTablePartitionDefinition definition,
            @NotNull DBTablePartitionOption option, String fullyQualifiedTableName) {
        return "";
    }

    @Override
    public String generateAddPartitionDefinitionDDL(String schemaName, @NonNull String tableName,
            @NotNull DBTablePartitionOption option, List<DBTablePartitionDefinition> definitions) {
        return "";
    }

    @Override
    protected void appendDefinitions(DBTablePartition partition, SqlBuilder sqlBuilder) {
        // no-op
    }

    @Override
    protected void appendDefinition(DBTablePartitionOption option, DBTablePartitionDefinition definition,
            SqlBuilder sqlBuilder) {
        // no-op
    }

    @Override
    protected String modifyPartitionType(@NotNull DBTablePartition oldPartition,
            @NotNull DBTablePartition newPartition) {
        return "";
    }
}
