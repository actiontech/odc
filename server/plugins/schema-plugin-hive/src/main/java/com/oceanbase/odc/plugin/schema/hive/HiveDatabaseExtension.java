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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.pf4j.Extension;

import com.oceanbase.odc.plugin.schema.api.DatabaseExtensionPoint;
import com.oceanbase.tools.dbbrowser.model.DBDatabase;
import com.oceanbase.tools.dbbrowser.model.DBObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;

/**
 * Hive database listing via {@code SHOW DATABASES}. Hive does not expose charset / collation / size
 * at the database level so the other {@link DBDatabase} fields are left null. Database creation is
 * not wired through the workbench (read-only release, design.md §2.3 decision 1).
 */
@Extension
public class HiveDatabaseExtension implements DatabaseExtensionPoint {

    private static final String SQL_SHOW_DATABASES = "SHOW DATABASES";

    @Override
    public List<DBObjectIdentity> list(Connection connection) {
        List<DBObjectIdentity> identities = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(SQL_SHOW_DATABASES)) {
            while (rs.next()) {
                identities.add(DBObjectIdentity.of(null, DBObjectType.DATABASE, rs.getString(1)));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list Hive databases: " + e.getMessage(), e);
        }
        return identities;
    }

    @Override
    public DBDatabase getDetail(Connection connection, String dbName) {
        return DBDatabase.of(dbName);
    }

    @Override
    public List<DBDatabase> listDetails(Connection connection) {
        List<DBDatabase> databases = new ArrayList<>();
        for (DBObjectIdentity identity : list(connection)) {
            databases.add(DBDatabase.of(identity.getName()));
        }
        return databases;
    }

    @Override
    public void create(Connection connection, DBDatabase database, String password) {
        throw new UnsupportedOperationException(
                "Hive database creation is not supported in this release. See design 2.3 decision 1.");
    }
}
