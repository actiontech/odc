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
package com.oceanbase.odc.plugin.schema.hive;

import java.sql.Connection;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.oceanbase.tools.dbbrowser.model.DBProcedure;

/**
 * Hive does not expose procedure metadata; every extension method must throw
 * {@link UnsupportedOperationException} so the global exception handler maps it to HTTP 400 (the
 * front-end {@code features.procedureVisible=false} flag handles the UI hide). These assertions pin
 * the backend backstop contract (design.md §2.3 decision 1 / R-4.3).
 */
public class HiveProcedureExtensionTest {

    private final HiveProcedureExtension extension = new HiveProcedureExtension();

    @Test
    public void list_throwsUnsupportedOperationException() {
        Connection conn = Mockito.mock(Connection.class);
        assertThrowsUnsupported(() -> extension.list(conn, "default"));
        Mockito.verifyNoInteractions(conn);
    }

    @Test
    public void getDetail_throwsUnsupportedOperationException() {
        Connection conn = Mockito.mock(Connection.class);
        assertThrowsUnsupported(() -> extension.getDetail(conn, "default", "p1"));
        Mockito.verifyNoInteractions(conn);
    }

    @Test
    public void drop_throwsUnsupportedOperationException() {
        Connection conn = Mockito.mock(Connection.class);
        assertThrowsUnsupported(() -> extension.drop(conn, "default", "p1"));
        Mockito.verifyNoInteractions(conn);
    }

    @Test
    public void generateCreateTemplate_throwsUnsupportedOperationException() {
        assertThrowsUnsupported(() -> extension.generateCreateTemplate(new DBProcedure()));
    }

    private static void assertThrowsUnsupported(Runnable action) {
        try {
            action.run();
            Assert.fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            Assert.assertTrue(
                    "message should mention 'not supported', got: " + msg,
                    msg.toLowerCase().contains("not supported"));
        }
    }
}
