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
package com.oceanbase.odc.plugin.connect.hive;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.oceanbase.odc.plugin.connect.api.HostAddress;
import com.oceanbase.odc.plugin.connect.model.JdbcUrlProperty;

/**
 * Map-case unit tests for {@link HiveJdbcUrlParser} covering jdbc URL build / parse paths and the
 * edge cases listed in plan.md §DEV-O08 (auth=NOSASL, transport_mode=binary, service missing,
 * multiple KV combinations).
 *
 * <p>
 * The two paths share the same parameter table object — each entry declares both the input
 * fragments and the expected serialized URL / parsed components — so missing branches are easy to
 * spot in code review.
 */
public class HiveJdbcUrlParserTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    // ---------- build path (JdbcUrlProperty -> jdbc url string) ----------

    /**
     * Case table for {@link HiveJdbcUrlParser#build(JdbcUrlProperty)}. Each row encodes one logical
     * branch (no schema / with schema / with kv / multi-kv / null-value kv skipped).
     */
    private static class BuildCase {
        final String name;
        final String host;
        final Integer port;
        final String defaultSchema;
        final LinkedHashMap<String, String> kv;
        final String expectedUrl;

        BuildCase(String name, String host, Integer port, String defaultSchema,
                LinkedHashMap<String, String> kv, String expectedUrl) {
            this.name = name;
            this.host = host;
            this.port = port;
            this.defaultSchema = defaultSchema;
            this.kv = kv;
            this.expectedUrl = expectedUrl;
        }
    }

    private static LinkedHashMap<String, String> kv(String... pairs) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    @Test
    public void build_mapCaseTable() {
        List<BuildCase> cases = Arrays.asList(
                new BuildCase("base_hostPortOnly", "10.0.0.1", 10000, null, null,
                        "jdbc:hive2://10.0.0.1:10000"),
                new BuildCase("base_hostPortEmptySchema", "10.0.0.1", 10000, "", null,
                        "jdbc:hive2://10.0.0.1:10000"),
                new BuildCase("withSchema", "10.0.0.1", 10000, "default", null,
                        "jdbc:hive2://10.0.0.1:10000/default"),
                new BuildCase("withAuthNosasl", "10.0.0.1", 10000, "default", kv("auth", "NOSASL"),
                        "jdbc:hive2://10.0.0.1:10000/default;auth=NOSASL"),
                new BuildCase("withTransportBinary", "10.0.0.1", 10000, "ods",
                        kv("transport_mode", "binary"),
                        "jdbc:hive2://10.0.0.1:10000/ods;transport_mode=binary"),
                new BuildCase("withMultipleKv", "10.0.0.1", 10000, "ods",
                        kv("auth", "NOSASL", "transport_mode", "binary"),
                        "jdbc:hive2://10.0.0.1:10000/ods;auth=NOSASL;transport_mode=binary"),
                // service / httpPath missing — verifies absence does not insert anything
                new BuildCase("noService", "10.0.0.1", 10000, "ods", kv("auth", "LDAP"),
                        "jdbc:hive2://10.0.0.1:10000/ods;auth=LDAP"),
                // null value entries silently skipped (current contract)
                new BuildCase("nullValueSkipped", "10.0.0.1", 10000, "ods",
                        kv("auth", "NOSASL", "service", null /* will be skipped */),
                        "jdbc:hive2://10.0.0.1:10000/ods;auth=NOSASL"));

        for (BuildCase c : cases) {
            JdbcUrlProperty p = new JdbcUrlProperty(c.host, c.port, c.defaultSchema, c.kv);
            String actual = new HiveJdbcUrlParser().build(p);
            Assert.assertEquals("case " + c.name, c.expectedUrl, actual);
        }
    }

    @Test
    public void build_emptyHost_throwsIllegalArgument() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("host can not be empty");
        new HiveJdbcUrlParser().build(new JdbcUrlProperty("", 10000, "default", new HashMap<>()));
    }

    // ---------- parse path (jdbc url string -> components) ----------

    @Test
    public void parse_hostPort_noSchema_noKv() {
        HiveJdbcUrlParser p = new HiveJdbcUrlParser("jdbc:hive2://10.0.0.1:10000");
        List<HostAddress> addrs = p.getHostAddresses();
        Assert.assertEquals(1, addrs.size());
        Assert.assertEquals("10.0.0.1", addrs.get(0).getHost());
        Assert.assertEquals(Integer.valueOf(10000), addrs.get(0).getPort());
        Assert.assertEquals("", p.getSchema());
        Assert.assertTrue(p.getParameters().isEmpty());
    }

    @Test
    public void parse_hostPortDb() {
        HiveJdbcUrlParser p = new HiveJdbcUrlParser("jdbc:hive2://10.0.0.1:10000/default");
        Assert.assertEquals("default", p.getSchema());
    }

    @Test
    public void parse_hostPortDbWithKv() {
        HiveJdbcUrlParser p = new HiveJdbcUrlParser(
                "jdbc:hive2://10.0.0.1:10000/ods;auth=NOSASL;transport_mode=binary");
        Assert.assertEquals("ods", p.getSchema());
        Map<String, Object> params = p.getParameters();
        Assert.assertEquals("NOSASL", params.get("auth"));
        Assert.assertEquals("binary", params.get("transport_mode"));
    }

    @Test
    public void parse_emptyKvSegment_isIgnored() {
        // ";;" is a malformed segment but must not crash
        HiveJdbcUrlParser p = new HiveJdbcUrlParser("jdbc:hive2://h:1;;auth=NOSASL");
        Assert.assertEquals("NOSASL", p.getParameters().get("auth"));
    }

    @Test
    public void parse_invalidScheme_throws() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Invalid Hive jdbc url");
        new HiveJdbcUrlParser("jdbc:mysql://10.0.0.1:10000/default");
    }

    @Test
    public void parse_missingPort_throws() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Invalid Hive jdbc url host:port");
        new HiveJdbcUrlParser("jdbc:hive2://10.0.0.1");
    }

    @Test
    public void parse_emptyPort_throws() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Invalid Hive jdbc url host:port");
        new HiveJdbcUrlParser("jdbc:hive2://10.0.0.1:");
    }

    @Test
    public void parse_nonNumericPort_throws() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Invalid Hive jdbc url port");
        new HiveJdbcUrlParser("jdbc:hive2://10.0.0.1:abcd");
    }

    @Test
    public void getParameters_returnsDefensiveCopy() {
        HiveJdbcUrlParser p = new HiveJdbcUrlParser("jdbc:hive2://h:1/db;k=v");
        Map<String, Object> snap1 = p.getParameters();
        snap1.put("injected", "x");
        Map<String, Object> snap2 = p.getParameters();
        Assert.assertFalse("getParameters() must return a defensive copy",
                snap2.containsKey("injected"));
    }
}
