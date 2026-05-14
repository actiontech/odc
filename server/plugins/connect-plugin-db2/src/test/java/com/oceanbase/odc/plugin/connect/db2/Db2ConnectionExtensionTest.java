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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.odc.plugin.connect.model.JdbcUrlProperty;

public class Db2ConnectionExtensionTest {

    private final Db2ConnectionExtension extension = new Db2ConnectionExtension();

    @Test
    public void getDriverClassName_returnsIBMDriver() {
        Assert.assertEquals("com.ibm.db2.jcc.DB2Driver", extension.getDriverClassName());
    }

    @Test
    public void getConnectionInitializers_returnsEmpty() {
        Assert.assertTrue(extension.getConnectionInitializers().isEmpty());
    }

    @Test
    public void generateJdbcUrl_minimal_returnsHostPortCatalog() {
        JdbcUrlProperty props = new JdbcUrlProperty("10.186.16.126", 50000, null, null, null, null, "testdb");
        String url = extension.generateJdbcUrl(props);
        Assert.assertEquals(
                "jdbc:db2://10.186.16.126:50000/testdb:retrieveMessagesFromServerOnGetMessage=true;",
                url);
    }

    @Test
    public void generateJdbcUrl_withDefaultSchema_appendsCurrentSchema() {
        JdbcUrlProperty props = new JdbcUrlProperty("h", 50000, "DB2INST1", null, null, null, "TESTDB");
        String url = extension.generateJdbcUrl(props);
        Assert.assertTrue("must include currentSchema=DB2INST1, got " + url,
                url.contains("currentSchema=DB2INST1"));
        Assert.assertTrue("must include retrieveMessagesFromServerOnGetMessage=true, got " + url,
                url.contains("retrieveMessagesFromServerOnGetMessage=true"));
        Assert.assertTrue("must start with prefix, got " + url, url.startsWith("jdbc:db2://h:50000/TESTDB:"));
    }

    @Test
    public void generateJdbcUrl_withExtraParams_paramsPreserved() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("sslConnection", "true");
        params.put("sslTrustStoreLocation", "/etc/db2/trust.jks");
        JdbcUrlProperty props = new JdbcUrlProperty("h", 50000, null, params, null, null, "TESTDB");
        String url = extension.generateJdbcUrl(props);
        Assert.assertTrue(url.contains("sslConnection=true"));
        Assert.assertTrue(url.contains("sslTrustStoreLocation=/etc/db2/trust.jks"));
        Assert.assertTrue(url.contains("retrieveMessagesFromServerOnGetMessage=true"));
    }

    /**
     * 用户已经显式传 currentSchema 时，不被 defaultSchema 覆盖。
     */
    @Test
    public void generateJdbcUrl_userParamCurrentSchema_notOverwritten() {
        Map<String, String> params = new HashMap<>();
        params.put("currentSchema", "FROM_PARAMS");
        JdbcUrlProperty props = new JdbcUrlProperty("h", 50000, "SHOULD_NOT_WIN", params, null, null, "TESTDB");
        String url = extension.generateJdbcUrl(props);
        Assert.assertTrue(url.contains("currentSchema=FROM_PARAMS"));
        Assert.assertFalse(url.contains("currentSchema=SHOULD_NOT_WIN"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void generateJdbcUrl_emptyHost_throwIllegalArgument() {
        JdbcUrlProperty props = new JdbcUrlProperty("dummy", 50000, null, null, null, null, "testdb");
        props.setHost("");
        extension.generateJdbcUrl(props);
    }

    @Test(expected = IllegalArgumentException.class)
    public void generateJdbcUrl_emptyCatalog_throwIllegalArgument() {
        JdbcUrlProperty props = new JdbcUrlProperty("h", 50000, null, null, null, null, "");
        extension.generateJdbcUrl(props);
    }

    @Test
    public void appendDefaultJdbcUrlParameters_emptyInput_returnsMapWithRetrieveMessagesFlag() {
        Map<String, String> result = invokeAppend(new HashMap<>());
        Assert.assertEquals("true", result.get("retrieveMessagesFromServerOnGetMessage"));
    }

    @Test
    public void appendDefaultJdbcUrlParameters_nullInput_returnsNonNullMap() {
        Map<String, String> result = invokeAppend(null);
        Assert.assertNotNull(result);
        Assert.assertEquals("true", result.get("retrieveMessagesFromServerOnGetMessage"));
    }

    @Test
    public void appendDefaultJdbcUrlParameters_existingRetrieveMessages_preserved() {
        Map<String, String> input = new HashMap<>();
        input.put("retrieveMessagesFromServerOnGetMessage", "false");
        Map<String, String> result = invokeAppend(input);
        Assert.assertEquals("false", result.get("retrieveMessagesFromServerOnGetMessage"));
    }

    /**
     * 反射调用 protected 方法（OBMySQLConnectionExtension 同包 protected）。
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> invokeAppend(Map<String, String> input) {
        try {
            java.lang.reflect.Method m = Db2ConnectionExtension.class
                    .getDeclaredMethod("appendDefaultJdbcUrlParameters", Map.class);
            m.setAccessible(true);
            return (Map<String, String>) m.invoke(extension, input);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
