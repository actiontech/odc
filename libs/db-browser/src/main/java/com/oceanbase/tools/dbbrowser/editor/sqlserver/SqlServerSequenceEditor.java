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
import com.oceanbase.tools.dbbrowser.model.DBSequence;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlServerSqlBuilder;

/**
 * @author yizhou.xw
 * @date 2024/12
 * @since ODC_release_4.3.4
 */
public class SqlServerSequenceEditor implements DBObjectEditor<DBSequence> {

    @Override
    public boolean editable() {
        return false;
    }

    @Override
    public String generateCreateObjectDDL(@NotNull DBSequence dbObject) {
        Validate.notBlank(dbObject.getName(), "Sequence name can not be blank");
        SqlBuilder sqlBuilder = new SqlServerSqlBuilder();
        sqlBuilder.append("CREATE SEQUENCE ").identifier(dbObject.getName());
        if (dbObject.getStartValue() != null) {
            sqlBuilder.append(" START WITH ").append(dbObject.getStartValue().toString());
        }
        if (dbObject.getIncreament() != null) {
            sqlBuilder.append(" INCREMENT BY ").append(dbObject.getIncreament().toString());
        }
        if (dbObject.getMinValue() != null) {
            sqlBuilder.append(" MINVALUE ").append(dbObject.getMinValue().toString());
        } else {
            sqlBuilder.append(" NO MINVALUE");
        }
        if (dbObject.getMaxValue() != null) {
            sqlBuilder.append(" MAXVALUE ").append(dbObject.getMaxValue().toString());
        } else {
            sqlBuilder.append(" NO MAXVALUE");
        }
        if (dbObject.getCycled() != null) {
            sqlBuilder.append(dbObject.getCycled() ? " CYCLE" : " NO CYCLE");
        }
        if (dbObject.getCached() != null) {
            if (dbObject.getCached() && dbObject.getCacheSize() != null) {
                sqlBuilder.append(" CACHE ").append(dbObject.getCacheSize().toString());
            } else {
                sqlBuilder.append(" NO CACHE");
            }
        }
        return sqlBuilder.toString();
    }

    @Override
    public String generateCreateDefinitionDDL(@NotNull DBSequence dbObject) {
        return generateCreateObjectDDL(dbObject);
    }

    @Override
    public String generateUpdateObjectDDL(@NotNull DBSequence oldObject, @NotNull DBSequence newObject) {
        Validate.notBlank(newObject.getName(), "Sequence name can not be blank");
        SqlBuilder sqlBuilder = new SqlServerSqlBuilder();
        sqlBuilder.append("ALTER SEQUENCE ").identifier(newObject.getName());
        if (newObject.getIncreament() != null
                && !Objects.equals(oldObject.getIncreament(), newObject.getIncreament())) {
            sqlBuilder.append(" INCREMENT BY ").append(newObject.getIncreament().toString());
        }
        if (newObject.getMinValue() != null && !Objects.equals(oldObject.getMinValue(), newObject.getMinValue())) {
            sqlBuilder.append(" MINVALUE ").append(newObject.getMinValue().toString());
        } else if (newObject.getMinValue() == null && oldObject.getMinValue() != null) {
            sqlBuilder.append(" NO MINVALUE");
        }
        if (newObject.getMaxValue() != null && !Objects.equals(oldObject.getMaxValue(), newObject.getMaxValue())) {
            sqlBuilder.append(" MAXVALUE ").append(newObject.getMaxValue().toString());
        } else if (newObject.getMaxValue() == null && oldObject.getMaxValue() != null) {
            sqlBuilder.append(" NO MAXVALUE");
        }
        if (newObject.getCycled() != null && !Objects.equals(oldObject.getCycled(), newObject.getCycled())) {
            sqlBuilder.append(newObject.getCycled() ? " CYCLE" : " NO CYCLE");
        }
        if (newObject.getCached() != null && !Objects.equals(oldObject.getCached(), newObject.getCached())) {
            if (newObject.getCached() && newObject.getCacheSize() != null) {
                sqlBuilder.append(" CACHE ").append(newObject.getCacheSize().toString());
            } else {
                sqlBuilder.append(" NO CACHE");
            }
        }
        if (newObject.getStartValue() != null
                && !Objects.equals(oldObject.getStartValue(), newObject.getStartValue())) {
            sqlBuilder.append(" RESTART WITH ").append(newObject.getStartValue().toString());
        }
        return sqlBuilder.toString();
    }

    @Override
    public String generateUpdateObjectListDDL(Collection<DBSequence> oldObjects, Collection<DBSequence> newObjects) {
        return "";
    }

    @Override
    public String generateRenameObjectDDL(@NotNull DBSequence oldObject, @NotNull DBSequence newObject) {
        return "";
    }

    @Override
    public String generateDropObjectDDL(@NotNull DBSequence dbObject) {
        return "DROP SEQUENCE " + new SqlServerSqlBuilder().identifier(dbObject.getName()).toString();
    }

}
