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
package com.oceanbase.odc.plugin.connect.gbase8a;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

import org.pf4j.Extension;

import com.oceanbase.odc.common.util.JdbcOperationsUtil;
import com.oceanbase.odc.common.util.StringUtils;
import com.oceanbase.odc.plugin.connect.model.DBClientInfo;
import com.oceanbase.odc.plugin.connect.obmysql.OBMySQLSessionExtension;

import lombok.extern.slf4j.Slf4j;

/**
 * Session extension for GBase-8a (MySQL-wire protocol; USE `db` for schema switch).
 */
@Slf4j
@Extension
public class GBase8aSessionExtension extends OBMySQLSessionExtension {

    @Override
    public void switchSchema(Connection connection, String schemaName) throws SQLException {
        String currentSchema = getCurrentSchema(connection);
        if (Objects.equals(currentSchema, schemaName)) {
            return;
        }
        // Prefer explicit USE `db` over JDBC setCatalog for GBase official connector.
        String sql = "USE " + StringUtils.quoteMysqlIdentifier(schemaName);
        JdbcOperationsUtil.getJdbcOperations(connection).execute(sql);
    }

    @Override
    public boolean setClientInfo(Connection connection, DBClientInfo clientInfo) {
        return false;
    }
}
