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
package com.oceanbase.tools.dbbrowser.schema;

import static com.oceanbase.tools.dbbrowser.editor.DBObjectUtilsTest.loadAsString;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.oceanbase.tools.dbbrowser.env.BaseTestEnv;
import com.oceanbase.tools.dbbrowser.model.DBPLObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBTrigger;
import com.oceanbase.tools.dbbrowser.util.DBSchemaAccessors;
import com.oceanbase.tools.dbbrowser.util.StringUtils;

/**
 * MySQL trigger list/detail accessor tests.
 */
public class MySQLSchemaAccessorTriggerTest extends BaseTestEnv {

    private static final String BASE_PATH = "src/test/resources/table/mysql/";
    private static String testTriggerDDL;
    private static String dropTables;
    private static JdbcTemplate jdbcTemplate = new JdbcTemplate(getMySQLDataSource());
    private static DBSchemaAccessor accessor = new DBSchemaAccessors(getMySQLDataSource()).createMysql();

    @BeforeClass
    public static void setUp() throws Exception {
        dropTables = loadAsString(BASE_PATH + "drop.sql");
        batchExecuteSql(dropTables, ";");
        testTriggerDDL = loadAsString(BASE_PATH + "testTriggerDDL.sql");
        batchExecuteSql(testTriggerDDL, ";");
    }

    @AfterClass
    public static void tearDown() {
        batchExecuteSql(dropTables, ";");
    }

    private static void batchExecuteSql(String str, String delimiter) {
        for (String ddl : Arrays.stream(str.split(delimiter)).filter(item -> StringUtils.isNotBlank(item))
                .collect(Collectors.toList())) {
            jdbcTemplate.execute(ddl);
        }
    }

    @Test
    public void listTriggers_Success() {
        List<DBPLObjectIdentity> triggers = accessor.listTriggers(getMySQLDataBaseName());
        Assert.assertEquals(2, triggers.size());
        for (DBPLObjectIdentity trigger : triggers) {
            Assert.assertTrue(trigger.getEnable());
            Assert.assertEquals("VALID", trigger.getStatus());
        }
    }

    @Test
    public void listTriggers_EmptySchema() {
        jdbcTemplate.execute("CREATE DATABASE IF NOT EXISTS `odc_trigger_empty_test`");
        try {
            List<DBPLObjectIdentity> triggers = accessor.listTriggers("odc_trigger_empty_test");
            Assert.assertTrue(triggers.isEmpty());
        } finally {
            jdbcTemplate.execute("DROP DATABASE IF EXISTS `odc_trigger_empty_test`");
        }
    }

    @Test
    public void getTrigger_Success() {
        DBTrigger trigger = accessor.getTrigger(getMySQLDataBaseName(), "test_trigger_bi");
        Assert.assertNotNull(trigger.getDdl());
        Assert.assertTrue(StringUtils.containsIgnoreCase(trigger.getDdl(), "CREATE TRIGGER"));
        Assert.assertTrue(StringUtils.containsIgnoreCase(trigger.getDdl(), "BEFORE INSERT"));
        Assert.assertEquals("test_trigger_table", trigger.getSchemaName());
        Assert.assertTrue(trigger.isEnable());
    }

    @Test
    public void getTrigger_NotExists() {
        DBTrigger trigger = accessor.getTrigger(getMySQLDataBaseName(), "not_exists_trigger");
        Assert.assertNull(trigger.getDdl());
    }

    @Test
    public void listTriggers_SpecialSchemaName() {
        jdbcTemplate.execute("CREATE DATABASE IF NOT EXISTS `odc-trig`");
        try {
            jdbcTemplate.execute("USE `odc-trig`");
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS `t` (`id` INT PRIMARY KEY)");
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS `tr`");
            jdbcTemplate.execute(
                    "CREATE TRIGGER `tr` BEFORE INSERT ON `t` FOR EACH ROW SET @x = 1");
            List<DBPLObjectIdentity> triggers = accessor.listTriggers("odc-trig");
            Assert.assertEquals(1, triggers.size());
            Assert.assertEquals("tr", triggers.get(0).getName());
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS `odc-trig`.`tr`");
            jdbcTemplate.execute("DROP DATABASE IF EXISTS `odc-trig`");
        }
    }
}
