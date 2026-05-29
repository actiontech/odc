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
package com.oceanbase.tools.dbbrowser.template.hana;

import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.Validate;

import com.oceanbase.tools.dbbrowser.model.DBFunction;
import com.oceanbase.tools.dbbrowser.template.DBObjectTemplate;
import com.oceanbase.tools.dbbrowser.util.HanaSqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;

/**
 * Function template for SAP HANA database.
 * <p>
 * Generates CREATE FUNCTION templates using HANA SQL syntax:
 * 
 * <pre>
 * CREATE FUNCTION "function_name" (IN param1 INT)
 * RETURNS result INT
 * LANGUAGE SQLSCRIPT
 * AS
 * BEGIN
 *     result := param1;
 * END
 * </pre>
 *
 * @since ODC_release_4.3.4
 */
public class HanaFunctionTemplate implements DBObjectTemplate<DBFunction> {

    @Override
    public String generateCreateObjectTemplate(@NotNull DBFunction dbObject) {
        Validate.notBlank(dbObject.getFunName(), "Function name can not be blank");
        SqlBuilder sqlBuilder = new HanaSqlBuilder();
        sqlBuilder.append("CREATE FUNCTION ")
                .identifier(dbObject.getFunName())
                .append(" (")
                .line().append("    IN param1 INT")
                .line().append(")")
                .line().append("RETURNS result INT")
                .line().append("LANGUAGE SQLSCRIPT")
                .line().append("AS")
                .line().append("BEGIN")
                .line().append("    result := param1;")
                .line().append("END");
        return sqlBuilder.toString();
    }

}
