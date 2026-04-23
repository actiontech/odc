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
package com.oceanbase.odc.service.dml;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.oceanbase.odc.common.util.Lazy;
import com.oceanbase.odc.core.session.ConnectionSession;
import com.oceanbase.odc.service.dml.model.DataModifyUnit;
import com.oceanbase.tools.dbbrowser.model.DBTableConstraint;
import com.oceanbase.tools.dbbrowser.util.OracleSqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;

import lombok.NonNull;

/**
 * {@link DB2DMLBuilder}
 *
 * DML builder for IBM DB2 database. DB2 uses double-quoted identifiers
 * like Oracle, so we reuse {@link OracleSqlBuilder}.
 *
 * @since ODC_release_4.3.4
 * @see BaseDMLBuilder
 */
public class DB2DMLBuilder extends BaseDMLBuilder {

    public DB2DMLBuilder(@NonNull List<DataModifyUnit> modifyUnits, List<String> whereColumns,
            ConnectionSession connectionSession, Lazy<List<DBTableConstraint>> constraints) {
        super(modifyUnits, whereColumns, connectionSession, constraints);
    }

    @Override
    public Set<String> getDataTypeNamesAvoidInWhereClause() {
        return new HashSet<>(Arrays.asList("BLOB", "CLOB", "DBCLOB", "XML"));
    }

    @Override
    public Set<String> getDataTypeNamesNeedUpload() {
        return new HashSet<>(Arrays.asList("BLOB", "CLOB", "DBCLOB"));
    }

    @Override
    public SqlBuilder createSQLBuilder() {
        return new OracleSqlBuilder();
    }

    @Override
    public String toSQLString(@NonNull DataValue dataValue) {
        return DataConvertUtil.convertToSqlString(connectionSession, dataValue);
    }

}
