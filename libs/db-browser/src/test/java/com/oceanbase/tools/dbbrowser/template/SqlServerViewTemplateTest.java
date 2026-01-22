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
package com.oceanbase.tools.dbbrowser.template;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.oceanbase.tools.dbbrowser.model.DBView;
import com.oceanbase.tools.dbbrowser.model.DBView.DBViewUnit;
import com.oceanbase.tools.dbbrowser.model.DBViewColumn;
import com.oceanbase.tools.dbbrowser.template.sqlserver.SqlServerViewTemplate;

/**
 * {@link SqlServerViewTemplateTest}
 *
 * @author yizhou.xw
 * @date 2024/12
 * @since ODC_release_4.3.4
 */
public class SqlServerViewTemplateTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void generateCreateObjectTemplate_viewWithoutName_expThrown() {
        DBObjectTemplate<DBView> template = new SqlServerViewTemplate();
        DBView view = new DBView();

        thrown.expectMessage("View name can not be blank");
        thrown.expect(NullPointerException.class);
        template.generateCreateObjectTemplate(view);
    }

    @Test
    public void generateCreateObjectTemplate_viewWithDatabaseAndSchema_generateSucceed() {
        DBObjectTemplate<DBView> template = new SqlServerViewTemplate();
        DBView view = new DBView();
        view.setViewName("v_test");
        view.setSchemaName("testdb.sales");
        String expect = "USE [testdb];\nGO\nCREATE VIEW [sales].[v_test] AS";
        Assert.assertEquals(expect, template.generateCreateObjectTemplate(view));
    }

    @Test
    public void generateCreateObjectTemplate_tableOperationsUnmatched_expThrown() {
        DBObjectTemplate<DBView> template = new SqlServerViewTemplate();
        DBView view = new DBView();
        view.setViewName("v_test");
        view.setSchemaName("testdb.dbo");
        view.setViewUnits(prepareViewUnits(2, false));

        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Unable to calculate, operationSize<>tableSize-1");
        template.generateCreateObjectTemplate(view);
    }

    @Test
    public void generateCreateObjectTemplate_singleTableView_generateSucceed() {
        DBObjectTemplate<DBView> template = new SqlServerViewTemplate();
        DBView view = new DBView();
        view.setViewName("v_test");
        view.setSchemaName("testdb.dbo");
        view.setViewUnits(prepareViewUnits(1, false));
        view.setOperations(Collections.emptyList());

        String expect = "USE [testdb];\nGO\n"
                + "CREATE VIEW [dbo].[v_test] AS\n"
                + "SELECT \n\t"
                + "*\n"
                + "FROM\n\t"
                + "[testdb].[dbo].[table_0]";
        Assert.assertEquals(expect, template.generateCreateObjectTemplate(view));
    }

    @Test
    public void generateCreateObjectTemplate_singleTableWithColumns_generateSucceed() {
        DBObjectTemplate<DBView> template = new SqlServerViewTemplate();
        DBView view = new DBView();
        view.setViewName("v_test");
        view.setSchemaName("testdb.dbo");
        view.setViewUnits(prepareViewUnits(1, true));
        view.setOperations(Collections.emptyList());
        view.setCreateColumns(prepareColumns(1));

        String expect = "USE [testdb];\nGO\n"
                + "CREATE VIEW [dbo].[v_test] AS\n"
                + "SELECT\n"
                + "\t[t0].[col_0] AS [alias_col0],\n"
                + "\t[t0].[col2_0] AS [alias_col2_0]\n"
                + "FROM\n\t"
                + "[testdb].[dbo].[table_0] [t0]";
        Assert.assertEquals(expect, template.generateCreateObjectTemplate(view));
    }

    @Test
    public void generateCreateObjectTemplate_multiTableLeftJoin_generateSucceed() {
        DBObjectTemplate<DBView> template = new SqlServerViewTemplate();
        DBView view = new DBView();
        view.setViewName("v_test");
        view.setSchemaName("testdb.dbo");
        view.setViewUnits(prepareViewUnits(2, true));
        view.setOperations(Collections.singletonList("left join"));
        view.setCreateColumns(prepareColumns(2));

        String expect = "USE [testdb];\nGO\n"
                + "CREATE VIEW [dbo].[v_test] AS\n"
                + "SELECT\n"
                + "\t[t0].[col_0] AS [alias_col0],\n"
                + "\t[t0].[col2_0] AS [alias_col2_0],\n"
                + "\t[t1].[col_1] AS [alias_col1],\n"
                + "\t[t1].[col2_1] AS [alias_col2_1]\n"
                + "FROM\n\t"
                + "[testdb].[dbo].[table_0] [t0]\n\t"
                + "LEFT JOIN [testdb].[dbo].[table_1] [t1] ON 1=1";
        Assert.assertEquals(expect, template.generateCreateObjectTemplate(view));
    }

    @Test
    public void generateCreateObjectTemplate_threePartNaming_generateSucceed() {
        DBObjectTemplate<DBView> template = new SqlServerViewTemplate();
        DBView view = new DBView();
        view.setViewName("v_test");
        view.setSchemaName("testdb.sales");

        List<DBViewUnit> units = new ArrayList<>();
        DBViewUnit unit = new DBViewUnit();
        unit.setDbName("testdb.sales");
        unit.setTableName("orders");
        unit.setTableAliasName("o");
        units.add(unit);
        view.setViewUnits(units);
        view.setOperations(Collections.emptyList());

        List<DBViewColumn> columns = new ArrayList<>();
        DBViewColumn col = new DBViewColumn();
        col.setDbName("testdb.sales");
        col.setTableName("orders");
        col.setTableAliasName("o");
        col.setColumnName("order_id");
        col.setAliasName("id");
        columns.add(col);
        view.setCreateColumns(columns);

        String expect = "USE [testdb];\nGO\n"
                + "CREATE VIEW [sales].[v_test] AS\n"
                + "SELECT\n"
                + "\t[o].[order_id] AS [id]\n"
                + "FROM\n\t"
                + "[testdb].[sales].[orders] [o]";
        Assert.assertEquals(expect, template.generateCreateObjectTemplate(view));
    }

    @Test
    public void generateCreateObjectTemplate_commaJoin_generateSucceed() {
        DBObjectTemplate<DBView> template = new SqlServerViewTemplate();
        DBView view = new DBView();
        view.setViewName("v_test");
        view.setSchemaName("testdb.dbo");
        view.setViewUnits(prepareViewUnits(2, true));
        view.setOperations(Collections.singletonList(","));
        view.setCreateColumns(prepareColumns(2));

        String expect = "USE [testdb];\nGO\n"
                + "CREATE VIEW [dbo].[v_test] AS\n"
                + "SELECT\n"
                + "\t[t0].[col_0] AS [alias_col0],\n"
                + "\t[t0].[col2_0] AS [alias_col2_0],\n"
                + "\t[t1].[col_1] AS [alias_col1],\n"
                + "\t[t1].[col2_1] AS [alias_col2_1]\n"
                + "FROM\n\t"
                + "[testdb].[dbo].[table_0] [t0], [testdb].[dbo].[table_1] [t1]\n"
                + "WHERE 1=1";
        Assert.assertEquals(expect, template.generateCreateObjectTemplate(view));
    }

    @Test
    public void generateCreateObjectTemplate_specialCharactersInNames_generateSucceed() {
        DBObjectTemplate<DBView> template = new SqlServerViewTemplate();
        DBView view = new DBView();
        view.setViewName("v test view"); // 空格
        view.setSchemaName("my db.my schema"); // database.schema 都有空格

        String result = template.generateCreateObjectTemplate(view);
        Assert.assertTrue(result.contains("USE [my db];"));
        Assert.assertTrue(result.contains("[my schema]"));
        Assert.assertTrue(result.contains("[v test view]"));
    }

    private List<DBViewColumn> prepareColumns(int size) {
        List<DBViewColumn> viewColumns = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            DBViewColumn viewColumn1 = new DBViewColumn();
            viewColumn1.setColumnName("col_" + i);
            viewColumn1.setAliasName("alias_col" + i);
            viewColumn1.setDbName("testdb.dbo");
            viewColumn1.setTableName("table_" + i);
            viewColumn1.setTableAliasName("t" + i);

            DBViewColumn viewColumn2 = new DBViewColumn();
            viewColumn2.setColumnName("col2_" + i);
            viewColumn2.setAliasName("alias_col2_" + i);
            viewColumn2.setDbName("testdb.dbo");
            viewColumn2.setTableName("table_" + i);
            viewColumn2.setTableAliasName("t" + i);

            viewColumns.add(viewColumn1);
            viewColumns.add(viewColumn2);
        }
        return viewColumns;
    }

    private List<DBViewUnit> prepareViewUnits(int size, boolean withAlias) {
        List<DBViewUnit> viewUnits = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            DBViewUnit viewUnit = new DBViewUnit();
            viewUnit.setDbName("testdb.dbo");
            viewUnit.setTableName("table_" + i);
            if (withAlias) {
                viewUnit.setTableAliasName("t" + i);
            }
            viewUnits.add(viewUnit);
        }
        return viewUnits;
    }

}
