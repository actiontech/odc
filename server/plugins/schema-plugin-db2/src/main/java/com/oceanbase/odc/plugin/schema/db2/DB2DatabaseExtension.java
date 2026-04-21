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
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.pf4j.Extension;

import com.oceanbase.odc.plugin.schema.db2.utils.DBAccessorUtil;
import com.oceanbase.odc.plugin.schema.obmysql.OBMySQLDatabaseExtension;
import com.oceanbase.tools.dbbrowser.model.DBDatabase;
import com.oceanbase.tools.dbbrowser.model.DBObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;

import lombok.NonNull;

@Extension
public class DB2DatabaseExtension extends OBMySQLDatabaseExtension {

    @Override
    protected DBSchemaAccessor getSchemaAccessor(Connection connection) {
        return DBAccessorUtil.getSchemaAccessor(connection);
    }

    /**
     * List DB2 schemas as databases (DB2 uses schemas, not databases). Uses SYSCAT.SCHEMATA to list all
     * available schemas.
     */
    @Override
    public List<DBObjectIdentity> list(@NonNull Connection connection) {
        List<DBObjectIdentity> result = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT SCHEMANAME FROM SYSCAT.SCHEMATA ORDER BY SCHEMANAME")) {
            while (rs.next()) {
                DBObjectIdentity identity = new DBObjectIdentity();
                identity.setName(rs.getString("SCHEMANAME").trim());
                identity.setType(DBObjectType.DATABASE);
                result.add(identity);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to list DB2 schemas", e);
        }
        return result;
    }

    /**
     * List DB2 schema details. Returns each schema as a DBDatabase with the schema name.
     */
    @Override
    public List<DBDatabase> listDetails(@NonNull Connection connection) {
        List<DBDatabase> result = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT SCHEMANAME FROM SYSCAT.SCHEMATA ORDER BY SCHEMANAME")) {
            while (rs.next()) {
                DBDatabase db = new DBDatabase();
                db.setName(rs.getString("SCHEMANAME").trim());
                result.add(db);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to list DB2 schemas", e);
        }
        return result;
    }

    /**
     * Get details for a specific DB2 schema.
     */
    @Override
    public DBDatabase getDetail(@NonNull Connection connection, @NonNull String dbName) {
        DBDatabase db = new DBDatabase();
        db.setName(dbName);
        return db;
    }

    /**
     * DB2 does not support CREATE SCHEMA through this interface.
     */
    @Override
    public void create(Connection connection, DBDatabase database, String password) {
        throw new UnsupportedOperationException("DB2 does not support creating schemas through ODC");
    }

}
