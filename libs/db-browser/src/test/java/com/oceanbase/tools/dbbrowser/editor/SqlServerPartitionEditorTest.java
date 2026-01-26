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
package com.oceanbase.tools.dbbrowser.editor;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oceanbase.tools.dbbrowser.editor.sqlserver.SqlServerPartitionEditor;
import com.oceanbase.tools.dbbrowser.model.DBTablePartition;
import com.oceanbase.tools.dbbrowser.model.DBTablePartitionDefinition;
import com.oceanbase.tools.dbbrowser.model.DBTablePartitionOption;

/**
 * @description: all tests for {@link SqlServerPartitionEditor}
 * @author: yizhou.xw
 * @date: 2024/12
 * @since: ODC_release_4.3.4
 */
public class SqlServerPartitionEditorTest {

    private DBTablePartitionEditor partitionEditor;

    @Before
    public void setUp() {
        partitionEditor = new SqlServerPartitionEditor();
    }

    @Test
    public void generateCreateObjectDDL() {
        DBTablePartition partition = new DBTablePartition();
        String ddl = partitionEditor.generateCreateObjectDDL(partition);
        Assert.assertEquals("", ddl);
    }

    @Test
    public void generateCreateDefinitionDDL() {
        DBTablePartition partition = new DBTablePartition();
        DBTablePartitionOption option = new DBTablePartitionOption();
        option.setExpression("myScheme");
        option.setColumnNames(Arrays.asList("id"));
        partition.setPartitionOption(option);

        String ddl = partitionEditor.generateCreateDefinitionDDL(partition);
        Assert.assertEquals(" ON myScheme([id])", ddl);
    }

    @Test
    public void generateDropObjectDDL() {
        DBTablePartition partition = new DBTablePartition();
        String ddl = partitionEditor.generateDropObjectDDL(partition);
        Assert.assertEquals("", ddl);
    }

    @Test
    public void generateUpdateSinglePartitionDDL() {
        DBTablePartition oldPartition = new DBTablePartition();
        oldPartition.setPartitionDefinitions(Collections.emptyList());

        DBTablePartition newPartition = new DBTablePartition();
        DBTablePartitionOption option = new DBTablePartitionOption();
        option.setExpression("myFunc");
        newPartition.setPartitionOption(option);
        newPartition.setPartitionDefinitions(Arrays.asList(new DBTablePartitionDefinition()));

        String ddl = partitionEditor.generateUpdateObjectDDL(oldPartition, newPartition);
        Assert.assertEquals("-- ALTER PARTITION FUNCTION myFunc() SPLIT RANGE (new_value);\n", ddl);
    }

    @Test
    public void generateUpdatePartitionListDDL() {
        String ddl = partitionEditor.generateUpdateObjectListDDL(Collections.emptyList(), Collections.emptyList());
        Assert.assertEquals("", ddl);
    }

    @Test
    public void generateAddPartitionDefinitionDDL_withDefinition() {
        DBTablePartitionOption option = new DBTablePartitionOption();
        option.setExpression("myFunc");
        DBTablePartitionDefinition definition = new DBTablePartitionDefinition();
        definition.setMaxValues(Arrays.asList("100"));

        String ddl = partitionEditor.generateAddPartitionDefinitionDDL(definition, option, "table1");
        Assert.assertEquals("-- ALTER PARTITION FUNCTION myFunc() SPLIT RANGE (100);\n", ddl);
    }

    @Test
    public void generateAddPartitionDefinitionDDL_withList() {
        DBTablePartitionOption option = new DBTablePartitionOption();
        option.setExpression("myFunc");
        DBTablePartitionDefinition definition = new DBTablePartitionDefinition();
        definition.setMaxValues(Arrays.asList("200"));

        String ddl = partitionEditor.generateAddPartitionDefinitionDDL("dbo", "table1", option,
                Arrays.asList(definition));
        Assert.assertEquals("-- ALTER PARTITION FUNCTION myFunc() SPLIT RANGE (200);\n", ddl);
    }

}
