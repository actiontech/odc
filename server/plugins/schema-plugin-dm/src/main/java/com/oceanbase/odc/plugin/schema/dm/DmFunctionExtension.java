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
package com.oceanbase.odc.plugin.schema.dm;

import java.sql.Connection;

import org.pf4j.Extension;

import com.oceanbase.odc.common.util.JdbcOperationsUtil;
import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.odc.plugin.schema.dm.utils.DBAccessorUtil;
import com.oceanbase.odc.plugin.schema.obmysql.OBMySQLFunctionExtension;
import com.oceanbase.tools.dbbrowser.DBBrowser;
import com.oceanbase.tools.dbbrowser.editor.DBObjectOperator;
import com.oceanbase.tools.dbbrowser.editor.GeneralSqlStatementBuilder;
import com.oceanbase.tools.dbbrowser.model.DBFunction;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;
import com.oceanbase.tools.dbbrowser.template.DBObjectTemplate;
import com.oceanbase.tools.dbbrowser.util.OracleSqlBuilder;

import lombok.NonNull;

/**
 * Function extension for DM (Dameng) database.
 * <p>
 * Provides function listing, detail retrieval, drop, and template generation.
 * </p>
 *
 * @author
 * @since ODC_release_4.3.4
 */
@Extension
public class DmFunctionExtension extends OBMySQLFunctionExtension {

    @Override
    protected DBSchemaAccessor getSchemaAccessor(Connection connection) {
        return DBAccessorUtil.getSchemaAccessor(connection);
    }

    @Override
    protected DBObjectOperator getOperator(Connection connection) {
        org.springframework.jdbc.core.JdbcOperations jdbcOps =
                JdbcOperationsUtil.getJdbcOperations(connection);
        return (objectType, schemaName, objectName) -> {
            String sql = GeneralSqlStatementBuilder.drop(
                    new OracleSqlBuilder(), objectType, schemaName, objectName);
            jdbcOps.execute(sql);
        };
    }

    @Override
    public String generateCreateTemplate(@NonNull DBFunction function) {
        return getTemplate().generateCreateObjectTemplate(function);
    }

    @Override
    protected DBObjectTemplate<DBFunction> getTemplate() {
        return DBBrowser.objectTemplate().functionTemplate()
                .setType(DialectType.DM.getDBBrowserDialectTypeName()).create();
    }

}
