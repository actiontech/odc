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
package com.oceanbase.tools.dbbrowser.schema.hana;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;

import org.springframework.jdbc.core.JdbcOperations;

import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for SAP HANA schema operations. Provides DDL retrieval via
 * SYS.GET_OBJECT_DEFINITION stored procedure.
 */
@Slf4j
public class HanaSchemaUtil {

    private static final String DDL_NOT_AVAILABLE = "-- DDL not available";

    /**
     * Retrieve the DDL (CREATE statement) for a database object by calling SYS.GET_OBJECT_DEFINITION.
     *
     * <p>
     * This method uses a CallableStatement to invoke the stored procedure, which returns the object
     * creation statement in a ResultSet with column OBJECT_CREATION_STATEMENT.
     * </p>
     *
     * @param jdbcOperations Spring JdbcOperations for database access
     * @param schemaName the schema containing the object
     * @param objectName the name of the object
     * @return the DDL string, or a placeholder comment if retrieval fails
     */
    public static String getObjectDDL(JdbcOperations jdbcOperations,
            String schemaName, String objectName) {
        try {
            return jdbcOperations.execute((Connection conn) -> {
                try (CallableStatement cs = conn.prepareCall("{CALL SYS.GET_OBJECT_DEFINITION(?, ?)}")) {
                    cs.setString(1, schemaName);
                    cs.setString(2, objectName);
                    boolean hasResultSet = cs.execute();
                    if (hasResultSet) {
                        try (ResultSet rs = cs.getResultSet()) {
                            if (rs.next()) {
                                String ddl = rs.getString("OBJECT_CREATION_STATEMENT");
                                if (ddl != null) {
                                    return ddl;
                                }
                            }
                        }
                    }
                    return DDL_NOT_AVAILABLE;
                }
            });
        } catch (Exception e) {
            log.warn("Failed to get DDL for {}.{}: {}", schemaName, objectName, e.getMessage());
            return DDL_NOT_AVAILABLE;
        }
    }
}
