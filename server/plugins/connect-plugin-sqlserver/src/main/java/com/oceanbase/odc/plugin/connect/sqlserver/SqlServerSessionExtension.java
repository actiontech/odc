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
package com.oceanbase.odc.plugin.connect.sqlserver;

import java.sql.Connection;
import java.sql.SQLException;

import org.pf4j.Extension;

import com.oceanbase.odc.common.util.JdbcOperationsUtil;
import com.oceanbase.odc.plugin.connect.model.DBClientInfo;
import com.oceanbase.odc.plugin.connect.obmysql.OBMySQLSessionExtension;

/**
 * @author yizhou.xw
 * @date 2024/12
 * @since ODC_release_4.3.4
 */
@Extension
public class SqlServerSessionExtension extends OBMySQLSessionExtension {

    @Override
    public void switchSchema(Connection connection, String schemaName) throws SQLException {
        String currentSchema = getCurrentSchema(connection);
        if (currentSchema != null && currentSchema.equals(schemaName)) {
            return;
        }
        // SQL Server uses USE statement or SET SCHEMA
        String sql = "USE " + schemaName;
        JdbcOperationsUtil.getJdbcOperations(connection).execute(sql);
    }

    @Override
    public String getCurrentSchema(Connection connection) {
        String querySql = "SELECT SCHEMA_NAME()";
        try {
            return JdbcOperationsUtil.getJdbcOperations(connection)
                    .queryForObject(querySql, String.class);
        } catch (Exception e) {
            // Fallback to default schema
            return "dbo";
        }
    }

    @Override
    public boolean setClientInfo(Connection connection, DBClientInfo clientInfo) {
        // SQL Server doesn't support setting client info in the same way as MySQL
        return false;
    }
}
