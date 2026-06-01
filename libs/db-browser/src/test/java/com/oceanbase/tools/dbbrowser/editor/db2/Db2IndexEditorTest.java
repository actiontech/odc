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
package com.oceanbase.tools.dbbrowser.editor.db2;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.tools.dbbrowser.model.DBIndexType;
import com.oceanbase.tools.dbbrowser.model.DBTableIndex;

/**
 * Mock-only unit tests for {@link Db2IndexEditor}.
 *
 * <p>
 * fix_report_20260601_031142 (Issue dms-ee#839, P0-2B) regression cases — focus on the null / empty
 * columnNames defence that prevents the workbench's "POST generateUpdateTableDDL HTTP 400/500
 * message=null" when DBTableIndexEditor.generateUpdateObjectListDDL routes a sparse DBTableIndex
 * into generateCreateObjectDDL.
 *
 * @since ODC_release_4.3.4 (Issue dms-ee#839)
 */
public class Db2IndexEditorTest {

    private final Db2IndexEditor editor = new Db2IndexEditor();

    /**
     * P0-2B: passing an index whose columnNames is null must not NPE; the editor returns an empty
     * string so DBTableIndexEditor.generateUpdateObjectListDDL concatenation continues with the other
     * indexes.
     */
    @Test
    public void generateCreateObjectDDL_nullColumnNames_returnsEmpty() {
        DBTableIndex index = new DBTableIndex();
        index.setSchemaName("DB2INST1");
        index.setName("PK_ORDERS");
        index.setTableName("ORDERS");
        index.setType(DBIndexType.UNIQUE);
        // columnNames intentionally left null — mirrors the pre-P0-2A defect path.

        String ddl = editor.generateCreateObjectDDL(index);

        Assert.assertEquals("null columnNames must short-circuit, not NPE", "", ddl);
    }

    /**
     * P0-2B: empty columnNames should produce an empty string for the same reason.
     */
    @Test
    public void generateCreateObjectDDL_emptyColumnNames_returnsEmpty() {
        DBTableIndex index = new DBTableIndex();
        index.setSchemaName("DB2INST1");
        index.setName("PK_ORDERS");
        index.setTableName("ORDERS");
        index.setType(DBIndexType.UNIQUE);
        index.setColumnNames(Collections.emptyList());

        String ddl = editor.generateCreateObjectDDL(index);

        Assert.assertEquals("empty columnNames must short-circuit", "", ddl);
    }

    /**
     * P0-2B positive path: a populated columnNames must still produce the
     * {@code CREATE [UNIQUE] INDEX "schema"."idx" ON "schema"."table" ("col1", "col2");} grammar.
     */
    @Test
    public void generateCreateObjectDDL_uniqueIndex_emitsDb2Grammar() {
        DBTableIndex index = new DBTableIndex();
        index.setSchemaName("DB2INST1");
        index.setName("PK_ORDERS");
        index.setTableName("ORDERS");
        index.setType(DBIndexType.UNIQUE);
        index.setColumnNames(Arrays.asList("ID", "ORDER_NO"));

        String ddl = editor.generateCreateObjectDDL(index);

        Assert.assertTrue("must start with CREATE UNIQUE INDEX", ddl.startsWith("CREATE UNIQUE INDEX "));
        Assert.assertTrue("must double-quote the index name", ddl.contains("\"DB2INST1\".\"PK_ORDERS\""));
        Assert.assertTrue("must reference the target table", ddl.contains(" ON \"DB2INST1\".\"ORDERS\""));
        Assert.assertTrue("must list the columns inside parens", ddl.contains("(\"ID\", \"ORDER_NO\")"));
        Assert.assertTrue("must terminate with semicolon + newline", ddl.endsWith(";\n"));
    }

    /**
     * P0-2B positive path: a NORMAL index drops the UNIQUE keyword.
     */
    @Test
    public void generateCreateObjectDDL_normalIndex_omitsUniqueKeyword() {
        DBTableIndex index = new DBTableIndex();
        index.setSchemaName("DB2INST1");
        index.setName("IDX_ORDERS_DATE");
        index.setTableName("ORDERS");
        index.setType(DBIndexType.NORMAL);
        index.setColumnNames(Collections.singletonList("ORDER_DATE"));

        String ddl = editor.generateCreateObjectDDL(index);

        Assert.assertTrue("non-unique index must start with CREATE INDEX", ddl.startsWith("CREATE INDEX "));
        Assert.assertFalse("non-unique index must not contain UNIQUE keyword", ddl.contains("UNIQUE"));
    }
}
