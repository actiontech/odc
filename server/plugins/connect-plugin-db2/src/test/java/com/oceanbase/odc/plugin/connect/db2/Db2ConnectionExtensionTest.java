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
package com.oceanbase.odc.plugin.connect.db2;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.odc.core.shared.constant.OdcConstants;
import com.oceanbase.odc.plugin.connect.model.JdbcUrlProperty;

/**
 * Map-case unit tests for {@link Db2ConnectionExtension}. Pure unit tests — no real JDBC connection
 * (R-14). Aligned with design.md §2.3 (extension method table) / §2.7 (IBM JDBC).
 *
 * @author actiontech-zihan
 * @since 4.3.4
 */
public class Db2ConnectionExtensionTest {

    private static final Db2ConnectionExtension EXTENSION = new Db2ConnectionExtension();

    @Test
    public void getDriverClassName_returnsConstant() {
        Assert.assertEquals("com.ibm.db2.jcc.DB2Driver", EXTENSION.getDriverClassName());
        // 强契约：必须引用 OdcConstants 常量，不允许裸字符串
        Assert.assertEquals(OdcConstants.DB2_DRIVER_CLASS_NAME, EXTENSION.getDriverClassName());
    }

    /**
     * Map case for {@link Db2ConnectionExtension#generateJdbcUrl(JdbcUrlProperty)}.
     * <p>
     * Each row = (描述, host, port, catalog, defaultSchema, 期望 jdbcUrl)。
     */
    @Test
    public void generateJdbcUrl_mapCases() {
        Map<String, Case> cases = new LinkedHashMap<>();
        cases.put("standard with explicit schema",
                new Case("10.186.16.126", 50000, "testdb", "DB2INST1",
                        "jdbc:db2://10.186.16.126:50000/testdb:currentSchema=DB2INST1;"));
        cases.put("lowercase schema is uppercased",
                new Case("10.186.16.126", 50000, "testdb", "db2inst1",
                        "jdbc:db2://10.186.16.126:50000/testdb:currentSchema=DB2INST1;"));
        cases.put("null schema → URL has no currentSchema segment",
                new Case("h", 50000, "testdb", null,
                        "jdbc:db2://h:50000/testdb"));
        cases.put("blank schema → URL has no currentSchema segment",
                new Case("h", 50000, "testdb", "   ",
                        "jdbc:db2://h:50000/testdb"));
        // Regression for fix-F (catalogName fallback to defaultSchema). Upstream DMS-EE
        // currently only carries the DB2 database name via the defaultSchema field of
        // CreateDatasourceRequest; the plugin must fall back rather than fail-fast.
        cases.put("null catalog falls back to defaultSchema (DMS-EE contract)",
                new Case("h", 50000, null, "testdb",
                        "jdbc:db2://h:50000/testdb"));
        cases.put("empty catalog falls back to defaultSchema",
                new Case("h", 50000, "", "testdb",
                        "jdbc:db2://h:50000/testdb"));
        cases.put("catalog equals schema (case-insensitive) → no currentSchema segment",
                new Case("h", 50000, "TESTDB", "testdb",
                        "jdbc:db2://h:50000/TESTDB"));
        cases.put("explicit catalog + distinct schema both honoured",
                new Case("h", 50000, "PROD_DB", "ALICE",
                        "jdbc:db2://h:50000/PROD_DB:currentSchema=ALICE;"));

        for (Map.Entry<String, Case> entry : cases.entrySet()) {
            Case c = entry.getValue();
            JdbcUrlProperty property =
                    new JdbcUrlProperty(c.host, c.port, c.schema, null, null, null, c.catalog);
            String actual = EXTENSION.generateJdbcUrl(property);
            Assert.assertEquals("case=[" + entry.getKey() + "]", c.expected, actual);
        }
    }

    @Test(expected = NullPointerException.class)
    public void generateJdbcUrl_bothCatalogAndSchemaNull_throws() {
        // After fix-F: catalogName fallback only succeeds when defaultSchema is non-empty.
        // Apache Commons Lang3 Validate.notEmpty(String) throws NPE for null and IAE for empty,
        // both with the descriptive "DB2 catalog (database name) can not be null" message.
        JdbcUrlProperty property =
                new JdbcUrlProperty("h", 50000, null, null, null, null, null);
        EXTENSION.generateJdbcUrl(property);
    }

    @Test(expected = IllegalArgumentException.class)
    public void generateJdbcUrl_bothCatalogAndSchemaEmpty_throws() {
        JdbcUrlProperty property =
                new JdbcUrlProperty("h", 50000, "", null, null, null, "");
        EXTENSION.generateJdbcUrl(property);
    }

    @Test(expected = NullPointerException.class)
    public void generateJdbcUrl_nullHost_throws() {
        JdbcUrlProperty property =
                new JdbcUrlProperty("placeholder", 50000, "DB2INST1", null, null, null, "testdb");
        property.setHost(null);
        EXTENSION.generateJdbcUrl(property);
    }

    @Test
    public void getConnectionInitializers_nonEmpty() {
        Assert.assertFalse(EXTENSION.getConnectionInitializers().isEmpty());
    }

    private static final class Case {
        final String host;
        final Integer port;
        final String catalog;
        final String schema;
        final String expected;

        Case(String host, Integer port, String catalog, String schema, String expected) {
            this.host = host;
            this.port = port;
            this.catalog = catalog;
            this.schema = schema;
            this.expected = expected;
        }
    }
}
