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
package com.oceanbase.tools.dbbrowser.util;

/**
 * fix-L commit-2 (Issue dms-ee#839, bug N2): DB2 dialect-aware {@link SqlBuilder}.
 *
 * <p>
 * DB2 quotes identifiers with double quotes (same as Oracle / SQL ANSI), and values with single
 * quotes (same as MySQL / Oracle). Before this builder existed, DB2 reused {@link MySQLSqlBuilder}
 * via the DML chain, which produced MySQL-style backtick identifiers — e.g.
 * {@code insert into `DB2INST1`.`TEST_ORDERS`(`ID`,...) values (...)} — that DB2 rejects with
 * SQLCODE=-7 / SQLSTATE=42601 in the parser, blocking the workbench cell-edit and row-insert paths
 * end-to-end.
 *
 * @since ODC_release_4.3.4 (Issue dms-ee#839)
 */
public class Db2SqlBuilder extends SqlBuilder {

    public Db2SqlBuilder() {
        super();
    }

    @Override
    public SqlBuilder identifier(String identifier) {
        // DB2 identifiers are wrapped with ANSI double quotes — same semantics as Oracle.
        return append(StringUtils.quoteOracleIdentifier(identifier));
    }

    @Override
    public SqlBuilder value(String value) {
        // DB2 string literals use single quotes with doubled-quote escaping — same as MySQL.
        return append(StringUtils.quoteMysqlValue(value));
    }

    @Override
    public SqlBuilder defaultValue(String value) {
        // No special handling for now — emit the DEFAULT expression verbatim, mirroring OracleSqlBuilder.
        return append(value);
    }
}
