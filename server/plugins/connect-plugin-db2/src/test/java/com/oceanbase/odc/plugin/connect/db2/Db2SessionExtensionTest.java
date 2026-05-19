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
package com.oceanbase.odc.plugin.connect.db2;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.Assert;
import org.junit.Test;

/**
 * Map-case unit tests for {@link Db2SessionExtension}. All JDBC interactions are mocked — no real
 * DB2 connection (R-14). The three-level fallback contract from design.md §2.3 / §7.2 / §11.1 R-03
 * is the spec under test.
 *
 * @author actiontech-zihan
 * @since 4.3.4
 */
public class Db2SessionExtensionTest {

    private final Db2SessionExtension extension = new Db2SessionExtension();

    // ---------- getConnectionId 三级降级 ----------

    @Test
    public void getConnectionId_level1_applicationId() throws SQLException {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("VALUES APPLICATION_ID()")).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getString(1)).thenReturn("*LOCAL.DB2.230101000000");

        Assert.assertEquals("*LOCAL.DB2.230101000000", extension.getConnectionId(connection));
    }

    @Test
    public void getConnectionId_level2_applicationHandle_whenLevel1Blank() throws SQLException {
        Connection connection = mock(Connection.class);
        Statement stmt1 = mock(Statement.class);
        Statement stmt2 = mock(Statement.class);
        ResultSet rs1 = mock(ResultSet.class);
        ResultSet rs2 = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(stmt1, stmt2);
        when(stmt1.executeQuery("VALUES APPLICATION_ID()")).thenReturn(rs1);
        when(rs1.next()).thenReturn(true);
        when(rs1.getString(1)).thenReturn("   ");
        when(stmt2.executeQuery(anyString())).thenReturn(rs2);
        when(rs2.next()).thenReturn(true);
        when(rs2.getString(1)).thenReturn("12345");

        Assert.assertEquals("12345", extension.getConnectionId(connection));
    }

    @Test
    public void getConnectionId_level2_applicationHandle_whenLevel1Throws() throws SQLException {
        Connection connection = mock(Connection.class);
        Statement stmt1 = mock(Statement.class);
        Statement stmt2 = mock(Statement.class);
        ResultSet rs2 = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(stmt1, stmt2);
        when(stmt1.executeQuery("VALUES APPLICATION_ID()"))
                .thenThrow(new SQLException("APPLICATION_ID() not supported"));
        when(stmt2.executeQuery(anyString())).thenReturn(rs2);
        when(rs2.next()).thenReturn(true);
        when(rs2.getString(1)).thenReturn("99");

        Assert.assertEquals("99", extension.getConnectionId(connection));
    }

    @Test
    public void getConnectionId_level3_hashCodeFallback_whenBothThrow() throws SQLException {
        Connection connection = mock(Connection.class);
        Statement stmt1 = mock(Statement.class);
        Statement stmt2 = mock(Statement.class);
        when(connection.createStatement()).thenReturn(stmt1, stmt2);
        when(stmt1.executeQuery(anyString())).thenThrow(new SQLException("L1 down"));
        when(stmt2.executeQuery(anyString())).thenThrow(new SQLException("L2 down"));

        String id = extension.getConnectionId(connection);
        Assert.assertNotNull("level3 must never return null", id);
        Assert.assertFalse("level3 must never return blank", id.trim().isEmpty());
        Assert.assertEquals(Integer.toHexString(connection.hashCode()), id);
    }

    @Test
    public void getConnectionId_level3_hashCodeFallback_whenBothBlank() throws SQLException {
        Connection connection = mock(Connection.class);
        Statement stmt1 = mock(Statement.class);
        Statement stmt2 = mock(Statement.class);
        ResultSet rs1 = mock(ResultSet.class);
        ResultSet rs2 = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(stmt1, stmt2);
        when(stmt1.executeQuery(anyString())).thenReturn(rs1);
        when(rs1.next()).thenReturn(true);
        when(rs1.getString(1)).thenReturn(null);
        when(stmt2.executeQuery(anyString())).thenReturn(rs2);
        when(rs2.next()).thenReturn(true);
        when(rs2.getString(1)).thenReturn("");

        String id = extension.getConnectionId(connection);
        Assert.assertNotNull(id);
        Assert.assertFalse(id.trim().isEmpty());
        Assert.assertEquals(Integer.toHexString(connection.hashCode()), id);
    }

    @Test
    public void getConnectionId_nullConnection_returnsNonBlankSentinel() {
        String id = extension.getConnectionId(null);
        Assert.assertNotNull(id);
        Assert.assertFalse(id.isEmpty());
        Assert.assertEquals(Integer.toHexString(0), id);
    }

    // ---------- getCurrentSchema ----------

    @Test
    public void getCurrentSchema_trimsAndReturns() throws SQLException {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("VALUES CURRENT SCHEMA")).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getString(1)).thenReturn("  DB2INST1  ");

        Assert.assertEquals("DB2INST1", extension.getCurrentSchema(connection));
    }

    @Test
    public void getCurrentSchema_emptyResultSet_returnsNull() throws SQLException {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet rs = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("VALUES CURRENT SCHEMA")).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        Assert.assertNull(extension.getCurrentSchema(connection));
    }

    @Test
    public void getCurrentSchema_sqlException_returnsNull() throws SQLException {
        Connection connection = mock(Connection.class);
        when(connection.createStatement()).thenThrow(new SQLException("boom"));
        Assert.assertNull(extension.getCurrentSchema(connection));
    }

    // ---------- getKillSessionSql / getKillQuerySql ----------

    @Test
    public void getKillSessionSql_exactTemplate() {
        Assert.assertEquals(
                "CALL SYSPROC.ADMIN_CMD('FORCE APPLICATION (123)')",
                extension.getKillSessionSql("123"));
        Assert.assertEquals(
                "CALL SYSPROC.ADMIN_CMD('FORCE APPLICATION (*LOCAL.DB2.230101000000)')",
                extension.getKillSessionSql("*LOCAL.DB2.230101000000"));
    }

    @Test
    public void getKillQuerySql_delegatesToKillSessionSql() {
        Assert.assertEquals(
                extension.getKillSessionSql("42"),
                extension.getKillQuerySql("42"));
    }

    // ---------- setClientInfo ----------

    @Test
    public void setClientInfo_returnsFalse() {
        Assert.assertFalse(extension.setClientInfo(null, null));
    }
}
