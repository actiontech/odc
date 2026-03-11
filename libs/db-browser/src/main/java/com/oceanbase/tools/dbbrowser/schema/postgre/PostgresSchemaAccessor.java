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
package com.oceanbase.tools.dbbrowser.schema.postgre;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.springframework.jdbc.BadSqlGrammarException;
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
import com.oceanbase.tools.dbbrowser.model.DBTablePartitionDefinition;
import com.oceanbase.tools.dbbrowser.model.DBTablePartitionOption;
import com.oceanbase.tools.dbbrowser.model.DBTablePartitionType;
import com.oceanbase.tools.dbbrowser.model.DBTableSubpartitionDefinition;
import com.oceanbase.tools.dbbrowser.model.DBTrigger;
import com.oceanbase.tools.dbbrowser.model.DBType;
import com.oceanbase.tools.dbbrowser.model.DBVariable;
import com.oceanbase.tools.dbbrowser.model.DBView;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;
import com.oceanbase.tools.dbbrowser.util.StringUtils;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PostgresSchemaAccessor implements DBSchemaAccessor {

    protected JdbcOperations jdbcOperations;

    public PostgresSchemaAccessor(@NonNull JdbcOperations jdbcOperations) {
        this.jdbcOperations = jdbcOperations;
    }

    @Override
    public List<String> showDatabases() {
        String sql = "SELECT schema_name FROM information_schema.schemata "
                + "where schema_name not like 'pg_%' "
                + "and schema_name <> 'information_schema'";
        return jdbcOperations.queryForList(sql, String.class);
    }

    @Override
    public DBDatabase getDatabase(String schemaName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBDatabase> listDatabases() {
        List<String> schemas = showDatabases();
        String sql = "SELECT "
                + "    datcollate AS collation, "
                + "    pg_encoding_to_char(encoding) AS charset "
                + "FROM pg_database "
                + "WHERE datname = current_database();";
        AtomicReference<String> charset = new AtomicReference<>();
        AtomicReference<String> collation = new AtomicReference<>();
        jdbcOperations.query(sql, rs -> {
            collation.set(rs.getString(1));
            charset.set(rs.getString(2));
        });
        return schemas.stream().map(schema -> {
            DBDatabase database = new DBDatabase();
            database.setId(schema);
            database.setName(schema);
            database.setCollation(collation.get());
            database.setCharset(charset.get());
            return database;
        }).collect(Collectors.toList());
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
    public List<String> showTables(String schemaName) {
        StringBuilder sb = new StringBuilder();
        sb.append("select table_name from information_schema.tables where table_schema = ");
        sb.append("'").append(schemaName).append("'");
        sb.append(" and table_type = 'BASE TABLE' ");
        sb.append(" and table_name not in (SELECT relname FROM pg_class c ");
        sb.append(" JOIN pg_inherits i ON c.oid = i.inhrelid);");
        List<String> tableNames;
        try {
            tableNames = jdbcOperations.query(sb.toString(), (rs, rowNum) -> rs.getString(1));
        } catch (BadSqlGrammarException e) {
            if (StringUtils.containsIgnoreCase(e.getMessage(), "Unknown schema")) {
                return Collections.emptyList();
            }
            throw e;
        }
        return tableNames;
    }

    @Override
    public List<String> showTablesLike(String schemaName, String tableNameLike) {
        // Implementation backed by information_schema. Wildcards (%, _) follow the same
        // semantics as SQL LIKE; an empty/blank pattern returns all tables in the schema.
        StringBuilder sb = new StringBuilder();
        sb.append("select table_name from information_schema.tables where table_schema = ");
        sb.append("'").append(schemaName).append("'");
        sb.append(" and table_type = 'BASE TABLE'");
        if (StringUtils.isNotBlank(tableNameLike)) {
            sb.append(" and table_name like ");
            sb.append("'").append(tableNameLike).append("'");
        }
        sb.append(";");
        try {
            return jdbcOperations.query(sb.toString(), (rs, rowNum) -> rs.getString(1));
        } catch (BadSqlGrammarException e) {
            if (StringUtils.containsIgnoreCase(e.getMessage(), "Unknown schema")) {
                return Collections.emptyList();
            }
            throw e;
        }
    }

    /**
     * 列出指定 schema 下的表
     * <p>
     * PostgreSQL 使用 pg_class 系统表查询表信息，relkind='r' 表示普通表，relkind='p' 表示分区表
     * </p>
     *
     * @param schemaName schema 名称
     * @param tableNameLike 表名匹配模式（可选）
     * @return 表对象列表
     */
    @Override
    public List<DBObjectIdentity> listTables(String schemaName, String tableNameLike) {
        if (StringUtils.isBlank(schemaName)) {
            // Drive the result entirely off information_schema; list tables across all user
            // schemas (excluding pg_* / information_schema), matching DBIdentitiesService.listTables.
            StringBuilder sb = new StringBuilder();
            sb.append("select table_schema, table_name from information_schema.tables where table_type = 'BASE TABLE'");
            sb.append(" and table_schema not like 'pg_%' and table_schema <> 'information_schema'");
            if (StringUtils.isNotBlank(tableNameLike)) {
                sb.append(" and table_name like '").append(tableNameLike).append("'");
            }
            sb.append(" order by table_schema, table_name;");
            try {
                return jdbcOperations.query(sb.toString(), (rs, rowNum) -> {
                    DBObjectIdentity identity = new DBObjectIdentity();
                    identity.setType(DBObjectType.TABLE);
                    identity.setSchemaName(rs.getString(1));
                    identity.setName(rs.getString(2));
                    return identity;
                });
            } catch (BadSqlGrammarException e) {
                if (StringUtils.containsIgnoreCase(e.getMessage(), "Unknown schema")) {
                    return Collections.emptyList();
                }
                throw e;
            }
        }

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT c.relname AS table_name, ");
        sql.append("       obj_description(c.oid) AS table_comment ");
        sql.append("FROM pg_catalog.pg_class c ");
        sql.append("INNER JOIN pg_catalog.pg_namespace n ON c.relnamespace = n.oid ");
        sql.append("WHERE n.nspname = ? ");
        sql.append("  AND c.relkind IN ('r', 'p') "); // r=普通表, p=分区表
        sql.append("  AND c.relispartition = false "); // 排除分区子表，只显示父表

        List<Object> params = new ArrayList<>();
        params.add(schemaName);

        if (StringUtils.isNotBlank(tableNameLike)) {
            sql.append("  AND c.relname LIKE ? ESCAPE '\\' ");
            params.add(StringUtils.escapeLike(tableNameLike));
        }

        sql.append("ORDER BY c.relname");

        try {
            return jdbcOperations.query(sql.toString(), params.toArray(), (rs, rowNum) -> {
                DBObjectIdentity identity = new DBObjectIdentity();
                identity.setSchemaName(schemaName);
                identity.setName(rs.getString("table_name"));
                identity.setType(DBObjectType.TABLE);
                return identity;
            });
        } catch (BadSqlGrammarException e) {
            if (StringUtils.containsIgnoreCase(e.getMessage(), "Unknown schema")) {
                return Collections.emptyList();
            }
            throw e;
        }
    }

    @Override
    public List<String> showExternalTablesLike(String schemaName, String tableNameLike) {
        // PostgreSQL/GaussDB don't expose ODC-style external tables; return an empty list so
        // callers that probe metadata (workbench identities, DBIdentitiesService.listExternalTables)
        // can degrade gracefully instead of bubbling an HTTP 500 to the UI.
        return Collections.emptyList();
    }

    @Override
    public List<DBObjectIdentity> listExternalTables(String schemaName, String tableNameLike) {
        // See showExternalTablesLike comment above; return empty for the same reason.
        return Collections.emptyList();
    }

    @Override
    public boolean isExternalTable(String schemaName, String tableName) {
        return false;
    }

    @Override
    public boolean syncExternalTableFiles(String schemaName, String tableName) {
        // PostgreSQL/GaussDB don't model external tables in the ODC sense; nothing to sync.
        return false;
    }

    @Override
    public List<DBObjectIdentity> listViews(String schemaName) {
        if (StringUtils.isBlank(schemaName)) {
            return Collections.emptyList();
        }
        String sql = "select table_schema, table_name from information_schema.views "
                + "where table_schema = '" + schemaName + "' "
                + "order by table_name;";
        try {
            return jdbcOperations.query(sql, (rs, rowNum) -> {
                DBObjectIdentity identity = new DBObjectIdentity();
                identity.setType(DBObjectType.VIEW);
                identity.setSchemaName(rs.getString(1));
                identity.setName(rs.getString(2));
                return identity;
            });
        } catch (BadSqlGrammarException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public List<DBObjectIdentity> listAllViews(String viewNameLike) {
        StringBuilder sb = new StringBuilder();
        sb.append("select table_schema, table_name from information_schema.views ");
        sb.append("where table_schema not like 'pg_%' and table_schema <> 'information_schema'");
        if (StringUtils.isNotBlank(viewNameLike)) {
            sb.append(" and table_name like '").append(viewNameLike).append("'");
        }
        sb.append(" order by table_schema, table_name;");
        try {
            return jdbcOperations.query(sb.toString(), (rs, rowNum) -> {
                DBObjectIdentity identity = new DBObjectIdentity();
                identity.setType(DBObjectType.VIEW);
                identity.setSchemaName(rs.getString(1));
                identity.setName(rs.getString(2));
                return identity;
            });
        } catch (BadSqlGrammarException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public List<DBObjectIdentity> listAllUserViews(String viewNameLike) {
        // Same as listAllViews — PG doesn't strongly distinguish user vs all here; we already
        // filter out pg_* / information_schema in listAllViews.
        return listAllViews(viewNameLike);
    }

    @Override
    public List<DBObjectIdentity> listAllSystemViews(String viewNameLike) {
        // System views in PG live in pg_catalog & information_schema. Returning an empty list
        // is safe for the workbench identities API (the user-facing tree does not surface them).
        return Collections.emptyList();
    }

    @Override
    public List<String> showSystemViews(String schemaName) {
        return Collections.emptyList();
    }

    @Override
    public List<DBObjectIdentity> listMViews(String schemaName) {
        // Materialized views — query pg_matviews; if it doesn't exist (very old PG) fall back
        // to an empty list rather than 500-ing the workbench identities API.
        if (StringUtils.isBlank(schemaName)) {
            return Collections.emptyList();
        }
        String sql = "select schemaname, matviewname from pg_matviews "
                + "where schemaname = '" + schemaName + "' "
                + "order by matviewname;";
        try {
            return jdbcOperations.query(sql, (rs, rowNum) -> {
                DBObjectIdentity identity = new DBObjectIdentity();
                identity.setType(DBObjectType.MATERIALIZED_VIEW);
                identity.setSchemaName(rs.getString(1));
                identity.setName(rs.getString(2));
                return identity;
            });
        } catch (BadSqlGrammarException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public List<DBObjectIdentity> listAllMViewsLike(String mViewNameLike) {
        StringBuilder sb = new StringBuilder();
        sb.append("select schemaname, matviewname from pg_matviews ");
        sb.append("where schemaname not like 'pg_%' and schemaname <> 'information_schema'");
        if (StringUtils.isNotBlank(mViewNameLike)) {
            sb.append(" and matviewname like '").append(mViewNameLike).append("'");
        }
        sb.append(" order by schemaname, matviewname;");
        try {
            return jdbcOperations.query(sb.toString(), (rs, rowNum) -> {
                DBObjectIdentity identity = new DBObjectIdentity();
                identity.setType(DBObjectType.MATERIALIZED_VIEW);
                identity.setSchemaName(rs.getString(1));
                identity.setName(rs.getString(2));
                return identity;
            });
        } catch (BadSqlGrammarException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public Boolean refreshMVData(DBMViewRefreshParameter parameter) {
        throw new UnsupportedOperationException("not support yet");
    }

    @Override
    public DBMaterializedView getMView(String schemaName, String mViewName) {
        throw new UnsupportedOperationException("not support yet");
    }

    @Override
    public List<DBTableConstraint> listMViewConstraints(String schemaName, String mViewName) {
        throw new UnsupportedOperationException("not support yet");
    }

    @Override
    public List<DBMViewRefreshRecord> listMViewRefreshRecords(DBMViewRefreshRecordParam param) {
        throw new UnsupportedOperationException("not support yet");
    }

    @Override
    public List<DBTableIndex> listMViewIndexes(String schemaName, String mViewName) {
        throw new UnsupportedOperationException("not support yet");
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
        String sql = "select DISTINCT pg_encoding_to_char(collencoding) from pg_collation;";
        return jdbcOperations.queryForList(sql, String.class);
    }

    @Override
    public List<String> showCollation() {
        String sql = "select DISTINCT collname  from pg_collation;";
        return jdbcOperations.queryForList(sql, String.class);
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
    public List<DBObjectIdentity> listSynonyms(String schemaName,
            DBSynonymType synonymType) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    /**
     * 批量列出指定表的列信息
     */
    @Override
    public Map<String, List<DBTableColumn>> listTableColumns(
            String schemaName, List<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            return Collections.emptyMap();
        }

        // 构建 IN 子句
        StringBuilder inClause = new StringBuilder();
        List<Object> params = new ArrayList<>();
        params.add(schemaName);
        for (int i = 0; i < tableNames.size(); i++) {
            if (i > 0) {
                inClause.append(",");
            }
            inClause.append("?");
            params.add(tableNames.get(i));
        }

        String sql = "SELECT " +
                "    c.relname AS table_name, " +
                "    a.attnum AS ordinal_position, " +
                "    a.attname AS column_name, " +
                "    pg_catalog.format_type(a.atttypid, a.atttypmod) AS data_type, " +
                "    t.typname AS type_name, " +
                "    a.attnotnull AS not_null, " +
                "    pg_get_expr(d.adbin, d.adrelid) AS default_value, " +
                "    col_description(a.attrelid, a.attnum) AS column_comment, " +
                "    CASE WHEN a.atttypmod > 0 AND t.typname IN ('varchar', 'char', 'bpchar') " +
                "         THEN a.atttypmod - 4 " +
                "         ELSE NULL END AS char_length, " +
                "    CASE WHEN a.atttypmod > 0 AND t.typname = 'numeric' " +
                "         THEN ((a.atttypmod - 4) >> 16) & 65535 " +
                "         ELSE NULL END AS numeric_precision, " +
                "    CASE WHEN a.atttypmod > 0 AND t.typname = 'numeric' " +
                "         THEN (a.atttypmod - 4) & 65535 " +
                "         ELSE NULL END AS numeric_scale " +
                "FROM pg_catalog.pg_attribute a " +
                "INNER JOIN pg_catalog.pg_class c ON a.attrelid = c.oid " +
                "INNER JOIN pg_catalog.pg_namespace n ON c.relnamespace = n.oid " +
                "INNER JOIN pg_catalog.pg_type t ON a.atttypid = t.oid " +
                "LEFT JOIN pg_catalog.pg_attrdef d ON a.attrelid = d.adrelid AND a.attnum = d.adnum " +
                "WHERE n.nspname = ? " +
                "  AND c.relname IN (" + inClause.toString() + ") " +
                "  AND a.attnum > 0 " +
                "  AND NOT a.attisdropped " +
                "ORDER BY c.relname, a.attnum";

        try {
            List<DBTableColumn> columns = jdbcOperations.query(sql, params.toArray(), (rs, rowNum) -> {
                DBTableColumn column = new DBTableColumn();
                column.setSchemaName(schemaName);
                String tableName = rs.getString("table_name");
                column.setTableName(tableName);
                column.setOrdinalPosition(rs.getInt("ordinal_position"));
                column.setName(rs.getString("column_name"));
                column.setTypeName(rs.getString("type_name"));
                column.setFullTypeName(rs.getString("data_type"));
                column.setNullable(!rs.getBoolean("not_null"));

                String defaultValue = rs.getString("default_value");
                if (StringUtils.isNotBlank(defaultValue)) {
                    column.fillDefaultValue(defaultValue);
                }

                String comment = rs.getString("column_comment");
                if (StringUtils.isNotBlank(comment)) {
                    column.setComment(comment);
                }

                // 处理字符长度
                Object charLengthObj = rs.getObject("char_length");
                if (charLengthObj != null) {
                    column.setMaxLength(rs.getLong("char_length"));
                }

                // 处理数值精度
                Object precisionObj = rs.getObject("numeric_precision");
                if (precisionObj != null) {
                    column.setPrecision(rs.getLong("numeric_precision"));
                }

                Object scaleObj = rs.getObject("numeric_scale");
                if (scaleObj != null) {
                    column.setScale(rs.getInt("numeric_scale"));
                }

                return column;
            });
            return columns.stream()
                    .filter(col -> col.getTableName() != null)
                    .collect(Collectors.groupingBy(DBTableColumn::getTableName));
        } catch (BadSqlGrammarException e) {
            if (StringUtils.containsIgnoreCase(e.getMessage(), "Unknown schema")) {
                return Collections.emptyMap();
            }
            throw e;
        }
    }

    /**
     * 列出指定表的完整列信息
     * <p>
     * 查询 pg_attribute 获取列信息，pg_attrdef 获取默认值，col_description 获取注释
     * </p>
     *
     * @param schemaName schema 名称
     * @param tableName 表名
     * @return 列信息列表
     */
    @Override
    public List<DBTableColumn> listTableColumns(String schemeName, String tableName) {
        // Build a DBTableColumn list from information_schema.columns. We deliberately use only
        // information_schema (portable across PostgreSQL / openGauss / GaussDB) plus a single
        // pg_class/pg_index probe for the PRIMARY KEY flag. Columns that aren't part of any
        // primary key get KeyType.NONE so the UI renders them normally.
        if (StringUtils.isBlank(schemeName) || StringUtils.isBlank(tableName)) {
            return Collections.emptyList();
        }
        // Step 1: pull the PK column names so we can stamp KeyType.PRI on the matching DBTableColumn.
        java.util.Set<String> pkColumns = new java.util.HashSet<>();
        String pkSql = "select kcu.column_name "
                + "from information_schema.table_constraints tc "
                + "join information_schema.key_column_usage kcu "
                + "  on tc.constraint_name = kcu.constraint_name "
                + " and tc.table_schema = kcu.table_schema "
                + " and tc.table_name = kcu.table_name "
                + "where tc.constraint_type = 'PRIMARY KEY' "
                + " and tc.table_schema = '" + schemeName + "' "
                + " and tc.table_name = '" + tableName + "';";
        try {
            pkColumns.addAll(jdbcOperations.query(pkSql, (rs, rowNum) -> rs.getString(1)));
        } catch (BadSqlGrammarException ignored) {
            // Swallow — proceed without PK info rather than failing the whole metadata read.
        }

        // Step 2: read the column list itself.
        String sql = "select column_name, data_type, udt_name, character_maximum_length, "
                + "numeric_precision, numeric_scale, is_nullable, column_default, "
                + "ordinal_position "
                + "from information_schema.columns "
                + "where table_schema = '" + schemeName + "' and table_name = '" + tableName + "' "
                + "order by ordinal_position;";
        try {
            return jdbcOperations.query(sql, (rs, rowNum) -> {
                DBTableColumn column = new DBTableColumn();
                column.setSchemaName(schemeName);
                column.setTableName(tableName);
                String name = rs.getString(1);
                column.setName(name);
                String dataType = rs.getString(2);
                String udtName = rs.getString(3);
                // udt_name is the most specific type (e.g. "varchar", "int4", "jsonb",
                // "_text" for arrays). Prefer it over data_type for ODC's column rendering,
                // but fall back to data_type when udt_name is null.
                String typeName = udtName != null ? udtName : dataType;
                column.setTypeName(typeName);
                // PG returns these length / precision / scale columns as Number subclasses
                // (Integer for openGauss / Long for some PG drivers). Use Number#xxxValue() so
                // we don't ClassCastException when the JDBC driver hands us an Integer where
                // we expected a Long (the bug seen in the initial 5104c79a build).
                Number charMaxLenObj = (Number) rs.getObject(4);
                Number numericPrecisionObj = (Number) rs.getObject(5);
                Number numericScaleObj = (Number) rs.getObject(6);
                column.setFullTypeName(
                        buildFullTypeName(typeName, charMaxLenObj, numericPrecisionObj, numericScaleObj));
                if (charMaxLenObj != null) {
                    column.setMaxLength(charMaxLenObj.longValue());
                }
                if (numericPrecisionObj != null) {
                    column.setPrecision(numericPrecisionObj.longValue());
                }
                if (numericScaleObj != null) {
                    column.setScale(numericScaleObj.intValue());
                }
                String isNullable = rs.getString(7);
                column.setNullable("YES".equalsIgnoreCase(isNullable));
                column.setDefaultValue(rs.getString(8));
                column.setOrdinalPosition(rs.getInt(9));
                if (pkColumns.contains(name)) {
                    column.setKeyType(DBTableColumn.KeyType.PRI);
                }
                return column;
            });
        } catch (BadSqlGrammarException e) {
            return Collections.emptyList();
        }
    }

    /**
     * Build a human-readable full type name. Mirrors information_schema conventions:
     * <ul>
     * <li>{@code varchar(255)} when only character_maximum_length is set</li>
     * <li>{@code numeric(10,2)} when numeric_precision + numeric_scale are set</li>
     * <li>{@code numeric(10)} when only numeric_precision is set</li>
     * <li>plain type name otherwise (e.g. {@code jsonb}, {@code timestamptz})</li>
     * </ul>
     */
    private String buildFullTypeName(String typeName, Number charMaxLen, Number numericPrecision,
            Number numericScale) {
        if (charMaxLen != null) {
            return typeName + "(" + charMaxLen.longValue() + ")";
        }
        if (numericPrecision != null) {
            if (numericScale != null) {
                return typeName + "(" + numericPrecision.longValue() + "," + numericScale.intValue() + ")";
            }
            return typeName + "(" + numericPrecision.longValue() + ")";
        }
        return typeName;
    }

    /**
     * 列出指定 schema 下所有表的基本列信息
     * <p>
     * 使用 pg_attribute 批量查询所有表的列信息，用于对象树展开场景
     * </p>
     *
     * @param schemaName schema 名称
     * @return 表名到列列表的映射
     */
    @Override
    public Map<String, List<DBTableColumn>> listBasicTableColumns(String schemaName) {
        String sql = "SELECT " +
                "    c.relname AS table_name, " +
                "    a.attnum AS ordinal_position, " +
                "    a.attname AS column_name, " +
                "    pg_catalog.format_type(a.atttypid, a.atttypmod) AS data_type, " +
                "    t.typname AS type_name, " +
                "    col_description(a.attrelid, a.attnum) AS column_comment " +
                "FROM pg_catalog.pg_attribute a " +
                "INNER JOIN pg_catalog.pg_class c ON a.attrelid = c.oid " +
                "INNER JOIN pg_catalog.pg_namespace n ON c.relnamespace = n.oid " +
                "INNER JOIN pg_catalog.pg_type t ON a.atttypid = t.oid " +
                "WHERE n.nspname = ? " +
                "  AND c.relkind IN ('r', 'p') " +
                "  AND a.attnum > 0 " +
                "  AND NOT a.attisdropped " +
                "ORDER BY c.relname, a.attnum";

        try {
            List<DBTableColumn> columns = jdbcOperations.query(sql, new Object[] {schemaName}, (rs, rowNum) -> {
                DBTableColumn column = new DBTableColumn();
                column.setSchemaName(schemaName);
                column.setTableName(rs.getString("table_name"));
                column.setOrdinalPosition(rs.getInt("ordinal_position"));
                column.setName(rs.getString("column_name"));
                column.setTypeName(rs.getString("type_name"));
                column.setFullTypeName(rs.getString("data_type"));
                String comment = rs.getString("column_comment");
                if (StringUtils.isNotBlank(comment)) {
                    column.setComment(comment);
                }
                return column;
            });
            return columns.stream()
                    .filter(col -> col.getTableName() != null)
                    .collect(Collectors.groupingBy(DBTableColumn::getTableName));
        } catch (BadSqlGrammarException e) {
            if (StringUtils.containsIgnoreCase(e.getMessage(), "Unknown schema")) {
                return Collections.emptyMap();
            }
            throw e;
        }
    }

    /**
     * 列出指定表的基本列信息
     */
    @Override
    public List<DBTableColumn> listBasicTableColumns(String schemaName, String tableName) {
        String sql = "SELECT " +
                "    a.attnum AS ordinal_position, " +
                "    a.attname AS column_name, " +
                "    pg_catalog.format_type(a.atttypid, a.atttypmod) AS data_type, " +
                "    t.typname AS type_name, " +
                "    col_description(a.attrelid, a.attnum) AS column_comment " +
                "FROM pg_catalog.pg_attribute a " +
                "INNER JOIN pg_catalog.pg_class c ON a.attrelid = c.oid " +
                "INNER JOIN pg_catalog.pg_namespace n ON c.relnamespace = n.oid " +
                "INNER JOIN pg_catalog.pg_type t ON a.atttypid = t.oid " +
                "WHERE n.nspname = ? " +
                "  AND c.relname = ? " +
                "  AND a.attnum > 0 " +
                "  AND NOT a.attisdropped " +
                "ORDER BY a.attnum";

        try {
            return jdbcOperations.query(sql, new Object[] {schemaName, tableName}, (rs, rowNum) -> {
                DBTableColumn column = new DBTableColumn();
                column.setSchemaName(schemaName);
                column.setTableName(tableName);
                column.setOrdinalPosition(rs.getInt("ordinal_position"));
                column.setName(rs.getString("column_name"));
                column.setTypeName(rs.getString("type_name"));
                column.setFullTypeName(rs.getString("data_type"));
                String comment = rs.getString("column_comment");
                if (StringUtils.isNotBlank(comment)) {
                    column.setComment(comment);
                }
                return column;
            });
        } catch (BadSqlGrammarException e) {
            if (StringUtils.containsIgnoreCase(e.getMessage(), "Unknown schema") ||
                    StringUtils.containsIgnoreCase(e.getMessage(), "relation")) {
                return Collections.emptyList();
            }
            throw e;
        }
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
        throw new UnsupportedOperationException("not support yet");
    }

    @Override
    public List<DBTableColumn> listBasicExternalTableColumns(String schemaName, String externalTableName) {
        throw new UnsupportedOperationException("not support yet");
    }

    @Override
    public Map<String, List<DBTableColumn>> listBasicMViewColumns(String schemaName) {
        throw new UnsupportedOperationException("not support yet");
    }

    @Override
    public List<DBTableColumn> listBasicMViewColumns(String schemaName, String externalTableName) {
        throw new UnsupportedOperationException("not support yet");
    }

    @Override
    public Map<String, List<DBTableColumn>> listBasicColumnsInfo(String schemaName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    /**
     * 列出指定 schema 下所有表的索引信息
     *
     * @param schemaName schema 名称
     * @return 表名到索引列表的映射
     */
    @Override
    public Map<String, List<DBTableIndex>> listTableIndexes(String schemaName) {
        String sql = "SELECT " +
                "    t.relname AS table_name, " +
                "    i.relname AS index_name, " +
                "    ix.indisunique AS is_unique, " +
                "    ix.indisprimary AS is_primary, " +
                "    am.amname AS index_type, " +
                "    a.attname AS column_name, " +
                "    array_position(ix.indkey, a.attnum) AS column_position " +
                "FROM pg_catalog.pg_index ix " +
                "INNER JOIN pg_catalog.pg_class i ON ix.indexrelid = i.oid " +
                "INNER JOIN pg_catalog.pg_class t ON ix.indrelid = t.oid " +
                "INNER JOIN pg_catalog.pg_namespace n ON t.relnamespace = n.oid " +
                "INNER JOIN pg_catalog.pg_am am ON i.relam = am.oid " +
                "INNER JOIN pg_catalog.pg_attribute a ON a.attrelid = t.oid AND a.attnum = ANY(ix.indkey) " +
                "WHERE n.nspname = ? " +
                "ORDER BY t.relname, i.relname, column_position";

        try {
            Map<String, Map<String, DBTableIndex>> tableIndexMap = new LinkedHashMap<>();
            Map<String, AtomicInteger> tableIndexCounterMap = new HashMap<>();

            jdbcOperations.query(sql, new Object[] {schemaName}, (rs, rowNum) -> {
                String tableName = rs.getString("table_name");
                String indexName = rs.getString("index_name");

                Map<String, DBTableIndex> indexMap = tableIndexMap.computeIfAbsent(tableName,
                        k -> new LinkedHashMap<>());
                DBTableIndex index = indexMap.get(indexName);

                if (index == null) {
                    index = new DBTableIndex();
                    index.setSchemaName(schemaName);
                    index.setTableName(tableName);
                    index.setName(indexName);
                    index.setOrdinalPosition(tableIndexCounterMap.computeIfAbsent(tableName, k -> new AtomicInteger(1))
                            .getAndIncrement());
                    index.setUnique(rs.getBoolean("is_unique"));
                    index.setPrimary(rs.getBoolean("is_primary"));
                    index.setNonUnique(!index.getUnique());
                    index.setColumnNames(new ArrayList<>());

                    String indexType = rs.getString("index_type");
                    if ("btree".equalsIgnoreCase(indexType)) {
                        if (index.getUnique()) {
                            index.setType(DBIndexType.UNIQUE);
                        } else {
                            index.setType(DBIndexType.NORMAL);
                        }
                    } else if ("hash".equalsIgnoreCase(indexType)) {
                        index.setType(DBIndexType.NORMAL);
                    } else if ("gin".equalsIgnoreCase(indexType)) {
                        index.setType(DBIndexType.FULLTEXT);
                    } else if ("gist".equalsIgnoreCase(indexType)) {
                        index.setType(DBIndexType.SPATIAL);
                    } else {
                        index.setType(DBIndexType.UNKNOWN);
                    }

                    indexMap.put(indexName, index);
                }

                String columnName = rs.getString("column_name");
                if (columnName != null) {
                    index.getColumnNames().add(columnName);
                }

                return null;
            });

            // 转换为 Map<String, List<DBTableIndex>>
            Map<String, List<DBTableIndex>> result = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, DBTableIndex>> entry : tableIndexMap.entrySet()) {
                result.put(entry.getKey(), new ArrayList<>(entry.getValue().values()));
            }
            return result;
        } catch (BadSqlGrammarException e) {
            if (StringUtils.containsIgnoreCase(e.getMessage(), "Unknown schema")) {
                return Collections.emptyMap();
            }
            throw e;
        }
    }

    /**
     * 列出指定表的索引信息
     *
     * @param schemaName schema 名称
     * @param tableName 表名
     * @return 索引列表
     */
    @Override
    public List<DBTableIndex> listTableIndexes(String schemaName, String tableName) {
        String sql = "SELECT " +
                "    i.relname AS index_name, " +
                "    ix.indisunique AS is_unique, " +
                "    ix.indisprimary AS is_primary, " +
                "    am.amname AS index_type, " +
                "    pg_get_indexdef(ix.indexrelid) AS index_definition, " +
                "    obj_description(ix.indexrelid) AS index_comment, " +
                "    a.attname AS column_name, " +
                "    array_position(ix.indkey, a.attnum) AS column_position " +
                "FROM pg_catalog.pg_index ix " +
                "INNER JOIN pg_catalog.pg_class i ON ix.indexrelid = i.oid " +
                "INNER JOIN pg_catalog.pg_class t ON ix.indrelid = t.oid " +
                "INNER JOIN pg_catalog.pg_namespace n ON t.relnamespace = n.oid " +
                "INNER JOIN pg_catalog.pg_am am ON i.relam = am.oid " +
                "INNER JOIN pg_catalog.pg_attribute a ON a.attrelid = t.oid AND a.attnum = ANY(ix.indkey) " +
                "WHERE n.nspname = ? AND t.relname = ? " +
                "ORDER BY i.relname, column_position";

        try {
            Map<String, DBTableIndex> indexMap = new LinkedHashMap<>();
            AtomicInteger ordinalCounter = new AtomicInteger(1);

            jdbcOperations.query(sql, new Object[] {schemaName, tableName}, (rs, rowNum) -> {
                String indexName = rs.getString("index_name");
                DBTableIndex index = indexMap.get(indexName);

                if (index == null) {
                    index = new DBTableIndex();
                    index.setSchemaName(schemaName);
                    index.setTableName(tableName);
                    index.setName(indexName);
                    index.setOrdinalPosition(ordinalCounter.getAndIncrement());
                    index.setUnique(rs.getBoolean("is_unique"));
                    index.setPrimary(rs.getBoolean("is_primary"));
                    index.setNonUnique(!index.getUnique());
                    index.setColumnNames(new ArrayList<>());

                    String indexType = rs.getString("index_type");
                    if ("btree".equalsIgnoreCase(indexType)) {
                        if (index.getUnique()) {
                            index.setType(DBIndexType.UNIQUE);
                        } else {
                            index.setType(DBIndexType.NORMAL);
                        }
                    } else if ("hash".equalsIgnoreCase(indexType)) {
                        index.setType(DBIndexType.NORMAL);
                    } else if ("gin".equalsIgnoreCase(indexType)) {
                        index.setType(DBIndexType.FULLTEXT);
                    } else if ("gist".equalsIgnoreCase(indexType)) {
                        index.setType(DBIndexType.SPATIAL);
                    } else {
                        index.setType(DBIndexType.UNKNOWN);
                    }

                    String definition = rs.getString("index_definition");
                    if (StringUtils.isNotBlank(definition)) {
                        index.setDdl(definition);
                    }

                    String comment = rs.getString("index_comment");
                    if (StringUtils.isNotBlank(comment)) {
                        index.setComment(comment);
                    }

                    indexMap.put(indexName, index);
                }

                String columnName = rs.getString("column_name");
                if (columnName != null) {
                    index.getColumnNames().add(columnName);
                }

                return null;
            });

            return new ArrayList<>(indexMap.values());
        } catch (BadSqlGrammarException e) {
            if (StringUtils.containsIgnoreCase(e.getMessage(), "Unknown schema") ||
                    StringUtils.containsIgnoreCase(e.getMessage(), "relation")) {
                return Collections.emptyList();
            }
            throw e;
        }
    }

    /**
     * 列出指定 schema 下所有表的约束信息
     *
     * @param schemaName schema 名称
     * @return 表名到约束列表的映射
     */
    @Override
    public Map<String, List<DBTableConstraint>> listTableConstraints(
            String schemaName) {
        Map<String, Map<String, DBTableConstraint>> tableConstraintMap = new LinkedHashMap<>();
        AtomicInteger constraintCounter = new AtomicInteger(1);

        // 查询主键和唯一约束
        String pkUniqueSql = "SELECT " +
                "    t.relname AS table_name, " +
                "    con.conname AS constraint_name, " +
                "    con.contype AS constraint_type, " +
                "    a.attname AS column_name " +
                "FROM pg_catalog.pg_constraint con " +
                "INNER JOIN pg_catalog.pg_class t ON con.conrelid = t.oid " +
                "INNER JOIN pg_catalog.pg_namespace n ON t.relnamespace = n.oid " +
                "INNER JOIN pg_catalog.pg_attribute a ON a.attrelid = t.oid AND a.attnum = ANY(con.conkey) " +
                "WHERE n.nspname = ? " +
                "  AND con.contype IN ('p', 'u') " +
                "ORDER BY t.relname, con.conname, a.attnum";

        jdbcOperations.query(pkUniqueSql, new Object[] {schemaName}, (rs, rowNum) -> {
            String tableName = rs.getString("table_name");
            String constraintName = rs.getString("constraint_name");
            String constraintType = rs.getString("constraint_type");

            Map<String, DBTableConstraint> constraintMap = tableConstraintMap.computeIfAbsent(tableName,
                    k -> new LinkedHashMap<>());
            DBTableConstraint constraint = constraintMap.get(constraintName);

            if (constraint == null) {
                constraint = new DBTableConstraint();
                constraint.setName(constraintName);
                constraint.setSchemaName(schemaName);
                constraint.setTableName(tableName);
                constraint.setOwner(schemaName);
                constraint.setOrdinalPosition(constraintCounter.getAndIncrement());
                constraint.setColumnNames(new ArrayList<>());

                if ("p".equals(constraintType)) {
                    constraint.setType(DBConstraintType.PRIMARY_KEY);
                } else if ("u".equals(constraintType)) {
                    constraint.setType(DBConstraintType.UNIQUE);
                }

                constraintMap.put(constraintName, constraint);
            }

            String columnName = rs.getString("column_name");
            if (columnName != null) {
                constraint.getColumnNames().add(columnName);
            }

            return null;
        });

        // 查询外键约束
        String fkSql = "SELECT " +
                "    t.relname AS table_name, " +
                "    con.conname AS constraint_name, " +
                "    a.attname AS column_name, " +
                "    ref_ns.nspname AS referenced_schema_name, " +
                "    ref_t.relname AS referenced_table_name, " +
                "    ref_a.attname AS referenced_column_name, " +
                "    con.confupdtype AS update_action, " +
                "    con.confdeltype AS delete_action " +
                "FROM pg_catalog.pg_constraint con " +
                "INNER JOIN pg_catalog.pg_class t ON con.conrelid = t.oid " +
                "INNER JOIN pg_catalog.pg_namespace n ON t.relnamespace = n.oid " +
                "INNER JOIN pg_catalog.pg_class ref_t ON con.confrelid = ref_t.oid " +
                "INNER JOIN pg_catalog.pg_namespace ref_ns ON ref_t.relnamespace = ref_ns.oid " +
                "INNER JOIN pg_catalog.pg_attribute a ON a.attrelid = t.oid AND a.attnum = ANY(con.conkey) " +
                "INNER JOIN pg_catalog.pg_attribute ref_a ON ref_a.attrelid = ref_t.oid AND ref_a.attnum = ANY(con.confkey) "
                +
                "WHERE n.nspname = ? " +
                "  AND con.contype = 'f' " +
                "ORDER BY t.relname, con.conname, a.attnum";

        jdbcOperations.query(fkSql, new Object[] {schemaName}, (rs, rowNum) -> {
            String tableName = rs.getString("table_name");
            String constraintName = rs.getString("constraint_name");

            Map<String, DBTableConstraint> constraintMap = tableConstraintMap.computeIfAbsent(tableName,
                    k -> new LinkedHashMap<>());
            DBTableConstraint constraint = constraintMap.get(constraintName);

            if (constraint == null) {
                constraint = new DBTableConstraint();
                constraint.setName(constraintName);
                constraint.setSchemaName(schemaName);
                constraint.setTableName(tableName);
                constraint.setOwner(schemaName);
                constraint.setType(DBConstraintType.FOREIGN_KEY);
                constraint.setOrdinalPosition(constraintCounter.getAndIncrement());
                constraint.setColumnNames(new ArrayList<>());
                constraint.setReferenceColumnNames(new ArrayList<>());
                constraint.setReferenceSchemaName(rs.getString("referenced_schema_name"));
                constraint.setReferenceTableName(rs.getString("referenced_table_name"));

                // 解析 ON UPDATE 和 ON DELETE 规则
                constraint.setOnUpdateRule(mapPgConstraintAction(rs.getString("update_action")));
                constraint.setOnDeleteRule(mapPgConstraintAction(rs.getString("delete_action")));

                constraintMap.put(constraintName, constraint);
            }

            String columnName = rs.getString("column_name");
            if (columnName != null && !constraint.getColumnNames().contains(columnName)) {
                constraint.getColumnNames().add(columnName);
            }

            String refColumnName = rs.getString("referenced_column_name");
            if (refColumnName != null && !constraint.getReferenceColumnNames().contains(refColumnName)) {
                constraint.getReferenceColumnNames().add(refColumnName);
            }

            return null;
        });

        // 查询检查约束
        String checkSql = "SELECT " +
                "    t.relname AS table_name, " +
                "    con.conname AS constraint_name, " +
                "    pg_get_constraintdef(con.oid) AS constraint_definition " +
                "FROM pg_catalog.pg_constraint con " +
                "INNER JOIN pg_catalog.pg_class t ON con.conrelid = t.oid " +
                "INNER JOIN pg_catalog.pg_namespace n ON t.relnamespace = n.oid " +
                "WHERE n.nspname = ? " +
                "  AND con.contype = 'c' " +
                "ORDER BY t.relname, con.conname";

        jdbcOperations.query(checkSql, new Object[] {schemaName}, (rs, rowNum) -> {
            String tableName = rs.getString("table_name");
            String constraintName = rs.getString("constraint_name");
            String definition = rs.getString("constraint_definition");

            Map<String, DBTableConstraint> constraintMap = tableConstraintMap.computeIfAbsent(tableName,
                    k -> new LinkedHashMap<>());
            DBTableConstraint constraint = new DBTableConstraint();
            constraint.setName(constraintName);
            constraint.setSchemaName(schemaName);
            constraint.setTableName(tableName);
            constraint.setOwner(schemaName);
            constraint.setType(DBConstraintType.CHECK);
            constraint.setOrdinalPosition(constraintCounter.getAndIncrement());
            constraint.setColumnNames(new ArrayList<>());

            if (StringUtils.isNotBlank(definition)) {
                // 从定义中提取 CHECK 子句
                int checkStart = definition.toUpperCase().indexOf("CHECK");
                if (checkStart >= 0) {
                    constraint.setCheckClause(definition.substring(checkStart));
                } else {
                    constraint.setCheckClause(definition);
                }
            }

            constraintMap.put(constraintName, constraint);
            return null;
        });

        // 转换为 Map<String, List<DBTableConstraint>>
        Map<String, List<DBTableConstraint>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, DBTableConstraint>> entry : tableConstraintMap.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue().values()));
        }
        return result;
    }

    /**
     * 列出指定表的约束信息
     *
     * @param schemaName schema 名称
     * @param tableName 表名
     * @return 约束列表
     */
    @Override
    public List<DBTableConstraint> listTableConstraints(String schemaName, String tableName) {
        List<DBTableConstraint> constraints = new ArrayList<>();
        AtomicInteger ordinalCounter = new AtomicInteger(1);

        // 查询主键和唯一约束
        String pkUniqueSql = "SELECT " +
                "    con.conname AS constraint_name, " +
                "    con.contype AS constraint_type, " +
                "    a.attname AS column_name " +
                "FROM pg_catalog.pg_constraint con " +
                "INNER JOIN pg_catalog.pg_class t ON con.conrelid = t.oid " +
                "INNER JOIN pg_catalog.pg_namespace n ON t.relnamespace = n.oid " +
                "INNER JOIN pg_catalog.pg_attribute a ON a.attrelid = t.oid AND a.attnum = ANY(con.conkey) " +
                "WHERE n.nspname = ? AND t.relname = ? " +
                "  AND con.contype IN ('p', 'u') " +
                "ORDER BY con.conname, a.attnum";

        Map<String, DBTableConstraint> constraintMap = new LinkedHashMap<>();

        jdbcOperations.query(pkUniqueSql, new Object[] {schemaName, tableName}, (rs, rowNum) -> {
            String constraintName = rs.getString("constraint_name");
            String constraintType = rs.getString("constraint_type");

            DBTableConstraint constraint = constraintMap.get(constraintName);

            if (constraint == null) {
                constraint = new DBTableConstraint();
                constraint.setName(constraintName);
                constraint.setSchemaName(schemaName);
                constraint.setTableName(tableName);
                constraint.setOwner(schemaName);
                constraint.setOrdinalPosition(ordinalCounter.getAndIncrement());
                constraint.setColumnNames(new ArrayList<>());

                if ("p".equals(constraintType)) {
                    constraint.setType(DBConstraintType.PRIMARY_KEY);
                } else if ("u".equals(constraintType)) {
                    constraint.setType(DBConstraintType.UNIQUE);
                }

                constraintMap.put(constraintName, constraint);
            }

            String columnName = rs.getString("column_name");
            if (columnName != null) {
                constraint.getColumnNames().add(columnName);
            }

            return null;
        });

        // 查询外键约束
        String fkSql = "SELECT " +
                "    con.conname AS constraint_name, " +
                "    a.attname AS column_name, " +
                "    ref_ns.nspname AS referenced_schema_name, " +
                "    ref_t.relname AS referenced_table_name, " +
                "    ref_a.attname AS referenced_column_name, " +
                "    con.confupdtype AS update_action, " +
                "    con.confdeltype AS delete_action " +
                "FROM pg_catalog.pg_constraint con " +
                "INNER JOIN pg_catalog.pg_class t ON con.conrelid = t.oid " +
                "INNER JOIN pg_catalog.pg_namespace n ON t.relnamespace = n.oid " +
                "INNER JOIN pg_catalog.pg_class ref_t ON con.confrelid = ref_t.oid " +
                "INNER JOIN pg_catalog.pg_namespace ref_ns ON ref_t.relnamespace = ref_ns.oid " +
                "INNER JOIN pg_catalog.pg_attribute a ON a.attrelid = t.oid AND a.attnum = ANY(con.conkey) " +
                "INNER JOIN pg_catalog.pg_attribute ref_a ON ref_a.attrelid = ref_t.oid AND ref_a.attnum = ANY(con.confkey) "
                +
                "WHERE n.nspname = ? AND t.relname = ? " +
                "  AND con.contype = 'f' " +
                "ORDER BY con.conname, a.attnum";

        jdbcOperations.query(fkSql, new Object[] {schemaName, tableName}, (rs, rowNum) -> {
            String constraintName = rs.getString("constraint_name");

            DBTableConstraint constraint = constraintMap.get(constraintName);

            if (constraint == null) {
                constraint = new DBTableConstraint();
                constraint.setName(constraintName);
                constraint.setSchemaName(schemaName);
                constraint.setTableName(tableName);
                constraint.setOwner(schemaName);
                constraint.setType(DBConstraintType.FOREIGN_KEY);
                constraint.setOrdinalPosition(ordinalCounter.getAndIncrement());
                constraint.setColumnNames(new ArrayList<>());
                constraint.setReferenceColumnNames(new ArrayList<>());
                constraint.setReferenceSchemaName(rs.getString("referenced_schema_name"));
                constraint.setReferenceTableName(rs.getString("referenced_table_name"));

                // 解析 ON UPDATE 和 ON DELETE 规则
                constraint.setOnUpdateRule(mapPgConstraintAction(rs.getString("update_action")));
                constraint.setOnDeleteRule(mapPgConstraintAction(rs.getString("delete_action")));

                constraintMap.put(constraintName, constraint);
            }

            String columnName = rs.getString("column_name");
            if (columnName != null && !constraint.getColumnNames().contains(columnName)) {
                constraint.getColumnNames().add(columnName);
            }

            String refColumnName = rs.getString("referenced_column_name");
            if (refColumnName != null && !constraint.getReferenceColumnNames().contains(refColumnName)) {
                constraint.getReferenceColumnNames().add(refColumnName);
            }

            return null;
        });

        // 查询检查约束
        String checkSql = "SELECT " +
                "    con.conname AS constraint_name, " +
                "    pg_get_constraintdef(con.oid) AS constraint_definition " +
                "FROM pg_catalog.pg_constraint con " +
                "INNER JOIN pg_catalog.pg_class t ON con.conrelid = t.oid " +
                "INNER JOIN pg_catalog.pg_namespace n ON t.relnamespace = n.oid " +
                "WHERE n.nspname = ? AND t.relname = ? " +
                "  AND con.contype = 'c' " +
                "ORDER BY con.conname";

        jdbcOperations.query(checkSql, new Object[] {schemaName, tableName}, (rs, rowNum) -> {
            String constraintName = rs.getString("constraint_name");
            String definition = rs.getString("constraint_definition");

            DBTableConstraint constraint = new DBTableConstraint();
            constraint.setName(constraintName);
            constraint.setSchemaName(schemaName);
            constraint.setTableName(tableName);
            constraint.setOwner(schemaName);
            constraint.setType(DBConstraintType.CHECK);
            constraint.setOrdinalPosition(ordinalCounter.getAndIncrement());
            constraint.setColumnNames(new ArrayList<>());

            if (StringUtils.isNotBlank(definition)) {
                // 从定义中提取 CHECK 子句
                int checkStart = definition.toUpperCase().indexOf("CHECK");
                if (checkStart >= 0) {
                    constraint.setCheckClause(definition.substring(checkStart));
                } else {
                    constraint.setCheckClause(definition);
                }
            }

            constraintMap.put(constraintName, constraint);
            return null;
        });

        constraints.addAll(constraintMap.values());
        return constraints;
    }

    /**
     * 映射 PostgreSQL 约束动作到外部模型 PostgreSQL action codes: 'a' = NO ACTION, 'r' = RESTRICT, 'c' = CASCADE,
     * 'n' = SET NULL, 'd' = SET DEFAULT
     */
    private DBForeignKeyModifyRule mapPgConstraintAction(String action) {
        if (action == null || action.isEmpty()) {
            return DBForeignKeyModifyRule.NO_ACTION;
        }
        switch (action.charAt(0)) {
            case 'c':
                return DBForeignKeyModifyRule.CASCADE;
            case 'n':
                return DBForeignKeyModifyRule.SET_NULL;
            case 'd':
                return DBForeignKeyModifyRule.SET_DEFAULT;
            case 'r':
                return DBForeignKeyModifyRule.NO_ACTION; // RESTRICT 类似 NO ACTION
            case 'a':
            default:
                return DBForeignKeyModifyRule.NO_ACTION;
        }
    }

    /**
     * 列出指定 schema 下所有表的选项信息
     * <p>
     * PostgreSQL 表选项包括：表注释、创建时间、修改时间等
     * </p>
     *
     * @param schemaName schema 名称
     * @return 表名到表选项的映射
     */
    @Override
    public Map<String, DBTableOptions> listTableOptions(
            String schemaName) {
        String sql = "SELECT " +
                "    c.relname AS table_name, " +
                "    obj_description(c.oid) AS table_comment, " +
                "    pg_catalog.pg_size_pretty(pg_catalog.pg_total_relation_size(c.oid)) AS total_size " +
                "FROM pg_catalog.pg_class c " +
                "INNER JOIN pg_catalog.pg_namespace n ON c.relnamespace = n.oid " +
                "WHERE n.nspname = ? " +
                "  AND c.relkind IN ('r', 'p') " +
                "ORDER BY c.relname";

        try {
            Map<String, DBTableOptions> result = new LinkedHashMap<>();
            jdbcOperations.query(sql, new Object[] {schemaName}, (rs, rowNum) -> {
                String tableName = rs.getString("table_name");
                DBTableOptions options = new DBTableOptions();

                String comment = rs.getString("table_comment");
                if (StringUtils.isNotBlank(comment)) {
                    options.setComment(comment);
                }

                result.put(tableName, options);
                return null;
            });
            return result;
        } catch (BadSqlGrammarException e) {
            if (StringUtils.containsIgnoreCase(e.getMessage(), "Unknown schema")) {
                return Collections.emptyMap();
            }
            throw e;
        }
    }

    /**
     * 获取指定表的选项信息
     * <p>
     * PostgreSQL 表选项包括：表注释等
     * </p>
     *
     * @param schemaName schema 名称
     * @param tableName 表名
     * @return 表选项
     */
    @Override
    public DBTableOptions getTableOptions(String schemaName, String tableName) {
        String sql = "SELECT " +
                "    obj_description(c.oid) AS table_comment " +
                "FROM pg_catalog.pg_class c " +
                "INNER JOIN pg_catalog.pg_namespace n ON c.relnamespace = n.oid " +
                "WHERE n.nspname = ? AND c.relname = ?";

        DBTableOptions options = new DBTableOptions();
        try {
            jdbcOperations.query(sql, new Object[] {schemaName, tableName}, rs -> {
                if (rs.next()) {
                    String comment = rs.getString("table_comment");
                    if (StringUtils.isNotBlank(comment)) {
                        options.setComment(comment);
                    }
                }
            });
        } catch (BadSqlGrammarException e) {
            if (StringUtils.containsIgnoreCase(e.getMessage(), "Unknown schema") ||
                    StringUtils.containsIgnoreCase(e.getMessage(), "relation")) {
                return options;
            }
            throw e;
        }
        return options;
    }

    @Override
    public DBTableOptions getTableOptions(String schemaName, String tableName, String ddl) {
        return getTableOptions(schemaName, tableName);
    }

    @Override
    public Map<String, DBTablePartition> listTablePartitions(
            @NonNull String schemaName, List<String> tableNames) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBTablePartition> listTableRangePartitionInfo(String tenantName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBTableSubpartitionDefinition> listSubpartitions(
            String schemaName, String tableName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public Boolean isLowerCaseTableName() {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public List<DBObjectIdentity> listPartitionTables(String partitionMethod) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    /**
     * 获取表的分区信息
     * <p>
     * PostgreSQL 从 10 版本开始支持声明式分区，通过 pg_class.relkind = 'p' 识别分区表 分区类型包括：RANGE, LIST, HASH
     * </p>
     *
     * @param schemaName schema 名称
     * @param tableName 表名
     * @return 分区信息，如果不是分区表则返回 null
     */
    @Override
    public DBTablePartition getPartition(String schemaName, String tableName) {
        // 首先检查是否是分区表
        String checkPartitionSql = "SELECT c.relkind, p.partstrat " +
                "FROM pg_catalog.pg_class c " +
                "INNER JOIN pg_catalog.pg_namespace n ON c.relnamespace = n.oid " +
                "LEFT JOIN pg_catalog.pg_partitioned_table p ON c.oid = p.partrelid " +
                "WHERE n.nspname = ? AND c.relname = ?";

        AtomicReference<String> partitionStrategy = new AtomicReference<>();
        AtomicReference<String> relKind = new AtomicReference<>();

        try {
            jdbcOperations.query(checkPartitionSql, new Object[] {schemaName, tableName}, rs -> {
                relKind.set(rs.getString("relkind"));
                partitionStrategy.set(rs.getString("partstrat"));
            });
        } catch (BadSqlGrammarException e) {
            if (StringUtils.containsIgnoreCase(e.getMessage(), "Unknown schema") ||
                    StringUtils.containsIgnoreCase(e.getMessage(), "relation")) {
                return null;
            }
            throw e;
        }

        // 如果不是分区表，返回 null
        if (!"p".equals(relKind.get()) || partitionStrategy.get() == null) {
            return null;
        }

        DBTablePartition partition = new DBTablePartition();
        partition.setSchemaName(schemaName);
        partition.setTableName(tableName);

        // 设置分区选项
        DBTablePartitionOption option = new DBTablePartitionOption();
        String strategy = partitionStrategy.get();
        if ("r".equalsIgnoreCase(strategy)) {
            option.setType(DBTablePartitionType.RANGE);
        } else if ("l".equalsIgnoreCase(strategy)) {
            option.setType(DBTablePartitionType.LIST);
        } else if ("h".equalsIgnoreCase(strategy)) {
            option.setType(DBTablePartitionType.HASH);
        }
        partition.setPartitionOption(option);

        // 获取分区键列
        String partitionKeySql = "SELECT a.attname " +
                "FROM pg_catalog.pg_partitioned_table p " +
                "INNER JOIN pg_catalog.pg_class c ON p.partrelid = c.oid " +
                "INNER JOIN pg_catalog.pg_namespace n ON c.relnamespace = n.oid " +
                "INNER JOIN pg_catalog.pg_attribute a ON a.attrelid = c.oid AND a.attnum = p.partkey[1] " +
                "WHERE n.nspname = ? AND c.relname = ?";

        List<String> partitionColumns = new ArrayList<>();
        try {
            jdbcOperations.query(partitionKeySql, new Object[] {schemaName, tableName}, rs -> {
                partitionColumns.add(rs.getString("attname"));
            });
        } catch (Exception e) {
            log.warn("Failed to get partition key for table: " + schemaName + "." + tableName, e);
        }

        if (!partitionColumns.isEmpty()) {
            option.setColumnNames(partitionColumns);
        }

        // 获取分区定义列表
        String partitionDefSql = "SELECT " +
                "    c.relname AS partition_name, " +
                "    pg_get_expr(c.relpartbound, c.oid) AS partition_bound, " +
                "    obj_description(c.oid) AS partition_comment " +
                "FROM pg_catalog.pg_class c " +
                "INNER JOIN pg_catalog.pg_inherits i ON c.oid = i.inhrelid " +
                "INNER JOIN pg_catalog.pg_class parent ON i.inhparent = parent.oid " +
                "INNER JOIN pg_catalog.pg_namespace n ON parent.relnamespace = n.oid " +
                "WHERE n.nspname = ? AND parent.relname = ? " +
                "ORDER BY c.relname";

        List<DBTablePartitionDefinition> partitionDefinitions = new ArrayList<>();
        try {
            jdbcOperations.query(partitionDefSql, new Object[] {schemaName, tableName}, rs -> {
                DBTablePartitionDefinition def = new DBTablePartitionDefinition();
                def.setName(rs.getString("partition_name"));
                def.setType(option.getType());

                String bound = rs.getString("partition_bound");
                if (StringUtils.isNotBlank(bound)) {
                    def.fillValues(bound);
                }

                String comment = rs.getString("partition_comment");
                if (StringUtils.isNotBlank(comment)) {
                    def.setComment(comment);
                }

                partitionDefinitions.add(def);
            });
        } catch (Exception e) {
            log.warn("Failed to get partition definitions for table: " + schemaName + "." + tableName, e);
        }

        partition.setPartitionDefinitions(partitionDefinitions);

        return partition;
    }

    /**
     * 解析分区边界表达式 PostgreSQL 返回的边界格式如：FOR VALUES FROM ('a') TO ('z') 或 FOR VALUES IN ('a', 'b')
     */
    private List<String> parsePartitionBound(String bound, DBTablePartitionType type) {
        List<String> values = new ArrayList<>();
        if (StringUtils.isBlank(bound)) {
            return values;
        }

        // 简单解析，提取括号中的值
        int start = bound.indexOf('(');
        int end = bound.lastIndexOf(')');
        if (start >= 0 && end > start) {
            String content = bound.substring(start + 1, end);
            // 分割多个值（如果有）
            String[] parts = content.split(",");
            for (String part : parts) {
                String trimmed = part.trim();
                // 移除引号
                if (trimmed.startsWith("'") && trimmed.endsWith("'")) {
                    trimmed = trimmed.substring(1, trimmed.length() - 1);
                }
                values.add(trimmed);
            }
        }
        return values;
    }

    /**
     * 获取表的 DDL
     * <p>
     * PostgreSQL 没有类似 MySQL SHOW CREATE TABLE 的内置函数，需要程序化拼装 DDL
     * </p>
     *
     * @param schemaName schema 名称
     * @param tableName 表名
     * @return CREATE TABLE DDL 语句
     */
    @Override
    public String getTableDDL(String schemaName, String tableName) {
        StringBuilder ddl = new StringBuilder();

        // 1. 获取列信息并生成列定义
        List<DBTableColumn> columns = listTableColumns(schemaName, tableName);
        if (columns.isEmpty()) {
            return "";
        }

        ddl.append("CREATE TABLE \"").append(schemaName).append("\".\"").append(tableName).append("\" (\n");

        // 生成列定义
        List<String> columnDefs = new ArrayList<>();
        for (DBTableColumn column : columns) {
            columnDefs.add(buildColumnDefinition(column));
        }
        ddl.append("  ").append(String.join(",\n  ", columnDefs));

        // 2. 获取主键约束并添加到列定义后
        List<DBTableConstraint> constraints = listTableConstraints(schemaName, tableName);
        for (DBTableConstraint constraint : constraints) {
            if (constraint.getType() == DBConstraintType.PRIMARY_KEY) {
                ddl.append(",\n  ");
                ddl.append("CONSTRAINT \"").append(constraint.getName()).append("\" PRIMARY KEY (");
                ddl.append(constraint.getColumnNames().stream()
                        .map(col -> "\"" + col + "\"")
                        .collect(Collectors.joining(", ")));
                ddl.append(")");
            }
        }

        ddl.append("\n);\n");

        // 3. 添加表注释
        DBTableOptions options = getTableOptions(schemaName, tableName);
        if (StringUtils.isNotBlank(options.getComment())) {
            ddl.append("\nCOMMENT ON TABLE \"").append(schemaName).append("\".\"").append(tableName)
                    .append("\" IS '").append(escapeString(options.getComment())).append("';\n");
        }

        // 4. 添加列注释
        for (DBTableColumn column : columns) {
            if (StringUtils.isNotBlank(column.getComment())) {
                ddl.append("COMMENT ON COLUMN \"").append(schemaName).append("\".\"").append(tableName)
                        .append("\".\"").append(column.getName()).append("\" IS '")
                        .append(escapeString(column.getComment())).append("';\n");
            }
        }

        // 5. 添加索引（非主键索引）
        List<DBTableIndex> indexes = listTableIndexes(schemaName, tableName);
        for (DBTableIndex index : indexes) {
            if (!Boolean.TRUE.equals(index.getPrimary())) {
                ddl.append("\n").append(buildIndexDDL(schemaName, tableName, index));
            }
        }

        // 6. 添加其他约束（外键、唯一约束、检查约束）
        for (DBTableConstraint constraint : constraints) {
            if (constraint.getType() == DBConstraintType.FOREIGN_KEY) {
                ddl.append("\n").append(buildForeignKeyDDL(schemaName, tableName, constraint));
            } else if (constraint.getType() == DBConstraintType.UNIQUE) {
                // 唯一约束如果已有对应唯一索引，则不需要额外创建
            } else if (constraint.getType() == DBConstraintType.CHECK) {
                ddl.append("\n").append(buildCheckConstraintDDL(schemaName, tableName, constraint));
            }
        }

        return ddl.toString();
    }

    /**
     * 构建列定义
     */
    private String buildColumnDefinition(DBTableColumn column) {
        StringBuilder def = new StringBuilder();
        def.append("\"").append(column.getName()).append("\" ");
        def.append(column.getFullTypeName() != null ? column.getFullTypeName() : column.getTypeName());

        // NOT NULL
        if (Boolean.FALSE.equals(column.getNullable())) {
            def.append(" NOT NULL");
        }

        // DEFAULT
        if (StringUtils.isNotBlank(column.getDefaultValue())) {
            def.append(" DEFAULT ").append(column.getDefaultValue());
        }

        return def.toString();
    }

    /**
     * 构建索引 DDL
     */
    private String buildIndexDDL(String schemaName, String tableName, DBTableIndex index) {
        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE ");
        if (Boolean.TRUE.equals(index.getUnique())) {
            ddl.append("UNIQUE ");
        }
        ddl.append("INDEX \"").append(index.getName()).append("\" ON \"")
                .append(schemaName).append("\".\"").append(tableName).append("\"");
        if (index.getType() != null) {
            String indexMethod = mapIndexTypeToMethod(index.getType());
            ddl.append(" USING ").append(indexMethod);
        }
        if (index.getColumnNames() != null && !index.getColumnNames().isEmpty()) {
            ddl.append(" (").append(index.getColumnNames().stream()
                    .map(col -> "\"" + col + "\"")
                    .collect(Collectors.joining(", "))).append(")");
        }
        ddl.append(";");
        return ddl.toString();
    }

    /**
     * 映射索引类型到 PostgreSQL 索引方法
     */
    private String mapIndexTypeToMethod(DBIndexType type) {
        switch (type) {
            case BITMAP:
                return "bitmap";
            case FULLTEXT:
                return "gin"; // GIN 用于全文搜索
            case SPATIAL:
                return "gist";
            case NORMAL:
            case UNIQUE:
            default:
                return "btree";
        }
    }

    /**
     * 构建外键约束 DDL
     */
    private String buildForeignKeyDDL(String schemaName, String tableName, DBTableConstraint constraint) {
        StringBuilder ddl = new StringBuilder();
        ddl.append("ALTER TABLE \"").append(schemaName).append("\".\"").append(tableName).append("\" ");
        ddl.append("ADD CONSTRAINT \"").append(constraint.getName()).append("\" ");
        ddl.append("FOREIGN KEY (");
        if (constraint.getColumnNames() != null) {
            ddl.append(constraint.getColumnNames().stream()
                    .map(col -> "\"" + col + "\"")
                    .collect(Collectors.joining(", ")));
        }
        ddl.append(") REFERENCES \"");
        if (StringUtils.isNotBlank(constraint.getReferenceSchemaName())) {
            ddl.append(constraint.getReferenceSchemaName()).append("\".\"");
        }
        ddl.append(constraint.getReferenceTableName()).append("\"(");
        if (constraint.getReferenceColumnNames() != null) {
            ddl.append(constraint.getReferenceColumnNames().stream()
                    .map(col -> "\"" + col + "\"")
                    .collect(Collectors.joining(", ")));
        }
        ddl.append(")");

        // ON DELETE
        if (constraint.getOnDeleteRule() != null && constraint.getOnDeleteRule() != DBForeignKeyModifyRule.NO_ACTION) {
            ddl.append(" ON DELETE ").append(mapForeignKeyRule(constraint.getOnDeleteRule()));
        }

        // ON UPDATE
        if (constraint.getOnUpdateRule() != null && constraint.getOnUpdateRule() != DBForeignKeyModifyRule.NO_ACTION) {
            ddl.append(" ON UPDATE ").append(mapForeignKeyRule(constraint.getOnUpdateRule()));
        }

        ddl.append(";");
        return ddl.toString();
    }

    /**
     * 构建检查约束 DDL
     */
    private String buildCheckConstraintDDL(String schemaName, String tableName, DBTableConstraint constraint) {
        StringBuilder ddl = new StringBuilder();
        ddl.append("ALTER TABLE \"").append(schemaName).append("\".\"").append(tableName).append("\" ");
        ddl.append("ADD CONSTRAINT \"").append(constraint.getName()).append("\" ");
        ddl.append("CHECK (").append(constraint.getCheckClause()).append(");");
        return ddl.toString();
    }

    /**
     * 映射外键规则到 PostgreSQL 语法
     */
    private String mapForeignKeyRule(DBForeignKeyModifyRule rule) {
        switch (rule) {
            case CASCADE:
                return "CASCADE";
            case SET_NULL:
                return "SET NULL";
            case SET_DEFAULT:
                return "SET DEFAULT";
            case NO_ACTION:
            default:
                return "NO ACTION";
        }
    }

    /**
     * 转义字符串中的单引号
     */
    private String escapeString(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("'", "''");
    }

    @Override
    public List<DBColumnGroupElement> listTableColumnGroups(String schemaName,
            String tableName) {
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
    public DBSynonym getSynonym(String schemaName, String synonymName,
            DBSynonymType synonymType) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public Map<String, DBTable> getTables(String schemaName,
            List<String> tableNames) {
        throw new UnsupportedOperationException("Not supported yet");
    }
}
