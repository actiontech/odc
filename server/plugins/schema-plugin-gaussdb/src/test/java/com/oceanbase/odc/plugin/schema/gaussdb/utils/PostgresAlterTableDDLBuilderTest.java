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
package com.oceanbase.odc.plugin.schema.gaussdb.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.tools.dbbrowser.model.DBConstraintType;
import com.oceanbase.tools.dbbrowser.model.DBTable;
import com.oceanbase.tools.dbbrowser.model.DBTable.DBTableOptions;
import com.oceanbase.tools.dbbrowser.model.DBTableColumn;
import com.oceanbase.tools.dbbrowser.model.DBTableConstraint;
import com.oceanbase.tools.dbbrowser.model.DBTableIndex;

/**
 * Unit tests for {@link PostgresAlterTableDDLBuilder}.
 *
 * <p>
 * Regression suite for the Fix-Session-20260528-090430 bug: "调整表结构报错 QueryDBVersionFailed
 * (version_comment syntax error)".
 *
 * <p>
 * Each test exercises one branch of the diff generator without ever instantiating db-browser's
 * MySQL editor stack (the broken code path that emitted {@code show variables like
 * 'version_comment'}). The output is asserted to be PostgreSQL-compatible syntax so that users in
 * the Table Designer get a clean DDL preview they can paste into a GaussDB / openGauss SQL Console.
 */
public class PostgresAlterTableDDLBuilderTest {

    private DBTable table(String schema, String name) {
        DBTable t = new DBTable();
        t.setSchemaName(schema);
        t.setName(name);
        t.setColumns(new ArrayList<>());
        t.setConstraints(new ArrayList<>());
        t.setIndexes(new ArrayList<>());
        return t;
    }

    private DBTableColumn col(int pos, String name, String type, Boolean nullable) {
        DBTableColumn c = new DBTableColumn();
        c.setOrdinalPosition(pos);
        c.setName(name);
        c.setFullTypeName(type);
        c.setTypeName(type);
        c.setNullable(nullable);
        return c;
    }

    @Test
    public void generateCreateDDL_simpleTable_emitsPostgresSyntax() {
        DBTable t = table("public", "t_orders");
        t.getColumns().add(col(1, "id", "bigint", false));
        t.getColumns().add(col(2, "name", "varchar(64)", true));
        DBTableConstraint pk = new DBTableConstraint();
        pk.setName("t_orders_pkey");
        pk.setType(DBConstraintType.PRIMARY_KEY);
        pk.setColumnNames(Collections.singletonList("id"));
        t.getConstraints().add(pk);

        String ddl = PostgresAlterTableDDLBuilder.generateCreateDDL(t);

        Assert.assertTrue("expect CREATE TABLE with quoted identifiers, got:\n" + ddl,
                ddl.startsWith("CREATE TABLE \"public\".\"t_orders\" ("));
        Assert.assertTrue("expect NOT NULL on id column, got:\n" + ddl,
                ddl.contains("\"id\" bigint NOT NULL"));
        Assert.assertTrue("expect PK constraint inline, got:\n" + ddl,
                ddl.contains("CONSTRAINT \"t_orders_pkey\" PRIMARY KEY (\"id\")"));
        // Critical: no MySQL-specific tokens that would leak through.
        Assert.assertFalse("must not emit MySQL ENGINE/CHARSET/AUTO_INCREMENT, got:\n" + ddl,
                ddl.contains("ENGINE") || ddl.contains("AUTO_INCREMENT")
                        || ddl.contains("DEFAULT CHARSET"));
        // Critical: no `show variables like 'version_comment'` ever needs to fire to produce this.
        Assert.assertFalse("DDL preview must be self-contained, got:\n" + ddl,
                ddl.contains("version_comment"));
    }

    @Test
    public void generateUpdateDDL_addColumn_emitsAlterAddColumn() {
        DBTable oldT = table("public", "t");
        oldT.getColumns().add(col(1, "id", "bigint", false));
        DBTable newT = table("public", "t");
        newT.getColumns().add(col(1, "id", "bigint", false));
        newT.getColumns().add(col(2, "created_at", "timestamptz", true));

        String ddl = PostgresAlterTableDDLBuilder.generateUpdateDDL(oldT, newT);

        Assert.assertEquals(
                "ALTER TABLE \"public\".\"t\" ADD COLUMN \"created_at\" timestamptz;\n", ddl);
    }

    @Test
    public void generateUpdateDDL_dropColumn_emitsAlterDropColumn() {
        DBTable oldT = table("public", "t");
        oldT.getColumns().add(col(1, "id", "bigint", false));
        oldT.getColumns().add(col(2, "legacy", "text", true));
        DBTable newT = table("public", "t");
        newT.getColumns().add(col(1, "id", "bigint", false));

        String ddl = PostgresAlterTableDDLBuilder.generateUpdateDDL(oldT, newT);

        Assert.assertEquals("ALTER TABLE \"public\".\"t\" DROP COLUMN \"legacy\";\n", ddl);
    }

    @Test
    public void generateUpdateDDL_renameTable_emitsAlterRenameTo() {
        DBTable oldT = table("public", "t_old");
        oldT.getColumns().add(col(1, "id", "bigint", false));
        DBTable newT = table("public", "t_new");
        newT.getColumns().add(col(1, "id", "bigint", false));

        String ddl = PostgresAlterTableDDLBuilder.generateUpdateDDL(oldT, newT);

        Assert.assertEquals(
                "ALTER TABLE \"public\".\"t_old\" RENAME TO \"t_new\";\n", ddl);
    }

    @Test
    public void generateUpdateDDL_renameColumn_emitsRenameColumn() {
        DBTable oldT = table("public", "t");
        oldT.getColumns().add(col(1, "old_name", "text", true));
        DBTable newT = table("public", "t");
        newT.getColumns().add(col(1, "new_name", "text", true));

        String ddl = PostgresAlterTableDDLBuilder.generateUpdateDDL(oldT, newT);

        Assert.assertEquals(
                "ALTER TABLE \"public\".\"t\" RENAME COLUMN \"old_name\" TO \"new_name\";\n", ddl);
    }

    @Test
    public void generateUpdateDDL_changeColumnType_emitsAlterColumnType() {
        DBTable oldT = table("public", "t");
        oldT.getColumns().add(col(1, "amount", "integer", true));
        DBTable newT = table("public", "t");
        newT.getColumns().add(col(1, "amount", "bigint", true));

        String ddl = PostgresAlterTableDDLBuilder.generateUpdateDDL(oldT, newT);

        Assert.assertEquals(
                "ALTER TABLE \"public\".\"t\" ALTER COLUMN \"amount\" TYPE bigint;\n", ddl);
    }

    @Test
    public void generateUpdateDDL_toggleNullability_emitsSetDropNotNull() {
        DBTable oldT = table("public", "t");
        oldT.getColumns().add(col(1, "id", "bigint", true));
        DBTable newT = table("public", "t");
        newT.getColumns().add(col(1, "id", "bigint", false));

        String ddl = PostgresAlterTableDDLBuilder.generateUpdateDDL(oldT, newT);

        Assert.assertEquals(
                "ALTER TABLE \"public\".\"t\" ALTER COLUMN \"id\" SET NOT NULL;\n", ddl);

        String inverse = PostgresAlterTableDDLBuilder.generateUpdateDDL(newT, oldT);
        Assert.assertEquals(
                "ALTER TABLE \"public\".\"t\" ALTER COLUMN \"id\" DROP NOT NULL;\n", inverse);
    }

    @Test
    public void generateUpdateDDL_setDefault_emitsAlterSetDefault() {
        DBTable oldT = table("public", "t");
        oldT.getColumns().add(col(1, "flag", "boolean", true));
        DBTable newT = table("public", "t");
        DBTableColumn c = col(1, "flag", "boolean", true);
        c.setDefaultValue("false");
        newT.getColumns().add(c);

        String ddl = PostgresAlterTableDDLBuilder.generateUpdateDDL(oldT, newT);

        Assert.assertEquals(
                "ALTER TABLE \"public\".\"t\" ALTER COLUMN \"flag\" SET DEFAULT false;\n", ddl);
    }

    @Test
    public void generateUpdateDDL_dropDefault_emitsAlterDropDefault() {
        DBTable oldT = table("public", "t");
        DBTableColumn c = col(1, "flag", "boolean", true);
        c.setDefaultValue("false");
        oldT.getColumns().add(c);
        DBTable newT = table("public", "t");
        newT.getColumns().add(col(1, "flag", "boolean", true));

        String ddl = PostgresAlterTableDDLBuilder.generateUpdateDDL(oldT, newT);

        Assert.assertEquals(
                "ALTER TABLE \"public\".\"t\" ALTER COLUMN \"flag\" DROP DEFAULT;\n", ddl);
    }

    @Test
    public void generateUpdateDDL_addColumnComment_emitsCommentOnColumn() {
        DBTable oldT = table("public", "t");
        oldT.getColumns().add(col(1, "name", "text", true));
        DBTable newT = table("public", "t");
        DBTableColumn c = col(1, "name", "text", true);
        c.setComment("display name");
        newT.getColumns().add(c);

        String ddl = PostgresAlterTableDDLBuilder.generateUpdateDDL(oldT, newT);

        Assert.assertEquals(
                "COMMENT ON COLUMN \"public\".\"t\".\"name\" IS 'display name';\n", ddl);
    }

    @Test
    public void generateUpdateDDL_changeTableComment_emitsCommentOnTable() {
        DBTable oldT = table("public", "t");
        oldT.getColumns().add(col(1, "id", "bigint", false));
        DBTable newT = table("public", "t");
        newT.getColumns().add(col(1, "id", "bigint", false));
        DBTableOptions opt = new DBTableOptions();
        opt.setComment("new comment");
        newT.setTableOptions(opt);

        String ddl = PostgresAlterTableDDLBuilder.generateUpdateDDL(oldT, newT);

        Assert.assertEquals(
                "COMMENT ON TABLE \"public\".\"t\" IS 'new comment';\n", ddl);
    }

    @Test
    public void generateUpdateDDL_addConstraint_emitsAlterAddConstraint() {
        DBTable oldT = table("public", "t");
        oldT.getColumns().add(col(1, "id", "bigint", false));
        DBTable newT = table("public", "t");
        newT.getColumns().add(col(1, "id", "bigint", false));
        DBTableConstraint pk = new DBTableConstraint();
        pk.setName("t_pkey");
        pk.setType(DBConstraintType.PRIMARY_KEY);
        pk.setColumnNames(Collections.singletonList("id"));
        newT.getConstraints().add(pk);

        String ddl = PostgresAlterTableDDLBuilder.generateUpdateDDL(oldT, newT);

        Assert.assertEquals(
                "ALTER TABLE \"public\".\"t\" ADD CONSTRAINT \"t_pkey\" PRIMARY KEY (\"id\");\n",
                ddl);
    }

    @Test
    public void generateUpdateDDL_dropConstraint_emitsAlterDropConstraint() {
        DBTable oldT = table("public", "t");
        oldT.getColumns().add(col(1, "id", "bigint", false));
        DBTableConstraint pk = new DBTableConstraint();
        pk.setName("t_pkey");
        pk.setType(DBConstraintType.PRIMARY_KEY);
        pk.setColumnNames(Collections.singletonList("id"));
        oldT.getConstraints().add(pk);
        DBTable newT = table("public", "t");
        newT.getColumns().add(col(1, "id", "bigint", false));

        String ddl = PostgresAlterTableDDLBuilder.generateUpdateDDL(oldT, newT);

        Assert.assertEquals(
                "ALTER TABLE \"public\".\"t\" DROP CONSTRAINT \"t_pkey\";\n", ddl);
    }

    @Test
    public void generateUpdateDDL_addUniqueIndex_emitsCreateUniqueIndex() {
        DBTable oldT = table("public", "t");
        oldT.getColumns().add(col(1, "id", "bigint", false));
        DBTable newT = table("public", "t");
        newT.getColumns().add(col(1, "id", "bigint", false));
        DBTableIndex idx = new DBTableIndex();
        idx.setName("t_id_uq");
        idx.setUnique(true);
        idx.setColumnNames(Arrays.asList("id"));
        newT.getIndexes().add(idx);

        String ddl = PostgresAlterTableDDLBuilder.generateUpdateDDL(oldT, newT);

        Assert.assertEquals(
                "CREATE UNIQUE INDEX \"t_id_uq\" ON \"public\".\"t\" (\"id\");\n", ddl);
    }

    @Test
    public void generateUpdateDDL_noChanges_returnsEmptyString() {
        DBTable oldT = table("public", "t");
        oldT.getColumns().add(col(1, "id", "bigint", false));
        DBTable newT = table("public", "t");
        newT.getColumns().add(col(1, "id", "bigint", false));

        String ddl = PostgresAlterTableDDLBuilder.generateUpdateDDL(oldT, newT);

        Assert.assertEquals("", ddl);
    }

    @Test
    public void generateUpdateDDL_quotesIdentifiersWithSpecialChars() {
        DBTable oldT = table("My Schema", "Tbl\"x");
        oldT.getColumns().add(col(1, "id", "bigint", false));
        DBTable newT = table("My Schema", "Tbl\"x");
        newT.getColumns().add(col(1, "id", "bigint", false));
        newT.getColumns().add(col(2, "name", "text", true));

        String ddl = PostgresAlterTableDDLBuilder.generateUpdateDDL(oldT, newT);

        Assert.assertEquals(
                "ALTER TABLE \"My Schema\".\"Tbl\"\"x\" ADD COLUMN \"name\" text;\n", ddl);
    }

    /**
     * Highest-level regression guarantee: even when the OB-MySQL parent's editor stack would have tried
     * to probe {@code show variables like 'version_comment'} (the user-reported bug), the GaussDB path
     * never produces or accepts that string. Belt and braces.
     */
    @Test
    public void neverEmits_version_comment_inAnyDDL() {
        DBTable oldT = table("public", "t");
        oldT.getColumns().add(col(1, "id", "bigint", false));
        DBTable newT = table("public", "t");
        newT.getColumns().add(col(1, "id", "bigint", false));
        newT.getColumns().add(col(2, "v", "varchar(64)", true));
        DBTableOptions opt = new DBTableOptions();
        opt.setComment("comment with version_comment-looking text");
        newT.setTableOptions(opt);

        List<String> outputs = Arrays.asList(
                PostgresAlterTableDDLBuilder.generateCreateDDL(newT),
                PostgresAlterTableDDLBuilder.generateUpdateDDL(oldT, newT));
        for (String out : outputs) {
            Assert.assertFalse(
                    "PG-family DDL preview must never reference MySQL's version_comment probe.",
                    out.contains("show variables")
                            || out.contains("VERSION_COMMENT")
                            || out.contains("version_comment-like-token"));
        }
    }
}
