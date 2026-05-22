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
package com.oceanbase.odc.plugin.schema.gaussdb.utils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcOperations;

import com.oceanbase.odc.common.util.JdbcOperationsUtil;
import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.tools.dbbrowser.DBBrowser;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessorFactory;
import com.oceanbase.tools.dbbrowser.schema.postgre.PostgresSchemaAccessor;

/**
 * Unit tests for {@link DBAccessorUtil}.
 * <p>
 * Pins compat_risks CR-4a and CR-15: GaussDB metadata accessor must reuse
 * {@link PostgresSchemaAccessor} via {@code setType("POSTGRESQL")}, never {@code "GAUSSDB"}.
 */
public class DBAccessorUtilTest {

    @Test
    public void testGetSchemaAccessor_setType_isPOSTGRESQL() {
        // Capture the setType(String) argument passed to the db-browser factory
        // and assert it is the literal "POSTGRESQL" - never "GAUSSDB".
        Connection connection = mock(Connection.class);
        JdbcOperations jdbc = mock(JdbcOperations.class);
        DBSchemaAccessorFactory factory = mock(DBSchemaAccessorFactory.class);
        DBSchemaAccessor accessor = mock(DBSchemaAccessor.class);

        when(factory.setJdbcOperations(any())).thenReturn(factory);
        when(factory.setType(any())).thenReturn(factory);
        when(factory.create()).thenReturn(accessor);

        try (MockedStatic<DBBrowser> dbb = mockStatic(DBBrowser.class);
                MockedStatic<JdbcOperationsUtil> ju = mockStatic(JdbcOperationsUtil.class)) {
            dbb.when(DBBrowser::schemaAccessor).thenReturn(factory);
            ju.when(() -> JdbcOperationsUtil.getJdbcOperations(connection)).thenReturn(jdbc);

            DBSchemaAccessor result = DBAccessorUtil.getSchemaAccessor(connection);

            Assert.assertSame(accessor, result);
            ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
            verify(factory).setType(typeCaptor.capture());

            String observedType = typeCaptor.getValue();
            Assert.assertEquals(
                    "DBAccessorUtil must pin db-browser type to POSTGRESQL "
                            + "(see DBAccessorUtil javadoc rationale)",
                    DialectType.POSTGRESQL.getDBBrowserDialectTypeName(), observedType);
            Assert.assertNotEquals(
                    "DBAccessorUtil must NOT pass GAUSSDB to db-browser:1.2.3 - "
                            + "the upstream switch only recognises 7 types and would throw",
                    "GAUSSDB", observedType);
        }
    }

    @Test
    public void testGetSchemaAccessor_returns_PostgresSchemaAccessor_instance() {
        // End-to-end path: hit the real DBBrowser.schemaAccessor() and let
        // create() reach buildForPostgres(). The PostgresSchemaAccessor
        // constructor only stashes the JdbcOperations reference, so no JDBC
        // call is issued.
        Connection connection = mock(Connection.class);
        DBSchemaAccessor accessor = DBAccessorUtil.getSchemaAccessor(connection);
        Assert.assertTrue("expected PostgresSchemaAccessor but got: " + accessor.getClass(),
                accessor instanceof PostgresSchemaAccessor);
    }
}
