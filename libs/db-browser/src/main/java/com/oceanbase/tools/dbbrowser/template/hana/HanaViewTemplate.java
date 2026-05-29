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

import com.oceanbase.tools.dbbrowser.model.DBView;
import com.oceanbase.tools.dbbrowser.template.BaseViewTemplate;
import com.oceanbase.tools.dbbrowser.util.HanaSqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;

/**
 * View template for SAP HANA database.
 * <p>
 * Generates CREATE VIEW templates using HANA SQL syntax with double-quote identifiers. HANA uses a
 * two-level naming scheme: "schema"."view_name".
 *
 * @since ODC_release_4.3.4
 */
public class HanaViewTemplate extends BaseViewTemplate {

    @Override
    protected String preHandle(String str) {
        return str.toUpperCase();
    }

    @Override
    protected SqlBuilder sqlBuilder() {
        return new HanaSqlBuilder();
    }

    @Override
    protected String doGenerateCreateObjectTemplate(SqlBuilder sqlBuilder, DBView dbObject) {
        return sqlBuilder.toString();
    }

}
