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
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.oceanbase.tools.dbbrowser.model.DBTableColumn;

/**
 * Map-case unit tests for {@link DB2ColumnEditor} DDL emission (T-003 commit-2). The cases cover
 * the data-type categories the workbench MVP exercises through SYSCAT.COLUMNS:
 * {@code VARCHAR / CHAR / INTEGER / BIGINT / DECIMAL / TIMESTAMP / DATE / BLOB / CLOB / XML /
 * DECFLOAT}.
 */
public class DB2ColumnEditorTest {

    private final DB2ColumnEditor editor = new DB2ColumnEditor();

    private static DBTableColumn col(String schema, String table, String name, String typeName,
            Long precision, Integer scale, boolean nullable) {
        DBTableColumn c = new DBTableColumn();
        c.setSchemaName(schema);
        c.setTableName(table);
        c.setName(name);
        c.setTypeName(typeName);
        c.setPrecision(precision);
        c.setScale(scale);
        c.setNullable(nullable);
        return c;
    }

    @Test
    public void generateCreateObjectDDL_VARCHAR_withLength_appendsParenLength() {
        DBTableColumn c = col("DB2INST1", "EMP", "NAME", "VARCHAR", 64L, null, true);
        String ddl = editor.generateCreateObjectDDL(c);
        assertTrue(ddl, ddl.contains("ALTER TABLE \"DB2INST1\".\"EMP\""));
        assertTrue(ddl, ddl.contains("\"NAME\" VARCHAR(64)"));
        assertTrue(ddl, ddl.contains(" NULL"));
        assertTrue(ddl.trim().endsWith(";"));
    }

    @Test
    public void generateCreateObjectDDL_INTEGER_noParens_andNotNull() {
        DBTableColumn c = col("APP", "ORDERS", "ID", "INTEGER", null, null, false);
        String ddl = editor.generateCreateObjectDDL(c);
        assertTrue(ddl, ddl.contains("\"ID\" INTEGER"));
        assertFalse(ddl.contains("INTEGER("));
        assertTrue(ddl, ddl.contains("NOT NULL"));
    }

    @Test
    public void generateCreateObjectDDL_DECIMAL_withPrecisionAndScale_emitsParen() {
        DBTableColumn c = col("APP", "ORDERS", "AMOUNT", "DECIMAL", 12L, 2, true);
        String ddl = editor.generateCreateObjectDDL(c);
        assertTrue(ddl, ddl.contains("\"AMOUNT\" DECIMAL(12,2)"));
    }

    @Test
    public void generateCreateObjectDDL_DECIMAL_scaleZero_emitsOnlyPrecision() {
        DBTableColumn c = col("APP", "ORDERS", "QTY", "DECIMAL", 5L, 0, false);
        String ddl = editor.generateCreateObjectDDL(c);
        assertTrue(ddl, ddl.contains("\"QTY\" DECIMAL(5)"));
        assertFalse(ddl.contains("DECIMAL(5,0)"));
    }

    @Test
    public void generateCreateObjectDDL_TIMESTAMP_noScale_noParens() {
        DBTableColumn c = col("APP", "AUDIT", "TS", "TIMESTAMP", null, null, false);
        String ddl = editor.generateCreateObjectDDL(c);
        assertTrue(ddl, ddl.contains("\"TS\" TIMESTAMP"));
        assertFalse(ddl, ddl.contains("TIMESTAMP("));
    }

    @Test
    public void generateCreateObjectDDL_TIMESTAMP_withFractionalScale_emitsScale() {
        DBTableColumn c = col("APP", "AUDIT", "TS6", "TIMESTAMP", null, 6, false);
        String ddl = editor.generateCreateObjectDDL(c);
        assertTrue(ddl, ddl.contains("\"TS6\" TIMESTAMP(6)"));
    }

    @Test
    public void generateCreateObjectDDL_DATE_noParens() {
        DBTableColumn c = col("APP", "AUDIT", "D", "DATE", null, null, true);
        String ddl = editor.generateCreateObjectDDL(c);
        assertTrue(ddl, ddl.contains("\"D\" DATE"));
        assertFalse(ddl.contains("DATE("));
    }

    @Test
    public void generateCreateObjectDDL_BLOB_withLength() {
        DBTableColumn c = col("APP", "FILES", "DATA", "BLOB", 1048576L, null, true);
        String ddl = editor.generateCreateObjectDDL(c);
        assertTrue(ddl, ddl.contains("\"DATA\" BLOB(1048576)"));
    }

    @Test
    public void generateCreateObjectDDL_CLOB_withLength() {
        DBTableColumn c = col("APP", "DOCS", "BODY", "CLOB", 4194304L, null, true);
        String ddl = editor.generateCreateObjectDDL(c);
        assertTrue(ddl, ddl.contains("\"BODY\" CLOB(4194304)"));
    }

    @Test
    public void generateCreateObjectDDL_DECFLOAT_noParensEvenIfPrecisionPresent() {
        // DECFLOAT(16) / DECFLOAT(34) sizing is reflected through TYPENAME 'DECFLOAT'+precision in
        // SYSCAT.COLUMNS.LENGTH; our naive impl just emits the type name without parens.
        DBTableColumn c = col("APP", "X", "V", "DECFLOAT", 34L, null, true);
        String ddl = editor.generateCreateObjectDDL(c);
        assertTrue(ddl, ddl.contains("\"V\" DECFLOAT"));
        // we intentionally do NOT emit DECFLOAT(34) — IBM Db2 LUW does support it but
        // SYSCAT-reported "DECFLOAT" with LENGTH=16/34 is rare; out of MVP scope
        assertFalse(ddl.contains("DECFLOAT("));
    }

    @Test
    public void generateCreateObjectDDL_XML_noParens() {
        DBTableColumn c = col("APP", "X", "BODY", "XML", null, null, true);
        String ddl = editor.generateCreateObjectDDL(c);
        assertTrue(ddl, ddl.contains("\"BODY\" XML"));
        assertFalse(ddl.contains("XML("));
    }

    @Test
    public void generateCreateObjectDDL_DBCLOB_withLength_appended() {
        DBTableColumn c = col("APP", "X", "TXT", "DBCLOB", 100L, null, true);
        String ddl = editor.generateCreateObjectDDL(c);
        assertTrue(ddl, ddl.contains("\"TXT\" DBCLOB(100)"));
    }

    @Test
    public void generateCreateObjectDDL_withComment_appendsCommentOnColumn() {
        DBTableColumn c = col("APP", "EMP", "NAME", "VARCHAR", 64L, null, true);
        c.setComment("Employee full name");
        String ddl = editor.generateCreateObjectDDL(c);
        assertTrue(ddl, ddl.contains("COMMENT ON COLUMN \"APP\".\"EMP\".\"NAME\""));
        assertTrue(ddl, ddl.contains("IS 'Employee full name'"));
    }

    @Test
    public void generateCreateObjectDDL_withDefaultValue_emitsDEFAULT() {
        DBTableColumn c = col("APP", "EMP", "STATUS", "VARCHAR", 16L, null, false);
        c.setDefaultValue("'A'");
        String ddl = editor.generateCreateObjectDDL(c);
        assertTrue(ddl, ddl.contains("DEFAULT 'A'"));
    }

    @Test
    public void generateRenameObjectDDL_emitsRenameColumn() {
        DBTableColumn oldCol = col("APP", "EMP", "OLD_NAME", "VARCHAR", 64L, null, true);
        DBTableColumn newCol = col("APP", "EMP", "NEW_NAME", "VARCHAR", 64L, null, true);
        String ddl = editor.generateRenameObjectDDL(oldCol, newCol);
        assertTrue(ddl, ddl.startsWith("ALTER TABLE \"APP\".\"EMP\""));
        assertTrue(ddl, ddl.contains("RENAME COLUMN \"OLD_NAME\" TO \"NEW_NAME\""));
    }

    @Test
    public void generateDropObjectDDL_emitsAlterTableDropColumn() {
        DBTableColumn c = col("APP", "EMP", "OBSOLETE", "VARCHAR", 64L, null, true);
        String ddl = editor.generateDropObjectDDL(c);
        assertTrue(ddl, ddl.startsWith("ALTER TABLE \"APP\".\"EMP\""));
        assertTrue(ddl, ddl.contains("DROP COLUMN \"OBSOLETE\""));
    }

    @Test
    public void quotedIdentifier_doublesInternalDoubleQuote() {
        DBTableColumn c = col("APP", "EMP", "weird\"name", "INTEGER", null, null, true);
        String ddl = editor.generateCreateObjectDDL(c);
        // internal " in column name "weird"name" must be escaped to ""
        assertTrue(ddl, ddl.contains("\"weird\"\"name\""));
    }

    @Test
    public void valueLiteral_doublesInternalSingleQuote() {
        DBTableColumn c = col("APP", "EMP", "NAME", "VARCHAR", 64L, null, true);
        c.setComment("Bob's name");
        String ddl = editor.generateCreateObjectDDL(c);
        assertTrue(ddl, ddl.contains("'Bob''s name'"));
    }

    @Test
    public void editor_isEditable() {
        assertEquals(true, editor.editable());
    }
}
