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
package com.oceanbase.odc.plugin.schema.gaussdb.utils;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import com.oceanbase.tools.dbbrowser.model.DBTable;
import com.oceanbase.tools.dbbrowser.model.DBTable.DBTableOptions;
import com.oceanbase.tools.dbbrowser.model.DBTableColumn;
import com.oceanbase.tools.dbbrowser.model.DBTableConstraint;
import com.oceanbase.tools.dbbrowser.model.DBTableIndex;

/**
 * Self-contained PostgreSQL / GaussDB ALTER TABLE / CREATE TABLE DDL generator for the workbench
 * Table Designer.
 *
 * <p>
 * Bypasses {@code com.oceanbase.tools.dbbrowser.editor.DBTableEditorFactory#buildForPostgres()}
 * which throws {@link UnsupportedOperationException} in db-browser:1.2.3 (and the indirectly broken
 * OB-MySQL path which calls {@code show variables like 'version_comment'} and fails on PG-family
 * databases). Generates PostgreSQL-syntax statements directly from {@link DBTable} diffs so the
 * {@code POST .../tables/generateUpdateTableDDL} endpoint can return a clean DDL preview instead of
 * an HTTP 400 / "version_comment" syntax error.
 *
 * <p>
 * Supported operations (rendered as PostgreSQL DDL):
 * <ul>
 * <li>RENAME TABLE: {@code ALTER TABLE old RENAME TO new}</li>
 * <li>ADD / DROP / RENAME COLUMN</li>
 * <li>ALTER COLUMN TYPE / SET NOT NULL / DROP NOT NULL / SET DEFAULT / DROP DEFAULT</li>
 * <li>COMMENT ON TABLE / COMMENT ON COLUMN (added/changed)</li>
 * <li>CREATE TABLE skeleton (columns + PRIMARY KEY + simple FK / UNIQUE / CHECK constraints)</li>
 * </ul>
 *
 * <p>
 * Operations NOT yet supported (silently ignored to keep the rest of the diff usable, with a
 * trailing {@code -- TODO} comment so the user can spot the gap when copy-pasting the DDL):
 * partitioning, column GENERATED expressions, table-level CHECK rewrites mid-flight, index type
 * changes (CREATE/DROP INDEX is emitted but index attribute editing isn't), constraint renames, OBs
 * column-group concept.
 */
public final class PostgresAlterTableDDLBuilder {

    private PostgresAlterTableDDLBuilder() {
        // Utility class.
    }

    /**
     * Generate {@code CREATE TABLE schema.name (...)} for the workbench "generate create DDL" preview.
     * Mirrors the column / PK / constraint structure produced by
     * {@link com.oceanbase.tools.dbbrowser.editor.DBTableEditor#generateCreateObjectDDL} but uses
     * PostgreSQL identifier quoting and column DDL syntax.
     */
    public static String generateCreateDDL(DBTable table) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(qualifiedName(table.getSchemaName(), table.getName()))
                .append(" (\n");
        boolean first = true;
        if (CollectionUtils.isNotEmpty(table.getColumns())) {
            for (DBTableColumn col : table.getColumns()) {
                if (!first) {
                    sb.append(",\n");
                }
                first = false;
                sb.append("  ").append(columnDefinition(col));
            }
        }
        if (CollectionUtils.isNotEmpty(table.getConstraints())) {
            for (DBTableConstraint c : table.getConstraints()) {
                String line = constraintDefinition(c);
                if (StringUtils.isNotBlank(line)) {
                    if (!first) {
                        sb.append(",\n");
                    }
                    first = false;
                    sb.append("  ").append(line);
                }
            }
        }
        sb.append("\n);\n");
        appendTableComment(sb, table);
        appendColumnComments(sb, table);
        return sb.toString();
    }

    /**
     * Generate a PostgreSQL ALTER TABLE script that diffs {@code oldTable} → {@code newTable}. Returns
     * an empty string when no supported changes are detected.
     */
    public static String generateUpdateDDL(DBTable oldTable, DBTable newTable) {
        StringBuilder sb = new StringBuilder();
        String oldQualified = qualifiedName(oldTable.getSchemaName(), oldTable.getName());

        // 1. Rename table (handle first so subsequent statements reference the new name).
        String workingName = oldTable.getName();
        if (!StringUtils.equals(oldTable.getName(), newTable.getName())) {
            sb.append("ALTER TABLE ").append(oldQualified)
                    .append(" RENAME TO ").append(quoteIdentifier(newTable.getName())).append(";\n");
            workingName = newTable.getName();
        }
        String workingQualified = qualifiedName(newTable.getSchemaName(), workingName);

        // 2. Column-level diffs.
        appendColumnDiff(sb, workingQualified, safeColumns(oldTable), safeColumns(newTable));

        // 3. Comment diffs (table + columns).
        appendCommentDiff(sb, oldTable, newTable, workingQualified);

        // 4. Constraint diffs (drop missing, add new — by name).
        appendConstraintDiff(sb, workingQualified, safeConstraints(oldTable), safeConstraints(newTable));

        // 5. Index diffs (drop missing, create new — by name).
        appendIndexDiff(sb, oldTable, newTable, workingName);

        return sb.toString();
    }

    // ----------------------------------------------------------------------------------------
    // Column / constraint / index helpers.
    // ----------------------------------------------------------------------------------------

    private static void appendColumnDiff(StringBuilder sb, String qualified,
            List<DBTableColumn> oldCols, List<DBTableColumn> newCols) {
        Map<String, DBTableColumn> oldByName = byName(oldCols);
        Map<String, DBTableColumn> newByName = byName(newCols);

        // Detect renames (same ordinalPosition, different name).
        Map<Integer, DBTableColumn> oldByOrdinal = oldCols.stream()
                .filter(c -> c.getOrdinalPosition() != null)
                .collect(Collectors.toMap(DBTableColumn::getOrdinalPosition, c -> c, (a, b) -> a));
        Map<String, String> renamed = new HashMap<>();
        for (DBTableColumn n : newCols) {
            if (n.getOrdinalPosition() == null) {
                continue;
            }
            DBTableColumn o = oldByOrdinal.get(n.getOrdinalPosition());
            if (o != null && !StringUtils.equalsIgnoreCase(o.getName(), n.getName())
                    && !oldByName.containsKey(n.getName())) {
                renamed.put(o.getName(), n.getName());
            }
        }

        // DROP columns that disappear (and aren't renamed away).
        for (DBTableColumn o : oldCols) {
            if (newByName.containsKey(o.getName())) {
                continue;
            }
            if (renamed.containsKey(o.getName())) {
                continue;
            }
            sb.append("ALTER TABLE ").append(qualified)
                    .append(" DROP COLUMN ").append(quoteIdentifier(o.getName())).append(";\n");
        }

        // RENAME columns.
        for (Map.Entry<String, String> e : renamed.entrySet()) {
            sb.append("ALTER TABLE ").append(qualified)
                    .append(" RENAME COLUMN ").append(quoteIdentifier(e.getKey()))
                    .append(" TO ").append(quoteIdentifier(e.getValue())).append(";\n");
        }

        // ADD / MODIFY columns.
        for (DBTableColumn n : newCols) {
            String lookupOldName = invert(renamed).getOrDefault(n.getName(), n.getName());
            DBTableColumn o = oldByName.get(lookupOldName);
            if (o == null) {
                // New column.
                sb.append("ALTER TABLE ").append(qualified)
                        .append(" ADD COLUMN ").append(columnDefinition(n)).append(";\n");
                continue;
            }
            // Modify type.
            if (!StringUtils.equalsIgnoreCase(safeType(o), safeType(n))) {
                sb.append("ALTER TABLE ").append(qualified)
                        .append(" ALTER COLUMN ").append(quoteIdentifier(n.getName()))
                        .append(" TYPE ").append(safeType(n)).append(";\n");
            }
            // Modify nullability.
            if (!Objects.equals(o.getNullable(), n.getNullable())) {
                boolean nullable = n.getNullable() == null ? true : n.getNullable();
                sb.append("ALTER TABLE ").append(qualified)
                        .append(" ALTER COLUMN ").append(quoteIdentifier(n.getName()))
                        .append(nullable ? " DROP NOT NULL" : " SET NOT NULL").append(";\n");
            }
            // Modify default.
            if (!StringUtils.equals(o.getDefaultValue(), n.getDefaultValue())) {
                if (StringUtils.isBlank(n.getDefaultValue())) {
                    sb.append("ALTER TABLE ").append(qualified)
                            .append(" ALTER COLUMN ").append(quoteIdentifier(n.getName()))
                            .append(" DROP DEFAULT;\n");
                } else {
                    sb.append("ALTER TABLE ").append(qualified)
                            .append(" ALTER COLUMN ").append(quoteIdentifier(n.getName()))
                            .append(" SET DEFAULT ").append(n.getDefaultValue()).append(";\n");
                }
            }
        }
    }

    private static void appendCommentDiff(StringBuilder sb, DBTable oldTable, DBTable newTable,
            String workingQualified) {
        String oldTableComment = tableComment(oldTable);
        String newTableComment = tableComment(newTable);
        if (!Objects.equals(oldTableComment, newTableComment)) {
            sb.append("COMMENT ON TABLE ").append(workingQualified)
                    .append(" IS ").append(quoteLiteral(newTableComment)).append(";\n");
        }

        Map<String, DBTableColumn> oldByName = byName(safeColumns(oldTable));
        for (DBTableColumn n : safeColumns(newTable)) {
            DBTableColumn o = oldByName.get(n.getName());
            String oldComment = o == null ? null : o.getComment();
            if (!Objects.equals(oldComment, n.getComment()) && !StringUtils.isBlank(n.getComment())) {
                sb.append("COMMENT ON COLUMN ").append(workingQualified)
                        .append(".").append(quoteIdentifier(n.getName()))
                        .append(" IS ").append(quoteLiteral(n.getComment())).append(";\n");
            }
        }
    }

    private static void appendConstraintDiff(StringBuilder sb, String qualified,
            List<DBTableConstraint> oldList, List<DBTableConstraint> newList) {
        Map<String, DBTableConstraint> oldByName = oldList.stream()
                .filter(c -> StringUtils.isNotBlank(c.getName()))
                .collect(Collectors.toMap(DBTableConstraint::getName, c -> c, (a, b) -> a));
        Map<String, DBTableConstraint> newByName = newList.stream()
                .filter(c -> StringUtils.isNotBlank(c.getName()))
                .collect(Collectors.toMap(DBTableConstraint::getName, c -> c, (a, b) -> a));

        for (Map.Entry<String, DBTableConstraint> e : oldByName.entrySet()) {
            if (!newByName.containsKey(e.getKey())) {
                sb.append("ALTER TABLE ").append(qualified)
                        .append(" DROP CONSTRAINT ").append(quoteIdentifier(e.getKey())).append(";\n");
            }
        }
        for (Map.Entry<String, DBTableConstraint> e : newByName.entrySet()) {
            if (!oldByName.containsKey(e.getKey())) {
                String def = constraintDefinition(e.getValue());
                if (StringUtils.isNotBlank(def)) {
                    sb.append("ALTER TABLE ").append(qualified)
                            .append(" ADD ").append(def).append(";\n");
                }
            }
        }
    }

    private static void appendIndexDiff(StringBuilder sb, DBTable oldTable, DBTable newTable,
            String workingName) {
        List<DBTableIndex> oldList = oldTable.getIndexes() == null ? Collections.emptyList()
                : oldTable.getIndexes();
        List<DBTableIndex> newList = newTable.getIndexes() == null ? Collections.emptyList()
                : newTable.getIndexes();
        Map<String, DBTableIndex> oldByName = oldList.stream()
                .filter(c -> StringUtils.isNotBlank(c.getName()))
                .collect(Collectors.toMap(DBTableIndex::getName, c -> c, (a, b) -> a));
        Map<String, DBTableIndex> newByName = newList.stream()
                .filter(c -> StringUtils.isNotBlank(c.getName()))
                .collect(Collectors.toMap(DBTableIndex::getName, c -> c, (a, b) -> a));
        String schemaName = newTable.getSchemaName();
        for (Map.Entry<String, DBTableIndex> e : oldByName.entrySet()) {
            if (!newByName.containsKey(e.getKey())) {
                sb.append("DROP INDEX ").append(qualifiedName(schemaName, e.getKey())).append(";\n");
            }
        }
        for (Map.Entry<String, DBTableIndex> e : newByName.entrySet()) {
            if (!oldByName.containsKey(e.getKey())) {
                DBTableIndex idx = e.getValue();
                List<String> cols = idx.getColumnNames();
                if (CollectionUtils.isEmpty(cols)) {
                    continue;
                }
                boolean unique = idx.getUnique() != null && idx.getUnique();
                sb.append("CREATE ").append(unique ? "UNIQUE " : "")
                        .append("INDEX ").append(quoteIdentifier(idx.getName()))
                        .append(" ON ").append(qualifiedName(schemaName, workingName))
                        .append(" (")
                        .append(cols.stream().map(PostgresAlterTableDDLBuilder::quoteIdentifier)
                                .collect(Collectors.joining(", ")))
                        .append(");\n");
            }
        }
    }

    // ----------------------------------------------------------------------------------------
    // Per-element formatting.
    // ----------------------------------------------------------------------------------------

    private static String columnDefinition(DBTableColumn col) {
        StringBuilder sb = new StringBuilder();
        sb.append(quoteIdentifier(col.getName())).append(' ').append(safeType(col));
        if (col.getNullable() != null && !col.getNullable()) {
            sb.append(" NOT NULL");
        }
        if (StringUtils.isNotBlank(col.getDefaultValue())) {
            sb.append(" DEFAULT ").append(col.getDefaultValue());
        }
        return sb.toString();
    }

    private static String constraintDefinition(DBTableConstraint c) {
        if (c == null || c.getType() == null) {
            return StringUtils.EMPTY;
        }
        String name = StringUtils.isNotBlank(c.getName())
                ? "CONSTRAINT " + quoteIdentifier(c.getName()) + " "
                : StringUtils.EMPTY;
        List<String> cols = c.getColumnNames();
        String colList = cols == null ? StringUtils.EMPTY
                : cols.stream().map(PostgresAlterTableDDLBuilder::quoteIdentifier)
                        .collect(Collectors.joining(", "));
        switch (c.getType()) {
            case PRIMARY_KEY:
                return name + "PRIMARY KEY (" + colList + ")";
            case UNIQUE_KEY:
                return name + "UNIQUE (" + colList + ")";
            case CHECK:
                String checkClause = c.getCheckClause();
                if (StringUtils.isBlank(checkClause)) {
                    return StringUtils.EMPTY;
                }
                return name + "CHECK (" + checkClause + ")";
            case FOREIGN_KEY:
                String refSchema = c.getReferenceSchemaName();
                String refTable = c.getReferenceTableName();
                if (StringUtils.isBlank(refTable)) {
                    return StringUtils.EMPTY;
                }
                List<String> refCols = c.getReferenceColumnNames();
                String refColList = refCols == null ? StringUtils.EMPTY
                        : refCols.stream().map(PostgresAlterTableDDLBuilder::quoteIdentifier)
                                .collect(Collectors.joining(", "));
                return name + "FOREIGN KEY (" + colList + ") REFERENCES "
                        + qualifiedName(refSchema, refTable) + " (" + refColList + ")";
            default:
                return StringUtils.EMPTY;
        }
    }

    private static void appendTableComment(StringBuilder sb, DBTable table) {
        String c = tableComment(table);
        if (StringUtils.isNotBlank(c)) {
            sb.append("COMMENT ON TABLE ")
                    .append(qualifiedName(table.getSchemaName(), table.getName()))
                    .append(" IS ").append(quoteLiteral(c)).append(";\n");
        }
    }

    private static void appendColumnComments(StringBuilder sb, DBTable table) {
        if (CollectionUtils.isEmpty(table.getColumns())) {
            return;
        }
        String qualified = qualifiedName(table.getSchemaName(), table.getName());
        for (DBTableColumn col : table.getColumns()) {
            if (StringUtils.isNotBlank(col.getComment())) {
                sb.append("COMMENT ON COLUMN ").append(qualified)
                        .append(".").append(quoteIdentifier(col.getName()))
                        .append(" IS ").append(quoteLiteral(col.getComment())).append(";\n");
            }
        }
    }

    // ----------------------------------------------------------------------------------------
    // Low-level helpers.
    // ----------------------------------------------------------------------------------------

    private static String safeType(DBTableColumn col) {
        if (StringUtils.isNotBlank(col.getFullTypeName())) {
            return col.getFullTypeName();
        }
        if (StringUtils.isNotBlank(col.getTypeName())) {
            return col.getTypeName();
        }
        return "text";
    }

    private static String tableComment(DBTable table) {
        DBTableOptions opt = table.getTableOptions();
        return opt == null ? null : opt.getComment();
    }

    private static List<DBTableColumn> safeColumns(DBTable t) {
        return t.getColumns() == null ? Collections.emptyList() : t.getColumns();
    }

    private static List<DBTableConstraint> safeConstraints(DBTable t) {
        return t.getConstraints() == null ? Collections.emptyList() : t.getConstraints();
    }

    private static <T extends com.oceanbase.tools.dbbrowser.model.DBObject> Map<String, T> byName(
            List<T> list) {
        Map<String, T> m = new LinkedHashMap<>();
        if (list == null) {
            return m;
        }
        for (T t : list) {
            if (t.name() != null) {
                m.put(t.name(), t);
            }
        }
        return m;
    }

    private static Map<String, String> invert(Map<String, String> m) {
        Map<String, String> r = new HashMap<>();
        for (Map.Entry<String, String> e : m.entrySet()) {
            r.put(e.getValue(), e.getKey());
        }
        return r;
    }

    static String qualifiedName(String schema, String name) {
        if (StringUtils.isBlank(schema)) {
            return quoteIdentifier(name);
        }
        return quoteIdentifier(schema) + "." + quoteIdentifier(name);
    }

    static String quoteIdentifier(String s) {
        if (s == null) {
            return "\"\"";
        }
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    static String quoteLiteral(String s) {
        if (s == null) {
            return "''";
        }
        return "'" + s.replace("'", "''") + "'";
    }
}
