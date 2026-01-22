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

import java.util.Collection;
import java.util.Objects;

import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.Validate;

import com.oceanbase.tools.dbbrowser.editor.DBObjectEditor;
import com.oceanbase.tools.dbbrowser.model.DBSynonym;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlServerSqlBuilder;

/**
 * @author yizhou.xw
 * @date 2024/12
 * @since ODC_release_4.3.4
 */
public class SqlServerSynonymEditor implements DBObjectEditor<DBSynonym> {

    @Override
    public boolean editable() {
        return false;
    }

    @Override
    public String generateCreateObjectDDL(@NotNull DBSynonym dbObject) {
        Validate.notBlank(dbObject.getSynonymName(), "Synonym name can not be blank");
        Validate.notBlank(dbObject.getTableName(), "Target object name can not be blank");
        SqlBuilder sqlBuilder = new SqlServerSqlBuilder();
        sqlBuilder.append("CREATE SYNONYM ").identifier(dbObject.getSynonymName())
                .append(" FOR ").append(dbObject.getTableName());
        return sqlBuilder.toString();
    }

    @Override
    public String generateCreateDefinitionDDL(@NotNull DBSynonym dbObject) {
        return generateCreateObjectDDL(dbObject);
    }

    @Override
    public String generateUpdateObjectDDL(@NotNull DBSynonym oldObject, @NotNull DBSynonym newObject) {
        SqlBuilder sqlBuilder = new SqlServerSqlBuilder();
        if (!Objects.equals(oldObject.getSynonymName(), newObject.getSynonymName())
                || !Objects.equals(oldObject.getTableName(), newObject.getTableName())) {
            sqlBuilder.append(generateDropObjectDDL(oldObject)).append(";\n");
            sqlBuilder.append(generateCreateObjectDDL(newObject));
        }
        return sqlBuilder.toString();
    }

    @Override
    public String generateUpdateObjectListDDL(Collection<DBSynonym> oldObjects, Collection<DBSynonym> newObjects) {
        return "";
    }

    @Override
    public String generateRenameObjectDDL(@NotNull DBSynonym oldObject, @NotNull DBSynonym newObject) {
        return "";
    }

    @Override
    public String generateDropObjectDDL(@NotNull DBSynonym dbObject) {
        return "DROP SYNONYM " + new SqlServerSqlBuilder().identifier(dbObject.getSynonymName()).toString();
    }

}
