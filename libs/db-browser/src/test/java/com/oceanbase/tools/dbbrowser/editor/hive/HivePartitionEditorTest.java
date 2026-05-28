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
package com.oceanbase.tools.dbbrowser.editor.hive;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oceanbase.tools.dbbrowser.model.DBTablePartition;
import com.oceanbase.tools.dbbrowser.model.DBTablePartitionDefinition;
import com.oceanbase.tools.dbbrowser.model.DBTablePartitionOption;
import com.oceanbase.tools.dbbrowser.model.DBTablePartitionType;

/**
 * Tests for {@link HivePartitionEditor} covering design.md 5.2.5 scenarios.
 */
public class HivePartitionEditorTest {

    private HivePartitionEditor partitionEditor;

    @Before
    public void setUp() {
        partitionEditor = new HivePartitionEditor();
    }

    @Test
    public void testAddSinglePartition() {
        DBTablePartition partition = createPartition("test_db", "events");
        DBTablePartitionDefinition def = new DBTablePartitionDefinition();
        def.setName("dt='2024-01-01'");
        def.setType(DBTablePartitionType.LIST);
        partition.setPartitionDefinitions(Collections.singletonList(def));

        String ddl = partitionEditor.generateCreateObjectDDL(partition);
        Assert.assertEquals(
                "ALTER TABLE `test_db`.`events` ADD PARTITION (dt='2024-01-01');\n",
                ddl);
    }

    @Test
    public void testAddMultiplePartitions() {
        DBTablePartition partition = createPartition("test_db", "events");
        DBTablePartitionDefinition def1 = new DBTablePartitionDefinition();
        def1.setName("dt='2024-01-01'");
        def1.setType(DBTablePartitionType.LIST);
        DBTablePartitionDefinition def2 = new DBTablePartitionDefinition();
        def2.setName("dt='2024-01-02'");
        def2.setType(DBTablePartitionType.LIST);
        partition.setPartitionDefinitions(Arrays.asList(def1, def2));

        String ddl = partitionEditor.generateCreateObjectDDL(partition);
        Assert.assertEquals(
                "ALTER TABLE `test_db`.`events` ADD PARTITION (dt='2024-01-01');\n"
                        + "ALTER TABLE `test_db`.`events` ADD PARTITION (dt='2024-01-02');\n",
                ddl);
    }

    @Test
    public void testDropSinglePartition() {
        DBTablePartition partition = createPartition("test_db", "events");
        DBTablePartitionDefinition def = new DBTablePartitionDefinition();
        def.setName("dt='2024-01-01'");
        def.setType(DBTablePartitionType.LIST);
        partition.setPartitionDefinitions(Collections.singletonList(def));

        String ddl = partitionEditor.generateDropObjectDDL(partition);
        Assert.assertEquals(
                "ALTER TABLE `test_db`.`events` DROP PARTITION (dt='2024-01-01');\n",
                ddl);
    }

    @Test
    public void testDropPartitionDefinition() {
        DBTablePartitionDefinition def = new DBTablePartitionDefinition();
        def.setName("dt='2024-03-15'");
        def.setType(DBTablePartitionType.LIST);

        String ddl = partitionEditor.generateDropPartitionDefinitionDDL(def, "`test_db`.`events`");
        Assert.assertEquals(
                "ALTER TABLE `test_db`.`events` DROP PARTITION (dt='2024-03-15');\n",
                ddl);
    }

    @Test
    public void testAddPartitionDefinition() {
        DBTablePartitionDefinition def = new DBTablePartitionDefinition();
        def.setName("dt='2024-06-01',region='us'");
        def.setType(DBTablePartitionType.LIST);
        DBTablePartitionOption option = new DBTablePartitionOption();
        option.setType(DBTablePartitionType.LIST);

        String ddl = partitionEditor.generateAddPartitionDefinitionDDL(def, option, "`test_db`.`events`");
        Assert.assertEquals(
                "ALTER TABLE `test_db`.`events` ADD PARTITION (dt='2024-06-01',region='us');\n",
                ddl);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testModifyPartitionTypeUnsupported() {
        DBTablePartition oldPartition = createPartition("test_db", "events");
        DBTablePartition newPartition = createPartition("test_db", "events");
        oldPartition.getPartitionOption().setType(DBTablePartitionType.LIST);
        newPartition.getPartitionOption().setType(DBTablePartitionType.HASH);

        partitionEditor.generateUpdateObjectDDL(oldPartition, newPartition);
    }

    private static DBTablePartition createPartition(String schema, String table) {
        DBTablePartition partition = new DBTablePartition();
        partition.setSchemaName(schema);
        partition.setTableName(table);
        DBTablePartitionOption option = new DBTablePartitionOption();
        option.setType(DBTablePartitionType.LIST);
        partition.setPartitionOption(option);
        return partition;
    }

}
