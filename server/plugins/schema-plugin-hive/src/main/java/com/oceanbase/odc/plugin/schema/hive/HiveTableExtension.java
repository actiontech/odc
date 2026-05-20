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
package com.oceanbase.odc.plugin.schema.hive;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.pf4j.Extension;

import com.oceanbase.odc.plugin.schema.api.TableExtensionPoint;
import com.oceanbase.odc.plugin.schema.hive.utils.HiveDescribeParser;
import com.oceanbase.tools.dbbrowser.model.DBObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;
import com.oceanbase.tools.dbbrowser.model.DBTable;
import com.oceanbase.tools.dbbrowser.model.DBTable.DBTableOptions;
import com.oceanbase.tools.dbbrowser.model.DBTableColumn;

/**
 * Hive table listing / detail / DDL via {@code SHOW TABLES}, {@code DESCRIBE FORMATTED} and
 * {@code SHOW CREATE TABLE}. Read-only release: {@link #drop}, DDL generation and external file
 * sync all throw {@link UnsupportedOperationException} as a backend backstop on top of the
 * front-end {@code features.tableDataEditable=false} gate (design.md §2.3 decision 1 / R-4.1).
 */
@Extension
public class HiveTableExtension implements TableExtensionPoint {

    private static final String SQL_SHOW_TABLES = "SHOW TABLES";
    private static final String SQL_DESCRIBE_FORMATTED_TMPL = "DESCRIBE FORMATTED %s.%s";
    private static final String SQL_SHOW_CREATE_TABLE_TMPL = "SHOW CREATE TABLE %s.%s";

    @Override
    public List<DBObjectIdentity> list(Connection connection, String schemaName, DBObjectType tableType) {
        useDatabase(connection, schemaName);
        List<DBObjectIdentity> identities = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(SQL_SHOW_TABLES)) {
            DBObjectType emitType = tableType == null ? DBObjectType.TABLE : tableType;
            while (rs.next()) {
                identities.add(DBObjectIdentity.of(schemaName, emitType, rs.getString(1)));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to list Hive tables in schema " + schemaName + ": " + e.getMessage(), e);
        }
        return identities;
    }

    @Override
    public List<String> showNamesLike(Connection connection, String schemaName, String tableNameLike) {
        useDatabase(connection, schemaName);
        String like = tableNameLike == null ? "*" : tableNameLike;
        List<String> names = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("SHOW TABLES LIKE ?")) {
            ps.setString(1, like);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to filter Hive tables in schema " + schemaName + ": " + e.getMessage(), e);
        }
        return names;
    }

    @Override
    public DBTable getDetail(Connection connection, String schemaName, String tableName) {
        HiveDescribeParser.ParsedDescribe parsed =
                HiveDescribeParser.parse(readDescribeFormatted(connection, schemaName, tableName));
        DBTable table = new DBTable();
        table.setSchemaName(schemaName);
        table.setOwner(parsed.owner == null ? schemaName : parsed.owner);
        table.setName(tableName);
        List<DBTableColumn> columns = new ArrayList<>(parsed.columns);
        for (DBTableColumn c : columns) {
            c.setSchemaName(schemaName);
            c.setTableName(tableName);
        }
        for (DBTableColumn p : parsed.partitionColumns) {
            p.setSchemaName(schemaName);
            p.setTableName(tableName);
            columns.add(p);
        }
        table.setColumns(columns);
        DBTableOptions options = new DBTableOptions();
        options.setComment(parsed.comment);
        table.setTableOptions(options);
        table.setType(resolveTableType(parsed.tableType));
        table.setDDL(getDDL(connection, schemaName, tableName));
        return table;
    }

    /** Extracted so unit tests can stub the IO without a live Hive cluster. */
    protected List<HiveDescribeParser.Row> readDescribeFormatted(Connection connection, String schemaName,
            String tableName) {
        String sql = String.format(SQL_DESCRIBE_FORMATTED_TMPL, quote(schemaName), quote(tableName));
        List<HiveDescribeParser.Row> rows = new ArrayList<>();
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                rows.add(new HiveDescribeParser.Row(rs.getString(1), rs.getString(2), rs.getString(3)));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to describe Hive table " + schemaName + "." + tableName + ": " + e.getMessage(), e);
        }
        return rows;
    }

    /** Issue {@code SHOW CREATE TABLE} and concatenate the multi-row DDL output. */
    public String getDDL(Connection connection, String schemaName, String tableName) {
        String sql = String.format(SQL_SHOW_CREATE_TABLE_TMPL, quote(schemaName), quote(tableName));
        StringBuilder sb = new StringBuilder();
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to read Hive DDL for " + schemaName + "." + tableName + ": " + e.getMessage(), e);
        }
        return sb.toString();
    }

    @Override
    public void drop(Connection connection, String schemaName, String tableName) {
        throw new UnsupportedOperationException(
                "Hive table drop is not supported in this release. See design 2.3 decision 1.");
    }

    @Override
    public String generateCreateDDL(Connection connection, DBTable table) {
        throw new UnsupportedOperationException(
                "Hive table create DDL generation is not supported in this release.");
    }

    @Override
    public String generateUpdateDDL(Connection connection, DBTable oldTable, DBTable newTable) {
        throw new UnsupportedOperationException(
                "Hive table update DDL generation is not supported in this release.");
    }

    @Override
    public boolean syncExternalTableFiles(Connection connection, String schemaName, String tableName) {
        throw new UnsupportedOperationException(
                "Hive external table file sync is not supported in this release.");
    }

    /** Map the Hive "Table Type" token to a {@link DBObjectType}. */
    static DBObjectType resolveTableType(String hiveTableType) {
        if (hiveTableType == null) {
            return DBObjectType.TABLE;
        }
        String t = hiveTableType.trim().toUpperCase();
        if (t.contains("EXTERNAL")) {
            return DBObjectType.EXTERNAL_TABLE;
        }
        if (t.contains("VIEW")) {
            return DBObjectType.VIEW;
        }
        return DBObjectType.TABLE;
    }

    /** Hive requires {@code USE schema} before unqualified {@code SHOW TABLES}. */
    private static void useDatabase(Connection connection, String schemaName) {
        if (schemaName == null || schemaName.isEmpty()) {
            return;
        }
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("USE " + quote(schemaName));
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to switch to Hive schema " + schemaName + ": " + e.getMessage(), e);
        }
    }

    /** Quote a Hive identifier with backticks; embedded backticks are doubled per Hive grammar. */
    static String quote(String identifier) {
        Objects.requireNonNull(identifier, "identifier");
        return '`' + identifier.replace("`", "``") + '`';
    }
}
