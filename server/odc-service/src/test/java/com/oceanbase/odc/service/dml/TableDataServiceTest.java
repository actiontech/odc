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
package com.oceanbase.odc.service.dml;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.oceanbase.odc.core.session.ConnectionSession;
import com.oceanbase.odc.core.session.ConnectionSessionUtil;
import com.oceanbase.odc.core.shared.constant.ConnectType;
import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.odc.service.connection.model.ConnectionConfig;
import com.oceanbase.odc.service.db.browser.DBSchemaAccessors;
import com.oceanbase.odc.service.dml.model.BatchDataModifyReq;
import com.oceanbase.odc.service.dml.model.BatchDataModifyReq.Operate;
import com.oceanbase.odc.service.dml.model.BatchDataModifyReq.Row;
import com.oceanbase.odc.service.dml.model.DataModifyUnit;
import com.oceanbase.tools.dbbrowser.model.DBTableColumn;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;

/**
 * Unit tests for {@link TableDataService#batchGetModifySql} Hive branch (compat-RISK R-4.1 — Hive
 * table data is intentionally read-only on the ODC workbench).
 *
 * <p>
 * The Hive branch in the production code is a single
 * {@code throw new UnsupportedOperationException} with a message containing "Hive table data is
 * read-only" — the message text is part of the backend ⇄ frontend contract (design §8.3 / R-7.1)
 * and is asserted exactly here. If the message drifts, the matching toast in odc-client will go
 * stale; the assertion below makes that drift impossible to merge without updating the frontend
 * together.
 *
 * <p>
 * Non-Hive branches are pinned with a smoke test (MySQL / OB_MYSQL / OB_ORACLE) so that an
 * accidental short-circuit on every dialect cannot pass the suite.
 */
public class TableDataServiceTest {

    private final TableDataService service = new TableDataService();

    // ---------- Hive: must throw UOE with the contractual message ----------

    @Test
    public void batchGetModifySql_hive_throwsUnsupportedOperation_withReadOnlyMessage() {
        // build stub OUTSIDE mockStatic block to avoid UnfinishedStubbingException
        DBSchemaAccessor accessor = newAccessorStub();
        ConnectionSession session = stubSession(DialectType.HIVE, ConnectType.HIVE);
        BatchDataModifyReq req = oneRowReq(Operate.UPDATE, "v_new");

        try (MockedStatic<ConnectionSessionUtil> sessionUtil = Mockito.mockStatic(ConnectionSessionUtil.class);
                MockedStatic<DBSchemaAccessors> schemaAccessors = Mockito.mockStatic(DBSchemaAccessors.class)) {
            sessionUtil.when(() -> ConnectionSessionUtil.getConnectionConfig(session)).thenReturn(hiveConfig());
            sessionUtil.when(() -> ConnectionSessionUtil.getCurrentSchema(session)).thenReturn("default");
            schemaAccessors.when(() -> DBSchemaAccessors.create(session)).thenReturn(accessor);

            try {
                service.batchGetModifySql(session, req);
                Assert.fail("Expected UnsupportedOperationException for Hive branch");
            } catch (UnsupportedOperationException e) {
                Assert.assertNotNull(e.getMessage());
                Assert.assertTrue("message must mention 'Hive table data is read-only', got: " + e.getMessage(),
                        e.getMessage().contains("Hive table data is read-only"));
                Assert.assertTrue("message must reference design 2.3 decision 1, got: " + e.getMessage(),
                        e.getMessage().contains("design 2.3 decision 1"));
            }
        }
    }

    @Test
    public void batchGetModifySql_hive_deleteOperate_alsoThrowsUnsupported() {
        DBSchemaAccessor accessor = newAccessorStub();
        ConnectionSession session = stubSession(DialectType.HIVE, ConnectType.HIVE);
        BatchDataModifyReq req = oneRowReq(Operate.DELETE, "v_new");

        try (MockedStatic<ConnectionSessionUtil> sessionUtil = Mockito.mockStatic(ConnectionSessionUtil.class);
                MockedStatic<DBSchemaAccessors> schemaAccessors = Mockito.mockStatic(DBSchemaAccessors.class)) {
            sessionUtil.when(() -> ConnectionSessionUtil.getConnectionConfig(session)).thenReturn(hiveConfig());
            sessionUtil.when(() -> ConnectionSessionUtil.getCurrentSchema(session)).thenReturn("default");
            schemaAccessors.when(() -> DBSchemaAccessors.create(session)).thenReturn(accessor);

            try {
                service.batchGetModifySql(session, req);
                Assert.fail("Expected UnsupportedOperationException for Hive DELETE branch");
            } catch (UnsupportedOperationException e) {
                Assert.assertTrue(e.getMessage().contains("Hive table data is read-only"));
            }
        }
    }

    // ---------- Non-Hive smoke: MySQL/OB_MYSQL/OB_ORACLE must NOT throw the Hive UOE ----------

    @Test
    public void batchGetModifySql_mysql_doesNotThrowHiveUnsupported() {
        DBSchemaAccessor accessor = newAccessorStub();
        ConnectionSession session = stubSession(DialectType.MYSQL, ConnectType.MYSQL);
        BatchDataModifyReq req = noOpUpdateReq();

        try (MockedStatic<ConnectionSessionUtil> sessionUtil = Mockito.mockStatic(ConnectionSessionUtil.class);
                MockedStatic<DBSchemaAccessors> schemaAccessors = Mockito.mockStatic(DBSchemaAccessors.class)) {
            sessionUtil.when(() -> ConnectionSessionUtil.getConnectionConfig(session))
                    .thenReturn(connectionConfig(ConnectType.MYSQL));
            sessionUtil.when(() -> ConnectionSessionUtil.getCurrentSchema(session)).thenReturn("test_db");
            schemaAccessors.when(() -> DBSchemaAccessors.create(session)).thenReturn(accessor);
            service.batchGetModifySql(session, req); // must not throw the Hive UOE
        }
    }

    @Test
    public void batchGetModifySql_obMysql_noOpUpdate_doesNotThrowHiveUnsupported() {
        DBSchemaAccessor accessor = newAccessorStub();
        ConnectionSession session = stubSession(DialectType.OB_MYSQL, ConnectType.OB_MYSQL);
        BatchDataModifyReq req = noOpUpdateReq();
        try (MockedStatic<ConnectionSessionUtil> sessionUtil = Mockito.mockStatic(ConnectionSessionUtil.class);
                MockedStatic<DBSchemaAccessors> schemaAccessors = Mockito.mockStatic(DBSchemaAccessors.class)) {
            sessionUtil.when(() -> ConnectionSessionUtil.getConnectionConfig(session))
                    .thenReturn(connectionConfig(ConnectType.OB_MYSQL));
            sessionUtil.when(() -> ConnectionSessionUtil.getCurrentSchema(session)).thenReturn("test_db");
            schemaAccessors.when(() -> DBSchemaAccessors.create(session)).thenReturn(accessor);
            service.batchGetModifySql(session, req);
        }
    }

    @Test
    public void batchGetModifySql_obOracle_noOpUpdate_doesNotThrowHiveUnsupported() {
        DBSchemaAccessor accessor = newAccessorStub();
        ConnectionSession session = stubSession(DialectType.OB_ORACLE, ConnectType.OB_ORACLE);
        BatchDataModifyReq req = noOpUpdateReq();
        try (MockedStatic<ConnectionSessionUtil> sessionUtil = Mockito.mockStatic(ConnectionSessionUtil.class);
                MockedStatic<DBSchemaAccessors> schemaAccessors = Mockito.mockStatic(DBSchemaAccessors.class)) {
            sessionUtil.when(() -> ConnectionSessionUtil.getConnectionConfig(session))
                    .thenReturn(connectionConfig(ConnectType.OB_ORACLE));
            sessionUtil.when(() -> ConnectionSessionUtil.getCurrentSchema(session)).thenReturn("TEST");
            schemaAccessors.when(() -> DBSchemaAccessors.create(session)).thenReturn(accessor);
            service.batchGetModifySql(session, req);
        }
    }

    // ---------- builders (kept outside mockStatic blocks to avoid stubbing conflicts) ----------

    private static ConnectionSession stubSession(DialectType dialect, ConnectType connectType) {
        ConnectionSession session = Mockito.mock(ConnectionSession.class);
        Mockito.when(session.getDialectType()).thenReturn(dialect);
        Mockito.when(session.getConnectType()).thenReturn(connectType);
        return session;
    }

    private static ConnectionConfig hiveConfig() {
        return connectionConfig(ConnectType.HIVE);
    }

    private static ConnectionConfig connectionConfig(ConnectType type) {
        ConnectionConfig config = new ConnectionConfig();
        config.setType(type);
        return config;
    }

    private static DBSchemaAccessor newAccessorStub() {
        DBSchemaAccessor accessor = Mockito.mock(DBSchemaAccessor.class);
        DBTableColumn col = new DBTableColumn();
        col.setName("c1");
        Mockito.when(accessor.listTableColumns(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Collections.singletonList(col));
        return accessor;
    }

    private static BatchDataModifyReq oneRowReq(Operate op, String newValue) {
        BatchDataModifyReq req = new BatchDataModifyReq();
        req.setTableName("t1");
        req.setSchemaName("default");
        DataModifyUnit unit = new DataModifyUnit();
        unit.setColumnName("c1");
        unit.setOldData("v_old");
        unit.setNewData(newValue);
        Row row = new Row();
        row.setOperate(op);
        row.setUnits(Arrays.asList(unit));
        req.setRows(Arrays.asList(row));
        return req;
    }

    private static BatchDataModifyReq noOpUpdateReq() {
        // old == new -> hasModifiedUnit=false; the for-row body continues without entering the
        // dispatch branch — proves we never throw the Hive UOE for non-Hive dialects.
        BatchDataModifyReq req = new BatchDataModifyReq();
        req.setTableName("t1");
        req.setSchemaName("test_db");
        DataModifyUnit unit = new DataModifyUnit();
        unit.setColumnName("c1");
        unit.setOldData("same");
        unit.setNewData("same");
        Row row = new Row();
        row.setOperate(Operate.UPDATE);
        row.setUnits(Arrays.asList(unit));
        req.setRows(Arrays.asList(row));
        return req;
    }
}
