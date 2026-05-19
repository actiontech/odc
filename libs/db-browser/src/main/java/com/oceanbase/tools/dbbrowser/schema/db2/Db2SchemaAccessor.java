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
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcOperations;

import com.oceanbase.tools.dbbrowser.model.DBColumnGroupElement;
import com.oceanbase.tools.dbbrowser.model.DBConstraintType;
import com.oceanbase.tools.dbbrowser.model.DBDatabase;
import com.oceanbase.tools.dbbrowser.model.DBFunction;
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
 * DB2 schema accessor implementation (B-06 / B-07).
 *
 * <p>
 * 主用 SYSCAT.* 视图（DB2 11.5 默认）；仅实现本期需要的 6 类查询：列 schema / 列 table / 列 view / 列 column / 列 index / 列
 * constraint，详见 docs/spec/design.md §6。
 *
 * <p>
 * 其余接口方法按现有同仓 PostgresSchemaAccessor 风格抛 {@link UnsupportedOperationException}， 不影响 ODC
 * 工作台基本能力（表浏览、列查看）。
 *
 * @since ODC_release_4.3.4 (Issue dms-ee#839)
 */
@Slf4j
public class Db2SchemaAccessor implements DBSchemaAccessor {

    protected final JdbcOperations jdbcOperations;

    public Db2SchemaAccessor(@NonNull JdbcOperations jdbcOperations) {
        this.jdbcOperations = jdbcOperations;
    }

    @Override
    public List<String> showDatabases() {
        // fix-G bug C: design.md §6 prescribes an 11-entry system-schema blacklist
        // (SYSCAT / SYSIBM / SYSIBMADM / SYSIBMINTERNAL / SYSIBMTS / SYSFUN / SYSPROC /
        // SYSSTAT / SYSTOOLS / SYSPUBLIC / NULLID). The original implementation filtered by
        // SYSCAT.SCHEMATA.DEFINER, but on DB2 11.5 several system schemas (NULLID, SQLJ,
        // SYSTOOLS) are *created* by the instance owner (e.g. db2inst1) rather than SYSIBM,
        // so DEFINER NOT IN(...) lets them slip through into the user schema list. The blacklist
        // must therefore match SCHEMANAME directly. SQLJ is added to the list (DB2 JDBC stored
        // procedures schema, meaningless to end users; see batch-3 round-2 evidence in
        // case-2-1.md).
        String sql = "SELECT TRIM(SCHEMANAME) AS SCHEMA_NAME FROM SYSCAT.SCHEMATA "
                + "WHERE SCHEMANAME NOT IN ('SYSIBM','SYSCAT','SYSIBMADM','SYSIBMINTERNAL',"
                + "'SYSIBMTS','SYSFUN','SYSPROC','SYSSTAT','SYSTOOLS','SYSPUBLIC','NULLID','SQLJ') "
                + "ORDER BY SCHEMANAME";
        return jdbcOperations.queryForList(sql, String.class);
    }

    @Override
    public DBDatabase getDatabase(String schemaName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBDatabase> listDatabases() {
        List<String> schemas = showDatabases();
        List<DBDatabase> result = new ArrayList<>(schemas.size());
        for (String schema : schemas) {
            DBDatabase db = new DBDatabase();
            db.setId(schema);
            db.setName(schema);
            result.add(db);
        }
        return result;
    }

    @Override
    public void switchDatabase(String schemaName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBObjectIdentity> listUsers() {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<String> showTablesLike(String schemaName, String tableNameLike) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT TABNAME FROM SYSCAT.TABLES ");
        sb.append("WHERE TABSCHEMA = ? AND TYPE IN ('T','S','U') ");
        if (tableNameLike != null && !tableNameLike.isEmpty()) {
            sb.append("AND TABNAME LIKE ? ");
        }
        sb.append("ORDER BY TABNAME");
        if (tableNameLike != null && !tableNameLike.isEmpty()) {
            return jdbcOperations.queryForList(sb.toString(), String.class, schemaName, tableNameLike);
        }
        return jdbcOperations.queryForList(sb.toString(), String.class, schemaName);
    }

    @Override
    public List<DBObjectIdentity> listTables(String schemaName, String tableNameLike) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT TABSCHEMA, TABNAME FROM SYSCAT.TABLES ");
        sb.append("WHERE TABSCHEMA = ? AND TYPE IN ('T','S','U') ");
        Object[] args;
        if (tableNameLike != null && !tableNameLike.isEmpty()) {
            sb.append("AND TABNAME LIKE ? ");
            args = new Object[] {schemaName, tableNameLike};
        } else {
            args = new Object[] {schemaName};
        }
        sb.append("ORDER BY TABNAME");
        return jdbcOperations.query(sb.toString(), args,
                (rs, rowNum) -> DBObjectIdentity.of(rs.getString(1).trim(), DBObjectType.TABLE,
                        rs.getString(2).trim()));
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
        String sql = "SELECT VIEWSCHEMA, TABNAME FROM SYSCAT.VIEWS WHERE VIEWSCHEMA = ? ORDER BY TABNAME";
        return jdbcOperations.query(sql, new Object[] {schemaName},
                (rs, rowNum) -> DBObjectIdentity.of(rs.getString(1).trim(), DBObjectType.VIEW,
                        rs.getString(2).trim()));
    }

    @Override
    public List<DBObjectIdentity> listAllViews(String viewNameLike) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBObjectIdentity> listAllUserViews(String viewNameLike) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBObjectIdentity> listAllSystemViews(String viewNameLike) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<String> showSystemViews(String schemaName) {
        throw new UnsupportedOperationException("Not supported yet");
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
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBVariable> showSessionVariables() {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBVariable> showGlobalVariables() {
        throw new UnsupportedOperationException("Not supported yet");
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
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBPLObjectIdentity> listProcedures(String schemaName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBPLObjectIdentity> listPackages(String schemaName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBPLObjectIdentity> listPackageBodies(String schemaName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBPLObjectIdentity> listTriggers(String schemaName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBPLObjectIdentity> listTypes(String schemaName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBObjectIdentity> listSequences(String schemaName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBObjectIdentity> listSynonyms(String schemaName, DBSynonymType synonymType) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public Map<String, List<DBTableColumn>> listTableColumns(String schemaName, List<String> tableNames) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBTableColumn> listTableColumns(String schemaName, String tableName) {
        String sql = "SELECT COLNAME, TYPENAME, LENGTH, SCALE, NULLS, DEFAULT, REMARKS, COLNO "
                + "FROM SYSCAT.COLUMNS WHERE TABSCHEMA = ? AND TABNAME = ? ORDER BY COLNO";
        return jdbcOperations.query(sql, new Object[] {schemaName, tableName}, (rs, rowNum) -> {
            DBTableColumn column = new DBTableColumn();
            column.setSchemaName(schemaName);
            column.setTableName(tableName);
            column.setName(rs.getString("COLNAME"));
            column.setTypeName(rs.getString("TYPENAME"));
            long len = rs.getLong("LENGTH");
            column.setMaxLength(len);
            column.setPrecision(len);
            column.setScale(rs.getInt("SCALE"));
            column.setNullable(!"N".equalsIgnoreCase(rs.getString("NULLS")));
            column.setDefaultValue(rs.getString("DEFAULT"));
            column.setComment(rs.getString("REMARKS"));
            column.setOrdinalPosition(rs.getInt("COLNO"));
            return column;
        });
    }

    @Override
    public Map<String, List<DBTableColumn>> listBasicTableColumns(String schemaName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBTableColumn> listBasicTableColumns(String schemaName, String tableName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public Map<String, List<DBTableColumn>> listBasicViewColumns(String schemaName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBTableColumn> listBasicViewColumns(String schemaName, String viewName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public Map<String, List<DBTableColumn>> listBasicExternalTableColumns(String schemaName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBTableColumn> listBasicExternalTableColumns(String schemaName, String externalTableName) {
        throw new UnsupportedOperationException("Not supported yet");
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
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public Map<String, List<DBTableIndex>> listTableIndexes(String schemaName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public Map<String, List<DBTableConstraint>> listTableConstraints(String schemaName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public Map<String, DBTableOptions> listTableOptions(String schemaName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public Map<String, DBTablePartition> listTablePartitions(@NonNull String schemaName, List<String> tableNames) {
        throw new UnsupportedOperationException("Not supported yet");
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
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBTableConstraint> listTableConstraints(String schemaName, String tableName) {
        String sql = "SELECT TABSCHEMA, TABNAME, CONSTNAME, TYPE FROM SYSCAT.TABCONST "
                + "WHERE TABSCHEMA = ? AND TABNAME = ? ORDER BY CONSTNAME";
        return jdbcOperations.query(sql, new Object[] {schemaName, tableName}, (rs, rowNum) -> {
            DBTableConstraint constraint = new DBTableConstraint();
            constraint.setSchemaName(rs.getString("TABSCHEMA"));
            constraint.setTableName(rs.getString("TABNAME"));
            constraint.setName(rs.getString("CONSTNAME"));
            String type = rs.getString("TYPE");
            constraint.setType(mapDb2ConstraintType(type));
            return constraint;
        });
    }

    private DBConstraintType mapDb2ConstraintType(String db2Type) {
        if (db2Type == null) {
            return DBConstraintType.UNKNOWN;
        }
        switch (db2Type.trim().toUpperCase()) {
            case "P":
                return DBConstraintType.PRIMARY_KEY;
            case "U":
                return DBConstraintType.UNIQUE_KEY;
            case "F":
                return DBConstraintType.FOREIGN_KEY;
            case "K":
                return DBConstraintType.CHECK;
            default:
                return DBConstraintType.UNKNOWN;
        }
    }

    @Override
    public DBTablePartition getPartition(String schemaName, String tableName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBTableIndex> listTableIndexes(String schemaName, String tableName) {
        String sql = "SELECT INDSCHEMA, INDNAME, TABSCHEMA, TABNAME, UNIQUERULE "
                + "FROM SYSCAT.INDEXES WHERE TABSCHEMA = ? AND TABNAME = ? ORDER BY INDNAME";
        return jdbcOperations.query(sql, new Object[] {schemaName, tableName}, (rs, rowNum) -> {
            DBTableIndex index = new DBTableIndex();
            index.setSchemaName(rs.getString("INDSCHEMA"));
            index.setName(rs.getString("INDNAME"));
            index.setTableName(rs.getString("TABNAME"));
            String uniqueRule = rs.getString("UNIQUERULE");
            // DB2 UNIQUERULE: D=Duplicates allowed, U=Unique, P=Primary
            index.setUnique(uniqueRule != null && !"D".equalsIgnoreCase(uniqueRule.trim()));
            return index;
        });
    }

    @Override
    public String getTableDDL(String schemaName, String tableName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public DBTableOptions getTableOptions(String schemaName, String tableName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public DBTableOptions getTableOptions(String schemaName, String tableName, String ddl) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBColumnGroupElement> listTableColumnGroups(String schemaName, String tableName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public DBView getView(String schemaName, String viewName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public DBFunction getFunction(String schemaName, String functionName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public DBProcedure getProcedure(String schemaName, String procedureName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public DBPackage getPackage(String schemaName, String packageName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public DBTrigger getTrigger(String schemaName, String packageName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public DBType getType(String schemaName, String typeName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public DBSequence getSequence(String schemaName, String sequenceName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public DBSynonym getSynonym(String schemaName, String synonymName, DBSynonymType synonymType) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public Map<String, DBTable> getTables(String schemaName, List<String> tableNames) {
        throw new UnsupportedOperationException("Not supported yet");
    }
}
