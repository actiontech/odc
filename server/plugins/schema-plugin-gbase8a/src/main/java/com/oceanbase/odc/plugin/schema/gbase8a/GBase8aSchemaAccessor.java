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
package com.oceanbase.odc.plugin.schema.gbase8a;

import java.util.Collections;
import java.util.List;

import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.lang.NonNull;

import com.oceanbase.tools.dbbrowser.model.DBTableColumn;
import com.oceanbase.tools.dbbrowser.model.DBTablePartition;
import com.oceanbase.tools.dbbrowser.schema.mysql.MySQLNoLessThan5700SchemaAccessor;
import com.oceanbase.tools.dbbrowser.util.MySQLSqlBuilder;

/**
 * GBase-8a {@code information_schema.columns} lacks MySQL 5.7+ fields such as
 * {@code DATETIME_PRECISION} / {@code GENERATION_EXPRESSION}. Reuse MySQL accessor but substitute
 * those columns with NULL so table/column metadata works for the object tree.
 */
public class GBase8aSchemaAccessor extends MySQLNoLessThan5700SchemaAccessor {

    private static final String LIST_TABLE_COLUMNS_SQL = ""
            + "SELECT TABLE_NAME, TABLE_SCHEMA, ORDINAL_POSITION, COLUMN_NAME, DATA_TYPE, COLUMN_TYPE, "
            + "NUMERIC_SCALE, NUMERIC_PRECISION, NULL AS DATETIME_PRECISION, CHARACTER_MAXIMUM_LENGTH, "
            + "EXTRA, CHARACTER_SET_NAME, COLLATION_NAME, COLUMN_COMMENT, COLUMN_DEFAULT, "
            + "NULL AS GENERATION_EXPRESSION, IS_NULLABLE, COLUMN_KEY "
            + "FROM information_schema.columns "
            + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? "
            + "ORDER BY ORDINAL_POSITION ASC";

    public GBase8aSchemaAccessor(@NonNull JdbcOperations jdbcOperations) {
        super(jdbcOperations);
    }

    @Override
    protected boolean supportGeneratedColumn() {
        return false;
    }

    @Override
    protected String getListTableColumnsSql(String schemaName) {
        MySQLSqlBuilder sb = new MySQLSqlBuilder();
        sb.append(
                "select TABLE_NAME, TABLE_SCHEMA, ORDINAL_POSITION, COLUMN_NAME, DATA_TYPE, COLUMN_TYPE, "
                        + "NUMERIC_SCALE, NUMERIC_PRECISION, NULL AS DATETIME_PRECISION, CHARACTER_MAXIMUM_LENGTH, "
                        + "EXTRA, CHARACTER_SET_NAME, COLLATION_NAME, COLUMN_COMMENT, COLUMN_DEFAULT, IS_NULLABLE, "
                        + "NULL AS GENERATION_EXPRESSION, COLUMN_KEY from information_schema.columns where TABLE_SCHEMA = ");
        sb.value(schemaName);
        sb.append(" ORDER BY TABLE_NAME, ORDINAL_POSITION");
        return sb.toString();
    }

    @Override
    public List<DBTableColumn> listTableColumns(String schemaName, String tableName) {
        List<DBTableColumn> tableColumns =
                jdbcOperations.query(LIST_TABLE_COLUMNS_SQL, new Object[] {schemaName, tableName},
                        listTableRowMapper());
        correctColumnPrecisionIfNeed(tableColumns);
        return tableColumns;
    }

    @Override
    public DBTablePartition getPartition(String schemaName, String tableName) {
        // MySQL 5.7 partition SQL uses any_value(); GBase rejects it. Empty partition is enough for
        // object-tree / table-data (AC-6).
        DBTablePartition partition = new DBTablePartition();
        partition.setSchemaName(schemaName);
        partition.setTableName(tableName);
        partition.setPartitionDefinitions(Collections.emptyList());
        return partition;
    }
}
