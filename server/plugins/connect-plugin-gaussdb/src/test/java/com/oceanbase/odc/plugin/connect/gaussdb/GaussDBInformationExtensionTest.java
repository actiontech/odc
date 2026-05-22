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
package com.oceanbase.odc.plugin.connect.gaussdb;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcOperations;

import com.oceanbase.odc.common.util.JdbcOperationsUtil;
import com.oceanbase.odc.common.util.VersionUtils;
import com.oceanbase.odc.core.shared.exception.BadRequestException;

/**
 * Unit tests for {@link GaussDBInformationExtension}.
 * <p>
 * Verifies that the extension queries the PG-compatible {@code current_setting('server_version')}
 * (returns a pure numeric "9.2.4" on both GaussDB commercial and openGauss) rather than the
 * historical {@code SELECT version()} banner; the latter would feed
 * {@code com.oceanbase.odc.common.util.VersionUtils#compareVersions} a "gaussdb (GaussDB Kernel
 * 505..." string that triggers {@link NumberFormatException} on {@code Integer.parseInt},
 * manifesting as the 400 BadRequest blocker on {@code POST
 * /api/v2/datasource/databases/{id}/sessions} (Task-004-FIX-2 root cause).
 */
public class GaussDBInformationExtensionTest {

    private GaussDBInformationExtension extension;
    private Connection connection;
    private JdbcOperations jdbc;

    @Before
    public void setUp() {
        this.extension = new GaussDBInformationExtension();
        this.connection = mock(Connection.class);
        this.jdbc = mock(JdbcOperations.class);
    }

    @Test
    public void testGetDBVersion_usesServerVersionSetting() {
        try (MockedStatic<JdbcOperationsUtil> ms = mockStatic(JdbcOperationsUtil.class)) {
            ms.when(() -> JdbcOperationsUtil.getJdbcOperations(connection)).thenReturn(jdbc);
            when(jdbc.queryForObject(eq("SELECT current_setting('server_version');"),
                    eq(String.class))).thenReturn("9.2.4");

            String version = extension.getDBVersion(connection);

            Assert.assertEquals("9.2.4", version);
            // The SQL is load-bearing: see method-level Javadoc on
            // GaussDBInformationExtension#getDBVersion. Switching back to "SELECT version();"
            // would re-introduce the NumberFormatException-driven 400 on
            // createSessionByDatabase.
            verify(jdbc, times(1)).queryForObject("SELECT current_setting('server_version');",
                    String.class);
        }
    }

    @Test
    public void testGetDBVersion_returnedValueIsParseableByVersionUtils_gaussdb() {
        // Real value observed on GaussDB commercial 122.9.71.90:8000 (Kernel 505.2.1.SPC0800)
        try (MockedStatic<JdbcOperationsUtil> ms = mockStatic(JdbcOperationsUtil.class)) {
            ms.when(() -> JdbcOperationsUtil.getJdbcOperations(connection)).thenReturn(jdbc);
            when(jdbc.queryForObject(eq("SELECT current_setting('server_version');"),
                    eq(String.class))).thenReturn("9.2.4");

            String version = extension.getDBVersion(connection);

            // Round-trip the value through VersionUtils to catch any future regression
            // that lets a non-numeric segment slip through; the historical
            // "gaussdb (GaussDB Kernel 505..." banner would NumberFormatException here.
            Assert.assertTrue(VersionUtils.isGreaterThan0(version));
            Assert.assertTrue(VersionUtils.isGreaterThanOrEqualsTo(version, "9.0.0"));
            Assert.assertTrue(VersionUtils.isLessThan(version, "10.0.0"));
        }
    }

    @Test
    public void testGetDBVersion_returnedValueIsParseableByVersionUtils_opengauss() {
        // openGauss 6.0.0 build aee4abd5 reports current_setting('server_version') = '9.2.4'
        // (PG-compatible value), same as GaussDB commercial; the historical version() banner
        // "(openGauss 6.0.0 build ...)" would explode in VersionUtils.
        try (MockedStatic<JdbcOperationsUtil> ms = mockStatic(JdbcOperationsUtil.class)) {
            ms.when(() -> JdbcOperationsUtil.getJdbcOperations(connection)).thenReturn(jdbc);
            when(jdbc.queryForObject(eq("SELECT current_setting('server_version');"),
                    eq(String.class))).thenReturn("9.2.4");

            String version = extension.getDBVersion(connection);

            Assert.assertEquals(0, VersionUtils.compareVersions(version, "9.2.4"));
        }
    }

    @Test(expected = BadRequestException.class)
    public void testGetDBVersion_emptyResultSetThrowsBadRequest() {
        try (MockedStatic<JdbcOperationsUtil> ms = mockStatic(JdbcOperationsUtil.class)) {
            ms.when(() -> JdbcOperationsUtil.getJdbcOperations(connection)).thenReturn(jdbc);
            when(jdbc.queryForObject(eq("SELECT current_setting('server_version');"),
                    eq(String.class))).thenThrow(new RuntimeException("empty result set"));

            extension.getDBVersion(connection);
        }
    }
}
