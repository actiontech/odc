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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.oceanbase.tools.dbbrowser.model.DBDatabase;
import com.oceanbase.tools.dbbrowser.model.DBObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;

/**
 * Unit tests for {@link HiveDatabaseExtension}. The list / listDetails branches are exercised with
 * a mocked JDBC {@link ResultSet} returning the three databases Hive 4.x ships out of the box
 * (default, sqle_compare_test, sys), while the create branch is pinned to the read-only design
 * contract.
 *
 * <p>
 * Covers compat-RISK R-4.2 (read-only backstop for write operations) and R-7.1 (SHOW DATABASES
 * happy path).
 */
public class HiveDatabaseExtensionTest {

    private final HiveDatabaseExtension extension = new HiveDatabaseExtension();

    @Test
    public void list_returnsDatabasesFromShowDatabases() throws SQLException {
        Connection conn = Mockito.mock(Connection.class);
        Statement stmt = Mockito.mock(Statement.class);
        ResultSet rs = Mockito.mock(ResultSet.class);
        Mockito.when(conn.createStatement()).thenReturn(stmt);
        Mockito.when(stmt.executeQuery("SHOW DATABASES")).thenReturn(rs);
        // simulate three database rows then exhausted
        Mockito.when(rs.next()).thenReturn(true, true, true, false);
        Mockito.when(rs.getString(1)).thenReturn("default", "sqle_compare_test", "sys");

        List<DBObjectIdentity> dbs = extension.list(conn);

        Assert.assertEquals(3, dbs.size());
        Assert.assertEquals("default", dbs.get(0).getName());
        Assert.assertEquals(DBObjectType.DATABASE, dbs.get(0).getType());
        Assert.assertNull(dbs.get(0).getSchemaName());
        Assert.assertEquals("sqle_compare_test", dbs.get(1).getName());
        Assert.assertEquals("sys", dbs.get(2).getName());
    }

    @Test
    public void list_emptyResultSet_returnsEmptyList() throws SQLException {
        Connection conn = Mockito.mock(Connection.class);
        Statement stmt = Mockito.mock(Statement.class);
        ResultSet rs = Mockito.mock(ResultSet.class);
        Mockito.when(conn.createStatement()).thenReturn(stmt);
        Mockito.when(stmt.executeQuery("SHOW DATABASES")).thenReturn(rs);
        Mockito.when(rs.next()).thenReturn(false);

        List<DBObjectIdentity> dbs = extension.list(conn);
        Assert.assertNotNull(dbs);
        Assert.assertTrue(dbs.isEmpty());
    }

    @Test
    public void list_sqlExceptionIsWrappedAsIllegalState() throws SQLException {
        Connection conn = Mockito.mock(Connection.class);
        Mockito.when(conn.createStatement()).thenThrow(new SQLException("connection lost"));

        try {
            extension.list(conn);
            Assert.fail("Expected IllegalStateException when JDBC fails");
        } catch (IllegalStateException e) {
            Assert.assertTrue(
                    "message should hint the listing context, got: " + e.getMessage(),
                    e.getMessage().contains("Failed to list Hive databases"));
        }
    }

    @Test
    public void listDetails_mirrorsListNames() throws SQLException {
        Connection conn = Mockito.mock(Connection.class);
        Statement stmt = Mockito.mock(Statement.class);
        ResultSet rs = Mockito.mock(ResultSet.class);
        Mockito.when(conn.createStatement()).thenReturn(stmt);
        Mockito.when(stmt.executeQuery("SHOW DATABASES")).thenReturn(rs);
        Mockito.when(rs.next()).thenReturn(true, true, false);
        Mockito.when(rs.getString(1)).thenReturn("default", "sqle_compare_test");

        List<DBDatabase> details = extension.listDetails(conn);
        Assert.assertEquals(2, details.size());
        Assert.assertEquals("default", details.get(0).getName());
        Assert.assertEquals("sqle_compare_test", details.get(1).getName());
    }

    @Test
    public void getDetail_returnsDbDatabaseWithName() {
        // getDetail does not query — it simply wraps the name. Pass a fake connection to confirm
        // there is no JDBC interaction (Mockito would record a call if any happened).
        Connection conn = Mockito.mock(Connection.class);
        DBDatabase db = extension.getDetail(conn, "default");
        Assert.assertEquals("default", db.getName());
        Mockito.verifyNoInteractions(conn);
    }

    @Test
    public void create_throwsUnsupportedOperationException() {
        // design.md §2.3 decision 1 — Hive ships read-only; backend backstop must throw UOE so the
        // global exception handler maps it to HTTP 400.
        Connection conn = Mockito.mock(Connection.class);
        DBDatabase db = DBDatabase.of("new_db");
        try {
            extension.create(conn, db, "password");
            Assert.fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            Assert.assertTrue(
                    "message should reference design contract, got: " + msg,
                    msg.contains("design 2.3 decision 1"));
        }
        Mockito.verifyNoInteractions(conn);
    }
}
