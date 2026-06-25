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
package com.oceanbase.odc.service.session;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;

import org.junit.Test;
import org.mockito.MockedStatic;

import com.oceanbase.odc.core.session.ConnectionSession;
import com.oceanbase.odc.core.session.ConnectionSessionUtil;
import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.odc.core.sql.execute.model.JdbcGeneralResult;
import com.oceanbase.odc.core.sql.execute.model.SqlTuple;
import com.oceanbase.odc.core.sql.parser.AbstractSyntaxTreeFactories;
import com.oceanbase.odc.service.session.model.AsyncExecuteContext;

public class OBQueryProfileExecutionListenerTest {

    @Test
    public void onExecutionEnd_isNoOpAndDoesNotPrefetchProfile() {
        ConnectionSession session = mock(ConnectionSession.class);
        try (MockedStatic<ConnectionSessionUtil> util = mockStatic(ConnectionSessionUtil.class)) {
            util.when(() -> ConnectionSessionUtil.getVersion(session)).thenReturn("3.0.0");
            OBQueryProfileExecutionListener listener = new OBQueryProfileExecutionListener(session);
            JdbcGeneralResult result = mock(JdbcGeneralResult.class);
            listener.onExecutionEnd(mock(SqlTuple.class), Collections.singletonList(result),
                    mock(AsyncExecuteContext.class));
        }
    }

    @Test
    public void isObVersionSupportQueryProfile_supportedVersions() {
        assertTrue(OBQueryProfileExecutionListener.isObVersionSupportQueryProfile("4.2.4"));
        assertTrue(OBQueryProfileExecutionListener.isObVersionSupportQueryProfile("4.2.5"));
        assertTrue(OBQueryProfileExecutionListener.isObVersionSupportQueryProfile("4.3.3.1"));
        assertTrue(OBQueryProfileExecutionListener.isObVersionSupportQueryProfile("4.3.4"));
    }

    @Test
    public void isObVersionSupportQueryProfile_unsupportedVersions() {
        assertFalse(OBQueryProfileExecutionListener.isObVersionSupportQueryProfile("4.3.0"));
        assertFalse(OBQueryProfileExecutionListener.isObVersionSupportQueryProfile("4.3.2"));
        assertFalse(OBQueryProfileExecutionListener.isObVersionSupportQueryProfile("3.2.4"));
    }

    @Test
    public void isSqlTypeSupportProfile_selectSupported() throws Exception {
        SqlTuple sqlTuple = buildSqlTuple("select 1 from dual");
        assertTrue(OBQueryProfileExecutionListener.isSqlTypeSupportProfile(sqlTuple));
    }

    @Test
    public void isSqlTypeSupportProfile_ddlNotSupported() throws Exception {
        SqlTuple sqlTuple = buildSqlTuple("create table t1(id int)");
        assertFalse(OBQueryProfileExecutionListener.isSqlTypeSupportProfile(sqlTuple));
    }

    @Test
    public void enrichmentAcquiresConsoleLockImmediatelyAfterAsyncReleaseWithoutProfileBackgroundTask()
            throws Exception {
        Connection mockConnection = mock(Connection.class);
        org.mockito.Mockito.when(mockConnection.isValid(org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
        org.mockito.Mockito.doNothing().when(mockConnection).close();

        try (com.oceanbase.odc.core.datasource.SingleConnectionDataSource dataSource =
                new com.oceanbase.odc.core.datasource.SingleConnectionDataSource(false, false, 500) {
                    @Override
                    protected Connection newConnectionFromDriver(String username, String password)
                            throws SQLException {
                        return mockConnection;
                    }
                }) {
            dataSource.setAutoCommit(true);

            Thread asyncExecution = new Thread(() -> {
                try (Connection conn = dataSource.getConnection()) {
                    Thread.sleep(50);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            asyncExecution.start();
            asyncExecution.join();

            long start = System.currentTimeMillis();
            try (Connection conn = dataSource.getConnection()) {
                conn.isValid(0);
            }
            long elapsed = System.currentTimeMillis() - start;
            assertTrue("Enrichment thread should acquire console lock immediately after async release, waited "
                    + elapsed + "ms", elapsed < 200);
        }
    }

    private SqlTuple buildSqlTuple(String sql) throws Exception {
        SqlTuple sqlTuple = SqlTuple.newTuple(sql);
        sqlTuple.initAst(AbstractSyntaxTreeFactories.getAstFactory(DialectType.OB_MYSQL, 0));
        return sqlTuple;
    }
}
