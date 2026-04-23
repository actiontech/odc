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
package com.oceanbase.tools.dbbrowser.schema.dm;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.NonNull;

import com.oceanbase.tools.dbbrowser.model.DBColumnGroupElement;
import com.oceanbase.tools.dbbrowser.model.DBColumnTypeDisplay;
import com.oceanbase.tools.dbbrowser.model.DBConstraintDeferability;
import com.oceanbase.tools.dbbrowser.model.DBConstraintType;
import com.oceanbase.tools.dbbrowser.model.DBDatabase;
import com.oceanbase.tools.dbbrowser.model.DBForeignKeyModifyRule;
import com.oceanbase.tools.dbbrowser.model.DBFunction;
import com.oceanbase.tools.dbbrowser.model.DBIndexAlgorithm;
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
import com.oceanbase.tools.dbbrowser.model.DBPackageBasicInfo;
import com.oceanbase.tools.dbbrowser.model.DBPackageDetail;
import com.oceanbase.tools.dbbrowser.model.DBProcedure;
import com.oceanbase.tools.dbbrowser.model.DBSequence;
import com.oceanbase.tools.dbbrowser.model.DBSynonym;
import com.oceanbase.tools.dbbrowser.model.DBSynonymType;
import com.oceanbase.tools.dbbrowser.model.DBTable;
import com.oceanbase.tools.dbbrowser.model.DBTable.DBTableOptions;
import com.oceanbase.tools.dbbrowser.model.DBTableColumn;
import com.oceanbase.tools.dbbrowser.model.DBTableColumn.CharUnit;
import com.oceanbase.tools.dbbrowser.model.DBTableConstraint;
import com.oceanbase.tools.dbbrowser.model.DBTableIndex;
import com.oceanbase.tools.dbbrowser.model.DBTablePartition;
import com.oceanbase.tools.dbbrowser.model.DBTablePartitionDefinition;
import com.oceanbase.tools.dbbrowser.model.DBTablePartitionOption;
import com.oceanbase.tools.dbbrowser.model.DBTablePartitionType;
import com.oceanbase.tools.dbbrowser.model.DBTableSubpartitionDefinition;
import com.oceanbase.tools.dbbrowser.model.DBTrigger;
import com.oceanbase.tools.dbbrowser.model.DBType;
import com.oceanbase.tools.dbbrowser.model.DBVariable;
import com.oceanbase.tools.dbbrowser.model.DBView;
import com.oceanbase.tools.dbbrowser.model.OracleConstants;
import com.oceanbase.tools.dbbrowser.model.PLConstants;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessorSqlMapper;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessorSqlMappers;
import com.oceanbase.tools.dbbrowser.schema.constant.Statements;
import com.oceanbase.tools.dbbrowser.schema.constant.StatementsFiles;
import com.oceanbase.tools.dbbrowser.util.DBSchemaAccessorUtil;
import com.oceanbase.tools.dbbrowser.util.DmSqlBuilder;
import com.oceanbase.tools.dbbrowser.util.PLObjectErrMsgUtils;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * DM (DaMeng) database schema accessor. DM system views are largely compatible with Oracle
 * (ALL_TABLES, ALL_TAB_COLUMNS, etc.). This class implements DBSchemaAccessor independently (D-003:
 * does not inherit OracleSchemaAccessor).
 */
@Slf4j
public class DmSchemaAccessor implements DBSchemaAccessor {

    private static final String DM_TABLE_COMMENT_DDL_TEMPLATE =
            "COMMENT ON TABLE ${schemaName}.${tableName} IS ${comment}";
    private static final String DM_COLUMN_COMMENT_DDL_TEMPLATE =
            "COMMENT ON COLUMN ${schemaName}.${tableName}.${columnName} IS ${comment}";

    protected JdbcOperations jdbcOperations;
    protected DBSchemaAccessorSqlMapper sqlMapper;

    public DmSchemaAccessor(@NonNull JdbcOperations jdbcOperations) {
        this.jdbcOperations = jdbcOperations;
        this.sqlMapper = DBSchemaAccessorSqlMappers.get(StatementsFiles.DM_8);
    }

    @Override
    public List<String> showDatabases() {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.append("SELECT USERNAME FROM SYS.ALL_USERS ORDER BY USERNAME");
        return jdbcOperations.queryForList(sb.toString(), String.class);
    }

    @Override
    public DBDatabase getDatabase(String schemaName) {
        DBDatabase database = new DBDatabase();
        String sql = this.sqlMapper.getSql(Statements.GET_DATABASE);
        jdbcOperations.query(sql, new Object[] {schemaName}, rs -> {
            database.setId(rs.getString(2));
            database.setName(rs.getString(1));
        });
        try {
            String charsetSql = "SELECT UNICODE FROM V$INSTANCE";
            jdbcOperations.query(charsetSql, rs -> {
                database.setCharset(rs.getString(1));
            });
        } catch (Exception e) {
            log.warn("Failed to get DM charset, error message:{}", e.getMessage());
        }
        return database;
    }

    @Override
    public List<DBDatabase> listDatabases() {
        List<DBDatabase> databases = new ArrayList<>();
        String sql = this.sqlMapper.getSql(Statements.LIST_DATABASE);
        this.jdbcOperations.query(sql, (rs) -> {
            DBDatabase database = new DBDatabase();
            database.setId(rs.getString(2));
            database.setName(rs.getString(1));
            databases.add(database);
        });
        return databases;
    }

    @Override
    public void switchDatabase(String schemaName) {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.append("SET SCHEMA ");
        sb.identifier(schemaName);
        jdbcOperations.execute(sb.toString());
    }

    @Override
    public List<DBObjectIdentity> listUsers() {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.append("SELECT USERNAME FROM SYS.ALL_USERS");
        return jdbcOperations.query(sb.toString(), (rs, rowNum) -> {
            DBObjectIdentity dbUser = new DBObjectIdentity();
            dbUser.setName(rs.getString(1));
            dbUser.setType(DBObjectType.USER);
            return dbUser;
        });
    }

    @Override
    public List<String> showTablesLike(String schemaName, String tableNameLike) {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.append("SELECT TABLE_NAME FROM SYS.ALL_TABLES WHERE OWNER=");
        sb.value(schemaName);
        if (StringUtils.isNotBlank(tableNameLike)) {
            sb.append(" AND ").like("TABLE_NAME", tableNameLike);
        }
        sb.append(" ORDER BY TABLE_NAME ASC");
        return jdbcOperations.queryForList(sb.toString(), String.class);
    }

    @Override
    public List<DBObjectIdentity> listTables(String schemaName, String tableNameLike) {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.append("SELECT OWNER AS schema_name, 'TABLE' AS type, TABLE_NAME AS name");
        sb.append(" FROM SYS.ALL_TABLES WHERE 1=1 ");
        if (StringUtils.isNotBlank(schemaName)) {
            sb.append(" AND OWNER=");
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
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBObjectIdentity> listViews(String schemaName) {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.append("SELECT OWNER AS schema_name, 'VIEW' AS type, VIEW_NAME AS name FROM SYS.ALL_VIEWS WHERE OWNER=");
        sb.value(schemaName);
        sb.append(" ORDER BY VIEW_NAME ASC");
        return jdbcOperations.query(sb.toString(), new BeanPropertyRowMapper<>(DBObjectIdentity.class));
    }

    @Override
    public List<DBObjectIdentity> listAllViews(String viewNameLike) {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.append("SELECT OWNER AS schema_name, VIEW_NAME AS name, 'VIEW' AS type FROM SYS.ALL_VIEWS");
        if (StringUtils.isNotBlank(viewNameLike)) {
            sb.append(" WHERE ").like("VIEW_NAME", viewNameLike);
        }
        sb.append(" ORDER BY name ASC");
        return jdbcOperations.query(sb.toString(), new BeanPropertyRowMapper<>(DBObjectIdentity.class));
    }

    @Override
    public List<DBObjectIdentity> listAllUserViews(String viewNameLike) {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.append("SELECT OWNER AS schema_name, 'VIEW' AS type, VIEW_NAME AS name FROM SYS.ALL_VIEWS");
        if (StringUtils.isNotBlank(viewNameLike)) {
            sb.append(" WHERE ").like("VIEW_NAME", viewNameLike);
        }
        sb.append(" ORDER BY schema_name, type, name");
        return jdbcOperations.query(sb.toString(), new BeanPropertyRowMapper<>(DBObjectIdentity.class));
    }

    @Override
    public List<DBObjectIdentity> listAllSystemViews(String viewNameLike) {
        return Collections.emptyList();
    }

    @Override
    public List<String> showSystemViews(String schemaName) {
        if (!StringUtils.equalsIgnoreCase("SYS", schemaName)) {
            return Collections.emptyList();
        }
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.append("SELECT VIEW_NAME FROM SYS.ALL_VIEWS WHERE OWNER='SYS' ORDER BY VIEW_NAME");
        return jdbcOperations.queryForList(sb.toString(), String.class);
    }

    @Override
    public List<DBObjectIdentity> listMViews(String schemaName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBObjectIdentity> listAllMViewsLike(String mViewNameLike) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public Boolean refreshMVData(DBMViewRefreshParameter parameter) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public DBMaterializedView getMView(String schemaName, String mViewName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBTableConstraint> listMViewConstraints(String schemaName, String mViewName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBMViewRefreshRecord> listMViewRefreshRecords(DBMViewRefreshRecordParam param) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBTableIndex> listMViewIndexes(String schemaName, String mViewName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBVariable> showVariables() {
        String sql = "SELECT NAME, VALUE FROM V$PARAMETER";
        return jdbcOperations.query(sql, (rs, rowNum) -> {
            DBVariable variable = new DBVariable();
            variable.setName(rs.getString(1));
            variable.setValue(rs.getString(2));
            return variable;
        });
    }

    @Override
    public List<DBVariable> showSessionVariables() {
        String sql = "SELECT NAME, VALUE FROM V$PARAMETER";
        return jdbcOperations.query(sql, (rs, rowNum) -> {
            DBVariable variable = new DBVariable();
            variable.setName(rs.getString(1));
            variable.setValue(rs.getString(2));
            return variable;
        });
    }

    @Override
    public List<DBVariable> showGlobalVariables() {
        String sql = "SELECT NAME, VALUE FROM V$PARAMETER";
        return jdbcOperations.query(sql, (rs, rowNum) -> {
            DBVariable variable = new DBVariable();
            variable.setName(rs.getString(1));
            variable.setValue(rs.getString(2));
            return variable;
        });
    }

    @Override
    public List<String> showCharset() {
        try {
            String sql = "SELECT UNICODE FROM V$INSTANCE";
            return jdbcOperations.queryForList(sql, String.class);
        } catch (Exception e) {
            log.warn("Failed to get DM charset, error message:{}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<String> showCollation() {
        return Collections.emptyList();
    }

    @Override
    public List<DBPLObjectIdentity> listFunctions(String schemaName) {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.append(
                "SELECT OWNER AS schema_name, OBJECT_TYPE AS type, OBJECT_NAME AS name, STATUS FROM SYS.ALL_OBJECTS")
                .append(" WHERE OBJECT_TYPE = 'FUNCTION' AND OWNER=")
                .value(schemaName)
                .append(" ORDER BY OBJECT_NAME ASC");

        List<DBPLObjectIdentity> functions =
                jdbcOperations.query(sb.toString(), new BeanPropertyRowMapper<>(DBPLObjectIdentity.class));

        Map<String, String> errorText = PLObjectErrMsgUtils.acquireErrorMessage(jdbcOperations,
                schemaName, DBObjectType.FUNCTION.name(), null);
        for (DBPLObjectIdentity function : functions) {
            if (StringUtils.containsIgnoreCase(function.getStatus(), PLConstants.PL_OBJECT_STATUS_INVALID)) {
                function.setErrorMessage(errorText.get(function.getName()));
            }
        }
        return functions;
    }

    @Override
    public List<DBPLObjectIdentity> listProcedures(String schemaName) {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.append(
                "SELECT OBJECT_NAME AS name, OBJECT_TYPE AS type, OWNER AS schema_name, STATUS FROM SYS.ALL_OBJECTS");
        sb.append(" WHERE OBJECT_TYPE = 'PROCEDURE' AND OWNER=");
        sb.value(schemaName);
        sb.append(" ORDER BY OBJECT_NAME ASC");

        List<DBPLObjectIdentity> procedures =
                jdbcOperations.query(sb.toString(), new BeanPropertyRowMapper<>(DBPLObjectIdentity.class));

        Map<String, String> errorText = PLObjectErrMsgUtils.acquireErrorMessage(jdbcOperations,
                schemaName, DBObjectType.PROCEDURE.name(), null);
        for (DBPLObjectIdentity procedure : procedures) {
            if (StringUtils.containsIgnoreCase(procedure.getStatus(), PLConstants.PL_OBJECT_STATUS_INVALID)) {
                procedure.setErrorMessage(errorText.get(procedure.getName()));
            }
        }
        return procedures;
    }

    @Override
    public List<DBPLObjectIdentity> listPackages(String schemaName) {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.append(
                "SELECT OBJECT_NAME AS name, OBJECT_TYPE AS type, OWNER, STATUS FROM SYS.ALL_OBJECTS");
        sb.append(" WHERE (OBJECT_TYPE = 'PACKAGE' OR OBJECT_TYPE = 'PACKAGE BODY') AND OWNER=");
        sb.value(schemaName);
        sb.append(" ORDER BY name ASC");

        List<DBPLObjectIdentity> packages = jdbcOperations.query(sb.toString(), (rs, rowNum) -> {
            DBPLObjectIdentity dbPackage = new DBPLObjectIdentity();
            dbPackage.setName(rs.getString("name"));
            dbPackage.setStatus(rs.getString("STATUS"));
            dbPackage.setSchemaName(rs.getString("OWNER"));
            dbPackage.setType(DBObjectType.getEnumByName(rs.getString("type")));
            return dbPackage;
        });

        List<DBPLObjectIdentity> filtered = new ArrayList<>();
        Map<String, String> name2Status = new HashMap<>();
        for (DBPLObjectIdentity dbPackage : packages) {
            String pkgName = dbPackage.getName();
            String status = dbPackage.getStatus();
            if (name2Status.containsKey(pkgName)) {
                if (PLConstants.PL_OBJECT_STATUS_INVALID.equalsIgnoreCase(status)) {
                    name2Status.put(pkgName, status);
                }
            } else {
                name2Status.put(pkgName, status);
            }
        }
        Map<String, String> errorText = PLObjectErrMsgUtils.acquireErrorMessage(jdbcOperations,
                schemaName, DBObjectType.PACKAGE.name(), null);
        String pkgName = null;
        for (DBPLObjectIdentity pkg : packages) {
            if (Objects.isNull(pkgName) || !StringUtils.equals(pkgName, pkg.getName())) {
                pkgName = pkg.getName();
                DBPLObjectIdentity dbPackage = new DBPLObjectIdentity();
                dbPackage.setName(pkg.getName());
                dbPackage.setStatus(name2Status.get(pkg.getName()));
                dbPackage.setSchemaName(pkg.getSchemaName());
                dbPackage.setType(pkg.getType());
                if (StringUtils.containsIgnoreCase(dbPackage.getStatus(),
                        PLConstants.PL_OBJECT_STATUS_INVALID)) {
                    dbPackage.setErrorMessage(errorText.get(dbPackage.getName()));
                }
                filtered.add(dbPackage);
            }
        }
        return filtered;
    }

    @Override
    public List<DBPLObjectIdentity> listPackageBodies(String schemaName) {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.append(
                "SELECT OBJECT_NAME AS name, OBJECT_TYPE AS type, OWNER, STATUS FROM SYS.ALL_OBJECTS");
        sb.append(" WHERE OBJECT_TYPE = 'PACKAGE BODY' AND OWNER=");
        sb.value(schemaName);
        sb.append(" ORDER BY name ASC");

        return jdbcOperations.query(sb.toString(), (rs, rowNum) -> {
            DBPLObjectIdentity dbPackage = new DBPLObjectIdentity();
            dbPackage.setName(rs.getString("name"));
            dbPackage.setStatus(rs.getString("STATUS"));
            dbPackage.setSchemaName(rs.getString("OWNER"));
            dbPackage.setType(DBObjectType.getEnumByName(rs.getString("type")));
            return dbPackage;
        });
    }

    @Override
    public List<DBPLObjectIdentity> listTriggers(String schemaName) {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.append("SELECT o.OWNER, s.STATUS, o.STATUS AS ENABLE_STATUS, TRIGGER_NAME FROM "
                + "(SELECT * FROM SYS.ALL_OBJECTS WHERE OBJECT_TYPE='TRIGGER') s RIGHT JOIN "
                + "SYS.ALL_TRIGGERS o ON s.OBJECT_NAME=o.TRIGGER_NAME AND s.OWNER=o.OWNER WHERE o.OWNER=");
        sb.value(schemaName);
        sb.append(" ORDER BY TRIGGER_NAME ASC");

        List<DBPLObjectIdentity> triggers = jdbcOperations.query(sb.toString(), (rs, rowNum) -> {
            DBPLObjectIdentity trigger = new DBPLObjectIdentity();
            trigger.setName(rs.getString("TRIGGER_NAME"));
            trigger.setSchemaName(rs.getString("OWNER"));
            trigger.setStatus(rs.getString("STATUS"));
            trigger.setEnable("ENABLED".equals(rs.getString("ENABLE_STATUS")));
            trigger.setType(DBObjectType.TRIGGER);
            return trigger;
        });

        Map<String, String> errorText = PLObjectErrMsgUtils.acquireErrorMessage(jdbcOperations,
                schemaName, DBObjectType.TRIGGER.name(), null);
        for (DBPLObjectIdentity trigger : triggers) {
            if (StringUtils.containsIgnoreCase(trigger.getStatus(), PLConstants.PL_OBJECT_STATUS_INVALID)) {
                trigger.setErrorMessage(errorText.get(trigger.getName()));
            }
        }
        return triggers;
    }

    @Override
    public List<DBPLObjectIdentity> listTypes(String schemaName) {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.append(
                "SELECT OBJECT_NAME AS name, STATUS, OBJECT_TYPE AS type, OWNER AS schema_name FROM SYS.ALL_OBJECTS");
        sb.append(" WHERE OBJECT_TYPE='TYPE' AND OWNER=");
        sb.value(schemaName);
        sb.append(" ORDER BY OBJECT_NAME ASC");

        return jdbcOperations.query(sb.toString(), new BeanPropertyRowMapper<>(DBPLObjectIdentity.class));
    }

    @Override
    public List<DBObjectIdentity> listSequences(String schemaName) {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.append("SELECT SEQUENCE_NAME AS name, SEQUENCE_OWNER AS schema_name FROM SYS.ALL_SEQUENCES");
        sb.append(" WHERE SEQUENCE_OWNER=");
        sb.value(schemaName);
        sb.append(" ORDER BY name ASC");

        return jdbcOperations.query(sb.toString(), (rs, rowNum) -> {
            DBObjectIdentity sequence = new DBObjectIdentity();
            sequence.setName(rs.getString("name"));
            sequence.setSchemaName(rs.getString("schema_name"));
            sequence.setType(DBObjectType.SEQUENCE);
            return sequence;
        });
    }

    @Override
    public List<DBObjectIdentity> listSynonyms(String schemaName, DBSynonymType synonymType) {
        DmSqlBuilder sb = new DmSqlBuilder();
        if (DBSynonymType.PUBLIC.equals(synonymType)) {
            sb.append(
                    "SELECT OWNER AS schema_name, SYNONYM_NAME AS name, 'PUBLIC_SYNONYM' AS type FROM ALL_SYNONYMS WHERE OWNER='PUBLIC'");
        } else if (DBSynonymType.COMMON.equals(synonymType)) {
            sb.append(
                    "SELECT OWNER AS schema_name, SYNONYM_NAME AS name, 'SYNONYM' AS type FROM ALL_SYNONYMS WHERE OWNER=")
                    .value(schemaName);
        } else {
            throw new UnsupportedOperationException("Not supported Synonym type");
        }
        return jdbcOperations.query(sb.toString(), new BeanPropertyRowMapper<>(DBObjectIdentity.class));
    }

    @Override
    public Map<String, List<DBTableColumn>> listTableColumns(String schemaName, List<String> tableNames) {
        List<DBTableColumn> tableColumns = DBSchemaAccessorUtil.partitionFind(tableNames,
                DBSchemaAccessorUtil.OB_MAX_IN_SIZE, names -> {
                    String sql =
                            filterByValues(sqlMapper.getSql(Statements.LIST_SCHEMA_COLUMNS), "TABLE_NAME", names);
                    return jdbcOperations.query(sql, new Object[] {schemaName}, listColumnsRowMapper());
                });
        Map<String, List<DBTableColumn>> tableName2Columns = tableColumns.stream()
                .collect(Collectors.groupingBy(DBTableColumn::getTableName));
        tableName2Columns.forEach((table, cols) -> {
            Map<String, String> name2Comments = mapColumnName2ColumnComments(schemaName, table);
            cols.forEach(col -> {
                if (name2Comments.containsKey(col.getName())) {
                    col.setComment(name2Comments.get(col.getName()));
                }
            });
        });
        return tableName2Columns;
    }

    @Override
    public List<DBTableColumn> listTableColumns(String schemaName, String tableName) {
        String sql = this.sqlMapper.getSql(Statements.LIST_TABLE_COLUMNS);
        List<DBTableColumn> tableColumns =
                this.jdbcOperations.query(sql, new Object[] {schemaName, tableName},
                        listColumnsRowMapper());
        Map<String, String> name2Comments = mapColumnName2ColumnComments(schemaName, tableName);
        tableColumns.forEach(dbTableColumn -> {
            if (name2Comments.containsKey(dbTableColumn.getName())) {
                dbTableColumn.setComment(name2Comments.get(dbTableColumn.getName()));
            }
        });
        return tableColumns;
    }

    @Override
    public Map<String, List<DBTableColumn>> listBasicTableColumns(String schemaName) {
        String sql = sqlMapper.getSql(Statements.LIST_BASIC_SCHEMA_TABLE_COLUMNS);
        List<DBTableColumn> tableColumns =
                jdbcOperations.query(sql, new Object[] {schemaName, schemaName}, listBasicColumnsRowMapper());
        return tableColumns.stream().collect(Collectors.groupingBy(DBTableColumn::getTableName));
    }

    @Override
    public List<DBTableColumn> listBasicTableColumns(String schemaName, String tableName) {
        String sql = sqlMapper.getSql(Statements.LIST_BASIC_TABLE_COLUMNS);
        return jdbcOperations.query(sql, new Object[] {schemaName, tableName}, listBasicColumnsRowMapper());
    }

    @Override
    public Map<String, List<DBTableColumn>> listBasicViewColumns(String schemaName) {
        String sql = sqlMapper.getSql(Statements.LIST_BASIC_SCHEMA_VIEW_COLUMNS);
        List<DBTableColumn> tableColumns =
                jdbcOperations.query(sql, new Object[] {schemaName, schemaName}, listBasicColumnsRowMapper());
        return tableColumns.stream().collect(Collectors.groupingBy(DBTableColumn::getTableName));
    }

    @Override
    public List<DBTableColumn> listBasicViewColumns(String schemaName, String viewName) {
        String sql = sqlMapper.getSql(Statements.LIST_BASIC_VIEW_COLUMNS);
        return jdbcOperations.query(sql, new Object[] {schemaName, viewName}, listBasicColumnsRowMapper());
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
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBTableColumn> listBasicMViewColumns(String schemaName, String externalTableName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public Map<String, List<DBTableColumn>> listBasicColumnsInfo(String schemaName) {
        String sql = sqlMapper.getSql(Statements.LIST_BASIC_SCHEMA_COLUMNS_INFO);
        List<DBTableColumn> tableColumns =
                jdbcOperations.query(sql, new Object[] {schemaName}, listBasicColumnsIdentityRowMapper());
        return tableColumns.stream().collect(Collectors.groupingBy(DBTableColumn::getTableName));
    }

    @Override
    public Map<String, List<DBTableIndex>> listTableIndexes(String schemaName) {
        Map<String, List<DBTableIndex>> tableName2Indexes = new LinkedHashMap<>();
        String sql = sqlMapper.getSql(Statements.LIST_SCHEMA_INDEX);
        jdbcOperations.query(sql, new Object[] {schemaName}, (rs, num) -> {
            String indexName = rs.getString(OracleConstants.INDEX_NAME);
            String tableName = rs.getString(OracleConstants.INDEX_TABLE_NAME);
            if (tableName2Indexes.containsKey(tableName)) {
                List<DBTableIndex> tableIndexes = tableName2Indexes.get(tableName);
                Optional<DBTableIndex> existingIndex = tableIndexes.stream()
                        .filter(idx -> idx.getName().equals(indexName))
                        .findFirst();
                if (existingIndex.isPresent()) {
                    List<String> columnNames = existingIndex.get().getColumnNames();
                    columnNames.add(rs.getString("COLUMN_NAME"));
                } else {
                    tableIndexes.add(createIndexByResultSet(rs, num));
                }
            } else {
                tableName2Indexes.put(tableName,
                        new ArrayList<>(Collections.singletonList(createIndexByResultSet(rs, num))));
            }
            return null;
        });
        return tableName2Indexes;
    }

    @Override
    public List<DBTableIndex> listTableIndexes(String schemaName, String tableName) {
        String sql = this.sqlMapper.getSql(Statements.LIST_TABLE_INDEXES);
        Map<String, DBTableIndex> indexName2Index = new LinkedHashMap<>();
        jdbcOperations.query(sql, new Object[] {schemaName, tableName}, (rs, num) -> {
            String indexName = rs.getString(OracleConstants.INDEX_NAME);
            if (indexName2Index.containsKey(indexName)) {
                indexName2Index.get(indexName).getColumnNames().add(rs.getString("COLUMN_NAME"));
            } else {
                indexName2Index.put(indexName, createIndexByResultSet(rs, num));
            }
            return null;
        });
        return new ArrayList<>(indexName2Index.values());
    }

    @Override
    public Map<String, List<DBTableConstraint>> listTableConstraints(String schemaName) {
        String sql = this.sqlMapper.getSql(Statements.LIST_SCHEMA_CONSTRAINTS);
        Map<String, List<DBTableConstraint>> tableName2Constraints = new LinkedHashMap<>();
        jdbcOperations.query(sql, new Object[] {schemaName}, (rs, num) -> {
            String tableName = rs.getString("TABLE_NAME");
            String constraintName = rs.getString(OracleConstants.CONS_NAME);
            if (tableName2Constraints.containsKey(tableName)) {
                Map<String, DBTableConstraint> constraintName2Constraint =
                        tableName2Constraints.get(tableName).stream().collect(
                                Collectors.toMap(DBTableConstraint::getName, cons -> cons));
                int currentPosition = constraintName2Constraint.size();
                if (!constraintName2Constraint.containsKey(constraintName)) {
                    tableName2Constraints.get(tableName).add(createConstraintByResultSet(rs, currentPosition + 1));
                } else {
                    constraintName2Constraint.get(constraintName).getColumnNames()
                            .add(rs.getString("COLUMN_NAME"));
                    constraintName2Constraint.get(constraintName).getReferenceColumnNames()
                            .add(rs.getString(OracleConstants.CONS_R_COLUMN_NAME));
                }
            } else {
                tableName2Constraints.put(tableName,
                        new ArrayList<>(Collections.singletonList(createConstraintByResultSet(rs, 1))));
            }
            return constraintName;
        });
        filterConstraintColumns(
                tableName2Constraints.values().stream().flatMap(List::stream).collect(Collectors.toList()));
        return tableName2Constraints;
    }

    @Override
    public List<DBTableConstraint> listTableConstraints(String schemaName, String tableName) {
        String sql = this.sqlMapper.getSql(Statements.LIST_TABLE_CONSTRAINTS);
        Map<String, DBTableConstraint> name2Constraint = new LinkedHashMap<>();
        jdbcOperations.query(sql, new Object[] {schemaName, tableName}, (rs, num) -> {
            String constraintName = rs.getString(OracleConstants.CONS_NAME);
            if (!name2Constraint.containsKey(constraintName)) {
                int currentPosition = name2Constraint.size();
                name2Constraint.put(constraintName, createConstraintByResultSet(rs, currentPosition + 1));
            } else {
                name2Constraint.get(constraintName).getColumnNames()
                        .add(rs.getString("COLUMN_NAME"));
                name2Constraint.get(constraintName).getReferenceColumnNames()
                        .add(rs.getString(OracleConstants.CONS_R_COLUMN_NAME));
            }
            return constraintName;
        });
        filterConstraintColumns(new ArrayList<>(name2Constraint.values()));
        return new ArrayList<>(name2Constraint.values());
    }

    @Override
    public Map<String, DBTableOptions> listTableOptions(String schemaName) {
        Map<String, DBTableOptions> tableName2Options = new LinkedHashMap<>();
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.append("SELECT OWNER, TABLE_NAME, COMMENTS FROM SYS.ALL_TAB_COMMENTS")
                .append(" WHERE TABLE_TYPE='TABLE' AND OWNER=")
                .value(schemaName)
                .append(" ORDER BY OWNER, TABLE_NAME ASC");
        jdbcOperations.query(sb.toString(), (rs, num) -> {
            DBTableOptions options = new DBTableOptions();
            options.setComment(rs.getString("COMMENTS"));
            tableName2Options.put(rs.getString("TABLE_NAME"), options);
            return null;
        });
        return tableName2Options;
    }

    @Override
    public Map<String, DBTablePartition> listTablePartitions(@NonNull String schemaName, List<String> tableNames) {
        List<Map<String, Object>> defRows = DBSchemaAccessorUtil.partitionFind(tableNames,
                DBSchemaAccessorUtil.OB_MAX_IN_SIZE, names -> {
                    String queryDefsSql = filterByValues(sqlMapper.getSql(Statements.LIST_PARTITIONS_DEFINITIONS),
                            "TABLE_NAME", names);
                    return jdbcOperations.query(queryDefsSql, new Object[] {schemaName}, (rs, num) -> {
                        Map<String, Object> rows = new HashMap<>();
                        rows.put("TABLE_NAME", rs.getString("TABLE_NAME"));
                        rows.put("PARTITION_NAME", rs.getString("PARTITION_NAME"));
                        rows.put("PARTITION_POSITION", rs.getInt("PARTITION_POSITION"));
                        rows.put("HIGH_VALUE", rs.getString("HIGH_VALUE"));
                        return rows;
                    });
                });
        List<Map<String, Object>> optRows = DBSchemaAccessorUtil.partitionFind(tableNames,
                DBSchemaAccessorUtil.OB_MAX_IN_SIZE, names -> {
                    String queryOptsSql =
                            filterByValues(sqlMapper.getSql(Statements.LIST_PARTITIONS_OPTIONS), "TABLE_NAME", names);
                    return jdbcOperations.query(queryOptsSql, new Object[] {schemaName}, (rs, num) -> {
                        Map<String, Object> rows = new HashMap<>();
                        rows.put("TABLE_NAME", rs.getString("TABLE_NAME"));
                        rows.put("PARTITIONING_TYPE", rs.getString("PARTITIONING_TYPE"));
                        return rows;
                    });
                });
        SqlBuilder sqlBuilder = new DmSqlBuilder().append("SELECT NAME, COLUMN_NAME FROM SYS.ALL_PART_KEY_COLUMNS")
                .append(" WHERE OWNER = ").value(schemaName);
        if (CollectionUtils.isNotEmpty(tableNames)) {
            String tables = tableNames.stream().map(s -> new DmSqlBuilder().value(s).toString())
                    .collect(Collectors.joining(","));
            sqlBuilder.append(" AND NAME IN (").append(tables).append(")");
        }
        List<Map<String, Object>> colRows = jdbcOperations.query(sqlBuilder.toString(), (rs, num) -> {
            Map<String, Object> rows = new HashMap<>();
            rows.put("TABLE_NAME", rs.getString("NAME"));
            rows.put("COLUMN_NAME", rs.getString("COLUMN_NAME"));
            return rows;
        });
        Map<String, List<Map<String, Object>>> tblName2DefRows = defRows.stream().collect(
                Collectors.groupingBy(m -> (String) m.get("TABLE_NAME")));
        Map<String, List<Map<String, Object>>> tblName2OptRows = optRows.stream().collect(
                Collectors.groupingBy(m -> (String) m.get("TABLE_NAME")));
        Map<String, List<Map<String, Object>>> tblName2ColRows = colRows.stream().collect(
                Collectors.groupingBy(m -> (String) m.get("TABLE_NAME")));
        return tblName2DefRows.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> {
            String tblName = e.getKey();
            List<Map<String, Object>> subOptRows = tblName2OptRows.get(tblName);
            if (subOptRows == null) {
                throw new IllegalStateException("Failed to find partition option by table, " + tblName);
            }
            DBTablePartitionOption option = new DBTablePartitionOption();
            option.setType(DBTablePartitionType.fromValue((String) subOptRows.get(0).get("PARTITIONING_TYPE")));
            if (option.getType() == DBTablePartitionType.NOT_PARTITIONED) {
                throw new IllegalStateException(
                        "Unrecognized partition type, " + subOptRows.get(0).get("PARTITIONING_TYPE"));
            }
            List<Map<String, Object>> subColRows = tblName2ColRows.get(tblName);
            if (subColRows == null) {
                throw new IllegalStateException("Failed to find partition key by table, " + tblName);
            }
            List<String> columnNames =
                    subColRows.stream().map(m -> (String) m.get("COLUMN_NAME")).collect(Collectors.toList());
            if (option.getType().supportExpression()) {
                option.setExpression(String.join(",", columnNames));
            } else {
                option.setColumnNames(columnNames);
            }
            DBTablePartition partition = new DBTablePartition();
            partition.setSchemaName(schemaName);
            partition.setTableName(tblName);
            partition.setPartitionOption(option);
            List<Map<String, Object>> subDefRows = tblName2DefRows.get(tblName);
            partition.setPartitionDefinitions(subDefRows.stream().map(row -> {
                DBTablePartitionDefinition partitionDefinition = new DBTablePartitionDefinition();
                partitionDefinition.setName((String) row.get("PARTITION_NAME"));
                partitionDefinition.setOrdinalPosition((Integer) row.get("PARTITION_POSITION"));
                partitionDefinition.setType(option.getType());
                partitionDefinition.fillValues((String) row.get("HIGH_VALUE"));
                return partitionDefinition;
            }).collect(Collectors.toList()));
            if (CollectionUtils.isNotEmpty(partition.getPartitionDefinitions())) {
                partition.getPartitionOption().setPartitionsNum(partition.getPartitionDefinitions().size());
            }
            return partition;
        }));
    }

    @Override
    public List<DBTablePartition> listTableRangePartitionInfo(String tenantName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBTableSubpartitionDefinition> listSubpartitions(String schemaName, String tableName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public Boolean isLowerCaseTableName() {
        return false;
    }

    @Override
    public List<DBObjectIdentity> listPartitionTables(String partitionMethod) {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.append(
                "SELECT DISTINCT OWNER AS schema_name, TABLE_NAME AS name, 'TABLE' AS type FROM SYS.ALL_PART_TABLES WHERE PARTITIONING_TYPE = ");
        sb.value(partitionMethod);
        return jdbcOperations.query(sb.toString(), new BeanPropertyRowMapper<>(DBObjectIdentity.class));
    }

    @Override
    public DBTablePartition getPartition(String schemaName, String tableName) {
        DBTablePartition partition = new DBTablePartition();
        partition.setSchemaName(schemaName);
        partition.setTableName(tableName);
        partition.setPartitionOption(obtainPartitionOption(schemaName, tableName));
        partition.setPartitionDefinitions(
                obtainPartitionDefinition(schemaName, tableName, partition.getPartitionOption()));
        if (CollectionUtils.isNotEmpty(partition.getPartitionDefinitions())) {
            partition.getPartitionOption()
                    .setPartitionsNum(partition.getPartitionDefinitions().size());
        }
        return partition;
    }

    @Override
    public String getTableDDL(String schemaName, String tableName) {
        try {
            DmSqlBuilder sb = new DmSqlBuilder();
            sb.append("SELECT DBMS_METADATA.GET_DDL('TABLE', ")
                    .value(tableName)
                    .append(", ")
                    .value(schemaName)
                    .append(") AS DDL FROM DUAL");
            String ddl = StringUtils.strip(jdbcOperations.queryForObject(sb.toString(), String.class));
            return ddl;
        } catch (Exception e) {
            log.warn("Failed to get table DDL via DBMS_METADATA, falling back to manual construction, "
                    + "schema={}, table={}, error={}", schemaName, tableName, e.getMessage());
            return buildTableDDLManually(schemaName, tableName);
        }
    }

    @Override
    public DBTableOptions getTableOptions(String schemaName, String tableName) {
        DBTableOptions tableOptions = new DBTableOptions();
        obtainTableComment(schemaName, tableName, tableOptions);
        obtainTableCreateAndUpdateTime(schemaName, tableName, tableOptions);
        return tableOptions;
    }

    @Override
    public DBTableOptions getTableOptions(String schemaName, String tableName, String ddl) {
        return getTableOptions(schemaName, tableName);
    }

    @Override
    public List<DBColumnGroupElement> listTableColumnGroups(String schemaName, String tableName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public DBView getView(String schemaName, String viewName) {
        DBView view = new DBView();
        view.setViewName(viewName);
        view.setSchemaName(schemaName);
        view.setDefiner(schemaName);
        try {
            DmSqlBuilder getDDL = new DmSqlBuilder();
            getDDL.append("SELECT DBMS_METADATA.GET_DDL('VIEW', ")
                    .value(viewName)
                    .append(", ")
                    .value(schemaName)
                    .append(") AS DDL FROM DUAL");
            jdbcOperations.query(getDDL.toString(), rs -> {
                view.setDdl(StringUtils.trim(rs.getString(1)));
            });
        } catch (Exception e) {
            log.warn("Failed to get view DDL via DBMS_METADATA for view {}, error: {}", viewName, e.getMessage());
            DmSqlBuilder textSql = new DmSqlBuilder();
            textSql.append("SELECT TEXT FROM SYS.ALL_VIEWS WHERE OWNER=")
                    .value(schemaName).append(" AND VIEW_NAME=").value(viewName);
            jdbcOperations.query(textSql.toString(), rs -> {
                view.setDdl("CREATE VIEW " + viewName + " AS " + rs.getString(1));
            });
        }
        fullFillViewComment(view);
        DmSqlBuilder getColumns = new DmSqlBuilder();
        getColumns.append(
                "SELECT COLUMN_NAME, DATA_TYPE, NULLABLE, DATA_DEFAULT, COMMENTS FROM SYS.ALL_TAB_COLS NATURAL JOIN SYS.ALL_COL_COMMENTS WHERE OWNER = ")
                .value(schemaName).append(" AND TABLE_NAME=").value(viewName).append(" ORDER BY COLUMN_ID ASC");
        List<DBTableColumn> columns = jdbcOperations.query(getColumns.toString(), (rs, rowNum) -> {
            DBTableColumn column = new DBTableColumn();
            column.setName(rs.getString("COLUMN_NAME"));
            column.setTypeName(rs.getString("DATA_TYPE"));
            column.setComment(rs.getString("COMMENTS"));
            column.setNullable("Y".equalsIgnoreCase(rs.getString("NULLABLE")));
            column.setDefaultValue(rs.getString("DATA_DEFAULT"));
            column.setOrdinalPosition(rowNum);
            column.setTableName(view.getViewName());
            return column;
        });
        view.setColumns(columns);
        return view;
    }

    @Override
    public DBFunction getFunction(String schemaName, String functionName) {
        DmSqlBuilder info = new DmSqlBuilder();
        info.append("SELECT OWNER, STATUS, CREATED, LAST_DDL_TIME FROM SYS.ALL_OBJECTS")
                .append(" WHERE OBJECT_TYPE='FUNCTION' AND OWNER=")
                .value(schemaName)
                .append(" AND OBJECT_NAME=")
                .value(functionName);
        DBFunction function = new DBFunction();
        function.setFunName(functionName);
        function.setDefiner(schemaName);

        jdbcOperations.query(info.toString(), (rs) -> {
            function.setDefiner(rs.getString("OWNER"));
            function.setStatus(rs.getString("STATUS"));
            function.setCreateTime(Timestamp.valueOf(rs.getString("CREATED")));
            function.setModifyTime(Timestamp.valueOf(rs.getString("LAST_DDL_TIME")));
        });

        try {
            DmSqlBuilder ddl = new DmSqlBuilder();
            ddl.append("SELECT DBMS_METADATA.GET_DDL('FUNCTION', ")
                    .value(functionName)
                    .append(", ")
                    .value(schemaName)
                    .append(") AS DDL FROM DUAL");
            jdbcOperations.query(ddl.toString(), rs -> {
                function.setDdl(rs.getString(1));
            });
        } catch (Exception e) {
            log.warn("Failed to get function DDL via DBMS_METADATA, schema={}, function={}, error={}",
                    schemaName, functionName, e.getMessage());
        }

        DmSqlBuilder getParams = new DmSqlBuilder();
        getParams.append(
                "SELECT OWNER, OBJECT_NAME, ARGUMENT_NAME, DATA_TYPE, IN_OUT, PLS_TYPE, POSITION FROM SYS.ALL_ARGUMENTS")
                .append(" WHERE OWNER=")
                .value(schemaName)
                .append(" AND OBJECT_NAME=")
                .value(functionName)
                .append(" AND PACKAGE_NAME IS NULL ORDER BY POSITION");
        List<DBPLParam> params = new ArrayList<>();
        jdbcOperations.query(getParams.toString(), rs -> {
            if (Objects.isNull(rs.getString("ARGUMENT_NAME"))) {
                function.setReturnType(rs.getString("DATA_TYPE"));
            } else {
                DBPLParam param = new DBPLParam();
                param.setParamName(rs.getString("ARGUMENT_NAME"));
                param.setDataType(rs.getString("DATA_TYPE"));
                param.setParamMode(DBPLParamMode.getEnum(rs.getString("IN_OUT")));
                param.setSeqNum(rs.getInt("POSITION"));
                params.add(param);
            }
        });
        function.setParams(params);
        if (StringUtils.containsIgnoreCase(function.getStatus(), PLConstants.PL_OBJECT_STATUS_INVALID)) {
            function.setErrorMessage(PLObjectErrMsgUtils.getOraclePLObjErrMsg(jdbcOperations,
                    function.getDefiner(), DBObjectType.FUNCTION.name(), function.getFunName()));
        }
        return function;
    }

    @Override
    public DBProcedure getProcedure(String schemaName, String procedureName) {
        DmSqlBuilder info = new DmSqlBuilder();
        info.append("SELECT OWNER, STATUS, CREATED, LAST_DDL_TIME FROM SYS.ALL_OBJECTS")
                .append(" WHERE OBJECT_TYPE='PROCEDURE' AND OWNER=")
                .value(schemaName)
                .append(" AND OBJECT_NAME=")
                .value(procedureName);
        DBProcedure procedure = new DBProcedure();
        procedure.setProName(procedureName);

        jdbcOperations.query(info.toString(), (rs) -> {
            procedure.setDefiner(rs.getString("OWNER"));
            procedure.setStatus(rs.getString("STATUS"));
            procedure.setCreateTime(Timestamp.valueOf(rs.getString("CREATED")));
            procedure.setModifyTime(Timestamp.valueOf(rs.getString("LAST_DDL_TIME")));
        });

        try {
            DmSqlBuilder ddl = new DmSqlBuilder();
            ddl.append("SELECT DBMS_METADATA.GET_DDL('PROCEDURE', ")
                    .value(procedureName)
                    .append(", ")
                    .value(schemaName)
                    .append(") AS DDL FROM DUAL");
            jdbcOperations.query(ddl.toString(), rs -> {
                procedure.setDdl(rs.getString(1));
            });
        } catch (Exception e) {
            log.warn("Failed to get procedure DDL via DBMS_METADATA, schema={}, procedure={}, error={}",
                    schemaName, procedureName, e.getMessage());
        }

        DmSqlBuilder getParams = new DmSqlBuilder();
        getParams.append(
                "SELECT OWNER, OBJECT_NAME, ARGUMENT_NAME, DATA_TYPE, IN_OUT, PLS_TYPE, DEFAULT_VALUE, POSITION FROM SYS.ALL_ARGUMENTS")
                .append(" WHERE OWNER=")
                .value(schemaName)
                .append(" AND OBJECT_NAME=")
                .value(procedureName)
                .append(" AND PACKAGE_NAME IS NULL ORDER BY POSITION");
        List<DBPLParam> params = new ArrayList<>();
        jdbcOperations.query(getParams.toString(), rs -> {
            DBPLParam param = new DBPLParam();
            param.setParamName(rs.getString("ARGUMENT_NAME"));
            param.setDataType(rs.getString("DATA_TYPE"));
            param.setSeqNum(rs.getInt("POSITION"));
            param.setParamMode(DBPLParamMode.getEnum(rs.getString("IN_OUT")));
            params.add(param);
        });
        procedure.setParams(params);
        if (StringUtils.containsIgnoreCase(procedure.getStatus(), PLConstants.PL_OBJECT_STATUS_INVALID)) {
            procedure.setErrorMessage(PLObjectErrMsgUtils.getOraclePLObjErrMsg(jdbcOperations,
                    procedure.getDefiner(), DBObjectType.PROCEDURE.name(), procedure.getProName()));
        }
        return procedure;
    }

    @Override
    public DBPackage getPackage(String schemaName, String packageName) {
        DmSqlBuilder info = new DmSqlBuilder();
        info.append("SELECT OWNER, OBJECT_TYPE, STATUS, CREATED, LAST_DDL_TIME FROM SYS.ALL_OBJECTS")
                .append(" WHERE OBJECT_TYPE IN ('PACKAGE', 'PACKAGE BODY') AND OWNER=")
                .value(schemaName)
                .append(" AND OBJECT_NAME=")
                .value(packageName)
                .append(" ORDER BY OBJECT_TYPE");

        DBPackage dbPackage = new DBPackage();
        dbPackage.setPackageName(packageName);

        DBPackageDetail packageHead = new DBPackageDetail();
        DBPackageBasicInfo packageHeadBasicInfo = new DBPackageBasicInfo();
        packageHead.setBasicInfo(packageHeadBasicInfo);
        dbPackage.setPackageHead(packageHead);

        jdbcOperations.query(info.toString(), (rs) -> {
            dbPackage.setStatus(rs.getString("STATUS"));
            if (DBObjectType.PACKAGE.name().equalsIgnoreCase(rs.getString("OBJECT_TYPE"))) {
                packageHeadBasicInfo.setDefiner(rs.getString("OWNER"));
                packageHeadBasicInfo.setCreateTime(rs.getTimestamp("CREATED"));
                packageHeadBasicInfo.setModifyTime(rs.getTimestamp("LAST_DDL_TIME"));
            } else {
                DBPackageDetail packageBody = new DBPackageDetail();
                DBPackageBasicInfo packageBodyBasicInfo = new DBPackageBasicInfo();
                packageBodyBasicInfo.setDefiner(rs.getString("OWNER"));
                packageBodyBasicInfo.setCreateTime(rs.getTimestamp("CREATED"));
                packageBodyBasicInfo.setModifyTime(rs.getTimestamp("LAST_DDL_TIME"));
                packageBody.setBasicInfo(packageBodyBasicInfo);
                dbPackage.setPackageBody(packageBody);
            }
        });

        try {
            DmSqlBuilder packageHeadDDL = new DmSqlBuilder();
            packageHeadDDL.append("SELECT DBMS_METADATA.GET_DDL('PACKAGE_SPEC', ")
                    .value(packageName)
                    .append(", ")
                    .value(schemaName)
                    .append(") AS DDL FROM DUAL");
            jdbcOperations.query(packageHeadDDL.toString(), rs -> {
                packageHeadBasicInfo.setDdl(rs.getString(1));
            });
        } catch (Exception e) {
            log.warn("Failed to get package spec DDL via DBMS_METADATA, schema={}, package={}, error={}",
                    schemaName, packageName, e.getMessage());
        }

        if (Objects.nonNull(dbPackage.getPackageBody()) && Objects.nonNull(dbPackage.getPackageBody().getBasicInfo())) {
            try {
                DmSqlBuilder packageBodyDDL = new DmSqlBuilder();
                packageBodyDDL.append("SELECT DBMS_METADATA.GET_DDL('PACKAGE_BODY', ")
                        .value(packageName)
                        .append(", ")
                        .value(schemaName)
                        .append(") AS DDL FROM DUAL");
                jdbcOperations.query(packageBodyDDL.toString(), rs -> {
                    dbPackage.getPackageBody().getBasicInfo().setDdl(rs.getString(1));
                });
            } catch (Exception e) {
                log.warn("Failed to get package body DDL via DBMS_METADATA, schema={}, package={}, error={}",
                        schemaName, packageName, e.getMessage());
            }
        }

        if (StringUtils.containsIgnoreCase(dbPackage.getStatus(), PLConstants.PL_OBJECT_STATUS_INVALID)) {
            dbPackage.setErrorMessage(PLObjectErrMsgUtils.getOraclePLObjErrMsg(jdbcOperations,
                    schemaName, DBObjectType.PACKAGE.name(), dbPackage.getPackageName()));
        }
        return dbPackage;
    }

    @Override
    public DBTrigger getTrigger(String schemaName, String triggerName) {
        DBTrigger trigger = new DBTrigger();
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.append("SELECT s.OWNER, s.TRIGGER_NAME, s.BASE_OBJECT_TYPE, s.TABLE_OWNER, s.TABLE_NAME, ")
                .append("s.STATUS AS ENABLE_STATUS, o.STATUS")
                .append(" FROM (SELECT * FROM SYS.ALL_OBJECTS WHERE OBJECT_TYPE='TRIGGER') o ")
                .append("RIGHT JOIN SYS.ALL_TRIGGERS s ON o.OBJECT_NAME=s.TRIGGER_NAME AND o.OWNER=s.OWNER")
                .append(" WHERE s.OWNER=").value(schemaName)
                .append(" AND s.TRIGGER_NAME=").value(triggerName);
        jdbcOperations.query(sb.toString(), (rs) -> {
            trigger.setBaseObjectType(rs.getString("BASE_OBJECT_TYPE"));
            trigger.setTriggerName(rs.getString("TRIGGER_NAME"));
            trigger.setOwner(rs.getString("OWNER"));
            trigger.setSchemaMode(rs.getString("TABLE_OWNER"));
            trigger.setSchemaName(rs.getString("TABLE_NAME"));
            trigger.setEnable("ENABLED".equalsIgnoreCase(rs.getString("ENABLE_STATUS")));
            trigger.setStatus(rs.getString("STATUS"));
        });

        try {
            DmSqlBuilder ddl = new DmSqlBuilder();
            ddl.append("SELECT DBMS_METADATA.GET_DDL('TRIGGER',")
                    .value(triggerName)
                    .append(", ")
                    .value(schemaName)
                    .append(") AS DDL FROM DUAL");
            jdbcOperations.query(ddl.toString(), rs -> {
                trigger.setDdl(StringUtils.trim(rs.getString(1)));
            });
        } catch (Exception e) {
            log.warn("Failed to get trigger DDL via DBMS_METADATA, schema={}, trigger={}, error={}",
                    schemaName, triggerName, e.getMessage());
        }
        if (StringUtils.containsIgnoreCase(trigger.getStatus(), PLConstants.PL_OBJECT_STATUS_INVALID)) {
            trigger.setErrorMessage(PLObjectErrMsgUtils.getOraclePLObjErrMsg(jdbcOperations,
                    trigger.getOwner(), DBObjectType.TRIGGER.name(), trigger.getTriggerName()));
        }
        return trigger;
    }

    @Override
    public DBType getType(String schemaName, String typeName) {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.append("SELECT a.OWNER, a.OBJECT_NAME, u.TYPE_NAME, a.CREATED, a.LAST_DDL_TIME, u.TYPECODE, u.TYPEID")
                .append(" FROM SYS.ALL_OBJECTS a RIGHT JOIN SYS.ALL_TYPES u ON a.OBJECT_NAME=u.TYPE_NAME")
                .append(" WHERE a.OWNER=")
                .value(schemaName)
                .append(" AND u.TYPE_NAME=")
                .value(typeName);

        DBType type = new DBType();
        jdbcOperations.query(sb.toString(), (rs) -> {
            type.setOwner(rs.getString("OWNER"));
            type.setTypeName(rs.getString("TYPE_NAME"));
            type.setCreateTime(rs.getTimestamp("CREATED"));
            type.setLastDdlTime(rs.getTimestamp("LAST_DDL_TIME"));
            type.setTypeId(rs.getString("TYPEID"));
            type.setType(rs.getString("TYPECODE"));
        });

        try {
            DmSqlBuilder typeDDL = new DmSqlBuilder();
            typeDDL.append("SELECT DBMS_METADATA.GET_DDL('TYPE', ")
                    .value(type.getTypeName())
                    .append(", ")
                    .value(type.getOwner())
                    .append(") AS DDL FROM DUAL");
            String typeDdl = jdbcOperations.query(typeDDL.toString(), rs -> {
                if (!rs.next()) {
                    return null;
                }
                return rs.getString(1);
            });
            type.setDdl(typeDdl);
        } catch (Exception e) {
            log.warn("Failed to get type DDL via DBMS_METADATA, schema={}, type={}, error={}",
                    schemaName, typeName, e.getMessage());
        }

        String errorText = PLObjectErrMsgUtils.getOraclePLObjErrMsg(jdbcOperations,
                type.getOwner(), DBObjectType.TYPE.name(), type.getTypeName());
        if (StringUtils.isNotBlank(errorText)) {
            type.setStatus(PLConstants.PL_OBJECT_STATUS_INVALID);
            type.setErrorMessage(errorText);
        }
        return type;
    }

    @Override
    public DBSequence getSequence(String schemaName, String sequenceName) {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.append("SELECT * FROM SYS.ALL_SEQUENCES WHERE SEQUENCE_OWNER=")
                .value(schemaName)
                .append(" AND SEQUENCE_NAME=")
                .value(sequenceName);

        DBSequence sequence = new DBSequence();
        sequence.setName(sequenceName);
        jdbcOperations.query(sb.toString(), rs -> {
            sequence.setUser(rs.getString("SEQUENCE_OWNER"));
            sequence.setMinValue(rs.getBigDecimal("MIN_VALUE").toString());
            sequence.setMaxValue(rs.getBigDecimal("MAX_VALUE").toString());
            sequence.setIncreament(rs.getBigDecimal("INCREMENT_BY").longValue());
            sequence.setCycled("Y".equalsIgnoreCase(rs.getString("CYCLE_FLAG")));
            sequence.setOrderd("Y".equalsIgnoreCase(rs.getString("ORDER_FLAG")));
            long cacheSize = rs.getBigDecimal("CACHE_SIZE").longValue();
            if (cacheSize > 1) {
                sequence.setCacheSize(cacheSize);
                sequence.setCached(true);
            } else {
                sequence.setCached(false);
            }
            sequence.setNextCacheValue(rs.getBigDecimal("LAST_NUMBER").toString());
        });

        try {
            DmSqlBuilder ddl = new DmSqlBuilder();
            ddl.append("SELECT DBMS_METADATA.GET_DDL('SEQUENCE', ")
                    .value(sequence.getName())
                    .append(", ")
                    .value(sequence.getUser())
                    .append(") AS DDL FROM DUAL");
            String seqDdl = jdbcOperations.query(ddl.toString(), rs -> {
                if (!rs.next()) {
                    return null;
                }
                return rs.getString(1);
            });
            sequence.setDdl(seqDdl);
        } catch (Exception e) {
            log.warn("Failed to get sequence DDL via DBMS_METADATA, schema={}, sequence={}, error={}",
                    schemaName, sequenceName, e.getMessage());
        }
        return sequence;
    }

    @Override
    public DBSynonym getSynonym(String schemaName, String synonymName, DBSynonymType synonymType) {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.append("SELECT s.OWNER, s.SYNONYM_NAME, s.TABLE_OWNER, s.TABLE_NAME, s.DB_LINK, ")
                .append("o.CREATED, o.LAST_DDL_TIME, o.STATUS FROM SYS.ALL_SYNONYMS s LEFT JOIN ")
                .append("(SELECT * FROM SYS.ALL_OBJECTS WHERE OBJECT_TYPE='SYNONYM') o ")
                .append("ON s.SYNONYM_NAME=o.OBJECT_NAME AND s.OWNER=o.OWNER WHERE s.OWNER=");
        sb.value(getSynonymOwnerSymbol(synonymType, schemaName));
        sb.append(" AND s.SYNONYM_NAME=");
        sb.value(synonymName);

        DBSynonym synonym = new DBSynonym();
        synonym.setSynonymType(synonymType);
        jdbcOperations.query(sb.toString(), rs -> {
            synonym.setOwner(rs.getString("OWNER"));
            synonym.setSynonymName(rs.getString("SYNONYM_NAME"));
            synonym.setTableOwner(rs.getString("TABLE_OWNER"));
            synonym.setTableName(rs.getString("TABLE_NAME"));
            synonym.setDbLink(rs.getString("DB_LINK"));
            synonym.setCreated(rs.getTimestamp("CREATED"));
            synonym.setLastDdlTime(rs.getTimestamp("LAST_DDL_TIME"));
            synonym.setStatus(rs.getString("STATUS"));
        });

        try {
            DmSqlBuilder ddl = new DmSqlBuilder();
            ddl.append("SELECT DBMS_METADATA.GET_DDL('SYNONYM', ")
                    .value(synonym.getSynonymName())
                    .append(", ")
                    .value(synonym.getOwner())
                    .append(") AS DDL FROM DUAL");
            String synDdl = jdbcOperations.query(ddl.toString(), rs -> {
                if (!rs.next()) {
                    return null;
                }
                return rs.getString(1);
            });
            synonym.setDdl(synDdl);
        } catch (Exception e) {
            log.warn("Failed to get synonym DDL via DBMS_METADATA, error={}", e.getMessage());
        }
        return synonym;
    }

    @Override
    public Map<String, DBTable> getTables(String schemaName, List<String> tableNames) {
        throw new UnsupportedOperationException("Not supported for DM mode");
    }

    // ======================== Private helper methods ========================

    private DBTableIndex createIndexByResultSet(ResultSet rs, int num) throws SQLException {
        DBTableIndex index = new DBTableIndex();
        index.setName(rs.getString(OracleConstants.INDEX_NAME));
        index.setOrdinalPosition(num);
        index.setSchemaName(rs.getString("TABLE_OWNER"));
        index.setOwner(rs.getString("OWNER"));
        index.setTableName(rs.getString(OracleConstants.INDEX_TABLE_NAME));
        index.setNonUnique(!"UNIQUE".equalsIgnoreCase(rs.getString(OracleConstants.INDEX_UNIQUENESS)));
        index.setType(DBIndexType.fromString(rs.getString(OracleConstants.INDEX_TYPE)));
        index.setVisible("VISIBLE".equalsIgnoreCase(rs.getString("VISIBILITY")));
        if (index.isNonUnique()) {
            index.setType(DBIndexType.fromString(rs.getString(OracleConstants.INDEX_TYPE)));
        } else {
            index.setType(DBIndexType.UNIQUE);
        }
        index.setAlgorithm(DBIndexAlgorithm.fromString(rs.getString(OracleConstants.INDEX_TYPE)));
        index.setCompressInfo(rs.getString(OracleConstants.INDEX_COMPRESSION));
        index.setColumnNames(new ArrayList<>(Collections.singletonList(rs.getString("COLUMN_NAME"))));
        index.setAvailable(!"UNUSABLE".equals(rs.getString(OracleConstants.INDEX_STATUS)));
        index.setGlobal("NO".equalsIgnoreCase(rs.getString("PARTITIONED")));
        return index;
    }

    private DBTableConstraint createConstraintByResultSet(ResultSet rs, int num) throws SQLException {
        DBTableConstraint constraint = new DBTableConstraint();
        constraint.setName(rs.getString(OracleConstants.CONS_NAME));
        constraint.setOrdinalPosition(num);
        constraint.setSchemaName(rs.getString(OracleConstants.CONS_OWNER));
        constraint.setTableName(rs.getString(OracleConstants.COL_TABLE_NAME));
        constraint.setOwner(rs.getString(OracleConstants.CONS_OWNER));
        constraint.setValidate("VALIDATED".equalsIgnoreCase(rs.getString(OracleConstants.CONS_VALIDATED)));
        constraint.setType(DBConstraintType.fromValue(rs.getString(OracleConstants.CONS_TYPE)));
        constraint.setEnabled("ENABLED".equalsIgnoreCase(rs.getString("STATUS")));
        String deferrable = rs.getString(OracleConstants.CONS_DEFERRABLE);
        if (StringUtils.equalsIgnoreCase(deferrable, "DEFERRABLE")) {
            constraint.setDeferability(DBConstraintDeferability.fromString(rs.getString("DEFERRED")));
        } else {
            constraint.setDeferability(DBConstraintDeferability.NOT_DEFERRABLE);
        }
        List<String> columnNames = new ArrayList<>();
        columnNames.add(rs.getString("COLUMN_NAME"));
        constraint.setColumnNames(columnNames);
        List<String> refColumnNames = new ArrayList<>();
        constraint.setReferenceColumnNames(refColumnNames);
        if (DBConstraintType.FOREIGN_KEY == constraint.getType()) {
            constraint.setReferenceTableName(rs.getString(OracleConstants.CONS_R_TABLE_NAME));
            constraint.setReferenceSchemaName(rs.getString(OracleConstants.CONS_R_OWNER));
            refColumnNames.add(rs.getString(OracleConstants.CONS_R_COLUMN_NAME));
            constraint.setOnDeleteRule(
                    DBForeignKeyModifyRule.fromValue(rs.getString(OracleConstants.CONS_DELETE_RULE)));
        }
        constraint.setCheckClause(rs.getString("SEARCH_CONDITION"));
        return constraint;
    }

    private void filterConstraintColumns(List<DBTableConstraint> constraints) {
        for (DBTableConstraint constraint : constraints) {
            if (Objects.nonNull(constraint.getReferenceColumnNames())) {
                constraint.setReferenceColumnNames(constraint.getReferenceColumnNames().stream()
                        .filter(Objects::nonNull).distinct().collect(Collectors.toList()));
            }
            if (Objects.nonNull(constraint.getColumnNames())) {
                constraint.setColumnNames(constraint.getColumnNames().stream().filter(Objects::nonNull).distinct()
                        .collect(Collectors.toList()));
            }
        }
    }

    private Map<String, String> mapColumnName2ColumnComments(String schemaName, String tableName) {
        Map<String, String> commentsMap = new HashMap<>();
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.append("SELECT COLUMN_NAME, COMMENTS FROM SYS.ALL_COL_COMMENTS WHERE OWNER = ");
        sb.value(schemaName);
        sb.append(" AND TABLE_NAME = ");
        sb.value(tableName);
        jdbcOperations.query(sb.toString(), resultSet -> {
            commentsMap.put(resultSet.getString(OracleConstants.COL_COLUMN_NAME),
                    resultSet.getString(OracleConstants.COL_COMMENTS));
        });
        return commentsMap;
    }

    private RowMapper<DBTableColumn> listColumnsRowMapper() {
        final int[] hiddenColumnOrdinaryPosition = {-1};
        return (rs, rowNum) -> {
            DBTableColumn tableColumn = new DBTableColumn();
            String defaultValue = rs.getString(OracleConstants.COL_DATA_DEFAULT);
            tableColumn.setSchemaName(rs.getString(OracleConstants.CONS_OWNER));
            tableColumn.setTableName(rs.getString(OracleConstants.COL_TABLE_NAME));
            tableColumn.setName(rs.getString(OracleConstants.COL_COLUMN_NAME));
            tableColumn.setTypeName(
                    DBSchemaAccessorUtil.normalizeTypeName(rs.getString(OracleConstants.COL_DATA_TYPE)));
            tableColumn.setFullTypeName(rs.getString(OracleConstants.COL_DATA_TYPE));
            tableColumn.setCharUsed(CharUnit.fromString(rs.getString(OracleConstants.COL_CHAR_USED)));
            tableColumn.setOrdinalPosition(rs.getInt(OracleConstants.COL_COLUMN_ID));
            tableColumn.setTypeModifiers(Collections.singletonList(rs.getString(OracleConstants.COL_DATA_TYPE_MOD)));
            tableColumn.setMaxLength(
                    rs.getLong(tableColumn.getCharUsed() == CharUnit.CHAR ? OracleConstants.COL_CHAR_LENGTH
                            : OracleConstants.COL_DATA_LENGTH));
            tableColumn.setNullable("Y".equalsIgnoreCase(rs.getString(OracleConstants.COL_NULLABLE)));
            DBColumnTypeDisplay columnTypeDisplay = DBColumnTypeDisplay.fromName(tableColumn.getTypeName());
            if (columnTypeDisplay.displayScale()) {
                tableColumn.setScale(rs.getInt(OracleConstants.COL_DATA_SCALE));
            }
            if (columnTypeDisplay.displayPrecision()) {
                if (Objects.nonNull(rs.getObject(OracleConstants.COL_DATA_PRECISION))) {
                    tableColumn.setPrecision(rs.getLong(OracleConstants.COL_DATA_PRECISION));
                } else {
                    tableColumn.setPrecision(tableColumn.getMaxLength());
                }
            }
            tableColumn.setHidden("YES".equalsIgnoreCase(rs.getString(OracleConstants.COL_HIDDEN_COLUMN)));
            if (tableColumn.getHidden()) {
                tableColumn.setOrdinalPosition(hiddenColumnOrdinaryPosition[0]);
                hiddenColumnOrdinaryPosition[0]--;
            }
            tableColumn.setVirtual("YES".equalsIgnoreCase(rs.getString(OracleConstants.COL_VIRTUAL_COLUMN)));
            tableColumn.setDefaultValue("NULL".equals(defaultValue) ? null : defaultValue);
            if (tableColumn.getVirtual()) {
                tableColumn.setGenExpression(defaultValue);
            }
            return tableColumn;
        };
    }

    private RowMapper<DBTableColumn> listBasicColumnsRowMapper() {
        return (rs, rowNum) -> {
            DBTableColumn tableColumn = new DBTableColumn();
            tableColumn.setSchemaName(rs.getString(OracleConstants.CONS_OWNER));
            tableColumn.setTableName(rs.getString(OracleConstants.COL_TABLE_NAME));
            tableColumn.setName(rs.getString(OracleConstants.COL_COLUMN_NAME));
            tableColumn.setComment(rs.getString(OracleConstants.COL_COMMENTS));
            tableColumn.setTypeName(
                    DBSchemaAccessorUtil.normalizeTypeName(rs.getString(OracleConstants.COL_DATA_TYPE)));
            return tableColumn;
        };
    }

    private RowMapper<DBTableColumn> listBasicColumnsIdentityRowMapper() {
        return (rs, rowNum) -> {
            DBTableColumn tableColumn = new DBTableColumn();
            tableColumn.setSchemaName(rs.getString(OracleConstants.CONS_OWNER));
            tableColumn.setTableName(rs.getString(OracleConstants.COL_TABLE_NAME));
            tableColumn.setName(rs.getString(OracleConstants.COL_COLUMN_NAME));
            return tableColumn;
        };
    }

    private String filterByValues(String target, String colName, List<String> candidates) {
        if (CollectionUtils.isEmpty(candidates)) {
            return target;
        }
        String tables = candidates.stream().map(s -> new DmSqlBuilder().value(s).toString())
                .collect(Collectors.joining(","));
        SqlBuilder sqlBuilder = new DmSqlBuilder();
        return sqlBuilder.append("SELECT * FROM (")
                .append(target).append(") dbbrowser").append(" WHERE dbbrowser.").identifier(colName)
                .append(" IN (").append(tables).append(")").toString();
    }

    private DBTablePartitionOption obtainPartitionOption(String schemaName, String tableName) {
        DBTablePartitionOption option = new DBTablePartitionOption();
        option.setType(DBTablePartitionType.NOT_PARTITIONED);
        String queryPartitionTypeSql = this.sqlMapper.getSql(Statements.GET_PARTITION_OPTION);
        jdbcOperations.query(queryPartitionTypeSql, new Object[] {schemaName, tableName}, (rs, num) -> {
            option.setType(DBTablePartitionType.fromValue(rs.getString("PARTITIONING_TYPE")));
            return option;
        });
        if (Objects.isNull(option.getType()) || option.getType() == DBTablePartitionType.NOT_PARTITIONED) {
            return option;
        }
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.append("SELECT COLUMN_NAME FROM SYS.ALL_PART_KEY_COLUMNS WHERE OWNER = ")
                .value(schemaName)
                .append(" AND NAME = ")
                .value(tableName);
        List<String> columnNames = jdbcOperations.query(sb.toString(), (rs, num) -> rs.getString("COLUMN_NAME"));
        if (option.getType().supportExpression()) {
            option.setExpression(String.join(",", columnNames));
        } else {
            option.setColumnNames(columnNames);
        }
        return option;
    }

    private List<DBTablePartitionDefinition> obtainPartitionDefinition(String schemaName, String tableName,
            DBTablePartitionOption option) {
        String sql = this.sqlMapper.getSql(Statements.LIST_PARTITION_DEFINITIONS);
        return jdbcOperations.query(sql, new Object[] {schemaName, tableName}, (rs, num) -> {
            DBTablePartitionDefinition partitionDefinition = new DBTablePartitionDefinition();
            partitionDefinition.setName(rs.getString("PARTITION_NAME"));
            partitionDefinition.setOrdinalPosition(num);
            partitionDefinition.setType(option.getType());
            String description = rs.getString("HIGH_VALUE");
            partitionDefinition.fillValues(description);
            return partitionDefinition;
        });
    }

    private void obtainTableComment(String schemaName, String tableName, DBTableOptions tableOptions) {
        DmSqlBuilder sql = new DmSqlBuilder();
        sql.append("SELECT COMMENTS FROM SYS.ALL_TAB_COMMENTS WHERE OWNER=")
                .value(schemaName).append(" AND TABLE_NAME=").value(tableName)
                .append(" AND COMMENTS IS NOT NULL");
        jdbcOperations.query(sql.toString(), t -> {
            tableOptions.setComment(t.getString("COMMENTS"));
        });
    }

    private void obtainTableCreateAndUpdateTime(String schemaName, String tableName, DBTableOptions tableOptions) {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.append("SELECT CREATED, LAST_DDL_TIME FROM SYS.ALL_OBJECTS WHERE OBJECT_TYPE = ");
        sb.value("TABLE");
        sb.append(" AND OWNER = ");
        sb.value(schemaName);
        sb.append(" AND OBJECT_NAME = ");
        sb.value(tableName);
        jdbcOperations.query(sb.toString(), rs -> {
            tableOptions.setCreateTime(rs.getTimestamp("CREATED"));
            tableOptions.setUpdateTime(rs.getTimestamp("LAST_DDL_TIME"));
        });
    }

    private void fullFillViewComment(DBView view) {
        DmSqlBuilder sb = new DmSqlBuilder();
        sb.append("SELECT OWNER, TABLE_NAME, COMMENTS FROM SYS.ALL_TAB_COMMENTS")
                .append(" WHERE TABLE_TYPE='VIEW' AND OWNER=").value(view.getSchemaName())
                .append(" AND TABLE_NAME=").value(view.getViewName());
        try {
            this.jdbcOperations.query(sb.toString(), (rs, num) -> {
                view.setComment(rs.getString("COMMENTS"));
                return null;
            });
        } catch (Exception e) {
            log.warn("Failed to query view's comment, viewName={}, errMessage={}",
                    view.getViewName(), e.getMessage());
        }
    }

    private String buildTableDDLManually(String schemaName, String tableName) {
        StringBuilder ddl = new StringBuilder("-- Failed to retrieve DDL via DBMS_METADATA\n");
        ddl.append("-- Table: ").append(schemaName).append(".").append(tableName).append("\n");
        return ddl.toString();
    }

    private String getSynonymOwnerSymbol(DBSynonymType synonymType, String schemaName) {
        if (synonymType.equals(DBSynonymType.PUBLIC)) {
            return "PUBLIC";
        } else if (synonymType.equals(DBSynonymType.COMMON)) {
            return schemaName;
        } else {
            throw new UnsupportedOperationException("Not supported Synonym type");
        }
    }
}
