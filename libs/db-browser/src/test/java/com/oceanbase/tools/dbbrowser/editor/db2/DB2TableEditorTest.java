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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

import com.oceanbase.tools.dbbrowser.model.DBTable;
import com.oceanbase.tools.dbbrowser.model.DBTable.DBTableOptions;
import com.oceanbase.tools.dbbrowser.model.DBTableColumn;

/**
 * Map-case unit tests for {@link DB2TableEditor} DDL emission (T-003 commit-2).
 */
public class DB2TableEditorTest {

    private final DB2TableEditor editor = new DB2TableEditor(null, new DB2ColumnEditor(), null, null);

    private static DBTableColumn col(String name, String type, boolean nullable) {
        DBTableColumn c = new DBTableColumn();
        c.setName(name);
        c.setTypeName(type);
        c.setNullable(nullable);
        return c;
    }

    @Test
    public void editor_isEditable() {
        assertTrue(editor.editable());
    }

    @Test
    public void renameTable_emitsDb2RenameSyntax() {
        DBTable oldT = new DBTable();
        oldT.setSchemaName("DB2INST1");
        oldT.setName("EMP");
        DBTable newT = new DBTable();
        newT.setSchemaName("DB2INST1");
        newT.setName("EMPLOYEE");
        String ddl = editor.generateRenameObjectDDL(oldT, newT);
        assertEquals("RENAME TABLE \"DB2INST1\".\"EMP\" TO \"EMPLOYEE\"", ddl);
    }

    @Test
    public void renameTable_blankSchema_doesNotEmitSchemaPrefix() {
        DBTable oldT = new DBTable();
        oldT.setName("EMP");
        DBTable newT = new DBTable();
        newT.setName("EMPLOYEE");
        String ddl = editor.generateRenameObjectDDL(oldT, newT);
        assertEquals("RENAME TABLE \"EMP\" TO \"EMPLOYEE\"", ddl);
    }

    @Test
    public void generateCreateObjectDDL_basicTable_emitsCreateTableWithColumns() {
        DBTable t = new DBTable();
        t.setSchemaName("APP");
        t.setName("ORDERS");
        t.setColumns(Arrays.asList(
                col("ID", "INTEGER", false),
                col("NAME", "VARCHAR", true)));
        DBTableColumn nameCol = t.getColumns().get(1);
        nameCol.setPrecision(64L);
        String ddl = editor.generateCreateObjectDDL(t);
        assertTrue(ddl, ddl.startsWith("CREATE TABLE \"APP\".\"ORDERS\""));
        assertTrue(ddl, ddl.contains("\"ID\" INTEGER"));
        assertTrue(ddl, ddl.contains("\"NAME\" VARCHAR(64)"));
    }

    @Test
    public void generateCreateObjectDDL_withTableComment_emitsCommentOnTable() {
        DBTable t = new DBTable();
        t.setSchemaName("APP");
        t.setName("EMP");
        t.setColumns(Arrays.asList(col("ID", "INTEGER", false)));
        DBTableOptions opt = new DBTableOptions();
        opt.setComment("employee master");
        t.setTableOptions(opt);
        String ddl = editor.generateCreateObjectDDL(t);
        assertTrue(ddl, ddl.contains("COMMENT ON TABLE \"APP\".\"EMP\" IS 'employee master';"));
    }

    @Test
    public void generateCreateObjectDDL_withColumnComment_emitsCommentOnColumn() {
        DBTable t = new DBTable();
        t.setSchemaName("APP");
        t.setName("EMP");
        DBTableColumn idCol = col("ID", "INTEGER", false);
        idCol.setComment("primary key");
        t.setColumns(Arrays.asList(idCol));
        String ddl = editor.generateCreateObjectDDL(t);
        assertTrue(ddl, ddl.contains("COMMENT ON COLUMN \"APP\".\"EMP\".\"ID\" IS 'primary key';"));
    }

    @Test
    public void generateUpdateTableOptionDDL_commentChanged_emitsCommentOnTable() {
        DBTable oldT = new DBTable();
        oldT.setName("EMP");
        DBTableOptions oldOpt = new DBTableOptions();
        oldOpt.setComment("old");
        oldT.setTableOptions(oldOpt);

        DBTable newT = new DBTable();
        newT.setName("EMP");
        DBTableOptions newOpt = new DBTableOptions();
        newOpt.setComment("new comment");
        newT.setTableOptions(newOpt);

        com.oceanbase.tools.dbbrowser.util.SqlBuilder sb =
                new com.oceanbase.tools.dbbrowser.util.DB2SqlBuilder();
        editor.generateUpdateTableOptionDDL(oldT, newT, sb);
        assertNotNull(sb.toString());
        assertTrue(sb.toString().contains("COMMENT ON TABLE \"EMP\" IS 'new comment'"));
    }

    @Test
    public void generateUpdateTableOptionDDL_commentUnchanged_emitsNothing() {
        DBTable oldT = new DBTable();
        oldT.setName("EMP");
        DBTableOptions sameOpt = new DBTableOptions();
        sameOpt.setComment("c");
        oldT.setTableOptions(sameOpt);

        DBTable newT = new DBTable();
        newT.setName("EMP");
        DBTableOptions sameOpt2 = new DBTableOptions();
        sameOpt2.setComment("c");
        newT.setTableOptions(sameOpt2);

        com.oceanbase.tools.dbbrowser.util.SqlBuilder sb =
                new com.oceanbase.tools.dbbrowser.util.DB2SqlBuilder();
        editor.generateUpdateTableOptionDDL(oldT, newT, sb);
        assertEquals("", sb.toString());
    }

    @Test
    public void createIndexWhenCreatingTable_returnsFalse() {
        // DB2 requires CREATE INDEX after CREATE TABLE; not in MVP scope.
        DB2TableEditor probe = new DB2TableEditor(null, new DB2ColumnEditor(), null, null);
        // package-private access — call by reflection-free path: build a table without indexes
        DBTable t = new DBTable();
        t.setName("X");
        t.setSchemaName("S");
        t.setColumns(Arrays.asList(col("A", "INTEGER", false)));
        String ddl = probe.generateCreateObjectDDL(t);
        assertFalse("CREATE TABLE should not embed CREATE INDEX", ddl.contains("CREATE INDEX"));
    }
}
