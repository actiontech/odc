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
package com.oceanbase.tools.dbbrowser.editor.sqlserver;

import java.util.Objects;

import javax.validation.constraints.NotNull;

import com.oceanbase.tools.dbbrowser.editor.DBMViewEditor;
import com.oceanbase.tools.dbbrowser.editor.DBObjectEditor;
import com.oceanbase.tools.dbbrowser.model.DBMaterializedView;
import com.oceanbase.tools.dbbrowser.model.DBTableIndex;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlServerSqlBuilder;

/**
 * @author yizhou.xw
 * @date 2024/12
 * @since ODC_release_4.3.4
 */
public class SqlServerMViewEditor extends DBMViewEditor {

    public SqlServerMViewEditor(@NotNull DBObjectEditor<DBTableIndex> indexEditor) {
        super(indexEditor);
    }

    @Override
    protected SqlBuilder sqlBuilder() {
        return new SqlServerSqlBuilder();
    }

    @Override
    public String generateCreateObjectDDL(@NotNull DBMaterializedView dbObject) {
        SqlBuilder sqlBuilder = sqlBuilder();
        sqlBuilder.append("CREATE VIEW ").identifier(dbObject.getName())
                .append(" WITH SCHEMABINDING AS ").append(dbObject.getDdl());
        return sqlBuilder.toString();
    }

    @Override
    public String generateDropObjectDDL(@NotNull DBMaterializedView dbObject) {
        return "DROP VIEW " + sqlBuilder().identifier(dbObject.getName()).toString();
    }

    @Override
    public String generateUpdateObjectDDL(@NotNull DBMaterializedView oldObject,
            @NotNull DBMaterializedView newObject) {
        if (!Objects.equals(oldObject.getDdl(), newObject.getDdl())) {
            return "ALTER VIEW " + sqlBuilder().identifier(newObject.getName()).toString()
                    + " WITH SCHEMABINDING AS " + newObject.getDdl();
        }
        return "";
    }

}
