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

import com.oceanbase.tools.dbbrowser.model.DBFunction;
import com.oceanbase.tools.dbbrowser.model.DBPLParam;
import com.oceanbase.tools.dbbrowser.model.DBPLParamMode;
import com.oceanbase.tools.dbbrowser.template.DBObjectTemplate;
import com.oceanbase.tools.dbbrowser.util.PostgresSqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.StringUtils;

/**
 * PostgreSQL function template for generating CREATE FUNCTION statements.
 *
 * <p>
 * PostgreSQL uses PL/pgSQL as the procedural language and supports dollar-quoting for function body
 * to avoid escaping single quotes.
 * </p>
 *
 * <p>
 * Template output example:
 * </p>
 * 
 * <pre>
 * CREATE OR REPLACE FUNCTION "function_name" (
 *     p_param1 INTEGER
 * )
 * RETURNS INTEGER
 * LANGUAGE plpgsql
 * AS $$
 * BEGIN
 *     RETURN p_param1;
 * END;
 * $$;
 * </pre>
 *
 * @author odc
 * @since ODC_release_4.3.4
 */
public class PostgresFunctionTemplate implements DBObjectTemplate<DBFunction> {

    @Override
    public String generateCreateObjectTemplate(@NotNull DBFunction dbObject) {
        Validate.notBlank(dbObject.getFunName(), "Function name can not be blank");

        SqlBuilder sqlBuilder = new PostgresSqlBuilder();

        // Generate CREATE OR REPLACE FUNCTION
        sqlBuilder.append("CREATE OR REPLACE FUNCTION ").identifier(dbObject.getFunName()).append(" (");

        // Generate parameters
        List<DBPLParam> paramList = dbObject.getParams();
        if (CollectionUtils.isNotEmpty(paramList)) {
            String params = paramList.stream()
                    .map(p -> formatParameter(p))
                    .collect(Collectors.joining(",\n\t"));
            sqlBuilder.append("\n\t").append(params).append("\n");
        }

        sqlBuilder.append(")").line();

        // Generate RETURNS clause
        String returnType = dbObject.getReturnType();
        if (StringUtils.isBlank(returnType)) {
            returnType = "INTEGER"; // Default return type
        }
        sqlBuilder.append("RETURNS ").append(returnType).line();

        // Generate LANGUAGE clause
        sqlBuilder.append("LANGUAGE plpgsql").line();

        // Generate function body using dollar-quoting
        sqlBuilder.append("AS $$").line();
        sqlBuilder.append("BEGIN").line();
        sqlBuilder.append("\t-- Enter your function code here").line();

        // Add a default return statement
        sqlBuilder.append("\tRETURN NULL;").line();

        sqlBuilder.append("END;").line();
        sqlBuilder.append("$$;");

        return sqlBuilder.toString();
    }

    /**
     * Format a single PL/pgSQL function parameter.
     *
     * <p>
     * PostgreSQL function parameter format: [IN|OUT|INOUT] param_name data_type [DEFAULT default_value]
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
