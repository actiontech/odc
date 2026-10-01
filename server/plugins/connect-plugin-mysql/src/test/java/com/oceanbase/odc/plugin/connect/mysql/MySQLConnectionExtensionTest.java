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

package com.oceanbase.odc.plugin.connect.mysql;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.odc.plugin.connect.model.JdbcUrlProperty;

/**
 * {@link MySQLConnectionExtensionTest}
 */
public class MySQLConnectionExtensionTest {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 3306;
    private static final String SCHEMA = "testdb";

    private final MySQLConnectionExtension connectionExtension = new MySQLConnectionExtension();

    private String generateJdbcUrl(Map<String, String> jdbcParameters) {
        return connectionExtension.generateJdbcUrl(new JdbcUrlProperty(HOST, PORT, SCHEMA, jdbcParameters));
    }

    @Test
    public void generateJdbcUrl_noUserParameters_publicKeyRetrievalAllowed() {
        String url = generateJdbcUrl(new HashMap<>());

        Assert.assertTrue(url, url.contains("allowPublicKeyRetrieval=true"));
        Assert.assertTrue(url, url.contains("tinyInt1isBit=false"));
    }

    @Test
    public void generateJdbcUrl_userDisabledPublicKeyRetrieval_userValueKept() {
        Map<String, String> jdbcParameters = new HashMap<>();
        jdbcParameters.put("allowPublicKeyRetrieval", "false");

        String url = generateJdbcUrl(jdbcParameters);

        Assert.assertTrue(url, url.contains("allowPublicKeyRetrieval=false"));
        Assert.assertFalse(url, url.contains("allowPublicKeyRetrieval=true"));
    }

    @Test
    public void generateJdbcUrl_nullParameters_publicKeyRetrievalAllowed() {
        String url = generateJdbcUrl(null);

        Assert.assertTrue(url, url.contains("allowPublicKeyRetrieval=true"));
    }

    @Test
    public void generateJdbcUrl_immutableParameters_noExceptionThrown() {
        String url = generateJdbcUrl(Collections.emptyMap());

        Assert.assertTrue(url, url.contains("allowPublicKeyRetrieval=true"));
    }

    @Test
    public void generateJdbcUrl_callerParameters_notModified() {
        Map<String, String> jdbcParameters = new HashMap<>();

        generateJdbcUrl(jdbcParameters);

        Assert.assertTrue(jdbcParameters.toString(), jdbcParameters.isEmpty());
    }

    @Test
    public void generateJdbcUrl_mysql_withoutOceanBaseOnlyParameter() {
        String url = generateJdbcUrl(new HashMap<>());

        Assert.assertFalse(url, url.contains("enableFullLinkTrace"));
    }
}
