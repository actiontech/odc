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
import java.util.List;

import org.pf4j.Extension;

import com.oceanbase.odc.plugin.schema.db2.utils.DBAccessorUtil;
import com.oceanbase.odc.plugin.schema.obmysql.OBMySQLViewExtension;
import com.oceanbase.tools.dbbrowser.model.DBObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;
import com.oceanbase.tools.dbbrowser.model.DBView;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Extension
public class DB2ViewExtension extends OBMySQLViewExtension {

    /**
     * List views in a DB2 schema. Uses SYSCAT.TABLES directly to avoid the parent class's dependency on
     * SqlServerSchemaAccessor which uses SQL Server-specific syntax not supported by DB2.
     */
    @Override
    public List<DBObjectIdentity> list(@NonNull Connection connection, @NonNull String schemaName) {
        String sql = "SELECT TABNAME FROM SYSCAT.TABLES WHERE TABSCHEMA = ? AND TYPE = 'V' ORDER BY TABNAME";
        List<DBObjectIdentity> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DBObjectIdentity identity = new DBObjectIdentity();
                    identity.setType(DBObjectType.VIEW);
                    identity.setSchemaName(schemaName);
                    identity.setName(rs.getString("TABNAME").trim());
                    result.add(identity);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to list DB2 views", e);
        }
        return result;
    }

    /**
     * List system views in a DB2 schema.
     */
    @Override
    public List<String> listSystemViews(@NonNull Connection connection, @NonNull String schemaName) {
        String sql = "SELECT TABNAME FROM SYSCAT.TABLES WHERE TABSCHEMA = 'SYSIBM' AND TYPE = 'V' ORDER BY TABNAME";
        List<String> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getString("TABNAME").trim());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to list DB2 system views", e);
        }
        return result;
    }

    /**
     * Get view detail using DB2-native SYSCAT queries directly instead of going through
     * SqlServerSchemaAccessor, which calls getDatabaseName() using SQL Server-specific syntax.
     */
    @Override
    public DBView getDetail(@NonNull Connection connection, @NonNull String schemaName, @NonNull String viewName) {
        DBView view = new DBView();
        view.setViewName(viewName);
        view.setSchemaName(schemaName);
        // Get view definition from SYSCAT.VIEWS
        String sql = "SELECT TEXT FROM SYSCAT.VIEWS WHERE VIEWSCHEMA = ? AND VIEWNAME = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            ps.setString(2, viewName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String text = rs.getString("TEXT");
                    view.setDdl(text != null ? text.trim() : "");
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get DB2 view detail for {}.{}", schemaName, viewName, e);
        }
        // Get columns from SYSCAT.COLUMNS
        String colSql = "SELECT COLNAME, TYPENAME, LENGTH, SCALE, NULLS, COLNO"
                + " FROM SYSCAT.COLUMNS WHERE TABSCHEMA = ? AND TABNAME = ? ORDER BY COLNO";
        try (PreparedStatement ps = connection.prepareStatement(colSql)) {
            ps.setString(1, schemaName);
            ps.setString(2, viewName);
            try (ResultSet rs = ps.executeQuery()) {
                List<com.oceanbase.tools.dbbrowser.model.DBTableColumn> columns = new ArrayList<>();
                while (rs.next()) {
                    com.oceanbase.tools.dbbrowser.model.DBTableColumn col =
                            new com.oceanbase.tools.dbbrowser.model.DBTableColumn();
                    col.setName(rs.getString("COLNAME").trim());
                    col.setTypeName(rs.getString("TYPENAME").trim());
                    col.setMaxLength(rs.getLong("LENGTH"));
                    col.setScale(rs.getInt("SCALE"));
                    col.setNullable("Y".equalsIgnoreCase(rs.getString("NULLS")));
                    col.setOrdinalPosition(rs.getInt("COLNO") + 1);
                    columns.add(col);
                }
                view.setColumns(columns);
            }
        } catch (Exception e) {
            log.warn("Failed to get DB2 view columns for {}.{}", schemaName, viewName, e);
        }
        return view;
    }

    @Override
    protected DBSchemaAccessor getSchemaAccessor(Connection connection) {
        return DBAccessorUtil.getSchemaAccessor(connection);
    }

}
