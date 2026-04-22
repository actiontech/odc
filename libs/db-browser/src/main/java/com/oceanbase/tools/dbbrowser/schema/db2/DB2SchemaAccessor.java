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
package com.oceanbase.tools.dbbrowser.schema.db2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcOperations;

import com.oceanbase.tools.dbbrowser.model.DBColumnGroupElement;
import com.oceanbase.tools.dbbrowser.model.DBConstraintType;
import com.oceanbase.tools.dbbrowser.model.DBDatabase;
import com.oceanbase.tools.dbbrowser.model.DBFunction;
import com.oceanbase.tools.dbbrowser.model.DBIndexType;
import com.oceanbase.tools.dbbrowser.model.DBMViewRefreshParameter;
import com.oceanbase.tools.dbbrowser.model.DBMViewRefreshRecord;
import com.oceanbase.tools.dbbrowser.model.DBMViewRefreshRecordParam;
import com.oceanbase.tools.dbbrowser.model.DBMaterializedView;
import com.oceanbase.tools.dbbrowser.model.DBObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;
import com.oceanbase.tools.dbbrowser.model.DBPLObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBPackage;
import com.oceanbase.tools.dbbrowser.model.DBProcedure;
import com.oceanbase.tools.dbbrowser.model.DBSequence;
import com.oceanbase.tools.dbbrowser.model.DBSynonym;
import com.oceanbase.tools.dbbrowser.model.DBSynonymType;
import com.oceanbase.tools.dbbrowser.model.DBTable;
import com.oceanbase.tools.dbbrowser.model.DBTable.DBTableOptions;
import com.oceanbase.tools.dbbrowser.model.DBTableColumn;
import com.oceanbase.tools.dbbrowser.model.DBTableConstraint;
import com.oceanbase.tools.dbbrowser.model.DBTableIndex;
import com.oceanbase.tools.dbbrowser.model.DBTablePartition;
import com.oceanbase.tools.dbbrowser.model.DBTableSubpartitionDefinition;
import com.oceanbase.tools.dbbrowser.model.DBTrigger;
import com.oceanbase.tools.dbbrowser.model.DBType;
import com.oceanbase.tools.dbbrowser.model.DBVariable;
import com.oceanbase.tools.dbbrowser.model.DBView;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * DB2-specific implementation of {@link DBSchemaAccessor}. Uses DB2 SYSCAT catalog views
 * (SYSCAT.SCHEMATA, SYSCAT.TABLES, SYSCAT.COLUMNS, SYSCAT.INDEXES, SYSCAT.TABCONST, etc.) instead
 * of SQL Server system views, avoiding the getDatabaseName() issue.
 */
@Slf4j
public class DB2SchemaAccessor implements DBSchemaAccessor {

    protected final JdbcOperations jdbcOperations;

    public DB2SchemaAccessor(@NonNull JdbcOperations jdbcOperations) {
        this.jdbcOperations = jdbcOperations;
    }

    @Override
    public List<String> showDatabases() {
        String sql = "SELECT SCHEMANAME FROM SYSCAT.SCHEMATA ORDER BY SCHEMANAME";
        return jdbcOperations.queryForList(sql, String.class).stream()
                .map(String::trim).collect(Collectors.toList());
    }

    @Override
    public DBDatabase getDatabase(String schemaName) {
        DBDatabase db = new DBDatabase();
        db.setId(schemaName);
        db.setName(schemaName);
        return db;
    }

    @Override
    public List<DBDatabase> listDatabases() {
        return showDatabases().stream().map(schema -> {
            DBDatabase db = new DBDatabase();
            db.setId(schema);
            db.setName(schema);
            return db;
        }).collect(Collectors.toList());
    }

    @Override
    public void switchDatabase(String schemaName) {
        jdbcOperations.execute("SET SCHEMA " + schemaName);
    }

    @Override
    public List<DBObjectIdentity> listUsers() {
        return Collections.emptyList();
    }

    @Override
    public List<String> showTables(String schemaName) {
        return showTablesLike(schemaName, null);
    }

    @Override
    public List<String> showTablesLike(String schemaName, String tableNameLike) {
        String sql;
        if (tableNameLike == null || tableNameLike.isEmpty() || "%".equals(tableNameLike)) {
            sql = "SELECT TABNAME FROM SYSCAT.TABLES WHERE TABSCHEMA = ? AND TYPE = 'T' ORDER BY TABNAME";
            return jdbcOperations.query(sql, new Object[] {schemaName},
                    (rs, rowNum) -> rs.getString("TABNAME").trim());
        }
        sql = "SELECT TABNAME FROM SYSCAT.TABLES WHERE TABSCHEMA = ? AND TYPE = 'T'"
                + " AND TABNAME LIKE ? ORDER BY TABNAME";
        return jdbcOperations.query(sql, new Object[] {schemaName, tableNameLike},
                (rs, rowNum) -> rs.getString("TABNAME").trim());
    }

    @Override
    public List<DBObjectIdentity> listTables(String schemaName, String tableNameLike) {
        List<String> names;
        if (tableNameLike == null || tableNameLike.isEmpty()) {
            names = showTables(schemaName);
        } else {
            names = showTablesLike(schemaName, "%" + tableNameLike + "%");
        }
        return names.stream().map(name -> {
            DBObjectIdentity identity = new DBObjectIdentity();
            identity.setType(DBObjectType.TABLE);
            identity.setSchemaName(schemaName);
            identity.setName(name);
            return identity;
        }).collect(Collectors.toList());
    }

    @Override
    public List<String> showExternalTablesLike(String schemaName, String tableNameLike) {
        return Collections.emptyList();
    }

    @Override
    public List<DBObjectIdentity> listExternalTables(String schemaName, String tableNameLike) {
        return Collections.emptyList();
    }

    @Override
    public boolean isExternalTable(String schemaName, String tableName) {
        return false;
    }

    @Override
    public boolean syncExternalTableFiles(String schemaName, String tableName) {
        return false;
    }

    @Override
    public List<DBObjectIdentity> listViews(String schemaName) {
        String sql = "SELECT TABNAME FROM SYSCAT.TABLES WHERE TABSCHEMA = ? AND TYPE = 'V' ORDER BY TABNAME";
        return jdbcOperations.query(sql, new Object[] {schemaName}, (rs, rowNum) -> {
            DBObjectIdentity identity = new DBObjectIdentity();
            identity.setType(DBObjectType.VIEW);
            identity.setSchemaName(schemaName);
            identity.setName(rs.getString("TABNAME").trim());
            return identity;
        });
    }

    @Override
    public List<DBObjectIdentity> listAllViews(String viewNameLike) {
        return Collections.emptyList();
    }

    @Override
    public List<DBObjectIdentity> listAllUserViews(String viewNameLike) {
        return Collections.emptyList();
    }

    @Override
    public List<DBObjectIdentity> listAllSystemViews(String viewNameLike) {
        return Collections.emptyList();
    }

    @Override
    public List<String> showSystemViews(String schemaName) {
        return Collections.emptyList();
    }

    @Override
    public List<DBObjectIdentity> listMViews(String schemaName) {
        return Collections.emptyList();
    }

    @Override
    public List<DBObjectIdentity> listAllMViewsLike(String mViewNameLike) {
        return Collections.emptyList();
    }

    @Override
    public Boolean refreshMVData(DBMViewRefreshParameter parameter) {
        throw new UnsupportedOperationException("Not supported for DB2");
    }

    @Override
    public DBMaterializedView getMView(String schemaName, String mViewName) {
        throw new UnsupportedOperationException("Not supported for DB2");
    }

    @Override
    public List<DBTableConstraint> listMViewConstraints(String schemaName, String mViewName) {
        return Collections.emptyList();
    }

    @Override
    public List<DBMViewRefreshRecord> listMViewRefreshRecords(DBMViewRefreshRecordParam param) {
        return Collections.emptyList();
    }

    @Override
    public List<DBTableIndex> listMViewIndexes(String schemaName, String mViewName) {
        return Collections.emptyList();
    }

    @Override
    public List<DBVariable> showVariables() {
        return Collections.emptyList();
    }

    @Override
    public List<DBVariable> showSessionVariables() {
        return Collections.emptyList();
    }

    @Override
    public List<DBVariable> showGlobalVariables() {
        return Collections.emptyList();
    }

    @Override
    public List<String> showCharset() {
        return Collections.emptyList();
    }

    @Override
    public List<String> showCollation() {
        return Collections.emptyList();
    }

    @Override
    public List<DBPLObjectIdentity> listFunctions(String schemaName) {
        String sql = "SELECT ROUTINENAME FROM SYSCAT.ROUTINES"
                + " WHERE ROUTINESCHEMA = ? AND ROUTINETYPE = 'F' ORDER BY ROUTINENAME";
        return jdbcOperations.query(sql, new Object[] {schemaName}, (rs, rowNum) -> {
            DBPLObjectIdentity identity = new DBPLObjectIdentity();
            identity.setSchemaName(schemaName);
            identity.setName(rs.getString("ROUTINENAME").trim());
            identity.setType(DBObjectType.FUNCTION);
            return identity;
        });
    }

    @Override
    public List<DBPLObjectIdentity> listProcedures(String schemaName) {
        String sql = "SELECT ROUTINENAME FROM SYSCAT.ROUTINES"
                + " WHERE ROUTINESCHEMA = ? AND ROUTINETYPE = 'P' ORDER BY ROUTINENAME";
        return jdbcOperations.query(sql, new Object[] {schemaName}, (rs, rowNum) -> {
            DBPLObjectIdentity identity = new DBPLObjectIdentity();
            identity.setSchemaName(schemaName);
            identity.setName(rs.getString("ROUTINENAME").trim());
            identity.setType(DBObjectType.PROCEDURE);
            return identity;
        });
    }

    @Override
    public List<DBPLObjectIdentity> listPackages(String schemaName) {
        return Collections.emptyList();
    }

    @Override
    public List<DBPLObjectIdentity> listPackageBodies(String schemaName) {
        return Collections.emptyList();
    }

    @Override
    public List<DBPLObjectIdentity> listTriggers(String schemaName) {
        String sql = "SELECT TRIGNAME FROM SYSCAT.TRIGGERS WHERE TRIGSCHEMA = ? ORDER BY TRIGNAME";
        return jdbcOperations.query(sql, new Object[] {schemaName}, (rs, rowNum) -> {
            DBPLObjectIdentity identity = new DBPLObjectIdentity();
            identity.setSchemaName(schemaName);
            identity.setName(rs.getString("TRIGNAME").trim());
            identity.setType(DBObjectType.TRIGGER);
            return identity;
        });
    }

    @Override
    public List<DBPLObjectIdentity> listTypes(String schemaName) {
        return Collections.emptyList();
    }

    @Override
    public List<DBObjectIdentity> listSequences(String schemaName) {
        String sql = "SELECT SEQNAME FROM SYSCAT.SEQUENCES WHERE SEQSCHEMA = ? ORDER BY SEQNAME";
        return jdbcOperations.query(sql, new Object[] {schemaName}, (rs, rowNum) -> {
            DBObjectIdentity identity = new DBObjectIdentity();
            identity.setSchemaName(schemaName);
            identity.setName(rs.getString("SEQNAME").trim());
            identity.setType(DBObjectType.SEQUENCE);
            return identity;
        });
    }

    @Override
    public List<DBObjectIdentity> listSynonyms(String schemaName, DBSynonymType synonymType) {
        return Collections.emptyList();
    }

    @Override
    public Map<String, List<DBTableColumn>> listTableColumns(String schemaName, List<String> tableNames) {
        Map<String, List<DBTableColumn>> result = new HashMap<>();
        if (tableNames == null || tableNames.isEmpty()) {
            return result;
        }
        for (String tableName : tableNames) {
            result.put(tableName, listTableColumns(schemaName, tableName));
        }
        return result;
    }

    @Override
    public List<DBTableColumn> listTableColumns(String schemaName, String tableName) {
        String sql = "SELECT COLNAME, TYPENAME, LENGTH, SCALE, NULLS, DEFAULT, COLNO, REMARKS"
                + " FROM SYSCAT.COLUMNS WHERE TABSCHEMA = ? AND TABNAME = ? ORDER BY COLNO";
        return jdbcOperations.query(sql, new Object[] {schemaName, tableName}, (rs, rowNum) -> {
            DBTableColumn column = new DBTableColumn();
            column.setSchemaName(schemaName);
            column.setTableName(tableName);
            column.setName(rs.getString("COLNAME").trim());
            column.setTypeName(rs.getString("TYPENAME").trim());
            long length = rs.getLong("LENGTH");
            column.setMaxLength(length);
            column.setPrecision(length);
            column.setScale(rs.getInt("SCALE"));
            column.setNullable("Y".equalsIgnoreCase(rs.getString("NULLS")));
            String defaultValue = rs.getString("DEFAULT");
            column.setDefaultValue(defaultValue != null ? defaultValue.trim() : null);
            column.setOrdinalPosition(rs.getInt("COLNO") + 1);
            String remarks = rs.getString("REMARKS");
            column.setComment(remarks != null ? remarks.trim() : null);
            return column;
        });
    }

    @Override
    public Map<String, List<DBTableColumn>> listBasicTableColumns(String schemaName) {
        String sql = "SELECT TABNAME, COLNAME, TYPENAME FROM SYSCAT.COLUMNS"
                + " WHERE TABSCHEMA = ? ORDER BY TABNAME, COLNO";
        Map<String, List<DBTableColumn>> result = new HashMap<>();
        jdbcOperations.query(sql, new Object[] {schemaName}, rs -> {
            String tableName = rs.getString("TABNAME").trim();
            DBTableColumn column = new DBTableColumn();
            column.setSchemaName(schemaName);
            column.setTableName(tableName);
            column.setName(rs.getString("COLNAME").trim());
            column.setTypeName(rs.getString("TYPENAME").trim());
            result.computeIfAbsent(tableName, k -> new ArrayList<>()).add(column);
        });
        return result;
    }

    @Override
    public List<DBTableColumn> listBasicTableColumns(String schemaName, String tableName) {
        return listTableColumns(schemaName, tableName);
    }

    @Override
    public Map<String, List<DBTableColumn>> listBasicViewColumns(String schemaName) {
        return Collections.emptyMap();
    }

    @Override
    public List<DBTableColumn> listBasicViewColumns(String schemaName, String viewName) {
        return listTableColumns(schemaName, viewName);
    }

    @Override
    public Map<String, List<DBTableColumn>> listBasicExternalTableColumns(String schemaName) {
        return Collections.emptyMap();
    }

    @Override
    public List<DBTableColumn> listBasicExternalTableColumns(String schemaName, String externalTableName) {
        return Collections.emptyList();
    }

    @Override
    public Map<String, List<DBTableColumn>> listBasicMViewColumns(String schemaName) {
        return Collections.emptyMap();
    }

    @Override
    public List<DBTableColumn> listBasicMViewColumns(String schemaName, String externalTableName) {
        return Collections.emptyList();
    }

    @Override
    public Map<String, List<DBTableColumn>> listBasicColumnsInfo(String schemaName) {
        return listBasicTableColumns(schemaName);
    }

    @Override
    public Map<String, List<DBTableIndex>> listTableIndexes(String schemaName) {
        String sql = "SELECT TABNAME, INDNAME, UNIQUERULE, COLNAMES FROM SYSCAT.INDEXES"
                + " WHERE TABSCHEMA = ? ORDER BY TABNAME, INDNAME";
        Map<String, List<DBTableIndex>> result = new HashMap<>();
        jdbcOperations.query(sql, new Object[] {schemaName}, rs -> {
            String tableName = rs.getString("TABNAME").trim();
            DBTableIndex index = new DBTableIndex();
            index.setSchemaName(schemaName);
            index.setTableName(tableName);
            index.setName(rs.getString("INDNAME").trim());
            String uniqueRule = rs.getString("UNIQUERULE").trim();
            index.setNonUnique(!"U".equals(uniqueRule) && !"P".equals(uniqueRule));
            index.setType("P".equals(uniqueRule) ? DBIndexType.UNIQUE : DBIndexType.NORMAL);
            String colNames = rs.getString("COLNAMES");
            index.setColumnNames(parseDB2IndexColumnNames(colNames));
            result.computeIfAbsent(tableName, k -> new ArrayList<>()).add(index);
        });
        return result;
    }

    @Override
    public Map<String, List<DBTableConstraint>> listTableConstraints(String schemaName) {
        String sql = "SELECT tc.TABNAME, tc.CONSTNAME, tc.TYPE, tc.ENFORCED, kcu.COLNAME"
                + " FROM SYSCAT.TABCONST tc"
                + " LEFT JOIN SYSCAT.KEYCOLUSE kcu ON tc.CONSTNAME = kcu.CONSTNAME"
                + " AND tc.TABSCHEMA = kcu.TABSCHEMA AND tc.TABNAME = kcu.TABNAME"
                + " WHERE tc.TABSCHEMA = ?"
                + " ORDER BY tc.TABNAME, tc.CONSTNAME, kcu.COLSEQ";
        Map<String, List<DBTableConstraint>> result = new HashMap<>();
        Map<String, DBTableConstraint> constraintMap = new HashMap<>();
        jdbcOperations.query(sql, new Object[] {schemaName}, rs -> {
            String tableName = rs.getString("TABNAME").trim();
            String constName = rs.getString("CONSTNAME").trim();
            String key = tableName + "." + constName;
            DBTableConstraint constraint = constraintMap.get(key);
            if (constraint == null) {
                constraint = new DBTableConstraint();
                constraint.setName(constName);
                constraint.setSchemaName(schemaName);
                constraint.setTableName(tableName);
                constraint.setEnabled(!"N".equalsIgnoreCase(rs.getString("ENFORCED")));
                constraint.setType(mapDB2ConstraintType(rs.getString("TYPE").trim()));
                constraint.setColumnNames(new ArrayList<>());
                constraintMap.put(key, constraint);
                result.computeIfAbsent(tableName, k -> new ArrayList<>()).add(constraint);
            }
            String colName = rs.getString("COLNAME");
            if (colName != null) {
                constraint.getColumnNames().add(colName.trim());
            }
        });
        return result;
    }

    @Override
    public Map<String, DBTableOptions> listTableOptions(String schemaName) {
        String sql = "SELECT TABNAME, REMARKS FROM SYSCAT.TABLES WHERE TABSCHEMA = ? AND TYPE = 'T'";
        Map<String, DBTableOptions> result = new HashMap<>();
        jdbcOperations.query(sql, new Object[] {schemaName}, rs -> {
            String tableName = rs.getString("TABNAME").trim();
            DBTableOptions options = new DBTableOptions();
            String remarks = rs.getString("REMARKS");
            options.setComment(remarks != null ? remarks.trim() : null);
            result.put(tableName, options);
        });
        return result;
    }

    @Override
    public Map<String, DBTablePartition> listTablePartitions(@NonNull String schemaName, List<String> tableNames) {
        return Collections.emptyMap();
    }

    @Override
    public List<DBTablePartition> listTableRangePartitionInfo(String tenantName) {
        return Collections.emptyList();
    }

    @Override
    public List<DBTableSubpartitionDefinition> listSubpartitions(String schemaName, String tableName) {
        return Collections.emptyList();
    }

    @Override
    public Boolean isLowerCaseTableName() {
        return false;
    }

    @Override
    public List<DBObjectIdentity> listPartitionTables(String partitionMethod) {
        return Collections.emptyList();
    }

    @Override
    public List<DBTableConstraint> listTableConstraints(String schemaName, String tableName) {
        String sql = "SELECT tc.CONSTNAME, tc.TYPE, tc.ENFORCED, kcu.COLNAME"
                + " FROM SYSCAT.TABCONST tc"
                + " LEFT JOIN SYSCAT.KEYCOLUSE kcu ON tc.CONSTNAME = kcu.CONSTNAME"
                + " AND tc.TABSCHEMA = kcu.TABSCHEMA AND tc.TABNAME = kcu.TABNAME"
                + " WHERE tc.TABSCHEMA = ? AND tc.TABNAME = ?"
                + " ORDER BY tc.CONSTNAME, kcu.COLSEQ";
        Map<String, DBTableConstraint> constraintMap = new HashMap<>();
        List<DBTableConstraint> constraints = new ArrayList<>();
        jdbcOperations.query(sql, new Object[] {schemaName, tableName}, rs -> {
            String constName = rs.getString("CONSTNAME").trim();
            DBTableConstraint constraint = constraintMap.get(constName);
            if (constraint == null) {
                constraint = new DBTableConstraint();
                constraint.setName(constName);
                constraint.setSchemaName(schemaName);
                constraint.setTableName(tableName);
                constraint.setEnabled(!"N".equalsIgnoreCase(rs.getString("ENFORCED")));
                constraint.setType(mapDB2ConstraintType(rs.getString("TYPE").trim()));
                constraint.setColumnNames(new ArrayList<>());
                constraintMap.put(constName, constraint);
                constraints.add(constraint);
            }
            String colName = rs.getString("COLNAME");
            if (colName != null) {
                constraint.getColumnNames().add(colName.trim());
            }
        });
        return constraints;
    }

    @Override
    public DBTablePartition getPartition(String schemaName, String tableName) {
        return null;
    }

    @Override
    public List<DBTableIndex> listTableIndexes(String schemaName, String tableName) {
        String sql = "SELECT INDNAME, UNIQUERULE, COLNAMES FROM SYSCAT.INDEXES"
                + " WHERE TABSCHEMA = ? AND TABNAME = ? ORDER BY INDNAME";
        return jdbcOperations.query(sql, new Object[] {schemaName, tableName}, (rs, rowNum) -> {
            DBTableIndex index = new DBTableIndex();
            index.setSchemaName(schemaName);
            index.setTableName(tableName);
            index.setName(rs.getString("INDNAME").trim());
            String uniqueRule = rs.getString("UNIQUERULE").trim();
            index.setNonUnique(!"U".equals(uniqueRule) && !"P".equals(uniqueRule));
            index.setType("P".equals(uniqueRule) ? DBIndexType.UNIQUE : DBIndexType.NORMAL);
            String colNames = rs.getString("COLNAMES");
            index.setColumnNames(parseDB2IndexColumnNames(colNames));
            return index;
        });
    }

    @Override
    public String getTableDDL(String schemaName, String tableName) {
        // DB2 does not have a simple SHOW CREATE TABLE equivalent.
        // Return a synthetic DDL based on column metadata.
        List<DBTableColumn> columns = listTableColumns(schemaName, tableName);
        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE \"").append(schemaName).append("\".\"").append(tableName).append("\" (\n");
        for (int i = 0; i < columns.size(); i++) {
            DBTableColumn col = columns.get(i);
            ddl.append("  \"").append(col.getName()).append("\" ").append(col.getTypeName());
            if (col.getMaxLength() != null && col.getMaxLength() > 0 && isLengthApplicable(col.getTypeName())) {
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
        ddl.append(")");
        return ddl.toString();
    }

    @Override
    public DBTableOptions getTableOptions(String schemaName, String tableName) {
        DBTableOptions options = new DBTableOptions();
        String sql = "SELECT REMARKS FROM SYSCAT.TABLES WHERE TABSCHEMA = ? AND TABNAME = ?";
        try {
            jdbcOperations.query(sql, new Object[] {schemaName, tableName}, rs -> {
                String remarks = rs.getString("REMARKS");
                options.setComment(remarks != null ? remarks.trim() : null);
            });
        } catch (Exception e) {
            log.warn("Failed to get DB2 table options for {}.{}", schemaName, tableName, e);
        }
        return options;
    }

    @Override
    public DBTableOptions getTableOptions(String schemaName, String tableName, String ddl) {
        return getTableOptions(schemaName, tableName);
    }

    @Override
    public List<DBColumnGroupElement> listTableColumnGroups(String schemaName, String tableName) {
        return Collections.emptyList();
    }

    @Override
    public DBView getView(String schemaName, String viewName) {
        DBView view = new DBView();
        view.setSchemaName(schemaName);
        view.setViewName(viewName);
        String sql = "SELECT TEXT FROM SYSCAT.VIEWS WHERE VIEWSCHEMA = ? AND VIEWNAME = ?";
        try {
            StringBuilder viewText = new StringBuilder();
            jdbcOperations.query(sql, new Object[] {schemaName, viewName}, rs -> {
                viewText.append(rs.getString("TEXT"));
            });
            view.setDdl("CREATE VIEW \"" + schemaName + "\".\"" + viewName + "\" AS " + viewText.toString());
        } catch (Exception e) {
            log.warn("Failed to get DB2 view definition for {}.{}", schemaName, viewName, e);
        }
        view.setColumns(listTableColumns(schemaName, viewName));
        return view;
    }

    @Override
    public DBFunction getFunction(String schemaName, String functionName) {
        throw new UnsupportedOperationException("Not supported for DB2");
    }

    @Override
    public DBProcedure getProcedure(String schemaName, String procedureName) {
        throw new UnsupportedOperationException("Not supported for DB2");
    }

    @Override
    public DBPackage getPackage(String schemaName, String packageName) {
        throw new UnsupportedOperationException("Not supported for DB2");
    }

    @Override
    public DBTrigger getTrigger(String schemaName, String triggerName) {
        throw new UnsupportedOperationException("Not supported for DB2");
    }

    @Override
    public DBType getType(String schemaName, String typeName) {
        throw new UnsupportedOperationException("Not supported for DB2");
    }

    @Override
    public DBSequence getSequence(String schemaName, String sequenceName) {
        throw new UnsupportedOperationException("Not supported for DB2");
    }

    @Override
    public DBSynonym getSynonym(String schemaName, String synonymName, DBSynonymType synonymType) {
        throw new UnsupportedOperationException("Not supported for DB2");
    }

    @Override
    public Map<String, DBTable> getTables(String schemaName, List<String> tableNames) {
        List<String> names = tableNames;
        if (names == null || names.isEmpty()) {
            names = showTables(schemaName);
        }
        Map<String, DBTable> result = new HashMap<>();
        for (String tableName : names) {
            DBTable table = new DBTable();
            table.setSchemaName(schemaName);
            table.setName(tableName);
            table.setColumns(listTableColumns(schemaName, tableName));
            table.setConstraints(listTableConstraints(schemaName, tableName));
            table.setIndexes(listTableIndexes(schemaName, tableName));
            table.setTableOptions(getTableOptions(schemaName, tableName));
            result.put(tableName, table);
        }
        return result;
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

    private List<String> parseDB2IndexColumnNames(String colNames) {
        List<String> columns = new ArrayList<>();
        if (colNames != null) {
            for (String col : colNames.trim().split("\\+")) {
                String c = col.replace("-", "").trim();
                if (!c.isEmpty()) {
                    columns.add(c);
                }
            }
        }
        return columns;
    }

    private boolean isLengthApplicable(String typeName) {
        if (typeName == null) {
            return false;
        }
        String upper = typeName.toUpperCase();
        return upper.contains("CHAR") || upper.contains("VARCHAR") || upper.contains("GRAPHIC")
                || upper.equals("DECIMAL") || upper.equals("DECFLOAT") || upper.contains("BINARY");
    }
}
