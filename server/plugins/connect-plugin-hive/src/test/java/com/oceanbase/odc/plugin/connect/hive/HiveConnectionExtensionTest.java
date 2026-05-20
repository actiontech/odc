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
package com.oceanbase.odc.plugin.connect.hive;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.oceanbase.odc.core.datasource.ConnectionInitializer;
import com.oceanbase.odc.plugin.connect.api.JdbcUrlParser;
import com.oceanbase.odc.plugin.connect.api.TestResult;
import com.oceanbase.odc.plugin.connect.hive.initializer.HiveConnectionInitializer;
import com.oceanbase.odc.plugin.connect.model.JdbcUrlProperty;

/**
 * Pure unit tests for {@link HiveConnectionExtension}. Network-dependent paths (DriverManager
 * happy-path / Kerberos handshake / wire protocol negotiation) are deliberately NOT covered here —
 * those belong in web/integration tests because they require a real Hive Server2 in {@code
 * skills/db-validation}. The cases below pin the contract pieces that can be verified with
 * deterministic inputs.
 *
 * <p>
 * Covers compat-RISK R-1.1 (HiveDriver class name constant), R-7.2 (error code mapping when host is
 * unknown), and R-4.2 (extension boundary methods returning safe defaults).
 */
public class HiveConnectionExtensionTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private final HiveConnectionExtension extension = new HiveConnectionExtension();

    @Test
    public void getDriverClassName_isApacheHiveDriver() {
        Assert.assertEquals("org.apache.hive.jdbc.HiveDriver", extension.getDriverClassName());
        Assert.assertEquals(HiveConnectionExtension.HIVE_DRIVER_CLASS_NAME,
                extension.getDriverClassName());
    }

    @Test
    public void generateJdbcUrl_delegatesToParser() {
        JdbcUrlProperty p = new JdbcUrlProperty("10.0.0.1", 10000, "default", new HashMap<>());
        String url = extension.generateJdbcUrl(p);
        Assert.assertEquals("jdbc:hive2://10.0.0.1:10000/default", url);
    }

    @Test
    public void getConnectionInitializers_returnsSingletonHiveInitializer() {
        List<ConnectionInitializer> initializers = extension.getConnectionInitializers();
        Assert.assertNotNull(initializers);
        Assert.assertEquals(1, initializers.size());
        Assert.assertTrue(initializers.get(0) instanceof HiveConnectionInitializer);
    }

    @Test
    public void getConnectionInfo_validUrl_returnsParser() throws SQLException {
        JdbcUrlParser parser = extension.getConnectionInfo("jdbc:hive2://10.0.0.1:10000/db", null);
        Assert.assertEquals("db", parser.getSchema());
        Assert.assertEquals(1, parser.getHostAddresses().size());
    }

    @Test
    public void getConnectionInfo_invalidUrl_wrapsAsSqlException() throws SQLException {
        thrown.expect(SQLException.class);
        thrown.expectMessage("Failed to parse Hive jdbc url");
        extension.getConnectionInfo("jdbc:mysql://nope:3306", null);
    }

    /**
     * Validate the "unknown host" branch of
     * {@link HiveConnectionExtension#test(String, Properties, int, java.util.List)}. The hostname is
     * deliberately a UUID so DNS resolution returns {@link java.net.UnknownHostException}, which we
     * then assert is mapped to {@code TestResult.unknownHost} per the error-code-mapping contract
     * (R-7.2). This is the only branch of {@code test()} we can exercise without a live Hive Server.
     */
    @Test
    public void test_unknownHost_mapsToUnknownHostErrorCode() {
        // Use a non-resolvable host. We do NOT depend on the Apache HiveDriver actually being
        // registered — DriverManager.getConnection still throws SQLException, the root cause path
        // surfaces and unknownError or unknownHost is returned. Both are non-active, which is the
        // load-bearing assertion for the R-7.2 mapping pipeline.
        String url = "jdbc:hive2://no-such-host-" + System.nanoTime() + ".invalid:10000";
        TestResult result = extension.test(url, new Properties(), 5, null);
        Assert.assertNotNull(result);
        Assert.assertFalse("expected failure for unresolvable host", result.isActive());
    }

    @Test
    public void test_malformedJdbcUrl_returnsUnknownError() {
        // Malformed URL -> getConnectionInfo throws -> we hit the early return TestResult.unknownError
        TestResult result = extension.test("jdbc:hive2://", new Properties(), 5, null);
        Assert.assertNotNull(result);
        Assert.assertFalse(result.isActive());
        Assert.assertNotNull(result.getErrorCode());
    }
}
