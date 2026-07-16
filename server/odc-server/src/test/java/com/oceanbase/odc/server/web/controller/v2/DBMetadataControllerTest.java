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
package com.oceanbase.odc.server.web.controller.v2;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.oceanbase.odc.core.session.ConnectionSession;
import com.oceanbase.odc.core.shared.constant.ErrorCodes;
import com.oceanbase.odc.core.shared.exception.BadRequestException;
import com.oceanbase.odc.core.shared.exception.ConflictException;
import com.oceanbase.odc.service.common.response.ListResponse;
import com.oceanbase.odc.service.db.DBIdentitiesService;
import com.oceanbase.odc.service.db.model.SchemaIdentities;
import com.oceanbase.odc.service.session.ConnectSessionService;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;

public class DBMetadataControllerTest {

    private DBMetadataController controller;
    private DBIdentitiesService identitiesService;
    private ConnectSessionService sessionService;
    private ConnectionSession session;

    @Before
    public void setUp() throws Exception {
        this.controller = new DBMetadataController();
        this.identitiesService = Mockito.mock(DBIdentitiesService.class);
        this.sessionService = Mockito.mock(ConnectSessionService.class);
        this.session = Mockito.mock(ConnectionSession.class);
        setField(controller, "identitiesService", identitiesService);
        setField(controller, "sessionService", sessionService);
        Mockito.when(sessionService.nullSafeGet("sid", true)).thenReturn(session);
    }

    @Test
    public void listIdentities_connectionOccupied_returnEmptyList() {
        List<DBObjectType> types = Collections.singletonList(DBObjectType.TABLE);
        Mockito.when(identitiesService.list(session, "db1", "t", types))
                .thenThrow(new ConflictException(ErrorCodes.ConnectionOccupied, new Object[] {}, "occupied"));

        ListResponse<SchemaIdentities> response = controller.listIdentities("sid", types, "db1", "t");

        Assert.assertTrue(response.getSuccessful());
        Assert.assertTrue(response.getData().getContents().isEmpty());
    }

    @Test
    public void listIdentities_badRequestConnectionOccupied_returnEmptyList() {
        List<DBObjectType> types = Collections.singletonList(DBObjectType.TABLE);
        Mockito.when(identitiesService.list(session, "db1", "t", types))
                .thenThrow(new BadRequestException(ErrorCodes.ConnectionOccupied, new Object[] {}, "occupied"));

        ListResponse<SchemaIdentities> response = controller.listIdentities("sid", types, "db1", "t");

        Assert.assertTrue(response.getSuccessful());
        Assert.assertTrue(response.getData().getContents().isEmpty());
    }

    @Test
    public void listIdentities_otherError_throwOriginalException() {
        List<DBObjectType> types = Collections.singletonList(DBObjectType.TABLE);
        BadRequestException exception = new BadRequestException("bad request");
        Mockito.when(identitiesService.list(session, "db1", "t", types)).thenThrow(exception);

        try {
            controller.listIdentities("sid", types, "db1", "t");
            Assert.fail("Expected BadRequestException");
        } catch (BadRequestException actual) {
            Assert.assertSame(exception, actual);
        }
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

}
