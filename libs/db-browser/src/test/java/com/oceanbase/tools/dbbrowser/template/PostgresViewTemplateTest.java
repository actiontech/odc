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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.oceanbase.tools.dbbrowser.model.DBView;
import com.oceanbase.tools.dbbrowser.model.DBView.DBViewUnit;
import com.oceanbase.tools.dbbrowser.model.DBViewColumn;
import com.oceanbase.tools.dbbrowser.template.postgre.PostgresViewTemplate;

/**
 * {@link PostgresViewTemplateTest}
 *
 * @author odc
 * @since ODC_release_4.3.4
 */
public class PostgresViewTemplateTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void generateCreateObjectTemplate_viewWithoutName_expThrown() {
        DBObjectTemplate<DBView> template = new PostgresViewTemplate();
        DBView view = new DBView();

        thrown.expectMessage("View name can not be blank");
        thrown.expect(NullPointerException.class);
        template.generateCreateObjectTemplate(view);
    }

    @Test
    public void generateCreateObjectTemplate_viewWithSchema_generateSucceed() {
        DBObjectTemplate<DBView> template = new PostgresViewTemplate();
        DBView view = new DBView();
        view.setViewName("v_test");
        view.setSchemaName("public");

        String result = template.generateCreateObjectTemplate(view);

        Assert.assertTrue(result.contains("create or replace view"));
        Assert.assertTrue(result.contains("\"public\".\"v_test\""));
        Assert.assertTrue(result.contains(" as"));
    }

    @Test
    public void generateCreateObjectTemplate_viewWithoutSchema_generateSucceed() {
        DBObjectTemplate<DBView> template = new PostgresViewTemplate();
        DBView view = new DBView();
        view.setViewName("v_test");

        String result = template.generateCreateObjectTemplate(view);

        Assert.assertTrue(result.contains("create or replace view"));
        Assert.assertTrue(result.contains("\"v_test\""));
    }

    @Test
    public void generateCreateObjectTemplate_tableOperationsUnmatched_expThrown() {
        DBObjectTemplate<DBView> template = new PostgresViewTemplate();
        DBView view = new DBView();
        view.setViewName("v_test");
        view.setSchemaName("public");
        view.setViewUnits(prepareViewUnits(2, false));

        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Unable to calculate, operationSize<>tableSize-1");
        template.generateCreateObjectTemplate(view);
    }

    @Test
    public void generateCreateObjectTemplate_singleTableView_generateSucceed() {
        DBObjectTemplate<DBView> template = new PostgresViewTemplate();
        DBView view = new DBView();
        view.setViewName("v_test");
        view.setSchemaName("public");
        view.setViewUnits(prepareViewUnits(1, false));
        view.setOperations(Collections.emptyList());

        String result = template.generateCreateObjectTemplate(view);

        Assert.assertTrue(result.contains("create or replace view"));
        Assert.assertTrue(result.contains("\"public\".\"v_test\""));
        Assert.assertTrue(result.contains("select"));
        Assert.assertTrue(result.contains("from"));
        Assert.assertTrue(result.contains("\"public\".\"table_0\""));
    }

    @Test
    public void generateCreateObjectTemplate_singleTableWithColumns_generateSucceed() {
        DBObjectTemplate<DBView> template = new PostgresViewTemplate();
        DBView view = new DBView();
        view.setViewName("v_test");
        view.setSchemaName("public");
        view.setViewUnits(prepareViewUnits(1, true));
        view.setOperations(Collections.emptyList());
        view.setCreateColumns(prepareColumns(1));

        String result = template.generateCreateObjectTemplate(view);

        Assert.assertTrue("Should contain 'create or replace view'", result.contains("create or replace view"));
        Assert.assertTrue("Should contain schema.viewName", result.contains("\"public\".\"v_test\""));
        // When tableAliasName exists, columns use alias prefix (e.g., t0."col_0")
        Assert.assertTrue("Should contain column references", result.contains("\"col_0\""));
        Assert.assertTrue("Should contain select", result.contains("select"));
        Assert.assertTrue("Should contain from", result.contains("from"));
    }

    @Test
    public void generateCreateObjectTemplate_multiTableLeftJoin_generateSucceed() {
        DBObjectTemplate<DBView> template = new PostgresViewTemplate();
        DBView view = new DBView();
        view.setViewName("v_test");
        view.setSchemaName("public");
        view.setViewUnits(prepareViewUnits(2, true));
        view.setOperations(Collections.singletonList("left join"));
        view.setCreateColumns(prepareColumns(2));

        String result = template.generateCreateObjectTemplate(view);

        Assert.assertTrue(result.contains("create or replace view"));
        Assert.assertTrue(result.contains("select"));
        Assert.assertTrue(result.contains("from"));
        Assert.assertTrue(result.contains("left join"));
        Assert.assertTrue(result.contains("\"public\".\"table_0\""));
        Assert.assertTrue(result.contains("\"public\".\"table_1\""));
    }

    @Test
    public void generateCreateObjectTemplate_commaJoin_generateSucceed() {
        DBObjectTemplate<DBView> template = new PostgresViewTemplate();
        DBView view = new DBView();
        view.setViewName("v_test");
        view.setSchemaName("public");
        view.setViewUnits(prepareViewUnits(2, true));
        view.setOperations(Collections.singletonList(","));
        view.setCreateColumns(prepareColumns(2));

        String result = template.generateCreateObjectTemplate(view);

        Assert.assertTrue(result.contains("create or replace view"));
        Assert.assertTrue(result.contains("select"));
        Assert.assertTrue(result.contains("from"));
        Assert.assertTrue(result.contains("where"));
    }

    @Test
    public void generateCreateObjectTemplate_specialCharactersInNames_generateSucceed() {
        DBObjectTemplate<DBView> template = new PostgresViewTemplate();
        DBView view = new DBView();
        view.setViewName("v test view"); // 名称包含空格
        view.setSchemaName("my schema"); // schema名称包含空格

        String result = template.generateCreateObjectTemplate(view);

        Assert.assertTrue(result.contains("\"my schema\""));
        Assert.assertTrue(result.contains("\"v test view\""));
    }

    @Test
    public void generateCreateObjectTemplate_caseInsensitiveKeywords_generateSucceed() {
        DBObjectTemplate<DBView> template = new PostgresViewTemplate();
        DBView view = new DBView();
        view.setViewName("v_test");
        view.setSchemaName("public");
        view.setViewUnits(prepareViewUnits(1, false));
        view.setOperations(Collections.emptyList());

        String result = template.generateCreateObjectTemplate(view);

        // PostgreSQL keywords should be lowercase
        Assert.assertTrue(result.contains("create or replace view"));
        Assert.assertTrue(result.contains("select"));
        Assert.assertTrue(result.contains("from"));
    }

    @Test
    public void generateCreateObjectTemplate_differentSchemaViews_generateSucceed() {
        DBObjectTemplate<DBView> template = new PostgresViewTemplate();

        // View in 'sales' schema
        DBView view1 = new DBView();
        view1.setViewName("v_orders");
        view1.setSchemaName("sales");

        String result1 = template.generateCreateObjectTemplate(view1);
        Assert.assertTrue(result1.contains("\"sales\".\"v_orders\""));

        // View in 'hr' schema
        DBView view2 = new DBView();
        view2.setViewName("v_employees");
        view2.setSchemaName("hr");

        String result2 = template.generateCreateObjectTemplate(view2);
        Assert.assertTrue(result2.contains("\"hr\".\"v_employees\""));
    }

    @Test
    public void generateCreateObjectTemplate_viewWithMultipleColumns_generateSucceed() {
        DBObjectTemplate<DBView> template = new PostgresViewTemplate();
        DBView view = new DBView();
        view.setViewName("v_test");
        view.setSchemaName("public");
        // Create view units for 3 tables
        view.setViewUnits(prepareViewUnits(3, true));
        view.setOperations(Arrays.asList("left join", "left join"));
        // Create columns for 3 tables
        view.setCreateColumns(prepareColumns(3));

        String result = template.generateCreateObjectTemplate(view);

        // Verify columns are present (with or without alias prefix depending on BaseViewTemplate logic)
        Assert.assertTrue("Should contain col_0", result.contains("\"col_0\""));
        Assert.assertTrue("Should contain col2_0", result.contains("\"col2_0\""));
        Assert.assertTrue("Should contain col_1", result.contains("\"col_1\""));
    }

    private List<DBViewColumn> prepareColumns(int size) {
        List<DBViewColumn> viewColumns = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            DBViewColumn viewColumn1 = new DBViewColumn();
            viewColumn1.setColumnName("col_" + i);
            viewColumn1.setAliasName("alias_col" + i);
            viewColumn1.setDbName("public");
            viewColumn1.setTableName("table_" + i);
            viewColumn1.setTableAliasName("t" + i);

            DBViewColumn viewColumn2 = new DBViewColumn();
            viewColumn2.setColumnName("col2_" + i);
            viewColumn2.setAliasName("alias_col2_" + i);
            viewColumn2.setDbName("public");
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
            viewUnit.setDbName("public");
            viewUnit.setTableName("table_" + i);
            if (withAlias) {
                viewUnit.setTableAliasName("t" + i);
            }
            viewUnits.add(viewUnit);
        }
        return viewUnits;
    }

}
