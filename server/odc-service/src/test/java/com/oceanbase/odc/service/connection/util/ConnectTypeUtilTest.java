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
package com.oceanbase.odc.service.connection.util;

import java.sql.SQLException;
import java.util.Properties;

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.odc.core.shared.constant.ConnectType;
import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.odc.plugin.connect.api.HostAddress;
import com.oceanbase.odc.plugin.connect.api.JdbcUrlParser;

/**
 * Unit tests for the Hive fast-return overload of {@link ConnectTypeUtil#getConnectType}. The
 * critical contract is: when the caller already knows the dialect is Hive, ODC must NOT issue the
 * OceanBase-specific {@code SHOW VARIABLES LIKE 'ob_compatibility_mode'} probe — that SQL would
 * throw on a Hive server and surface as a confusing connection failure (compat-RISK R-1.1 / design
 * §4.1.2).
 *
 * <p>
 * Verified by passing a deliberately invalid JDBC url + empty properties; if the fast-return is
 * removed, this would throw {@link java.sql.SQLException} because DriverManager.getConnection fails
 * before we can assert ConnectType.HIVE.
 */
public class ConnectTypeUtilTest {

    @Test
    public void getConnectType_hiveDialect_shortCircuitsWithoutProbe() throws SQLException {
        // Bogus URL that would explode if DriverManager.getConnection were invoked. Port stays
        // inside the legal 0–65535 range so any future refactor that does call DriverManager would
        // surface as SQLException (the assertion failure), not as IllegalArgumentException raised
        // by the URL parser when the port is out of range — the latter would mask the regression.
        String bogusUrl = "jdbc:hive2://no-such-host.invalid:10000";
        Properties props = new Properties();

        ConnectType actual = ConnectTypeUtil.getConnectType(bogusUrl, props, 5, DialectType.HIVE);

        Assert.assertEquals(ConnectType.HIVE, actual);
    }

    @Test
    public void getConnectType_nullDialect_doesNotShortCircuit() {
        // With null dialect we expect the overload to fall through to the probing overload —
        // which will then fail on DriverManager. We assert SQLException is surfaced so a future
        // refactor cannot accidentally swallow probing errors when dialectType is unknown.
        Properties props = new Properties();
        try {
            ConnectTypeUtil.getConnectType("jdbc:hive2://no-such-host.invalid:10000", props, 5, null);
            Assert.fail("expected SQLException because dialect is unknown and probe must run");
        } catch (SQLException expected) {
            // expected
        }
    }

    @Test
    public void getConnectType_nonHiveDialect_doesNotShortCircuit() {
        // OB_MYSQL must NOT short-circuit on the Hive fast path. We pass an unreachable jdbc url
        // (legal port so the URL parser does not reject it before DriverManager is invoked) and
        // assert SQLException — proving the implementation fell through to DriverManager.
        Properties props = new Properties();
        try {
            ConnectTypeUtil.getConnectType("jdbc:oceanbase://no-such-host.invalid:2883",
                    props, 5, DialectType.OB_MYSQL);
            Assert.fail("expected SQLException when dialect=OB_MYSQL and host unreachable");
        } catch (SQLException expected) {
            // expected
        }
    }

    @Test
    public void isCloud_singleAliyunHost_returnsTrue() {
        JdbcUrlParser parser = stubParser(new HostAddress("instance.oceanbase.aliyuncs.com", 3306));
        Assert.assertTrue(ConnectTypeUtil.isCloud(parser));
    }

    @Test
    public void isCloud_singleNonCloudHost_returnsFalse() {
        JdbcUrlParser parser = stubParser(new HostAddress("10.0.0.1", 3306));
        Assert.assertFalse(ConnectTypeUtil.isCloud(parser));
    }

    private static JdbcUrlParser stubParser(HostAddress addr) {
        return new JdbcUrlParser() {
            @Override
            public java.util.List<HostAddress> getHostAddresses() {
                return java.util.Collections.singletonList(addr);
            }

            @Override
            public String getSchema() {
                return null;
            }

            @Override
            public java.util.Map<String, Object> getParameters() {
                return java.util.Collections.emptyMap();
            }
        };
    }
}
