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
package com.oceanbase.tools.dbbrowser.template.sqlserver;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.validation.constraints.NotNull;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.Validate;

import com.oceanbase.tools.dbbrowser.model.DBView;
import com.oceanbase.tools.dbbrowser.model.DBView.DBViewUnit;
import com.oceanbase.tools.dbbrowser.model.DBViewColumn;
import com.oceanbase.tools.dbbrowser.schema.sqlserver.SqlServerSchemaUtil;
import com.oceanbase.tools.dbbrowser.template.BaseViewTemplate;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlServerSqlBuilder;
import com.oceanbase.tools.dbbrowser.util.StringUtils;

import lombok.Getter;
import lombok.Setter;

/**
 * @author yizhou.xw
 * @date 2024/12
 * @since ODC_release_4.3.4
 */
public class SqlServerViewTemplate extends BaseViewTemplate {

    @Override
    protected String preHandle(String str) {
        return str.toUpperCase();
    }

    @Override
    protected SqlBuilder sqlBuilder() {
        return new SqlServerSqlBuilder();
    }

    /**
     * 完全自定义 SQL Server 的视图生成逻辑，生成格式： USE [database]; GO CREATE VIEW [schema].[viewName] AS SELECT
     * [columns] FROM [database].[schema].[table] [JOIN ...]
     * 
     * schemaName 格式必须为 database.schema（如 wenshu_test.dbo）
     */
    @Override
    public String generateCreateObjectTemplate(@NotNull DBView dbObject) {
        Validate.notBlank(dbObject.getViewName(), "View name can not be blank");
        validOperations(dbObject);

        SqlBuilder sqlBuilder = sqlBuilder();

        // 解析 schema 名称（格式：database.schema）
        String[] dbAndSchema = SqlServerSchemaUtil.parseDatabaseAndSchema(dbObject.getViewUnits().get(0).getDbName());
        String databaseName = dbAndSchema[0] != null ? dbAndSchema[0] : "请填写数据库名";
        String actualSchemaName = dbAndSchema[1] != null ? dbAndSchema[1] : "dbo";

        // 1. 生成 USE 语句
        sqlBuilder.append("USE ").identifier(databaseName).append(";\n");
        sqlBuilder.append("GO\n");

        // 2. 生成 CREATE VIEW [schema].[viewName] AS
        sqlBuilder.append("CREATE VIEW ");
        if (StringUtils.isNotBlank(actualSchemaName)) {
            sqlBuilder.identifier(actualSchemaName).append(".");
        }
        sqlBuilder.identifier(dbObject.getViewName())
                .append(" AS");

        // 3. 生成查询部分（包含 SELECT、FROM、JOIN 等）
        generateQueryStatementForSqlServer(dbObject, sqlBuilder);

        return sqlBuilder.toString();
    }

    /**
     * 为 SQL Server 生成查询语句，正确处理三部分名称格式 [database].[schema].[table]
     */
    private void generateQueryStatementForSqlServer(DBView dbObject, SqlBuilder sqlBuilder) {
        // 创建参数对象来处理视图创建逻辑
        ViewCreateParameters params = new ViewCreateParameters(dbObject);
        params.getSubParameters().forEach(p -> {
            if (Objects.isNull(p.getViewUnits()) && Objects.isNull(p.getColumns())) {
                // here we will only get one operation
                sqlBuilder.append("\n").append(preHandle(p.getOperations().get(0))).space();
                return;
            }
            handleQueryForSqlServer(sqlBuilder, p);
        });
    }

    private void handleQueryForSqlServer(SqlBuilder sqlBuilder, ViewCreateSubParameters subParam) {
        sqlBuilder.append("\n").append(preHandle("select"));
        List<DBViewColumn> columns = subParam.getColumns();
        if (CollectionUtils.isEmpty(columns)) {
            sqlBuilder.space().append("\n\t").append("*");
        } else {
            for (int i = 0; i < columns.size(); i++) {
                DBViewColumn column = subParam.getColumns().get(i);
                sqlBuilder.append("\n\t");
                handleColumnNameForSqlServer(sqlBuilder, column, i, subParam);
                if (i < columns.size() - 1) {
                    sqlBuilder.append(",");
                }
            }
        }
        sqlBuilder.append(preHandle("\nfrom"));
        handleJoinForSqlServer(sqlBuilder, subParam);
    }

    private void handleColumnNameForSqlServer(SqlBuilder sqlBuilder, DBViewColumn currentColumn, int index,
            ViewCreateSubParameters subParam) {
        String currentName = currentColumn.getColumnName();
        String currentAlias = currentColumn.getAliasName();
        if (Objects.isNull(currentColumn.getDbName()) || Objects.isNull(currentColumn.getTableName())) {
            // 自定义列不作特殊处理，需要调用端自行保证正确性
            sqlBuilder.append(currentName);
            if (StringUtils.isNotEmpty(currentAlias)) {
                sqlBuilder.append(preHandle(" as ")).identifier(currentAlias);
            }
            return;
        }
        String tableAlias = currentColumn.getTableAliasName();
        if (StringUtils.isNotEmpty(tableAlias)) {
            // table alias name will be added as column prefix if exist
            sqlBuilder.identifier(tableAlias)
                    .append(".")
                    .identifier(currentName);
            if (StringUtils.isNotEmpty(currentAlias)) {
                sqlBuilder.append(preHandle(" as ")).identifier(currentAlias);
            }
            return;
        }
        // 构建表前缀（用于多表查询时区分列来源）
        SqlBuilder prefixBuilder = sqlBuilder();
        // 对于 SQL Server，需要解析 database.schema 格式
        String[] dbAndSchema = SqlServerSchemaUtil.parseDatabaseAndSchema(currentColumn.getDbName());
        String databaseName = dbAndSchema[0];
        String schemaName = dbAndSchema[1];
        if (!subParam.getParent().isSingleSchema()) {
            if (StringUtils.isNotBlank(databaseName)) {
                prefixBuilder.identifier(databaseName).append(".");
            }
            if (StringUtils.isNotBlank(schemaName)) {
                prefixBuilder.identifier(schemaName).append(".");
            }
        }
        prefixBuilder.identifier(currentColumn.getTableName());
        if (!subParam.getParent().isSingleTable()) {
            sqlBuilder.append(prefixBuilder.toString()).append(".");
        }
        sqlBuilder.identifier(currentName);
        // SQL Server 别名只能是单一标识符，不能是多部分名称
        if (StringUtils.isNotEmpty(currentAlias)) {
            sqlBuilder.append(preHandle(" as ")).identifier(currentAlias);
        }
    }

    private void handleJoinForSqlServer(SqlBuilder sqlBuilder, ViewCreateSubParameters subParam) {
        // construct join part with SQL Server three-part naming: [database].[schema].[table]
        boolean hasCommaOperation = false;
        List<DBViewUnit> units = subParam.getViewUnits();
        sqlBuilder.append("\n\t");
        for (int i = 0; i < units.size(); i++) {
            DBViewUnit currentUnit = units.get(i);
            // 解析 database.schema 格式，生成 [database].[schema].[table]
            String[] dbAndSchema = SqlServerSchemaUtil.parseDatabaseAndSchema(currentUnit.getDbName());
            String databaseName = dbAndSchema[0];
            String schemaName = dbAndSchema[1];
            if (StringUtils.isNotBlank(databaseName)) {
                sqlBuilder.identifier(databaseName).append(".");
            }
            if (StringUtils.isNotBlank(schemaName)) {
                sqlBuilder.identifier(schemaName).append(".");
            }
            sqlBuilder.identifier(currentUnit.getTableName());
            if (currentUnit.getTableAliasName() != null) {
                sqlBuilder.space().identifier(currentUnit.getTableAliasName());
            }
            if (i > 0) {
                String preOperation = subParam.getOperations().get(i - 1);
                if (",".equals(preOperation)) {
                    // , operation correspond to where at the end of sub sentence
                    hasCommaOperation = true;
                } else {
                    // other join operation correspond to on
                    // Note: JOIN conditions need to be manually added by the user
                    sqlBuilder.append(preHandle(" on"))
                            .append(" 1=1");
                }
            }
            if (i < units.size() - 1) {
                String currentOperation = subParam.getOperations().get(i);
                if (!",".equals(currentOperation)) {
                    sqlBuilder.append("\n\t");
                }
                sqlBuilder.append(preHandle(currentOperation)).space();
            }
        }
        if (!hasCommaOperation) {
            return;
        }
        // Note: WHERE conditions need to be manually added by the user when using comma-separated tables
        sqlBuilder.append(preHandle("\nwhere"))
                .append(" 1=1");
    }

    /**
     * 内部类：用于处理视图创建参数，复制基类逻辑但适配 SQL Server
     */
    @Getter
    @Setter
    private static class ViewCreateSubParameters {
        private List<DBViewUnit> viewUnits;
        private List<DBViewColumn> columns;
        private List<String> operations;
        private final ViewCreateParameters parent;

        public ViewCreateSubParameters(ViewCreateParameters parent) {
            this.parent = parent;
        }

        public void addOperation(String operation) {
            if (Objects.isNull(operations)) {
                operations = new ArrayList<>();
            }
            operations.add(operation);
        }
    }

    /**
     * 内部类：用于处理视图创建参数，复制基类逻辑但适配 SQL Server
     */
    @Getter
    @Setter
    private static class ViewCreateParameters {
        private List<ViewCreateSubParameters> subParameters;
        private Set<String> tableSet;
        private Set<String> schemaSet;

        public ViewCreateParameters(DBView view) {
            subParameters = new ArrayList<>();
            tableSet = new HashSet<>();
            schemaSet = new HashSet<>();
            transform(view);
        }

        public boolean isSingleSchema() {
            return schemaSet.size() == 1;
        }

        public boolean isSingleTable() {
            return tableSet.size() == 1;
        }

        private void transform(DBView view) {
            List<DBViewUnit> viewUnits = view.getViewUnits();
            List<String> operations = view.getOperations();
            int unitIndex = 0;
            int operationIndex = 0;
            while (operationIndex < operations.size()) {
                String currentOperation = operations.get(operationIndex);
                if (!isJoin(currentOperation)) {
                    // collect to the last join
                    collectColumns(view, unitIndex, operationIndex);
                    // collect current operation which is not join
                    ViewCreateSubParameters currentSubParam = new ViewCreateSubParameters(this);
                    currentSubParam.addOperation(currentOperation);
                    subParameters.add(currentSubParam);

                    unitIndex = operationIndex + 1;
                }
                operationIndex++;
            }
            if (unitIndex < viewUnits.size()) {
                collectColumns(view, unitIndex, operationIndex);
            }
        }

        private boolean isJoin(String operation) {
            // , means inner join
            return StringUtils.containsAnyIgnoreCase(operation, "join") || ",".equals(operation);
        }

        private void collectColumns(DBView view, int unitIndex, int operationIndex) {
            ViewCreateSubParameters subParam = new ViewCreateSubParameters(this);
            List<DBViewUnit> dbViewUnits = view.getViewUnits().subList(unitIndex, operationIndex + 1);
            subParam.setViewUnits(dbViewUnits);
            List<String> operationSub = view.getOperations().subList(unitIndex, operationIndex);
            subParam.setOperations(operationSub);
            // to distinguish whether current column belongs to current bucket
            Set<String> localTableSet = new HashSet<>();
            for (DBViewUnit unit : dbViewUnits) {
                localTableSet.add(unit.getDbName() + "." + unit.getTableName());
                schemaSet.add(unit.getDbName());
            }
            List<DBViewColumn> columns = new ArrayList<>();
            if (!Objects.isNull(view.getCreateColumns())) {
                for (DBViewColumn column : view.getCreateColumns()) {
                    if (Objects.isNull(column.getDbName()) || Objects.isNull(column.getTableName())) {
                        // if it is a custom column, we need to add it in with no condition
                        columns.add(column);
                    }
                    if (localTableSet.contains(column.getDbName() + "." + column.getTableName())) {
                        // if it's dbname && tablename match, we need to add it in
                        columns.add(column);
                    }
                }
            }
            subParam.setColumns(columns);
            tableSet.addAll(localTableSet);
            subParameters.add(subParam);
        }
    }


    @Override
    protected String doGenerateCreateObjectTemplate(SqlBuilder sqlBuilder, DBView dbObject) {
        // 这个方法在重写 generateCreateObjectTemplate 后不会被调用
        // 但为了满足抽象方法要求，保留一个简单实现
        return sqlBuilder.toString();
    }

}
