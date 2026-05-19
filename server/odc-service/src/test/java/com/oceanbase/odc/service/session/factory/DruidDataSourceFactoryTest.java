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
package com.oceanbase.odc.service.session.factory;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.odc.core.shared.constant.DialectType;

/**
 * Map-case unit tests for {@link DruidDataSourceFactory#validationQueryFor(DialectType)}. Covers
 * B-24 — DB2 validation query must be {@code "select 1 from SYSIBM.SYSDUMMY1"} (DB2 enforces a FROM
 * clause, design.md §2.5). Other dialects unchanged.
 *
 * @author actiontech-zihan
 * @since 4.3.4
 */
public class DruidDataSourceFactoryTest {

    @Test
    public void validationQueryFor_mapCases() {
        Map<DialectType, String> cases = new LinkedHashMap<>();
        cases.put(DialectType.DB2, "select 1 from SYSIBM.SYSDUMMY1");
        cases.put(DialectType.MYSQL, "select 1");
        cases.put(DialectType.OB_MYSQL, "select 1");
        cases.put(DialectType.DORIS, "select 1");
        cases.put(DialectType.TIDB, "select 1");
        cases.put(DialectType.POSTGRESQL, "select 1");
        cases.put(DialectType.SQL_SERVER, "select 1");
        cases.put(DialectType.OB_ORACLE, "select 1 from dual");
        cases.put(DialectType.ORACLE, "select 1 from dual");
        cases.put(DialectType.DM, "select 1 from dual");

        for (Map.Entry<DialectType, String> entry : cases.entrySet()) {
            Assert.assertEquals("dialect=" + entry.getKey(), entry.getValue(),
                    DruidDataSourceFactory.validationQueryFor(entry.getKey()));
        }
    }

    @Test
    public void validationQueryFor_nullDialect_defaultsToOracleStyle() {
        Assert.assertEquals("select 1 from dual",
                DruidDataSourceFactory.validationQueryFor(null));
    }
}
