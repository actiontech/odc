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
package com.oceanbase.odc.plugin.schema.kingbase;

import java.sql.Connection;
import java.util.List;
import java.util.stream.Collectors;

import org.pf4j.Extension;

import com.oceanbase.odc.plugin.schema.kingbase.utils.DBAccessorUtil;
import com.oceanbase.odc.plugin.schema.oboracle.OBOracleDatabaseExtension;
import com.oceanbase.tools.dbbrowser.model.DBDatabase;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;

import lombok.NonNull;

/**
 * Database (schema) listing for KingBase oracle mode.
 * <p>
 * Oracle {@code listDatabases}/{@code getDatabase} fill charset via {@code v_$nls_parameters},
 * which KingBase lacks and would fail the whole list. Prefer {@code showDatabases()} (ALL_USERS)
 * and skip charset/collation enrichment.
 * </p>
 */
@Extension
public class KingBaseDatabaseExtension extends OBOracleDatabaseExtension {

    @Override
    protected DBSchemaAccessor getSchemaAccessor(Connection connection) {
        return DBAccessorUtil.getSchemaAccessor(connection);
    }

    @Override
    public List<DBDatabase> listDetails(@NonNull Connection connection) {
        // ALL_USERS may return mixed case; ALL_TABLES.OWNER is uppercase — normalize for tree→tables.
        return getSchemaAccessor(connection).showDatabases().stream().map(name -> {
            DBDatabase database = new DBDatabase();
            String normalized = name == null ? null : name.toUpperCase(java.util.Locale.ROOT);
            database.setName(normalized);
            database.setId(normalized);
            return database;
        }).collect(Collectors.toList());
    }

    @Override
    public List<com.oceanbase.tools.dbbrowser.model.DBObjectIdentity> list(@NonNull Connection connection) {
        return listDetails(connection).stream().map(db -> {
            com.oceanbase.tools.dbbrowser.model.DBObjectIdentity identity =
                    new com.oceanbase.tools.dbbrowser.model.DBObjectIdentity();
            identity.setName(db.getName());
            identity.setType(com.oceanbase.tools.dbbrowser.model.DBObjectType.DATABASE);
            return identity;
        }).collect(Collectors.toList());
    }

    @Override
    public DBDatabase getDetail(@NonNull Connection connection, @NonNull String dbName) {
        DBDatabase database = new DBDatabase();
        String normalized = dbName.toUpperCase(java.util.Locale.ROOT);
        database.setName(normalized);
        database.setId(normalized);
        return database;
    }

}
