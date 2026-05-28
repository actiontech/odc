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
package com.oceanbase.tools.dbbrowser.editor.hive;

import org.springframework.jdbc.core.JdbcOperations;

import com.oceanbase.tools.dbbrowser.editor.DBObjectOperator;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;
import com.oceanbase.tools.dbbrowser.util.HiveSqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.StringUtils;

/**
 * Object operator for Apache Hive.
 * <p>
 * Supports DROP TABLE IF EXISTS, DROP VIEW IF EXISTS, and ALTER TABLE RENAME TO operations.
 * </p>
 */
public class HiveObjectOperator implements DBObjectOperator {

    protected final JdbcOperations syncJdbcExecutor;

    public HiveObjectOperator(JdbcOperations syncJdbcExecutor) {
        this.syncJdbcExecutor = syncJdbcExecutor;
    }

    @Override
    public void drop(DBObjectType objectType, String schemaName, String objectName) {
        String sql = buildDropSql(objectType, schemaName, objectName);
        syncJdbcExecutor.execute(sql);
    }

    /**
     * Build a DROP SQL statement for Hive.
     * <p>
     * Hive uses {@code DROP TABLE IF EXISTS} and {@code DROP VIEW IF EXISTS} syntax.
     * </p>
     *
     * @param objectType the type of object to drop (TABLE or VIEW)
     * @param schemaName the database name (may be null or blank)
     * @param objectName the object name
     * @return the DROP SQL string
     */
    static String buildDropSql(DBObjectType objectType, String schemaName, String objectName) {
        SqlBuilder sqlBuilder = new HiveSqlBuilder();
        sqlBuilder.append("DROP ").append(objectType.getName()).append(" IF EXISTS ");
        if (StringUtils.isNotBlank(schemaName)) {
            sqlBuilder.identifier(schemaName).append(".");
        }
        sqlBuilder.identifier(objectName);
        return sqlBuilder.toString();
    }

}
