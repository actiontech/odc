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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oceanbase.tools.dbbrowser.editor.sqlserver.SqlServerSequenceEditor;
import com.oceanbase.tools.dbbrowser.model.DBSequence;

/**
 * @description: all tests for {@link SqlServerSequenceEditor}
 * @author: yizhou.xw
 * @date: 2024/12
 * @since: ODC_release_4.3.4
 */
public class SqlServerSequenceEditorTest {

    private SqlServerSequenceEditor sequenceEditor;

    @Before
    public void setUp() {
        sequenceEditor = new SqlServerSequenceEditor();
    }

    @Test
    public void generateCreateObjectDDL() {
        DBSequence sequence = new DBSequence();
        sequence.setName("seq1");
        sequence.setStartValue("1");
        sequence.setIncreament(1L);
        sequence.setMinValue("1");
        sequence.setMaxValue("100");
        sequence.setCycled(true);
        sequence.setCached(true);
        sequence.setCacheSize(20L);

        String ddl = sequenceEditor.generateCreateObjectDDL(sequence);
        Assert.assertEquals("CREATE SEQUENCE [seq1] START WITH 1 INCREMENT BY 1 MINVALUE 1 MAXVALUE 100 CYCLE CACHE 20",
                ddl);
    }

    @Test
    public void generateCreateDefinitionDDL() {
        DBSequence sequence = new DBSequence();
        sequence.setName("seq1");
        sequence.setMinValue(null);
        sequence.setMaxValue(null);
        sequence.setCached(false);

        String ddl = sequenceEditor.generateCreateDefinitionDDL(sequence);
        Assert.assertEquals("CREATE SEQUENCE [seq1] NO MINVALUE NO MAXVALUE NO CACHE", ddl);
    }

    @Test
    public void generateUpdateObjectDDL() {
        DBSequence oldSeq = new DBSequence();
        oldSeq.setName("seq1");
        oldSeq.setIncreament(1L);
        oldSeq.setMinValue("1");
        oldSeq.setMaxValue("100");
        oldSeq.setCycled(false);
        oldSeq.setCached(false);

        DBSequence newSeq = new DBSequence();
        newSeq.setName("seq1");
        newSeq.setIncreament(2L);
        newSeq.setMinValue("2");
        newSeq.setMaxValue("200");
        newSeq.setCycled(true);
        newSeq.setCached(true);
        newSeq.setCacheSize(50L);
        newSeq.setStartValue("10");

        String ddl = sequenceEditor.generateUpdateObjectDDL(oldSeq, newSeq);
        Assert.assertEquals(
                "ALTER SEQUENCE [seq1] INCREMENT BY 2 MINVALUE 2 MAXVALUE 200 CYCLE CACHE 50 RESTART WITH 10", ddl);
    }

    @Test
    public void generateDropObjectDDL() {
        DBSequence sequence = new DBSequence();
        sequence.setName("seq1");
        String ddl = sequenceEditor.generateDropObjectDDL(sequence);
        Assert.assertEquals("DROP SEQUENCE [seq1]", ddl);
    }

}
