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
package com.oceanbase.odc.core.sql.parser;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.odc.core.shared.constant.DialectType;

public class AbstractSyntaxTreeFactoriesTest {

    @Test
    public void getAstFactory_tidb_returnsOBMySQLAstFactory() {
        AbstractSyntaxTreeFactory factory = AbstractSyntaxTreeFactories.getAstFactory(DialectType.TIDB, 0);
        Assert.assertNotNull("AST factory should not be null for TIDB", factory);
        Assert.assertTrue("TIDB should use OBMySQLAstFactory",
                factory instanceof OBMySQLAstFactory);
    }

    @Test
    public void getAstFactory_variousDialects_returnsExpectedFactoryType() {
        Map<DialectType, Class<? extends AbstractSyntaxTreeFactory>> cases = new LinkedHashMap<>();
        cases.put(DialectType.MYSQL, OBMySQLAstFactory.class);
        cases.put(DialectType.OB_MYSQL, OBMySQLAstFactory.class);
        cases.put(DialectType.DORIS, OBMySQLAstFactory.class);
        cases.put(DialectType.TIDB, OBMySQLAstFactory.class);
        cases.put(DialectType.GBASE_8A, OBMySQLAstFactory.class);
        cases.put(DialectType.OB_ORACLE, OBOracleAstFactory.class);

        for (Map.Entry<DialectType, Class<? extends AbstractSyntaxTreeFactory>> entry : cases.entrySet()) {
            AbstractSyntaxTreeFactory factory = AbstractSyntaxTreeFactories.getAstFactory(entry.getKey(), 0);
            Assert.assertNotNull("Factory should not be null for " + entry.getKey(), factory);
            Assert.assertEquals("Factory type mismatch for " + entry.getKey(),
                    entry.getValue(), factory.getClass());
        }
    }

    @Test
    public void getAstFactory_unknownDialect_returnsNull() {
        AbstractSyntaxTreeFactory factory = AbstractSyntaxTreeFactories.getAstFactory(DialectType.UNKNOWN, 0);
        Assert.assertNull("Factory should be null for UNKNOWN dialect", factory);
    }
}
