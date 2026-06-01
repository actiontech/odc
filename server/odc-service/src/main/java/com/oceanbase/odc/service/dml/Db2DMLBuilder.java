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
import com.oceanbase.tools.dbbrowser.util.Db2SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;

import lombok.NonNull;

/**
 * fix-L commit-2 (Issue dms-ee#839, bug N2): DB2-specific DML builder.
 *
 * <p>
 * Before this class existed, {@link com.oceanbase.odc.service.dml.TableDataService} routed DB2
 * sessions through {@link MySQLDMLBuilder}, which emits MySQL backtick identifiers — e.g.
 * {@code insert into `DB2INST1`.`TEST_ORDERS`(`ID`,...) values (...)} — that DB2 rejects with
 * {@code SQLCODE=-7 / SQLSTATE=42601} in the parser, blocking workbench cell edit and row insert.
 *
 * <p>
 * The fix uses {@link Db2SqlBuilder}, which quotes identifiers with ANSI double quotes (DB2 native)
 * and values with single quotes. Type-list behavior is conservatively shared with the MySQL builder
 * for the obvious overlap (text / blob / etc.) — anything DB2-specific can be tightened in a later
 * iteration without changing the surface contract.
 *
 * @see BaseDMLBuilder
 * @since ODC_release_4.3.4 (Issue dms-ee#839)
 */
public class Db2DMLBuilder extends BaseDMLBuilder {

    public Db2DMLBuilder(@NonNull List<DataModifyUnit> modifyUnits, List<String> whereColumns,
            ConnectionSession connectionSession, Lazy<List<DBTableConstraint>> constraints) {
        super(modifyUnits, whereColumns, connectionSession, constraints);
    }

    @Override
    public Set<String> getDataTypeNamesAvoidInWhereClause() {
        // CLOB / BLOB / DBCLOB and LONG types are excluded from WHERE predicates (jcc rejects equality
        // on LOB columns). Mirrors the spirit of MySQLDMLBuilder's blob/text exclusion.
        return new HashSet<>(Arrays.asList("clob", "blob", "dbclob", "nclob",
                "long varchar", "long vargraphic", "xml"));
    }

    @Override
    public Set<String> getDataTypeNamesNeedUpload() {
        return new HashSet<>(Arrays.asList("blob", "clob", "dbclob", "nclob"));
    }

    @Override
    public SqlBuilder createSQLBuilder() {
        return new Db2SqlBuilder();
    }

    @Override
    public String toSQLString(@NonNull DataValue dataValue) {
        return DataConvertUtil.convertToSqlString(connectionSession, dataValue);
    }
}
