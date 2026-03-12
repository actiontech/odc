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
package com.oceanbase.tools.dbbrowser.template.postgre;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.Validate;

import com.oceanbase.tools.dbbrowser.model.DBPLParam;
import com.oceanbase.tools.dbbrowser.model.DBPLParamMode;
import com.oceanbase.tools.dbbrowser.model.DBProcedure;
import com.oceanbase.tools.dbbrowser.template.DBObjectTemplate;
import com.oceanbase.tools.dbbrowser.util.PostgresSqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.StringUtils;

/**
 * PostgreSQL procedure template for generating CREATE PROCEDURE statements.
 *
 * <p>
 * PostgreSQL 11+ supports stored procedures. Like functions, procedures use PL/pgSQL as the
 * procedural language and dollar-quoting for the body.
 * </p>
 *
 * <p>
 * Template output example:
 * </p>
 * 
 * <pre>
 * CREATE OR REPLACE PROCEDURE "procedure_name" (
 *     p_param1 INTEGER
 * )
 * LANGUAGE plpgsql
 * AS $$
 * BEGIN
 *     -- procedure body
 *     NULL;
 * END;
 * $$;
 * </pre>
 *
 * <p>
 * Note: CREATE PROCEDURE is available in PostgreSQL 11 and later versions.
 * </p>
 *
 * @author odc
 * @since ODC_release_4.3.4
 */
public class PostgresProcedureTemplate implements DBObjectTemplate<DBProcedure> {

    @Override
    public String generateCreateObjectTemplate(@NotNull DBProcedure dbObject) {
        Validate.notBlank(dbObject.getProName(), "Procedure name can not be blank");

        SqlBuilder sqlBuilder = new PostgresSqlBuilder();

        // Generate CREATE OR REPLACE PROCEDURE
        sqlBuilder.append("CREATE OR REPLACE PROCEDURE ").identifier(dbObject.getProName()).append(" (");

        // Generate parameters
        List<DBPLParam> paramList = dbObject.getParams();
        if (CollectionUtils.isNotEmpty(paramList)) {
            String params = paramList.stream()
                    .map(p -> formatParameter(p))
                    .collect(Collectors.joining(",\n\t"));
            sqlBuilder.append("\n\t").append(params).append("\n");
        }

        sqlBuilder.append(")").line();

        // Generate LANGUAGE clause
        sqlBuilder.append("LANGUAGE plpgsql").line();

        // Generate procedure body using dollar-quoting
        sqlBuilder.append("AS $$").line();
        sqlBuilder.append("BEGIN").line();
        sqlBuilder.append("\t-- Enter your procedure code here").line();
        sqlBuilder.append("\tNULL;").line();
        sqlBuilder.append("END;").line();
        sqlBuilder.append("$$;");

        return sqlBuilder.toString();
    }

    /**
     * Format a single PL/pgSQL procedure parameter.
     *
     * <p>
     * PostgreSQL procedure parameter format: [IN|OUT|INOUT] param_name data_type [DEFAULT
     * default_value]
     * </p>
     *
     * <p>
     * Note: Unlike functions, procedures support OUT parameters but they work differently - they are
     * assigned values within the procedure body.
     * </p>
     *
     * @param param the parameter to format
     * @return formatted parameter string
     */
    private String formatParameter(DBPLParam param) {
        StringBuilder sb = new StringBuilder();

        // Add parameter mode (IN/OUT/INOUT) if specified
        DBPLParamMode paramMode = param.getParamMode();
        if (Objects.nonNull(paramMode) && paramMode != DBPLParamMode.UNKNOWN) {
            sb.append(paramMode.name()).append(" ");
        }

        // Add parameter name (handle special characters)
        sb.append(StringUtils.quoteOracleIdentifier(param.getParamName()));

        // Add data type
        if (StringUtils.isNotBlank(param.getDataType())) {
            sb.append(" ").append(param.getDataType());
        } else {
            sb.append(" INTEGER"); // Default type
        }

        // Add default value if specified (only for IN parameters)
        if (StringUtils.isNotBlank(param.getDefaultValue())
                && (paramMode == null || paramMode == DBPLParamMode.IN)) {
            sb.append(" DEFAULT ").append(param.getDefaultValue());
        }

        return sb.toString();
    }

}
