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
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcOperations;

import com.oceanbase.odc.common.util.JdbcOperationsUtil;
import com.oceanbase.odc.plugin.connect.model.DBClientInfo;

/**
 * Unit tests for {@link GaussDBSessionExtension}.
 * <p>
 * Verifies the seven PG-protocol overrides demanded by compat_risks CR-4b: pg_cancel_backend /
 * pg_terminate_backend / SET search_path / current_schema() / pg_backend_pid()::text /
 * current_setting() and the setClientInfo no-op. Every test stubs
 * {@link JdbcOperationsUtil#getJdbcOperations} via {@link Mockito#mockStatic} so the suite never
 * opens a real JDBC connection.
 */
public class GaussDBSessionExtensionTest {

    private GaussDBSessionExtension extension;
    private Connection connection;
    private JdbcOperations jdbc;

    @Before
    public void setUp() {
        this.extension = new GaussDBSessionExtension();
        this.connection = mock(Connection.class);
        this.jdbc = mock(JdbcOperations.class);
    }

    @Test
    public void testGetKillQuerySql_returns_pg_cancel_backend() {
        Assert.assertEquals("SELECT pg_cancel_backend(12345)",
                extension.getKillQuerySql("12345"));
    }

    @Test
    public void testGetKillSessionSql_returns_pg_terminate_backend() {
        Assert.assertEquals("SELECT pg_terminate_backend(12345)",
                extension.getKillSessionSql("12345"));
    }

    @Test
    public void testSwitchSchema_uses_SET_search_path() throws Exception {
        try (MockedStatic<JdbcOperationsUtil> ms = mockStatic(JdbcOperationsUtil.class)) {
            ms.when(() -> JdbcOperationsUtil.getJdbcOperations(connection)).thenReturn(jdbc);

            extension.switchSchema(connection, "public");

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(jdbc, times(1)).execute(sqlCaptor.capture());
            Assert.assertEquals("SET search_path TO \"public\"", sqlCaptor.getValue());
        }
    }

    @Test
    public void testGetCurrentSchema_uses_current_schema_func() {
        try (MockedStatic<JdbcOperationsUtil> ms = mockStatic(JdbcOperationsUtil.class)) {
            ms.when(() -> JdbcOperationsUtil.getJdbcOperations(connection)).thenReturn(jdbc);
            when(jdbc.queryForObject(eq("SELECT current_schema()"), eq(String.class)))
                    .thenReturn("public");

            String schema = extension.getCurrentSchema(connection);

            Assert.assertEquals("public", schema);
            verify(jdbc, times(1)).queryForObject("SELECT current_schema()", String.class);
        }
    }

    @Test
    public void testGetConnectionId_uses_pg_backend_pid_text() {
        try (MockedStatic<JdbcOperationsUtil> ms = mockStatic(JdbcOperationsUtil.class)) {
            ms.when(() -> JdbcOperationsUtil.getJdbcOperations(connection)).thenReturn(jdbc);
            when(jdbc.queryForObject(eq("SELECT pg_backend_pid()::text"), eq(String.class)))
                    .thenReturn("42");

            String id = extension.getConnectionId(connection);

            Assert.assertEquals("42", id);
            // The ::text cast is load-bearing: PG returns an int4 by default and the
            // queryForObject(..., String.class) would NPE on some PIDs that exceed
            // Integer.MAX_VALUE on certain platforms.
            verify(jdbc, times(1)).queryForObject("SELECT pg_backend_pid()::text", String.class);
        }
    }

    @Test
    public void testGetVariable_uses_current_setting() {
        try (MockedStatic<JdbcOperationsUtil> ms = mockStatic(JdbcOperationsUtil.class)) {
            ms.when(() -> JdbcOperationsUtil.getJdbcOperations(connection)).thenReturn(jdbc);
            when(jdbc.queryForObject(eq("SELECT current_setting('work_mem')"), eq(String.class)))
                    .thenReturn("4MB");

            String val = extension.getVariable(connection, "work_mem");

            Assert.assertEquals("4MB", val);
            verify(jdbc, times(1)).queryForObject("SELECT current_setting('work_mem')", String.class);
        }
    }

    @Test
    public void testSetClientInfo_returns_false() {
        Assert.assertFalse(extension.setClientInfo(connection, new DBClientInfo(null, null, null)));
    }
}
