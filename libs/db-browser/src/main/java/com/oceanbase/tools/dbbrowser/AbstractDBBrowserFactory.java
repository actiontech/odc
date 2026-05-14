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
package com.oceanbase.tools.dbbrowser;

import org.apache.commons.lang3.Validate;

import lombok.Setter;
import lombok.experimental.Accessors;

@Setter
@Accessors(chain = true)
public abstract class AbstractDBBrowserFactory<T> implements DBBrowserFactory<T> {

    protected String type;

    @Override
    public T create() {
        Validate.notNull(this.type, "Type can not be null");
        switch (type) {
            case ORACLE:
                return buildForOracle();
            case MYSQL:
                return buildForMySQL();
            case DORIS:
                return buildForDoris();
            case TIDB:
                return buildForTidb();
            case OB_ORACLE:
                return buildForOBOracle();
            case OB_MYSQL:
                return buildForOBMySQL();
            case ODP_SHARDING_OB_MYSQL:
                return buildForOdpSharding();
            case POSTGRESQL:
                return buildForPostgres();
            case SQL_SERVER:
                return buildForSqlServer();
            case DM:
                return buildForDm();
            case DB2:
                return buildForDB2();
            default:
                throw new IllegalStateException("Not supported for the type, " + type);
        }
    }

    public abstract T buildForDoris();

    public abstract T buildForTidb();

    public abstract T buildForMySQL();

    public abstract T buildForOBMySQL();

    public abstract T buildForOBOracle();

    public abstract T buildForOracle();

    public abstract T buildForOdpSharding();

    public abstract T buildForPostgres();

    public abstract T buildForSqlServer();

    public abstract T buildForDm();

    /**
     * Build the factory product for IBM Db2 LUW.
     *
     * <p>
     * Each concrete subclass must implement {@code buildForDB2()}; today most subclasses intentionally
     * throw {@link UnsupportedOperationException} ("Not supported for DB2 yet") so that misuse fails
     * fast with a grep-friendly keyword. The Schema / TableEditor / ColumnEditor factories provide real
     * DB2 implementations (see {@code schema/db2/DB2SchemaAccessor}, {@code editor/db2/DB2TableEditor},
     * {@code editor/db2/DB2ColumnEditor}). See {@code docs/spec/design.md} §3.4 / §3.5 and
     * {@code docs/dev/compat_risks.md} compat-RISK-7.
     */
    public abstract T buildForDB2();

}
