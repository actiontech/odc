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

import java.util.List;
import java.util.Objects;

import javax.validation.constraints.NotNull;

import org.apache.commons.collections4.CollectionUtils;

import com.oceanbase.tools.dbbrowser.editor.DBObjectEditor;
import com.oceanbase.tools.dbbrowser.editor.DBTableEditor;
import com.oceanbase.tools.dbbrowser.model.DBTable;
import com.oceanbase.tools.dbbrowser.model.DBTable.DBTableOptions;
import com.oceanbase.tools.dbbrowser.model.DBTableColumn;
import com.oceanbase.tools.dbbrowser.model.DBTableConstraint;
import com.oceanbase.tools.dbbrowser.model.DBTableIndex;
import com.oceanbase.tools.dbbrowser.model.DBTablePartition;
import com.oceanbase.tools.dbbrowser.util.HiveSqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.StringUtils;

/**
 * Table editor for Apache Hive.
 * <p>
 * Generates Hive-specific CREATE TABLE DDL with the following clause order:
 * <ol>
 * <li>Column definitions with types and comments</li>
 * <li>COMMENT (table-level comment, inline)</li>
 * <li>PARTITIONED BY clause (if partition columns exist)</li>
 * <li>ROW FORMAT DELIMITED FIELDS TERMINATED BY (if applicable)</li>
 * <li>STORED AS (ORC, PARQUET, TEXTFILE, etc.)</li>
 * </ol>
 * </p>
 */
public class HiveTableEditor extends DBTableEditor {

    public HiveTableEditor(DBObjectEditor<DBTableIndex> indexEditor,
            DBObjectEditor<DBTableColumn> columnEditor,
            DBObjectEditor<DBTableConstraint> constraintEditor,
            DBObjectEditor<DBTablePartition> partitionEditor) {
        super(indexEditor, columnEditor, constraintEditor, partitionEditor);
    }

    @Override
    protected SqlBuilder sqlBuilder() {
        return new HiveSqlBuilder();
    }

    @Override
    protected String getFullyQualifiedTableName(@NotNull DBTable table) {
        SqlBuilder sqlBuilder = sqlBuilder();
        if (StringUtils.isNotBlank(table.getSchemaName())) {
            sqlBuilder.identifier(table.getSchemaName()).append(".");
        }
        sqlBuilder.identifier(table.getName());
        return sqlBuilder.toString();
    }

    @Override
    protected boolean createIndexWhenCreatingTable() {
        return false;
    }

    /**
     * Override the full CREATE TABLE generation to produce Hive-specific clause ordering.
     * <p>
     * Hive CREATE TABLE DDL format:
     *
     * <pre>
     * CREATE TABLE `db`.`table_name` (
     *     `id` BIGINT COMMENT 'primary key',
     *     `name` STRING COMMENT 'user name'
     * )
     * COMMENT 'table comment'
     * PARTITIONED BY (`dt` STRING COMMENT 'date partition')
     * ROW FORMAT DELIMITED
     *     FIELDS TERMINATED BY ','
     * STORED AS ORC;
     * </pre>
     */
    @Override
    public String generateCreateObjectDDL(@NotNull DBTable table) {
        fillSchemaAndTableNames(table);
        SqlBuilder sqlBuilder = sqlBuilder();

        // 1. CREATE TABLE ... ( column definitions )
        sqlBuilder.append("CREATE TABLE ").append(getFullyQualifiedTableName(table))
                .append(" (").line();
        List<DBTableColumn> columns = table.getColumns();
        if (CollectionUtils.isNotEmpty(columns)) {
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) {
                    sqlBuilder.append(",").line();
                }
                sqlBuilder.append(columnEditor.generateCreateDefinitionDDL(columns.get(i)));
            }
        }
        sqlBuilder.line().append(")");

        // 2. COMMENT (table-level, inline before PARTITIONED BY)
        appendTableComment(table, sqlBuilder);

        // 3. PARTITIONED BY clause
        appendPartitionedBy(table, sqlBuilder);

        // 4. ROW FORMAT and STORED AS (Hive storage options)
        appendTableOptions(table, sqlBuilder);

        sqlBuilder.append(";\n");
        return sqlBuilder.toString();
    }

    /**
     * Append Hive PARTITIONED BY clause using the table's partition definition.
     * <p>
     * The partition columns in Hive are defined by column name, type, and optional comment in the
     * PARTITIONED BY clause, not as regular table columns.
     * </p>
     */
    private void appendPartitionedBy(DBTable table, SqlBuilder sqlBuilder) {
        if (Objects.isNull(table.getPartition())
                || Objects.isNull(table.getPartition().getPartitionOption())
                || CollectionUtils.isEmpty(table.getPartition().getPartitionOption().getColumnNames())) {
            return;
        }
        List<String> partitionColumnNames = table.getPartition().getPartitionOption().getColumnNames();
        sqlBuilder.line().append("PARTITIONED BY (");
        // Build partition column definitions from the partition option column names.
        // If partition column details (type, comment) are available from the partition definitions,
        // they should be appended here. For simplicity, we use the column names as raw expressions
        // that already include type and comment info.
        for (int i = 0; i < partitionColumnNames.size(); i++) {
            if (i > 0) {
                sqlBuilder.append(", ");
            }
            sqlBuilder.append(partitionColumnNames.get(i));
        }
        sqlBuilder.append(")");
    }

    /**
     * Append Hive storage options: ROW FORMAT and STORED AS.
     * <p>
     * Uses {@link DBTableOptions#getRowFormat()} for ROW FORMAT DELIMITED FIELDS TERMINATED BY, and
     * {@link DBTableOptions#getCompressionOption()} for STORED AS format.
     * </p>
     */
    @Override
    protected void appendTableOptions(DBTable table, SqlBuilder sqlBuilder) {
        DBTableOptions options = table.getTableOptions();
        if (Objects.isNull(options)) {
            return;
        }
        // ROW FORMAT DELIMITED FIELDS TERMINATED BY
        if (StringUtils.isNotBlank(options.getRowFormat())) {
            sqlBuilder.line().append("ROW FORMAT DELIMITED").line()
                    .append("    FIELDS TERMINATED BY ").value(options.getRowFormat());
        }
        // STORED AS (ORC, PARQUET, TEXTFILE, etc.)
        if (StringUtils.isNotBlank(options.getCompressionOption())) {
            sqlBuilder.line().append("STORED AS ").append(options.getCompressionOption());
        }
    }

    /**
     * Append inline COMMENT for Hive CREATE TABLE.
     * <p>
     * Hive table comments are placed between the column definitions and PARTITIONED BY clause:
     * {@code ) COMMENT 'xxx' PARTITIONED BY ...}
     * </p>
     */
    @Override
    protected void appendTableComment(DBTable table, SqlBuilder sqlBuilder) {
        if (Objects.nonNull(table.getTableOptions())
                && StringUtils.isNotBlank(table.getTableOptions().getComment())) {
            sqlBuilder.line().append("COMMENT ").value(table.getTableOptions().getComment());
        }
    }

    /**
     * Hive column comments are inline in the column definition (handled by HiveColumnEditor); no
     * separate comment DDL is needed.
     */
    @Override
    protected void appendColumnComment(DBTable table, SqlBuilder sqlBuilder) {
        // No-op: Hive column comments are inline
    }

    /**
     * Override to skip index/constraint/columnGroup editors that Hive does not support.
     * <p>
     * Only handles: table rename, table option changes (COMMENT), column changes, and partition
     * changes.
     * </p>
     */
    @Override
    public String generateUpdateObjectDDL(@NotNull DBTable oldTable, @NotNull DBTable newTable) {
        SqlBuilder sqlBuilder = sqlBuilder();
        // Table rename
        if (!StringUtils.equals(oldTable.getName(), newTable.getName())) {
            sqlBuilder.append(generateRenameObjectDDL(oldTable, newTable));
            sqlBuilder.append(";\n");
        }
        // Table option changes (COMMENT etc.)
        generateUpdateTableOptionDDL(oldTable, newTable, sqlBuilder);
        // Fill schema/table names into columns and partitions (parent's fillSchemaNameAndTableName
        // is private, so we inline the relevant logic here)
        fillSchemaAndTableNamesForUpdate(oldTable);
        fillSchemaAndTableNamesForUpdate(newTable);
        // Column changes (ADD COLUMNS / CHANGE COLUMN)
        sqlBuilder.append(columnEditor.generateUpdateObjectListDDL(
                oldTable.getColumns(), newTable.getColumns()));
        // Partition changes (ADD/DROP PARTITION)
        sqlBuilder.append(partitionEditor.generateUpdateObjectDDL(
                oldTable.getPartition(), newTable.getPartition()));
        // Skip: indexEditor, constraintEditor, generateUpdateColumnGroupDDL (OB-specific)
        return sqlBuilder.toString();
    }

    /**
     * Override to skip index/constraint/columnGroup editors for shadow table comparing.
     * <p>
     * Hive does not support indexes, foreign key constraints, or column groups, so those editors are
     * not invoked.
     * </p>
     */
    @Override
    public String generateUpdateObjectDDLWithoutRenaming(@NotNull DBTable oldTable,
            @NotNull DBTable newTable) {
        SqlBuilder sqlBuilder = sqlBuilder();
        generateUpdateTableOptionDDL(oldTable, newTable, sqlBuilder);
        fillSchemaAndTableNamesForUpdate(oldTable);
        fillSchemaAndTableNamesForUpdate(newTable);
        // Column changes only
        sqlBuilder.append(columnEditor.generateUpdateObjectListDDL(
                oldTable.getColumns(), newTable.getColumns()));
        // Partition changes
        sqlBuilder.append(partitionEditor.generateUpdateObjectDDL(
                oldTable.getPartition(), newTable.getPartition()));
        // Skip: indexEditor, constraintEditor, generateUpdateColumnGroupDDL
        return sqlBuilder.toString();
    }

    /**
     * Fill schema name and table name into columns and partition for update operations.
     * <p>
     * The parent class's {@code fillSchemaNameAndTableName} is private, so we replicate the relevant
     * subset here (columns + partition only, since Hive has no indexes/constraints).
     * </p>
     */
    private void fillSchemaAndTableNamesForUpdate(DBTable table) {
        String schemaName = table.getSchemaName();
        String tableName = table.getName();
        if (CollectionUtils.isNotEmpty(table.getColumns())) {
            table.getColumns().forEach(column -> {
                column.setSchemaName(schemaName);
                column.setTableName(tableName);
            });
        }
        if (Objects.nonNull(table.getPartition())) {
            table.getPartition().setSchemaName(schemaName);
            table.getPartition().setTableName(tableName);
        }
    }

    @Override
    public String generateRenameObjectDDL(@NotNull DBTable oldTable, @NotNull DBTable newTable) {
        SqlBuilder sqlBuilder = sqlBuilder();
        sqlBuilder.append("ALTER TABLE ").append(getFullyQualifiedTableName(oldTable))
                .append(" RENAME TO ").append(getFullyQualifiedTableName(newTable));
        return sqlBuilder.toString();
    }

    @Override
    public void generateUpdateTableOptionDDL(DBTable oldTable, DBTable newTable, SqlBuilder sqlBuilder) {
        // Hive ALTER TABLE does not support changing most table options (ROW FORMAT, STORED AS)
        // Only table comment can be updated
        String oldComment = Objects.nonNull(oldTable.getTableOptions())
                ? oldTable.getTableOptions().getComment()
                : null;
        String newComment = Objects.nonNull(newTable.getTableOptions())
                ? newTable.getTableOptions().getComment()
                : null;
        if (!Objects.equals(oldComment, newComment) && StringUtils.isNotBlank(newComment)) {
            sqlBuilder.append("ALTER TABLE ").append(getFullyQualifiedTableName(newTable))
                    .append(" SET TBLPROPERTIES ('comment' = ").value(newComment).append(");\n");
        }
    }

    @Override
    public String generateDropObjectDDL(@NotNull DBTable table) {
        SqlBuilder sqlBuilder = sqlBuilder();
        sqlBuilder.append("DROP TABLE IF EXISTS ").append(getFullyQualifiedTableName(table));
        return sqlBuilder.toString();
    }

    /**
     * Fill schema and table names into child objects (columns, indexes, constraints, partitions).
     */
    private void fillSchemaAndTableNames(DBTable table) {
        if (CollectionUtils.isNotEmpty(table.getColumns())) {
            table.getColumns().forEach(column -> {
                column.setSchemaName(table.getSchemaName());
                column.setTableName(table.getName());
            });
        }
        if (Objects.nonNull(table.getPartition())) {
            table.getPartition().setSchemaName(table.getSchemaName());
            table.getPartition().setTableName(table.getName());
        }
    }

}
