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
package com.oceanbase.odc.plugin.schema.db2;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.pf4j.Extension;

import com.oceanbase.odc.common.unit.BinarySizeUnit;
import com.oceanbase.odc.plugin.schema.db2.utils.DBAccessorUtil;
import com.oceanbase.odc.plugin.schema.obmysql.OBMySQLTableExtension;
import com.oceanbase.tools.dbbrowser.editor.DBTableEditor;
import com.oceanbase.tools.dbbrowser.model.DBConstraintType;
import com.oceanbase.tools.dbbrowser.model.DBObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;
import com.oceanbase.tools.dbbrowser.model.DBTable;
import com.oceanbase.tools.dbbrowser.model.DBTable.DBTableOptions;
import com.oceanbase.tools.dbbrowser.model.DBTableColumn;
import com.oceanbase.tools.dbbrowser.model.DBTableConstraint;
import com.oceanbase.tools.dbbrowser.model.DBTableIndex;
import com.oceanbase.tools.dbbrowser.model.DBTableStats;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;
import com.oceanbase.tools.dbbrowser.stats.DBStatsAccessor;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Extension
public class DB2TableExtension extends OBMySQLTableExtension {

    /**
     * List tables or external tables in a DB2 schema. Uses SYSCAT.TABLES directly to avoid the parent
     * class's dependency on SqlServerSchemaAccessor.getDatabaseName() which uses SQL Server-specific
     * syntax not supported by DB2.
     */
    @Override
    public List<DBObjectIdentity> list(@NonNull Connection connection, @NonNull String schemaName,
            @NonNull DBObjectType tableType) {
        if (tableType == DBObjectType.TABLE) {
            List<String> names = listTableNames(connection, schemaName);
            return names.stream().map(name -> {
                DBObjectIdentity identity = new DBObjectIdentity();
                identity.setType(DBObjectType.TABLE);
                identity.setSchemaName(schemaName);
                identity.setName(name);
                return identity;
            }).collect(Collectors.toList());
        }
        throw new IllegalArgumentException("Unsupported table type for DB2: " + tableType);
    }

    /**
     * Show table names matching a LIKE pattern in a DB2 schema. Uses SYSCAT.TABLES to avoid the parent
     * class's dependency on SqlServerSchemaAccessor which uses SQL Server-specific syntax.
     */
    @Override
    public List<String> showNamesLike(@NonNull Connection connection, @NonNull String schemaName,
            @NonNull String tableNameLike) {
        String sql = "SELECT TABNAME FROM SYSCAT.TABLES WHERE TABSCHEMA = ? AND TYPE = 'T'"
                + " AND TABNAME LIKE ? ORDER BY TABNAME";
        List<String> names = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            ps.setString(2, tableNameLike);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString("TABNAME").trim());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to list DB2 tables like '" + tableNameLike + "'", e);
        }
        return names;
    }

    private List<String> listTableNames(Connection connection, String schemaName) {
        String sql = "SELECT TABNAME FROM SYSCAT.TABLES WHERE TABSCHEMA = ? AND TYPE = 'T' ORDER BY TABNAME";
        List<String> names = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString("TABNAME").trim());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to list DB2 tables", e);
        }
        return names;
    }

    /**
     * Get table detail using DB2-native SYSCAT queries directly instead of going through
     * SqlServerSchemaAccessor, which calls getDatabaseName() using SQL Server-specific syntax.
     */
    @Override
    public DBTable getDetail(@NonNull Connection connection, @NonNull String schemaName, @NonNull String tableName) {
        DBTable table = new DBTable();
        table.setSchemaName(schemaName);
        table.setOwner(schemaName);
        table.setName(tableName);
        table.setType(DBObjectType.TABLE);
        table.setColumns(listDB2TableColumns(connection, schemaName, tableName));
        table.setConstraints(listDB2TableConstraints(connection, schemaName, tableName));
        table.setIndexes(listDB2TableIndexes(connection, schemaName, tableName));
        table.setTableOptions(getDB2TableOptions(connection, schemaName, tableName));
        table.setDDL(generateDB2CreateDDL(connection, schemaName, tableName, table));
        table.setStats(getTableStats(connection, schemaName, tableName));
        return table;
    }

    private List<DBTableColumn> listDB2TableColumns(Connection connection, String schemaName, String tableName) {
        String sql = "SELECT COLNAME, TYPENAME, LENGTH, SCALE, NULLS, DEFAULT, COLNO, REMARKS"
                + " FROM SYSCAT.COLUMNS WHERE TABSCHEMA = ? AND TABNAME = ? ORDER BY COLNO";
        List<DBTableColumn> columns = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DBTableColumn column = new DBTableColumn();
                    column.setName(rs.getString("COLNAME").trim());
                    column.setTypeName(rs.getString("TYPENAME").trim());
                    long length = rs.getLong("LENGTH");
                    column.setMaxLength(length);
                    column.setPrecision(length);
                    int scale = rs.getInt("SCALE");
                    column.setScale(scale);
                    column.setNullable("Y".equalsIgnoreCase(rs.getString("NULLS")));
                    String defaultValue = rs.getString("DEFAULT");
                    column.setDefaultValue(defaultValue != null ? defaultValue.trim() : null);
                    column.setOrdinalPosition(rs.getInt("COLNO") + 1);
                    String remarks = rs.getString("REMARKS");
                    column.setComment(remarks != null ? remarks.trim() : null);
                    columns.add(column);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to list DB2 table columns for {}.{}", schemaName, tableName, e);
        }
        return columns;
    }

    private List<DBTableConstraint> listDB2TableConstraints(Connection connection, String schemaName,
            String tableName) {
        String sql = "SELECT tc.CONSTNAME, tc.TYPE, tc.ENFORCED, kcu.COLNAME"
                + " FROM SYSCAT.TABCONST tc"
                + " LEFT JOIN SYSCAT.KEYCOLUSE kcu ON tc.CONSTNAME = kcu.CONSTNAME"
                + " AND tc.TABSCHEMA = kcu.TABSCHEMA AND tc.TABNAME = kcu.TABNAME"
                + " WHERE tc.TABSCHEMA = ? AND tc.TABNAME = ?"
                + " ORDER BY tc.CONSTNAME, kcu.COLSEQ";
        List<DBTableConstraint> constraints = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                String lastConstName = null;
                DBTableConstraint current = null;
                while (rs.next()) {
                    String constName = rs.getString("CONSTNAME").trim();
                    if (!constName.equals(lastConstName)) {
                        current = new DBTableConstraint();
                        current.setName(constName);
                        current.setSchemaName(schemaName);
                        current.setTableName(tableName);
                        current.setEnabled(!"N".equalsIgnoreCase(rs.getString("ENFORCED")));
                        String type = rs.getString("TYPE").trim();
                        current.setType(mapDB2ConstraintType(type));
                        current.setColumnNames(new ArrayList<>());
                        constraints.add(current);
                        lastConstName = constName;
                    }
                    String colName = rs.getString("COLNAME");
                    if (colName != null && current != null) {
                        current.getColumnNames().add(colName.trim());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to list DB2 table constraints for {}.{}", schemaName, tableName, e);
        }
        return constraints;
    }

    private DBConstraintType mapDB2ConstraintType(String db2Type) {
        switch (db2Type) {
            case "P":
                return DBConstraintType.PRIMARY_KEY;
            case "U":
                return DBConstraintType.UNIQUE_KEY;
            case "F":
                return DBConstraintType.FOREIGN_KEY;
            case "K":
                return DBConstraintType.CHECK;
            default:
                return DBConstraintType.CHECK;
        }
    }

    private List<DBTableIndex> listDB2TableIndexes(Connection connection, String schemaName, String tableName) {
        String sql = "SELECT INDNAME, UNIQUERULE, COLNAMES FROM SYSCAT.INDEXES"
                + " WHERE TABSCHEMA = ? AND TABNAME = ? ORDER BY INDNAME";
        List<DBTableIndex> indexes = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DBTableIndex index = new DBTableIndex();
                    index.setName(rs.getString("INDNAME").trim());
                    String uniqueRule = rs.getString("UNIQUERULE").trim();
                    index.setNonUnique(!"U".equals(uniqueRule) && !"P".equals(uniqueRule));
                    String colNames = rs.getString("COLNAMES");
                    if (colNames != null) {
                        List<String> columns = new ArrayList<>();
                        for (String col : colNames.trim().split("\\+")) {
                            String c = col.replace("-", "").trim();
                            if (!c.isEmpty()) {
                                columns.add(c);
                            }
                        }
                        index.setColumnNames(columns);
                    } else {
                        index.setColumnNames(Collections.emptyList());
                    }
                    indexes.add(index);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to list DB2 table indexes for {}.{}", schemaName, tableName, e);
        }
        return indexes;
    }

    private DBTableOptions getDB2TableOptions(Connection connection, String schemaName, String tableName) {
        DBTableOptions options = new DBTableOptions();
        String sql = "SELECT REMARKS FROM SYSCAT.TABLES WHERE TABSCHEMA = ? AND TABNAME = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String remarks = rs.getString("REMARKS");
                    options.setComment(remarks != null ? remarks.trim() : null);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get DB2 table options for {}.{}", schemaName, tableName, e);
        }
        return options;
    }

    private String generateDB2CreateDDL(Connection connection, String schemaName, String tableName, DBTable table) {
        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE \"").append(schemaName).append("\".\"").append(tableName).append("\" (\n");
        List<DBTableColumn> columns = table.getColumns();
        if (columns != null) {
            for (int i = 0; i < columns.size(); i++) {
                DBTableColumn col = columns.get(i);
                ddl.append("  \"").append(col.getName()).append("\" ").append(col.getTypeName());
                if (col.getMaxLength() != null && col.getMaxLength() > 0
                        && isLengthApplicable(col.getTypeName())) {
                    if (col.getScale() != null && col.getScale() > 0) {
                        ddl.append("(").append(col.getMaxLength()).append(",").append(col.getScale()).append(")");
                    } else {
                        ddl.append("(").append(col.getMaxLength()).append(")");
                    }
                }
                if (col.getNullable() != null && !col.getNullable()) {
                    ddl.append(" NOT NULL");
                }
                if (col.getDefaultValue() != null && !col.getDefaultValue().isEmpty()) {
                    ddl.append(" DEFAULT ").append(col.getDefaultValue());
                }
                if (i < columns.size() - 1) {
                    ddl.append(",");
                }
                ddl.append("\n");
            }
        }
        ddl.append(")");
        return ddl.toString();
    }

    private boolean isLengthApplicable(String typeName) {
        if (typeName == null) {
            return false;
        }
        String upper = typeName.toUpperCase();
        return upper.contains("CHAR") || upper.contains("VARCHAR") || upper.contains("GRAPHIC")
                || upper.equals("DECIMAL") || upper.equals("DECFLOAT") || upper.contains("BINARY");
    }

    @Override
    protected DBSchemaAccessor getSchemaAccessor(Connection connection) {
        return DBAccessorUtil.getSchemaAccessor(connection);
    }

    @Override
    protected DBStatsAccessor getStatsAccessor(Connection connection) {
        return DBAccessorUtil.getStatsAccessor(connection);
    }

    @Override
    protected DBTableEditor getTableEditor(@NonNull Connection connection) {
        return DBAccessorUtil.getTableEditor(connection);
    }

    @Override
    protected DBTableStats getTableStats(@NonNull Connection connection, @NonNull String schemaName,
            @NonNull String tableName) {
        try {
            DBStatsAccessor statsAccessor = getStatsAccessor(connection);
            if (statsAccessor == null) {
                return new DBTableStats();
            }
            DBTableStats tableStats = statsAccessor.getTableStats(schemaName, tableName);
            if (tableStats == null) {
                return new DBTableStats();
            }
            Long dataSizeInBytes = tableStats.getDataSizeInBytes();
            if (dataSizeInBytes == null || dataSizeInBytes < 0) {
                tableStats.setTableSize(null);
            } else {
                tableStats.setTableSize(BinarySizeUnit.B.of(dataSizeInBytes).toString());
            }
            return tableStats;
        } catch (Exception e) {
            log.warn("Failed to get DB2 table stats for {}.{}, returning empty stats", schemaName, tableName, e);
            return new DBTableStats();
        }
    }

    @Override
    public String generateCreateDDL(@NonNull Connection connection, @NonNull DBTable table) {
        return getTableEditor(connection).generateCreateObjectDDL(table);
    }

    @Override
    public String generateUpdateDDL(@NonNull Connection connection, @NonNull DBTable oldTable,
            @NonNull DBTable newTable) {
        return getTableEditor(connection).generateUpdateObjectDDL(oldTable, newTable);
    }

    @Override
    public boolean syncExternalTableFiles(Connection connection, String schemaName, String tableName) {
        throw new UnsupportedOperationException("not implemented yet");
    }
}
