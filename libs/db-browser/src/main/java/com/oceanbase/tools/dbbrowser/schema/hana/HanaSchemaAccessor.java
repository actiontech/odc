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
package com.oceanbase.tools.dbbrowser.schema.hana;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcOperations;

import com.oceanbase.tools.dbbrowser.model.DBColumnGroupElement;
import com.oceanbase.tools.dbbrowser.model.DBConstraintType;
import com.oceanbase.tools.dbbrowser.model.DBDatabase;
import com.oceanbase.tools.dbbrowser.model.DBForeignKeyModifyRule;
import com.oceanbase.tools.dbbrowser.model.DBFunction;
import com.oceanbase.tools.dbbrowser.model.DBIndexType;
import com.oceanbase.tools.dbbrowser.model.DBMViewRefreshParameter;
import com.oceanbase.tools.dbbrowser.model.DBMViewRefreshRecord;
import com.oceanbase.tools.dbbrowser.model.DBMViewRefreshRecordParam;
import com.oceanbase.tools.dbbrowser.model.DBMaterializedView;
import com.oceanbase.tools.dbbrowser.model.DBObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;
import com.oceanbase.tools.dbbrowser.model.DBPLObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBPLParam;
import com.oceanbase.tools.dbbrowser.model.DBPLParamMode;
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
import com.oceanbase.tools.dbbrowser.util.HanaSqlBuilder;
import com.oceanbase.tools.dbbrowser.util.StringUtils;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * SAP HANA database schema accessor. Queries HANA system views (SYS.SCHEMAS, SYS.TABLES,
 * SYS.TABLE_COLUMNS, SYS.INDEXES, SYS.INDEX_COLUMNS, SYS.CONSTRAINTS, SYS.VIEWS, SYS.FUNCTIONS,
 * SYS.PROCEDURES, SYS.TRIGGERS, SYS.SEQUENCES, etc.) for metadata browsing.
 *
 * <p>
 * HANA uses a two-level naming structure: schema.object. Unquoted identifiers are stored in upper
 * case. DDL retrieval uses SYS.GET_OBJECT_DEFINITION stored procedure via
 * {@link HanaSchemaUtil#getObjectDDL}.
 * </p>
 */
@Slf4j
public class HanaSchemaAccessor implements DBSchemaAccessor {

    protected JdbcOperations jdbcOperations;

    public HanaSchemaAccessor(@NonNull JdbcOperations jdbcOperations) {
        this.jdbcOperations = jdbcOperations;
    }

    // ======================== Database/Schema methods ========================

    @Override
    public List<String> showDatabases() {
        String sql = "SELECT SCHEMA_NAME FROM SYS.SCHEMAS"
                + " WHERE HAS_PRIVILEGES='TRUE'"
                + " ORDER BY SCHEMA_NAME";
        try {
            return jdbcOperations.queryForList(sql, String.class);
        } catch (Exception e) {
            log.warn("Failed to list schemas: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public DBDatabase getDatabase(String schemaName) {
        DBDatabase database = new DBDatabase();
        database.setId(schemaName);
        database.setName(schemaName);
        return database;
    }

    @Override
    public List<DBDatabase> listDatabases() {
        String sql = "SELECT SCHEMA_NAME FROM SYS.SCHEMAS"
                + " WHERE HAS_PRIVILEGES='TRUE'"
                + " ORDER BY SCHEMA_NAME";
        try {
            return jdbcOperations.query(sql, (rs, rowNum) -> {
                DBDatabase database = new DBDatabase();
                database.setId(rs.getString("SCHEMA_NAME"));
                database.setName(rs.getString("SCHEMA_NAME"));
                return database;
            });
        } catch (Exception e) {
            log.warn("Failed to list databases: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void switchDatabase(String schemaName) {
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.append("SET SCHEMA ");
        sb.identifier(schemaName);
        jdbcOperations.execute(sb.toString());
    }

    @Override
    public List<DBObjectIdentity> listUsers() {
        String sql = "SELECT USER_NAME FROM SYS.USERS ORDER BY USER_NAME";
        try {
            return jdbcOperations.query(sql, (rs, rowNum) -> {
                DBObjectIdentity user = new DBObjectIdentity();
                user.setName(rs.getString("USER_NAME"));
                user.setType(DBObjectType.USER);
                return user;
            });
        } catch (Exception e) {
            log.warn("Failed to list users: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ======================== Table methods ========================

    @Override
    public List<String> showTablesLike(String schemaName, String tableNameLike) {
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.append("SELECT TABLE_NAME FROM SYS.TABLES WHERE SCHEMA_NAME=");
        sb.value(schemaName);
        sb.append(" AND IS_SYSTEM_TABLE=");
        sb.value("FALSE");
        if (StringUtils.isNotBlank(tableNameLike)) {
            sb.append(" AND ").like("TABLE_NAME", tableNameLike);
        }
        sb.append(" ORDER BY TABLE_NAME ASC");
        return jdbcOperations.queryForList(sb.toString(), String.class);
    }

    @Override
    public List<DBObjectIdentity> listTables(String schemaName, String tableNameLike) {
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.append("SELECT SCHEMA_NAME AS schema_name, 'TABLE' AS type, TABLE_NAME AS name");
        sb.append(" FROM SYS.TABLES WHERE IS_SYSTEM_TABLE='FALSE'");
        if (StringUtils.isNotBlank(schemaName)) {
            sb.append(" AND SCHEMA_NAME=");
            sb.value(schemaName);
        }
        if (StringUtils.isNotBlank(tableNameLike)) {
            sb.append(" AND ").like("TABLE_NAME", tableNameLike);
        }
        sb.append(" ORDER BY schema_name, type, name");
        return jdbcOperations.query(sb.toString(), new BeanPropertyRowMapper<>(DBObjectIdentity.class));
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
        throw new UnsupportedOperationException("Not supported for HANA");
    }

    // ======================== View methods ========================

    @Override
    public List<DBObjectIdentity> listViews(String schemaName) {
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.append(
                "SELECT SCHEMA_NAME AS schema_name, 'VIEW' AS type, VIEW_NAME AS name FROM SYS.VIEWS WHERE SCHEMA_NAME=");
        sb.value(schemaName);
        sb.append(" AND VIEW_TYPE='ROW'");
        sb.append(" ORDER BY VIEW_NAME ASC");
        return jdbcOperations.query(sb.toString(), new BeanPropertyRowMapper<>(DBObjectIdentity.class));
    }

    @Override
    public List<DBObjectIdentity> listAllViews(String viewNameLike) {
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.append("SELECT SCHEMA_NAME AS schema_name, VIEW_NAME AS name, 'VIEW' AS type FROM SYS.VIEWS");
        sb.append(" WHERE VIEW_TYPE='ROW'");
        if (StringUtils.isNotBlank(viewNameLike)) {
            sb.append(" AND ").like("VIEW_NAME", viewNameLike);
        }
        sb.append(" ORDER BY name ASC");
        return jdbcOperations.query(sb.toString(), new BeanPropertyRowMapper<>(DBObjectIdentity.class));
    }

    @Override
    public List<DBObjectIdentity> listAllUserViews(String viewNameLike) {
        return listAllViews(viewNameLike);
    }

    @Override
    public List<DBObjectIdentity> listAllSystemViews(String viewNameLike) {
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.append(
                "SELECT SCHEMA_NAME AS schema_name, VIEW_NAME AS name, 'VIEW' AS type FROM SYS.VIEWS WHERE SCHEMA_NAME='SYS'");
        if (StringUtils.isNotBlank(viewNameLike)) {
            sb.append(" AND ").like("VIEW_NAME", viewNameLike);
        }
        sb.append(" ORDER BY name ASC");
        return jdbcOperations.query(sb.toString(), new BeanPropertyRowMapper<>(DBObjectIdentity.class));
    }

    @Override
    public List<String> showSystemViews(String schemaName) {
        if (!"SYS".equalsIgnoreCase(schemaName)) {
            return Collections.emptyList();
        }
        String sql = "SELECT VIEW_NAME FROM SYS.VIEWS WHERE SCHEMA_NAME='SYS' ORDER BY VIEW_NAME";
        return jdbcOperations.queryForList(sql, String.class);
    }

    // ======================== Materialized View methods (not supported) ========================

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
        throw new UnsupportedOperationException("Not supported for HANA");
    }

    @Override
    public DBMaterializedView getMView(String schemaName, String mViewName) {
        throw new UnsupportedOperationException("Not supported for HANA");
    }

    @Override
    public List<DBTableConstraint> listMViewConstraints(String schemaName, String mViewName) {
        throw new UnsupportedOperationException("Not supported for HANA");
    }

    @Override
    public List<DBMViewRefreshRecord> listMViewRefreshRecords(DBMViewRefreshRecordParam param) {
        throw new UnsupportedOperationException("Not supported for HANA");
    }

    @Override
    public List<DBTableIndex> listMViewIndexes(String schemaName, String mViewName) {
        throw new UnsupportedOperationException("Not supported for HANA");
    }

    // ======================== Variable methods ========================

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

    // ======================== PL Object list methods ========================

    @Override
    public List<DBPLObjectIdentity> listFunctions(String schemaName) {
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.append("SELECT SCHEMA_NAME AS schema_name, FUNCTION_NAME AS name, 'FUNCTION' AS type")
                .append(" FROM SYS.FUNCTIONS WHERE SCHEMA_NAME=")
                .value(schemaName)
                .append(" ORDER BY FUNCTION_NAME ASC");
        try {
            return jdbcOperations.query(sb.toString(), (rs, rowNum) -> {
                DBPLObjectIdentity identity = new DBPLObjectIdentity();
                identity.setSchemaName(rs.getString("schema_name"));
                identity.setName(rs.getString("name"));
                identity.setType(DBObjectType.FUNCTION);
                identity.setStatus("VALID");
                return identity;
            });
        } catch (Exception e) {
            log.warn("Failed to list functions in schema {}: {}", schemaName, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<DBPLObjectIdentity> listProcedures(String schemaName) {
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.append("SELECT SCHEMA_NAME AS schema_name, PROCEDURE_NAME AS name, 'PROCEDURE' AS type")
                .append(" FROM SYS.PROCEDURES WHERE SCHEMA_NAME=")
                .value(schemaName)
                .append(" AND IS_VALID='TRUE'")
                .append(" ORDER BY PROCEDURE_NAME ASC");
        try {
            return jdbcOperations.query(sb.toString(), (rs, rowNum) -> {
                DBPLObjectIdentity identity = new DBPLObjectIdentity();
                identity.setSchemaName(rs.getString("schema_name"));
                identity.setName(rs.getString("name"));
                identity.setType(DBObjectType.PROCEDURE);
                identity.setStatus("VALID");
                return identity;
            });
        } catch (Exception e) {
            log.warn("Failed to list procedures in schema {}: {}", schemaName, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<DBPLObjectIdentity> listPackages(String schemaName) {
        // HANA does not support packages
        return Collections.emptyList();
    }

    @Override
    public List<DBPLObjectIdentity> listPackageBodies(String schemaName) {
        // HANA does not support package bodies
        return Collections.emptyList();
    }

    @Override
    public List<DBPLObjectIdentity> listTriggers(String schemaName) {
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.append("SELECT SCHEMA_NAME AS schema_name, TRIGGER_NAME AS name, 'TRIGGER' AS type,")
                .append(" IS_ENABLED")
                .append(" FROM SYS.TRIGGERS WHERE SUBJECT_TABLE_SCHEMA=")
                .value(schemaName)
                .append(" ORDER BY TRIGGER_NAME ASC");
        try {
            return jdbcOperations.query(sb.toString(), (rs, rowNum) -> {
                DBPLObjectIdentity identity = new DBPLObjectIdentity();
                identity.setSchemaName(rs.getString("schema_name"));
                identity.setName(rs.getString("name"));
                identity.setType(DBObjectType.TRIGGER);
                String isEnabled = rs.getString("IS_ENABLED");
                identity.setEnable("TRUE".equalsIgnoreCase(isEnabled));
                identity.setStatus("TRUE".equalsIgnoreCase(isEnabled) ? "ENABLED" : "DISABLED");
                return identity;
            });
        } catch (Exception e) {
            log.warn("Failed to list triggers in schema {}: {}", schemaName, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<DBPLObjectIdentity> listTypes(String schemaName) {
        // HANA does not support user-defined types like Oracle
        return Collections.emptyList();
    }

    @Override
    public List<DBObjectIdentity> listSequences(String schemaName) {
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.append("SELECT SCHEMA_NAME AS schema_name, SEQUENCE_NAME AS name, 'SEQUENCE' AS type")
                .append(" FROM SYS.SEQUENCES WHERE SCHEMA_NAME=")
                .value(schemaName)
                .append(" ORDER BY SEQUENCE_NAME ASC");
        try {
            return jdbcOperations.query(sb.toString(), new BeanPropertyRowMapper<>(DBObjectIdentity.class));
        } catch (Exception e) {
            log.warn("Failed to list sequences in schema {}: {}", schemaName, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<DBObjectIdentity> listSynonyms(String schemaName, DBSynonymType synonymType) {
        // HANA synonyms exist but are not commonly used in the same way as Oracle
        return Collections.emptyList();
    }

    // ======================== Table Column methods ========================

    @Override
    public Map<String, List<DBTableColumn>> listTableColumns(String schemaName, List<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            return Collections.emptyMap();
        }
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.append("SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE_NAME, LENGTH, SCALE,")
                .append(" IS_NULLABLE, DEFAULT_VALUE, POSITION, COMMENTS")
                .append(" FROM SYS.TABLE_COLUMNS WHERE SCHEMA_NAME=")
                .value(schemaName);
        sb.append(" AND TABLE_NAME IN (");
        sb.values(tableNames);
        sb.append(") ORDER BY TABLE_NAME, POSITION");
        List<DBTableColumn> allColumns = jdbcOperations.query(sb.toString(), (rs, rowNum) -> {
            return mapColumn(rs);
        });
        return allColumns.stream().collect(Collectors.groupingBy(DBTableColumn::getTableName));
    }

    @Override
    public List<DBTableColumn> listTableColumns(String schemaName, String tableName) {
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.append("SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE_NAME, LENGTH, SCALE,")
                .append(" IS_NULLABLE, DEFAULT_VALUE, POSITION, COMMENTS")
                .append(" FROM SYS.TABLE_COLUMNS WHERE SCHEMA_NAME=")
                .value(schemaName)
                .append(" AND TABLE_NAME=")
                .value(tableName)
                .append(" ORDER BY POSITION");
        return jdbcOperations.query(sb.toString(), (rs, rowNum) -> {
            return mapColumn(rs);
        });
    }

    @Override
    public Map<String, List<DBTableColumn>> listBasicTableColumns(String schemaName) {
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.append("SELECT t.TABLE_NAME, c.COLUMN_NAME, c.DATA_TYPE_NAME, c.POSITION")
                .append(" FROM SYS.TABLE_COLUMNS c")
                .append(" JOIN SYS.TABLES t ON c.SCHEMA_NAME = t.SCHEMA_NAME AND c.TABLE_NAME = t.TABLE_NAME")
                .append(" WHERE c.SCHEMA_NAME=").value(schemaName)
                .append(" AND t.IS_SYSTEM_TABLE='FALSE'")
                .append(" ORDER BY c.TABLE_NAME, c.POSITION");
        List<DBTableColumn> columns = jdbcOperations.query(sb.toString(), (rs, rowNum) -> {
            return mapBasicColumn(rs);
        });
        return columns.stream().collect(Collectors.groupingBy(DBTableColumn::getTableName));
    }

    @Override
    public List<DBTableColumn> listBasicTableColumns(String schemaName, String tableName) {
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.append("SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE_NAME, POSITION")
                .append(" FROM SYS.TABLE_COLUMNS WHERE SCHEMA_NAME=")
                .value(schemaName)
                .append(" AND TABLE_NAME=")
                .value(tableName)
                .append(" ORDER BY POSITION");
        return jdbcOperations.query(sb.toString(), (rs, rowNum) -> {
            return mapBasicColumn(rs);
        });
    }

    @Override
    public Map<String, List<DBTableColumn>> listBasicViewColumns(String schemaName) {
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.append("SELECT VIEW_NAME AS TABLE_NAME, COLUMN_NAME, DATA_TYPE_NAME, POSITION")
                .append(" FROM SYS.VIEW_COLUMNS WHERE SCHEMA_NAME=")
                .value(schemaName)
                .append(" ORDER BY VIEW_NAME, POSITION");
        List<DBTableColumn> columns = jdbcOperations.query(sb.toString(), (rs, rowNum) -> {
            return mapBasicColumn(rs);
        });
        return columns.stream().collect(Collectors.groupingBy(DBTableColumn::getTableName));
    }

    @Override
    public List<DBTableColumn> listBasicViewColumns(String schemaName, String viewName) {
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.append("SELECT VIEW_NAME AS TABLE_NAME, COLUMN_NAME, DATA_TYPE_NAME, POSITION")
                .append(" FROM SYS.VIEW_COLUMNS WHERE SCHEMA_NAME=")
                .value(schemaName)
                .append(" AND VIEW_NAME=")
                .value(viewName)
                .append(" ORDER BY POSITION");
        return jdbcOperations.query(sb.toString(), (rs, rowNum) -> {
            return mapBasicColumn(rs);
        });
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
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.append("SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE_NAME, POSITION")
                .append(" FROM SYS.TABLE_COLUMNS WHERE SCHEMA_NAME=")
                .value(schemaName)
                .append(" ORDER BY TABLE_NAME, POSITION");
        List<DBTableColumn> columns = jdbcOperations.query(sb.toString(), (rs, rowNum) -> {
            DBTableColumn col = new DBTableColumn();
            col.setSchemaName(schemaName);
            col.setTableName(rs.getString("TABLE_NAME"));
            col.setName(rs.getString("COLUMN_NAME"));
            return col;
        });
        return columns.stream().collect(Collectors.groupingBy(DBTableColumn::getTableName));
    }

    // ======================== Table Index methods ========================

    @Override
    public Map<String, List<DBTableIndex>> listTableIndexes(String schemaName) {
        Map<String, List<DBTableIndex>> tableName2Indexes = new LinkedHashMap<>();
        String sql = "SELECT i.SCHEMA_NAME, i.TABLE_NAME, i.INDEX_NAME, i.INDEX_TYPE, i.CONSTRAINT,"
                + " ic.COLUMN_NAME, ic.POSITION"
                + " FROM SYS.INDEXES i"
                + " JOIN SYS.INDEX_COLUMNS ic"
                + " ON i.SCHEMA_NAME = ic.SCHEMA_NAME"
                + " AND i.TABLE_NAME = ic.TABLE_NAME"
                + " AND i.INDEX_NAME = ic.INDEX_NAME"
                + " WHERE i.SCHEMA_NAME = ?"
                + " ORDER BY i.TABLE_NAME, i.INDEX_NAME, ic.POSITION";
        jdbcOperations.query(sql, new Object[] {schemaName}, (rs, num) -> {
            String tableName = rs.getString("TABLE_NAME");
            String indexName = rs.getString("INDEX_NAME");
            if (tableName2Indexes.containsKey(tableName)) {
                List<DBTableIndex> indexes = tableName2Indexes.get(tableName);
                boolean found = false;
                for (DBTableIndex idx : indexes) {
                    if (idx.getName().equals(indexName)) {
                        idx.getColumnNames().add(rs.getString("COLUMN_NAME"));
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    indexes.add(createIndexFromResultSet(rs, schemaName));
                }
            } else {
                List<DBTableIndex> indexes = new ArrayList<>();
                indexes.add(createIndexFromResultSet(rs, schemaName));
                tableName2Indexes.put(tableName, indexes);
            }
            return null;
        });
        return tableName2Indexes;
    }

    @Override
    public List<DBTableIndex> listTableIndexes(String schemaName, String tableName) {
        String sql = "SELECT i.SCHEMA_NAME, i.TABLE_NAME, i.INDEX_NAME, i.INDEX_TYPE, i.CONSTRAINT,"
                + " ic.COLUMN_NAME, ic.POSITION"
                + " FROM SYS.INDEXES i"
                + " JOIN SYS.INDEX_COLUMNS ic"
                + " ON i.SCHEMA_NAME = ic.SCHEMA_NAME"
                + " AND i.TABLE_NAME = ic.TABLE_NAME"
                + " AND i.INDEX_NAME = ic.INDEX_NAME"
                + " WHERE i.SCHEMA_NAME = ? AND i.TABLE_NAME = ?"
                + " ORDER BY i.INDEX_NAME, ic.POSITION";
        Map<String, DBTableIndex> indexName2Index = new LinkedHashMap<>();
        jdbcOperations.query(sql, new Object[] {schemaName, tableName}, (rs, num) -> {
            String indexName = rs.getString("INDEX_NAME");
            if (indexName2Index.containsKey(indexName)) {
                indexName2Index.get(indexName).getColumnNames().add(rs.getString("COLUMN_NAME"));
            } else {
                indexName2Index.put(indexName, createIndexFromResultSet(rs, schemaName));
            }
            return null;
        });
        return new ArrayList<>(indexName2Index.values());
    }

    // ======================== Table Constraint methods ========================

    @Override
    public Map<String, List<DBTableConstraint>> listTableConstraints(String schemaName) {
        Map<String, List<DBTableConstraint>> tableName2Constraints = new LinkedHashMap<>();
        String sql = "SELECT c.SCHEMA_NAME, c.TABLE_NAME, c.CONSTRAINT_NAME,"
                + " c.IS_PRIMARY_KEY, c.IS_UNIQUE_KEY,"
                + " cc.COLUMN_NAME, cc.POSITION"
                + " FROM SYS.CONSTRAINTS c"
                + " JOIN SYS.CONSTRAINT_COLUMNS cc"
                + " ON c.SCHEMA_NAME = cc.SCHEMA_NAME"
                + " AND c.TABLE_NAME = cc.TABLE_NAME"
                + " AND c.CONSTRAINT_NAME = cc.CONSTRAINT_NAME"
                + " WHERE c.SCHEMA_NAME = ?"
                + " ORDER BY c.TABLE_NAME, c.CONSTRAINT_NAME, cc.POSITION";
        jdbcOperations.query(sql, new Object[] {schemaName}, (rs, num) -> {
            String tableName = rs.getString("TABLE_NAME");
            String constraintName = rs.getString("CONSTRAINT_NAME");
            if (tableName2Constraints.containsKey(tableName)) {
                List<DBTableConstraint> constraints = tableName2Constraints.get(tableName);
                boolean found = false;
                for (DBTableConstraint c : constraints) {
                    if (c.getName().equals(constraintName)) {
                        c.getColumnNames().add(rs.getString("COLUMN_NAME"));
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    constraints.add(createConstraintFromResultSet(rs, schemaName));
                }
            } else {
                List<DBTableConstraint> constraints = new ArrayList<>();
                constraints.add(createConstraintFromResultSet(rs, schemaName));
                tableName2Constraints.put(tableName, constraints);
            }
            return null;
        });
        return tableName2Constraints;
    }

    @Override
    public List<DBTableConstraint> listTableConstraints(String schemaName, String tableName) {
        // First query basic constraints from SYS.CONSTRAINTS + SYS.CONSTRAINT_COLUMNS
        String sql = "SELECT c.SCHEMA_NAME, c.TABLE_NAME, c.CONSTRAINT_NAME,"
                + " c.IS_PRIMARY_KEY, c.IS_UNIQUE_KEY,"
                + " cc.COLUMN_NAME, cc.POSITION"
                + " FROM SYS.CONSTRAINTS c"
                + " JOIN SYS.CONSTRAINT_COLUMNS cc"
                + " ON c.SCHEMA_NAME = cc.SCHEMA_NAME"
                + " AND c.TABLE_NAME = cc.TABLE_NAME"
                + " AND c.CONSTRAINT_NAME = cc.CONSTRAINT_NAME"
                + " WHERE c.SCHEMA_NAME = ? AND c.TABLE_NAME = ?"
                + " ORDER BY c.CONSTRAINT_NAME, cc.POSITION";
        Map<String, DBTableConstraint> name2Constraint = new LinkedHashMap<>();
        jdbcOperations.query(sql, new Object[] {schemaName, tableName}, (rs, num) -> {
            String constraintName = rs.getString("CONSTRAINT_NAME");
            if (name2Constraint.containsKey(constraintName)) {
                name2Constraint.get(constraintName).getColumnNames().add(rs.getString("COLUMN_NAME"));
            } else {
                name2Constraint.put(constraintName, createConstraintFromResultSet(rs, schemaName));
            }
            return null;
        });

        // Query foreign key constraints from SYS.REFERENTIAL_CONSTRAINTS
        String fkSql = "SELECT rc.CONSTRAINT_NAME, rc.REFERENCED_SCHEMA_NAME,"
                + " rc.REFERENCED_TABLE_NAME, rc.UPDATE_RULE, rc.DELETE_RULE,"
                + " cc.COLUMN_NAME, cc.POSITION"
                + " FROM SYS.REFERENTIAL_CONSTRAINTS rc"
                + " JOIN SYS.CONSTRAINT_COLUMNS cc"
                + " ON rc.SCHEMA_NAME = cc.SCHEMA_NAME"
                + " AND rc.TABLE_NAME = cc.TABLE_NAME"
                + " AND rc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME"
                + " WHERE rc.SCHEMA_NAME = ? AND rc.TABLE_NAME = ?"
                + " ORDER BY rc.CONSTRAINT_NAME, cc.POSITION";
        Map<String, DBTableConstraint> fkName2Constraint = new LinkedHashMap<>();
        jdbcOperations.query(fkSql, new Object[] {schemaName, tableName}, (rs, num) -> {
            String constraintName = rs.getString("CONSTRAINT_NAME");
            if (fkName2Constraint.containsKey(constraintName)) {
                fkName2Constraint.get(constraintName).getColumnNames().add(rs.getString("COLUMN_NAME"));
            } else {
                DBTableConstraint fk = new DBTableConstraint();
                fk.setName(constraintName);
                fk.setSchemaName(schemaName);
                fk.setTableName(tableName);
                fk.setType(DBConstraintType.FOREIGN_KEY);
                fk.setEnabled(true);
                fk.setReferenceSchemaName(rs.getString("REFERENCED_SCHEMA_NAME"));
                fk.setReferenceTableName(rs.getString("REFERENCED_TABLE_NAME"));
                fk.setOnDeleteRule(mapForeignKeyRule(rs.getString("DELETE_RULE")));
                fk.setOnUpdateRule(mapForeignKeyRule(rs.getString("UPDATE_RULE")));
                List<String> colNames = new ArrayList<>();
                colNames.add(rs.getString("COLUMN_NAME"));
                fk.setColumnNames(colNames);
                fk.setReferenceColumnNames(new ArrayList<>());
                fkName2Constraint.put(constraintName, fk);
            }
            return null;
        });

        // Merge FK constraints
        fkName2Constraint.forEach((name, fk) -> {
            if (!name2Constraint.containsKey(name)) {
                name2Constraint.put(name, fk);
            }
        });

        return new ArrayList<>(name2Constraint.values());
    }

    @Override
    public Map<String, DBTableOptions> listTableOptions(String schemaName) {
        Map<String, DBTableOptions> result = new LinkedHashMap<>();
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.append("SELECT TABLE_NAME, COMMENTS FROM SYS.TABLES")
                .append(" WHERE SCHEMA_NAME=").value(schemaName)
                .append(" AND IS_SYSTEM_TABLE='FALSE'")
                .append(" ORDER BY TABLE_NAME");
        jdbcOperations.query(sb.toString(), (rs, num) -> {
            DBTableOptions options = new DBTableOptions();
            options.setComment(rs.getString("COMMENTS"));
            result.put(rs.getString("TABLE_NAME"), options);
            return null;
        });
        return result;
    }

    @Override
    public Map<String, DBTablePartition> listTablePartitions(@NonNull String schemaName, List<String> tableNames) {
        // HANA partitioning is not exposed in the same way as Oracle/MySQL
        return Collections.emptyMap();
    }

    @Override
    public List<DBTablePartition> listTableRangePartitionInfo(String tenantName) {
        throw new UnsupportedOperationException("Not supported for HANA");
    }

    @Override
    public List<DBTableSubpartitionDefinition> listSubpartitions(String schemaName, String tableName) {
        throw new UnsupportedOperationException("Not supported for HANA");
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
    public DBTablePartition getPartition(String schemaName, String tableName) {
        return null;
    }

    // ======================== DDL and Table Detail methods ========================

    @Override
    public String getTableDDL(String schemaName, String tableName) {
        return HanaSchemaUtil.getObjectDDL(jdbcOperations, schemaName, tableName);
    }

    @Override
    public DBTableOptions getTableOptions(String schemaName, String tableName) {
        DBTableOptions options = new DBTableOptions();
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.append("SELECT COMMENTS FROM SYS.TABLES WHERE SCHEMA_NAME=")
                .value(schemaName)
                .append(" AND TABLE_NAME=")
                .value(tableName);
        try {
            jdbcOperations.query(sb.toString(), rs -> {
                options.setComment(rs.getString("COMMENTS"));
            });
        } catch (Exception e) {
            log.warn("Failed to get table options for {}.{}: {}", schemaName, tableName, e.getMessage());
        }
        return options;
    }

    @Override
    public DBTableOptions getTableOptions(String schemaName, String tableName, String ddl) {
        return getTableOptions(schemaName, tableName);
    }

    @Override
    public List<DBColumnGroupElement> listTableColumnGroups(String schemaName, String tableName) {
        throw new UnsupportedOperationException("Not supported for HANA");
    }

    // ======================== Object Detail methods ========================

    @Override
    public DBView getView(String schemaName, String viewName) {
        DBView view = new DBView();
        view.setViewName(viewName);
        view.setSchemaName(schemaName);
        view.setDefiner(schemaName);

        // Get view DDL
        String ddl = HanaSchemaUtil.getObjectDDL(jdbcOperations, schemaName, viewName);
        view.setDdl(ddl);

        // Get view columns
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.append("SELECT VIEW_NAME, COLUMN_NAME, DATA_TYPE_NAME, POSITION, COMMENTS")
                .append(" FROM SYS.VIEW_COLUMNS WHERE SCHEMA_NAME=")
                .value(schemaName)
                .append(" AND VIEW_NAME=")
                .value(viewName)
                .append(" ORDER BY POSITION");
        List<DBTableColumn> columns = jdbcOperations.query(sb.toString(), (rs, rowNum) -> {
            DBTableColumn column = new DBTableColumn();
            column.setName(rs.getString("COLUMN_NAME"));
            column.setTypeName(rs.getString("DATA_TYPE_NAME"));
            column.setComment(rs.getString("COMMENTS"));
            column.setOrdinalPosition(rs.getInt("POSITION"));
            column.setTableName(viewName);
            return column;
        });
        view.setColumns(columns);
        return view;
    }

    @Override
    public DBFunction getFunction(String schemaName, String functionName) {
        DBFunction function = new DBFunction();
        function.setFunName(functionName);
        function.setDefiner(schemaName);

        // Get function DDL
        String ddl = HanaSchemaUtil.getObjectDDL(jdbcOperations, schemaName, functionName);
        function.setDdl(ddl);

        // Get function parameters from SYS.FUNCTION_PARAMETERS
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.append("SELECT PARAMETER_NAME, DATA_TYPE_NAME, PARAMETER_TYPE, POSITION")
                .append(" FROM SYS.FUNCTION_PARAMETERS WHERE SCHEMA_NAME=")
                .value(schemaName)
                .append(" AND FUNCTION_NAME=")
                .value(functionName)
                .append(" ORDER BY POSITION");
        List<DBPLParam> params = new ArrayList<>();
        try {
            jdbcOperations.query(sb.toString(), rs -> {
                String paramName = rs.getString("PARAMETER_NAME");
                String paramType = rs.getString("PARAMETER_TYPE");
                if ("RETURN".equalsIgnoreCase(paramType)) {
                    function.setReturnType(rs.getString("DATA_TYPE_NAME"));
                } else {
                    DBPLParam param = new DBPLParam();
                    param.setParamName(paramName);
                    param.setDataType(rs.getString("DATA_TYPE_NAME"));
                    param.setSeqNum(rs.getInt("POSITION"));
                    param.setParamMode(mapParamMode(paramType));
                    params.add(param);
                }
            });
        } catch (Exception e) {
            log.warn("Failed to get function parameters for {}.{}: {}", schemaName, functionName, e.getMessage());
        }
        function.setParams(params);
        function.setStatus("VALID");
        return function;
    }

    @Override
    public DBProcedure getProcedure(String schemaName, String procedureName) {
        DBProcedure procedure = new DBProcedure();
        procedure.setProName(procedureName);
        procedure.setDefiner(schemaName);

        // Get procedure DDL
        String ddl = HanaSchemaUtil.getObjectDDL(jdbcOperations, schemaName, procedureName);
        procedure.setDdl(ddl);

        // Get procedure parameters from SYS.PROCEDURE_PARAMETERS
        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.append("SELECT PARAMETER_NAME, DATA_TYPE_NAME, PARAMETER_TYPE, POSITION")
                .append(" FROM SYS.PROCEDURE_PARAMETERS WHERE SCHEMA_NAME=")
                .value(schemaName)
                .append(" AND PROCEDURE_NAME=")
                .value(procedureName)
                .append(" ORDER BY POSITION");
        List<DBPLParam> params = new ArrayList<>();
        try {
            jdbcOperations.query(sb.toString(), rs -> {
                DBPLParam param = new DBPLParam();
                param.setParamName(rs.getString("PARAMETER_NAME"));
                param.setDataType(rs.getString("DATA_TYPE_NAME"));
                param.setSeqNum(rs.getInt("POSITION"));
                param.setParamMode(mapParamMode(rs.getString("PARAMETER_TYPE")));
                params.add(param);
            });
        } catch (Exception e) {
            log.warn("Failed to get procedure parameters for {}.{}: {}", schemaName, procedureName, e.getMessage());
        }
        procedure.setParams(params);
        procedure.setStatus("VALID");
        return procedure;
    }

    @Override
    public DBPackage getPackage(String schemaName, String packageName) {
        throw new UnsupportedOperationException("Not supported for HANA");
    }

    @Override
    public DBTrigger getTrigger(String schemaName, String triggerName) {
        DBTrigger trigger = new DBTrigger();
        trigger.setTriggerName(triggerName);
        trigger.setOwner(schemaName);

        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.append("SELECT SCHEMA_NAME, TRIGGER_NAME, SUBJECT_TABLE_SCHEMA, SUBJECT_TABLE_NAME,")
                .append(" TRIGGER_EVENT, TRIGGERED_ACTIONTIME, IS_ENABLED")
                .append(" FROM SYS.TRIGGERS WHERE SUBJECT_TABLE_SCHEMA=")
                .value(schemaName)
                .append(" AND TRIGGER_NAME=")
                .value(triggerName);
        try {
            jdbcOperations.query(sb.toString(), rs -> {
                trigger.setSchemaMode(rs.getString("SUBJECT_TABLE_SCHEMA"));
                trigger.setSchemaName(rs.getString("SUBJECT_TABLE_NAME"));
                trigger.setBaseObjectType("TABLE");
                trigger.setEnable("TRUE".equalsIgnoreCase(rs.getString("IS_ENABLED")));
                trigger.setStatus(trigger.isEnable() ? "ENABLED" : "DISABLED");
            });
        } catch (Exception e) {
            log.warn("Failed to get trigger info for {}.{}: {}", schemaName, triggerName, e.getMessage());
        }

        // Get trigger DDL
        String ddl = HanaSchemaUtil.getObjectDDL(jdbcOperations, schemaName, triggerName);
        trigger.setDdl(ddl);
        return trigger;
    }

    @Override
    public DBType getType(String schemaName, String typeName) {
        throw new UnsupportedOperationException("Not supported for HANA");
    }

    @Override
    public DBSequence getSequence(String schemaName, String sequenceName) {
        DBSequence sequence = new DBSequence();
        sequence.setName(sequenceName);

        HanaSqlBuilder sb = new HanaSqlBuilder();
        sb.append("SELECT SCHEMA_NAME, SEQUENCE_NAME, START_NUMBER, MIN_VALUE, MAX_VALUE,")
                .append(" INCREMENT_BY, IS_CYCLED, CACHE_SIZE")
                .append(" FROM SYS.SEQUENCES WHERE SCHEMA_NAME=")
                .value(schemaName)
                .append(" AND SEQUENCE_NAME=")
                .value(sequenceName);
        try {
            jdbcOperations.query(sb.toString(), rs -> {
                sequence.setUser(rs.getString("SCHEMA_NAME"));
                sequence.setMinValue(rs.getString("MIN_VALUE"));
                sequence.setMaxValue(rs.getString("MAX_VALUE"));
                sequence.setIncreament(rs.getLong("INCREMENT_BY"));
                sequence.setCycled("TRUE".equalsIgnoreCase(rs.getString("IS_CYCLED")));
                long cacheSize = rs.getLong("CACHE_SIZE");
                if (cacheSize > 1) {
                    sequence.setCacheSize(cacheSize);
                    sequence.setCached(true);
                } else {
                    sequence.setCached(false);
                }
            });
        } catch (Exception e) {
            log.warn("Failed to get sequence info for {}.{}: {}", schemaName, sequenceName, e.getMessage());
        }

        // Get sequence DDL
        String ddl = HanaSchemaUtil.getObjectDDL(jdbcOperations, schemaName, sequenceName);
        sequence.setDdl(ddl);
        return sequence;
    }

    @Override
    public DBSynonym getSynonym(String schemaName, String synonymName, DBSynonymType synonymType) {
        throw new UnsupportedOperationException("Not supported for HANA");
    }

    @Override
    public Map<String, DBTable> getTables(String schemaName, List<String> tableNames) {
        throw new UnsupportedOperationException("Not supported for HANA");
    }

    // ======================== Private helper methods ========================

    private DBTableColumn mapColumn(ResultSet rs) throws SQLException {
        DBTableColumn column = new DBTableColumn();
        column.setTableName(rs.getString("TABLE_NAME"));
        column.setName(rs.getString("COLUMN_NAME"));
        column.setTypeName(rs.getString("DATA_TYPE_NAME"));
        column.setNullable("TRUE".equalsIgnoreCase(rs.getString("IS_NULLABLE")));
        column.setDefaultValue(rs.getString("DEFAULT_VALUE"));
        column.setComment(rs.getString("COMMENTS"));
        column.setOrdinalPosition(rs.getInt("POSITION"));
        // Set precision and scale for numeric types
        long length = rs.getLong("LENGTH");
        int scale = rs.getInt("SCALE");
        column.setMaxLength(length);
        column.setScale(scale);
        return column;
    }

    private DBTableColumn mapBasicColumn(ResultSet rs) throws SQLException {
        DBTableColumn column = new DBTableColumn();
        column.setTableName(rs.getString("TABLE_NAME"));
        column.setName(rs.getString("COLUMN_NAME"));
        column.setTypeName(rs.getString("DATA_TYPE_NAME"));
        column.setOrdinalPosition(rs.getInt("POSITION"));
        return column;
    }

    private DBTableIndex createIndexFromResultSet(ResultSet rs, String schemaName) throws SQLException {
        DBTableIndex index = new DBTableIndex();
        index.setName(rs.getString("INDEX_NAME"));
        index.setSchemaName(schemaName);
        index.setTableName(rs.getString("TABLE_NAME"));
        String indexType = rs.getString("INDEX_TYPE");
        String constraint = rs.getString("CONSTRAINT");
        // Map HANA index types
        if ("PRIMARY KEY".equalsIgnoreCase(constraint)) {
            index.setType(DBIndexType.UNIQUE);
            index.setNonUnique(false);
        } else if ("UNIQUE".equalsIgnoreCase(constraint) || "NOT NULL UNIQUE".equalsIgnoreCase(constraint)) {
            index.setType(DBIndexType.UNIQUE);
            index.setNonUnique(false);
        } else {
            index.setType(DBIndexType.NORMAL);
            index.setNonUnique(true);
        }
        index.setVisible(true);
        index.setAvailable(true);
        List<String> columnNames = new ArrayList<>();
        columnNames.add(rs.getString("COLUMN_NAME"));
        index.setColumnNames(columnNames);
        return index;
    }

    private DBTableConstraint createConstraintFromResultSet(ResultSet rs, String schemaName) throws SQLException {
        DBTableConstraint constraint = new DBTableConstraint();
        constraint.setName(rs.getString("CONSTRAINT_NAME"));
        constraint.setSchemaName(schemaName);
        constraint.setTableName(rs.getString("TABLE_NAME"));
        constraint.setEnabled(true);
        String isPrimaryKey = rs.getString("IS_PRIMARY_KEY");
        String isUniqueKey = rs.getString("IS_UNIQUE_KEY");
        if ("TRUE".equalsIgnoreCase(isPrimaryKey)) {
            constraint.setType(DBConstraintType.PRIMARY_KEY);
        } else if ("TRUE".equalsIgnoreCase(isUniqueKey)) {
            constraint.setType(DBConstraintType.UNIQUE_KEY);
        } else {
            constraint.setType(DBConstraintType.CHECK);
        }
        List<String> columnNames = new ArrayList<>();
        columnNames.add(rs.getString("COLUMN_NAME"));
        constraint.setColumnNames(columnNames);
        constraint.setReferenceColumnNames(new ArrayList<>());
        return constraint;
    }

    private DBForeignKeyModifyRule mapForeignKeyRule(String rule) {
        if (rule == null) {
            return DBForeignKeyModifyRule.NO_ACTION;
        }
        switch (rule.toUpperCase()) {
            case "CASCADE":
                return DBForeignKeyModifyRule.CASCADE;
            case "SET NULL":
                return DBForeignKeyModifyRule.SET_NULL;
            case "SET DEFAULT":
                return DBForeignKeyModifyRule.SET_DEFAULT;
            case "RESTRICT":
                return DBForeignKeyModifyRule.RESTRICT;
            default:
                return DBForeignKeyModifyRule.NO_ACTION;
        }
    }

    private DBPLParamMode mapParamMode(String parameterType) {
        if (parameterType == null) {
            return DBPLParamMode.IN;
        }
        switch (parameterType.toUpperCase()) {
            case "IN":
                return DBPLParamMode.IN;
            case "OUT":
                return DBPLParamMode.OUT;
            case "INOUT":
                return DBPLParamMode.INOUT;
            default:
                return DBPLParamMode.IN;
        }
    }
}
