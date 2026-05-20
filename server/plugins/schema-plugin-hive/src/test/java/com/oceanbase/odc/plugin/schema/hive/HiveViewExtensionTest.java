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
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.oceanbase.tools.dbbrowser.model.DBView;

/**
 * Unit tests for {@link HiveViewExtension}. Pins the read-only contract (drop / template generation
 * raise {@link UnsupportedOperationException}) and the empty {@code listSystemViews} contract —
 * Hive has no concept of system views so the extension hard-codes an empty list. The list /
 * getDetail branches that traverse {@code HiveTableExtension} require a real Hive Server and are
 * out of unit scope.
 *
 * <p>
 * Covers compat-RISK R-4.2 / R-7.1 (extension boundary methods returning safe defaults / throwing
 * UOE on writes).
 */
public class HiveViewExtensionTest {

    private final HiveViewExtension extension = new HiveViewExtension();

    @Test
    public void listSystemViews_returnsEmptyList() {
        Connection conn = Mockito.mock(Connection.class);
        List<String> views = extension.listSystemViews(conn, "default");
        Assert.assertNotNull(views);
        Assert.assertTrue(views.isEmpty());
        Mockito.verifyNoInteractions(conn);
    }

    @Test
    public void drop_throwsUnsupportedOperationException() {
        Connection conn = Mockito.mock(Connection.class);
        try {
            extension.drop(conn, "default", "v1");
            Assert.fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            Assert.assertTrue(
                    "message should reference design contract, got: " + msg,
                    msg.contains("design 2.3 decision 1"));
        }
        Mockito.verifyNoInteractions(conn);
    }

    @Test
    public void generateCreateTemplate_throwsUnsupportedOperationException() {
        try {
            extension.generateCreateTemplate(new DBView());
            Assert.fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            Assert.assertNotNull(e.getMessage());
            Assert.assertTrue(e.getMessage().toLowerCase().contains("not supported"));
        }
    }
}
