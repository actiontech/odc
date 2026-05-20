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
package com.oceanbase.odc.plugin.connect.hive;

import java.sql.Connection;
import java.sql.SQLException;

import org.pf4j.Extension;

import com.oceanbase.odc.plugin.connect.api.SessionExtensionPoint;
import com.oceanbase.odc.plugin.connect.model.DBClientInfo;

import lombok.NonNull;

/**
 * Hive session management is intentionally not supported by HiveServer2 - there is no
 * {@code SHOW PROCESSLIST} / {@code KILL ...} equivalent that can safely cancel a running query
 * from another session. Any UI surface that depends on this extension MUST handle the
 * {@link UnsupportedOperationException} by hiding the corresponding control (see compat-RISK
 * R-4.2).
 *
 * @since ODC_release_4.3.4
 */
@Extension
public class HiveSessionExtension implements SessionExtensionPoint {

    private static final String UNSUPPORTED_MSG = "Hive session management not supported";

    @Override
    public String getConnectionId(@NonNull Connection connection) {
        throw new UnsupportedOperationException(UNSUPPORTED_MSG);
    }

    @Override
    public void killQuery(@NonNull Connection connection, @NonNull String connectionId) {
        throw new UnsupportedOperationException(UNSUPPORTED_MSG);
    }

    @Override
    public String getKillQuerySql(@NonNull String connectionId) {
        throw new UnsupportedOperationException(UNSUPPORTED_MSG);
    }

    @Override
    public String getKillSessionSql(@NonNull String connectionId) {
        throw new UnsupportedOperationException(UNSUPPORTED_MSG);
    }

    @Override
    public void switchSchema(Connection connection, String schemaName) throws SQLException {
        if (schemaName == null || schemaName.isEmpty()) {
            return;
        }
        // Hive uses "USE {db}" to switch the current database for the session.
        try (java.sql.Statement stmt = connection.createStatement()) {
            stmt.execute("USE " + schemaName);
        }
    }

    @Override
    public String getCurrentSchema(Connection connection) {
        try (java.sql.Statement stmt = connection.createStatement();
                java.sql.ResultSet rs = stmt.executeQuery("SELECT current_database()")) {
            if (rs.next()) {
                return rs.getString(1);
            }
            return null;
        } catch (SQLException e) {
            throw new UnsupportedOperationException("Failed to query current Hive database", e);
        }
    }

    @Override
    public String getVariable(Connection connection, String variableName) {
        throw new UnsupportedOperationException(UNSUPPORTED_MSG);
    }

    @Override
    public String getAlterVariableStatement(String variableScope, String variableName, String variableValue) {
        // Hive session conf SET is the closest equivalent. Scope is ignored - Hive only has
        // session conf and global hive-site.xml.
        return "SET " + variableName + "=" + variableValue;
    }

    @Override
    public boolean setClientInfo(Connection connection, DBClientInfo clientInfo) {
        return false;
    }
}
