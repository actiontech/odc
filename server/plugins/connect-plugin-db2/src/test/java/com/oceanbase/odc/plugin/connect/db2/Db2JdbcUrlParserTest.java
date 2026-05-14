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

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.odc.plugin.connect.api.HostAddress;

public class Db2JdbcUrlParserTest {

    // ---------- supports() ----------

    @Test
    public void supports_jdbcDb2_true() {
        Assert.assertTrue(Db2JdbcUrlParser.supports("jdbc:db2://h:50000/db"));
    }

    @Test
    public void supports_jdbcDb2_withParams_true() {
        Assert.assertTrue(Db2JdbcUrlParser.supports("jdbc:db2://h:50000/db:currentSchema=DB2INST1;"));
    }

    @Test
    public void supports_mysql_false() {
        Assert.assertFalse(Db2JdbcUrlParser.supports("jdbc:mysql://h:3306/db"));
    }

    @Test
    public void supports_postgres_false() {
        Assert.assertFalse(Db2JdbcUrlParser.supports("jdbc:postgresql://h:5432/db"));
    }

    @Test
    public void supports_oceanbase_false() {
        Assert.assertFalse(Db2JdbcUrlParser.supports("jdbc:oceanbase://h:2881/db"));
    }

    @Test
    public void supports_null_false() {
        Assert.assertFalse(Db2JdbcUrlParser.supports(null));
    }

    @Test
    public void supports_emptyString_false() {
        Assert.assertFalse(Db2JdbcUrlParser.supports(""));
    }

    // ---------- static parse() ----------

    @Test
    public void parse_basicUrl_returnsHostPort() {
        HostAddress ha = Db2JdbcUrlParser.parse("jdbc:db2://10.186.16.126:50000/testdb");
        Assert.assertNotNull(ha);
        Assert.assertEquals("10.186.16.126", ha.getHost());
        Assert.assertEquals(Integer.valueOf(50000), ha.getPort());
    }

    @Test
    public void parse_withParams_returnsHostPort() {
        HostAddress ha = Db2JdbcUrlParser.parse(
                "jdbc:db2://10.186.16.126:50000/testdb:currentSchema=FOO;sslConnection=true;");
        Assert.assertNotNull(ha);
        Assert.assertEquals("10.186.16.126", ha.getHost());
        Assert.assertEquals(Integer.valueOf(50000), ha.getPort());
    }

    @Test
    public void parse_nonDb2_returnsNull() {
        Assert.assertNull(Db2JdbcUrlParser.parse("jdbc:mysql://h:3306/db"));
    }

    @Test
    public void parseCatalog_basicUrl_returnsCatalog() {
        Assert.assertEquals("testdb", Db2JdbcUrlParser.parseCatalog("jdbc:db2://h:50000/testdb"));
    }

    @Test
    public void parseCatalog_withParams_returnsCatalog() {
        Assert.assertEquals("testdb",
                Db2JdbcUrlParser.parseCatalog("jdbc:db2://h:50000/testdb:k=v;"));
    }

    @Test
    public void parseCatalog_missingCatalog_returnsNull() {
        Assert.assertNull(Db2JdbcUrlParser.parseCatalog("jdbc:db2://h:50000"));
    }

    // ---------- instance contract: getHostAddresses / getSchema / getParameters ----------

    @Test
    public void instance_basicUrl_returnsHostList() throws SQLException {
        Db2JdbcUrlParser parser = new Db2JdbcUrlParser("jdbc:db2://h:50000/db");
        Assert.assertEquals(1, parser.getHostAddresses().size());
        Assert.assertEquals("h", parser.getHostAddresses().get(0).getHost());
        Assert.assertEquals(Integer.valueOf(50000), parser.getHostAddresses().get(0).getPort());
        Assert.assertTrue(parser.getParameters().isEmpty());
        Assert.assertNull(parser.getSchema());
    }

    @Test
    public void instance_withCurrentSchema_schemaIsPropagated() throws SQLException {
        Db2JdbcUrlParser parser = new Db2JdbcUrlParser(
                "jdbc:db2://h:50000/db:currentSchema=DB2INST1;sslConnection=true;");
        Assert.assertEquals("DB2INST1", parser.getSchema());
        Map<String, Object> params = parser.getParameters();
        Assert.assertEquals("DB2INST1", params.get("currentSchema"));
        Assert.assertEquals("true", params.get("sslConnection"));
    }

    @Test
    public void instance_missingPort_portIsNull() throws SQLException {
        Db2JdbcUrlParser parser = new Db2JdbcUrlParser("jdbc:db2://h/db");
        HostAddress ha = parser.getHostAddresses().get(0);
        Assert.assertEquals("h", ha.getHost());
        Assert.assertNull(ha.getPort());
    }

    @Test(expected = IllegalArgumentException.class)
    public void instance_nonDb2Url_throwIllegalArgument() throws SQLException {
        new Db2JdbcUrlParser("jdbc:mysql://h:3306/db");
    }

    /**
     * Map case 矩阵：覆盖 supports / parseCatalog / 实例化的关键场景。
     */
    @Test
    public void mapCase_supports_matrix() {
        Map<String, Boolean> cases = new LinkedHashMap<>();
        cases.put("jdbc:db2://10.0.0.1:50000/testdb", true);
        cases.put("jdbc:db2://10.0.0.1:50000/testdb:currentSchema=A;", true);
        cases.put("jdbc:db2://10.0.0.1/testdb", true);
        cases.put("jdbc:mysql://10.0.0.1:3306/db", false);
        cases.put("jdbc:postgresql://10.0.0.1:5432/db", false);
        cases.put("jdbc:sqlserver://10.0.0.1:1433;databaseName=db", false);
        cases.put("jdbc:oceanbase://10.0.0.1:2881/db", false);
        cases.put("", false);
        cases.put(null, false);
        for (Map.Entry<String, Boolean> entry : cases.entrySet()) {
            Assert.assertEquals("supports() for " + entry.getKey(),
                    entry.getValue(), Db2JdbcUrlParser.supports(entry.getKey()));
        }
    }
}
