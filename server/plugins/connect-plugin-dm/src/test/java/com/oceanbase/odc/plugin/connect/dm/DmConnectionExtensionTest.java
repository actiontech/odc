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
package com.oceanbase.odc.plugin.connect.dm;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oceanbase.odc.core.shared.constant.OdcConstants;
import com.oceanbase.odc.plugin.connect.model.JdbcUrlProperty;

/**
 * Unit tests for {@link DmConnectionExtension}.
 * <p>
 * Uses parameterized map-case style to verify JDBC URL generation.
 * </p>
 *
 * @author
 * @since ODC_release_4.3.4
 */
@RunWith(Parameterized.class)
public class DmConnectionExtensionTest {

    private final String description;
    private final JdbcUrlProperty property;
    private final String expectedUrlPrefix;
    private final boolean expectContainsSchema;
    private final String expectedSchema;

    public DmConnectionExtensionTest(String description, JdbcUrlProperty property,
            String expectedUrlPrefix, boolean expectContainsSchema, String expectedSchema) {
        this.description = description;
        this.property = property;
        this.expectedUrlPrefix = expectedUrlPrefix;
        this.expectContainsSchema = expectContainsSchema;
        this.expectedSchema = expectedSchema;
    }

    @Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                {"basic URL without schema",
                        new JdbcUrlProperty("192.168.1.100", 5236, null, null),
                        "jdbc:dm://192.168.1.100:5236", false, null},
                {"URL with default schema",
                        new JdbcUrlProperty("10.0.0.1", 5236, "SYSDBA", null),
                        "jdbc:dm://10.0.0.1:5236", true, "SYSDBA"},
                {"URL with custom schema",
                        new JdbcUrlProperty("myhost", 5236, "TESTDB", null),
                        "jdbc:dm://myhost:5236", true, "TESTDB"},
                {"URL with extra parameters",
                        new JdbcUrlProperty("localhost", 5236, "MYDB",
                                createParams("loginTimeout", "30")),
                        "jdbc:dm://localhost:5236", true, "MYDB"},
        });
    }

    @Test
    public void testGenerateJdbcUrl() {
        DmConnectionExtension extension = new DmConnectionExtension();
        String url = extension.generateJdbcUrl(property);

        Assert.assertTrue("URL should start with expected prefix: " + expectedUrlPrefix,
                url.startsWith(expectedUrlPrefix));
        if (expectContainsSchema) {
            Assert.assertTrue("URL should contain schema=" + expectedSchema,
                    url.contains("schema=" + expectedSchema));
        }
    }

    /**
     * Non-parameterized tests for driver class name and other properties.
     */
    public static class DmConnectionExtensionBasicTest {

        @Test
        public void getDriverClassName_returnsDmDriver() {
            DmConnectionExtension extension = new DmConnectionExtension();
            Assert.assertEquals(OdcConstants.DM_DRIVER_CLASS_NAME, extension.getDriverClassName());
        }

        @Test
        public void getConnectionInitializers_returnsEmpty() {
            DmConnectionExtension extension = new DmConnectionExtension();
            Assert.assertTrue(extension.getConnectionInitializers().isEmpty());
        }

        @Test
        public void generateJdbcUrl_withParams_containsAllParams() {
            Map<String, String> params = new HashMap<>();
            params.put("loginTimeout", "30");
            params.put("socketTimeout", "60");
            JdbcUrlProperty property = new JdbcUrlProperty("host", 5236, "MYDB", params);

            DmConnectionExtension extension = new DmConnectionExtension();
            String url = extension.generateJdbcUrl(property);

            Assert.assertTrue(url.contains("loginTimeout=30"));
            Assert.assertTrue(url.contains("socketTimeout=60"));
            Assert.assertTrue(url.contains("schema=MYDB"));
        }

        @Test
        public void generateJdbcUrl_noSchemaNoParams_noQueryString() {
            JdbcUrlProperty property = new JdbcUrlProperty("host", 5236, null, null);
            DmConnectionExtension extension = new DmConnectionExtension();
            String url = extension.generateJdbcUrl(property);

            Assert.assertEquals("jdbc:dm://host:5236", url);
        }
    }

    private static Map<String, String> createParams(String key, String value) {
        Map<String, String> params = new HashMap<>();
        params.put(key, value);
        return params;
    }
}
