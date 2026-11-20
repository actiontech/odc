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
package com.oceanbase.odc.plugin.schema.clickhouse;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.pf4j.Extension;

import com.oceanbase.odc.common.util.JdbcOperationsUtil;
import com.oceanbase.odc.plugin.schema.api.DatabaseExtensionPoint;
import com.oceanbase.tools.dbbrowser.model.DBDatabase;
import com.oceanbase.tools.dbbrowser.model.DBObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;

import lombok.extern.slf4j.Slf4j;

/**
 * Lists ClickHouse databases via {@code SHOW DATABASES}.
 */
@Slf4j
@Extension
public class ClickHouseDatabaseExtension implements DatabaseExtensionPoint {

    @Override
    public List<DBObjectIdentity> list(Connection connection) {
        List<DBObjectIdentity> result = new ArrayList<>();
        for (String name : showDatabases(connection)) {
            result.add(DBObjectIdentity.of(name, DBObjectType.DATABASE, name));
        }
        return result;
    }

    @Override
    public DBDatabase getDetail(Connection connection, String dbName) {
        return DBDatabase.of(dbName);
    }

    @Override
    public List<DBDatabase> listDetails(Connection connection) {
        List<DBDatabase> result = new ArrayList<>();
        for (String name : showDatabases(connection)) {
            result.add(DBDatabase.of(name));
        }
        return result;
    }

    @Override
    public void create(Connection connection, DBDatabase database, String password) {
        throw new UnsupportedOperationException("ClickHouse create database is out of scope for this slice");
    }

    private List<String> showDatabases(Connection connection) {
        try {
            return JdbcOperationsUtil.getJdbcOperations(connection).query("SHOW DATABASES",
                    (rs, rowNum) -> rs.getString(1));
        } catch (Exception e) {
            log.warn("Failed to execute SHOW DATABASES on ClickHouse", e);
            return Collections.emptyList();
        }
    }
}
