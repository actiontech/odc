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
package com.oceanbase.tools.dbbrowser.editor.postgre;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javax.validation.constraints.NotNull;

import com.oceanbase.tools.dbbrowser.editor.DBTableColumnEditor;
import com.oceanbase.tools.dbbrowser.model.DBTableColumn;
import com.oceanbase.tools.dbbrowser.util.PostgresSqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.StringUtils;

/**
 * PostgreSQL 列编辑器
 *
 * <p>
 * PostgreSQL 的 ALTER COLUMN 语法与 MySQL/Oracle 有较大差异：
 * </p>
 * <ul>
 * <li>修改类型：ALTER TABLE ... ALTER COLUMN ... TYPE new_type</li>
 * <li>设置默认值：ALTER TABLE ... ALTER COLUMN ... SET DEFAULT value</li>
 * <li>删除默认值：ALTER TABLE ... ALTER COLUMN ... DROP DEFAULT</li>
 * <li>设置非空：ALTER TABLE ... ALTER COLUMN ... SET NOT NULL</li>
 * <li>删除非空：ALTER TABLE ... ALTER COLUMN ... DROP NOT NULL</li>
 * <li>重命名列：ALTER TABLE ... RENAME COLUMN old TO new</li>
 * <li>列注释：COMMENT ON COLUMN ... IS 'comment'</li>
 * </ul>
 *
 * <p>
 * 注意：PostgreSQL 的 ALTER COLUMN 每次只能修改一个属性，不能合并为一条语句。
 * </p>
 *
 * @author odc
 * @since ODC_release_4.3.4
 */
public class PostgresColumnEditor extends DBTableColumnEditor {

    @Override
    protected SqlBuilder sqlBuilder() {
        return new PostgresSqlBuilder();
    }

    @Override
    protected boolean appendColumnKeyWord() {
        return false;
    }

    @Override
    protected List<DBColumnModifier> getSupportColumnModifiers() {
        return Arrays.asList(
                new PostgresDataTypeModifier(),
                new PostgresNullNotNullModifier(),
                new PostgresDefaultOptionModifier());
    }

    /**
     * 生成添加列的 DDL 语句
     *
     * <p>
     * PostgreSQL 添加列语法： ALTER TABLE "schema"."table" ADD COLUMN "column_name" type [NOT NULL] [DEFAULT
     * value];
     * </p>
     *
     * @param column 要添加的列定义
     * @return 生成的 DDL 语句字符串
     */
    @Override
    public String generateCreateObjectDDL(@NotNull DBTableColumn column) {
        SqlBuilder sqlBuilder = sqlBuilder();

        // 生成 ALTER TABLE ... ADD COLUMN 语句
        sqlBuilder.append("ALTER TABLE ").append(getFullyQualifiedTableName(column))
                .append(" ADD COLUMN ");
        appendColumnDefinition(column, sqlBuilder);
        sqlBuilder.append(";").line();

        // 生成列注释语句
        generateColumnComment(column, sqlBuilder);

        String ddl = sqlBuilder.toString();
        if (!ddl.trim().endsWith(";")) {
            ddl += ";\n";
        }
        return ddl;
    }

    /**
     * 生成更新列的 DDL 语句
     *
     * <p>
     * PostgreSQL 的 ALTER COLUMN 每次只能修改一个属性，因此需要对比新旧列， 对每个变更属性分别生成对应的 ALTER 语句。
     * </p>
     *
     * @param oldColumn 修改前的列定义
     * @param newColumn 修改后的列定义
     * @return 生成的 DDL 语句字符串
     */
    @Override
    public String generateUpdateObjectDDL(@NotNull DBTableColumn oldColumn,
            @NotNull DBTableColumn newColumn) {
        SqlBuilder sqlBuilder = sqlBuilder();

        // 步骤1：检查列名是否改变
        if (!StringUtils.equals(oldColumn.getName(), newColumn.getName())) {
            sqlBuilder.append(generateRenameObjectDDL(oldColumn, newColumn)).append("\n");
        }

        // 步骤2：检查数据类型是否改变
        if (!isTypeEqual(oldColumn, newColumn)) {
            generateAlterColumnType(newColumn, sqlBuilder);
        }

        // 步骤3：检查默认值是否改变
        if (!Objects.equals(oldColumn.getDefaultValue(), newColumn.getDefaultValue())) {
            generateAlterDefaultValue(newColumn, sqlBuilder);
        }

        // 步骤4：检查 NOT NULL 是否改变
        if (!Objects.equals(oldColumn.getNullable(), newColumn.getNullable())) {
            generateAlterNullable(newColumn, sqlBuilder);
        }

        // 步骤5：检查列注释是否改变
        if (!Objects.equals(oldColumn.getComment(), newColumn.getComment())) {
            generateColumnComment(newColumn, sqlBuilder);
        }

        return sqlBuilder.toString();
    }

    /**
     * 生成删除列的 DDL 语句
     *
     * <p>
     * PostgreSQL 删除列语法：ALTER TABLE "schema"."table" DROP COLUMN "column_name";
     * </p>
     *
     * @param column 要删除的列定义
     * @return 生成的 DDL 语句字符串
     */
    @Override
    public String generateDropObjectDDL(@NotNull DBTableColumn column) {
        SqlBuilder sqlBuilder = sqlBuilder();
        sqlBuilder.append("ALTER TABLE ").append(getFullyQualifiedTableName(column))
                .append(" DROP COLUMN ").identifier(column.getName()).append(";\n");
        return sqlBuilder.toString();
    }

    /**
     * 生成重命名列的 DDL 语句
     *
     * <p>
     * PostgreSQL 重命名列语法：ALTER TABLE "schema"."table" RENAME COLUMN "old" TO "new";
     * </p>
     *
     * @param oldColumn 重命名前的列定义
     * @param newColumn 重命名后的列定义
     * @return 生成的 DDL 语句字符串
     */
    @Override
    public String generateRenameObjectDDL(@NotNull DBTableColumn oldColumn,
            @NotNull DBTableColumn newColumn) {
        SqlBuilder sqlBuilder = sqlBuilder();
        sqlBuilder.append("ALTER TABLE ").append(getFullyQualifiedTableName(oldColumn))
                .append(" RENAME COLUMN ").identifier(oldColumn.getName())
                .append(" TO ").identifier(newColumn.getName()).append(";");
        return sqlBuilder.toString();
    }

    /**
     * 生成列注释的 DDL 语句
     *
     * <p>
     * PostgreSQL 列注释语法：COMMENT ON COLUMN "schema"."table"."column" IS 'comment';
     * </p>
     *
     * @param column 列定义
     * @param sqlBuilder SQL 构建器
     */
    @Override
    protected void generateColumnComment(DBTableColumn column, SqlBuilder sqlBuilder) {
        if (StringUtils.isBlank(column.getComment())) {
            return;
        }
        sqlBuilder.append("COMMENT ON COLUMN ")
                .append(getFullyQualifiedTableName(column)).append(".")
                .identifier(column.getName())
                .append(" IS ").value(column.getComment()).append(";\n");
    }

    /**
     * 生成修改列类型的 DDL 语句
     *
     * <p>
     * PostgreSQL 语法：ALTER TABLE "schema"."table" ALTER COLUMN "column" TYPE new_type;
     * </p>
     */
    private void generateAlterColumnType(DBTableColumn column, SqlBuilder sqlBuilder) {
        sqlBuilder.append("ALTER TABLE ").append(getFullyQualifiedTableName(column))
                .append(" ALTER COLUMN ").identifier(column.getName())
                .append(" TYPE ");
        appendColumnType(column, sqlBuilder);
        sqlBuilder.append(";\n");
    }

    /**
     * 生成修改列默认值的 DDL 语句
     *
     * <p>
     * PostgreSQL 语法：
     * <ul>
     * <li>设置默认值：ALTER TABLE ... ALTER COLUMN ... SET DEFAULT value;</li>
     * <li>删除默认值：ALTER TABLE ... ALTER COLUMN ... DROP DEFAULT;</li>
     * </ul>
     * </p>
     */
    private void generateAlterDefaultValue(DBTableColumn column, SqlBuilder sqlBuilder) {
        sqlBuilder.append("ALTER TABLE ").append(getFullyQualifiedTableName(column))
                .append(" ALTER COLUMN ").identifier(column.getName());
        if (StringUtils.isNotBlank(column.getDefaultValue())) {
            sqlBuilder.append(" SET DEFAULT ").append(column.getDefaultValue());
        } else {
            sqlBuilder.append(" DROP DEFAULT");
        }
        sqlBuilder.append(";\n");
    }

    /**
     * 生成修改列 NOT NULL 的 DDL 语句
     *
     * <p>
     * PostgreSQL 语法：
     * <ul>
     * <li>设置非空：ALTER TABLE ... ALTER COLUMN ... SET NOT NULL;</li>
     * <li>删除非空：ALTER TABLE ... ALTER COLUMN ... DROP NOT NULL;</li>
     * </ul>
     * </p>
     */
    private void generateAlterNullable(DBTableColumn column, SqlBuilder sqlBuilder) {
        sqlBuilder.append("ALTER TABLE ").append(getFullyQualifiedTableName(column))
                .append(" ALTER COLUMN ").identifier(column.getName());
        if (Boolean.FALSE.equals(column.getNullable())) {
            sqlBuilder.append(" SET NOT NULL");
        } else {
            sqlBuilder.append(" DROP NOT NULL");
        }
        sqlBuilder.append(";\n");
    }

    /**
     * 判断两个列的数据类型是否相等
     */
    private boolean isTypeEqual(DBTableColumn oldColumn, DBTableColumn newColumn) {
        if (!Objects.equals(oldColumn.getTypeName(), newColumn.getTypeName())) {
            return false;
        }
        if (!Objects.equals(oldColumn.getPrecision(), newColumn.getPrecision())) {
            return false;
        }
        if (!Objects.equals(oldColumn.getScale(), newColumn.getScale())) {
            return false;
        }
        return true;
    }

    /**
     * 追加列类型定义
     */
    private void appendColumnType(DBTableColumn column, SqlBuilder sqlBuilder) {
        String typeName = column.getTypeName();
        Long precision = column.getPrecision();
        Integer scale = column.getScale();

        sqlBuilder.append(typeName);
        if (needsPrecision(typeName)) {
            if (precision != null) {
                sqlBuilder.append("(").append(precision);
                if (scale != null) {
                    sqlBuilder.append(",").append(scale);
                }
                sqlBuilder.append(")");
            }
        }
    }

    /**
     * 判断数据类型是否需要精度参数
     */
    private boolean needsPrecision(String typeName) {
        if (StringUtils.isBlank(typeName)) {
            return false;
        }
        String lowerTypeName = typeName.toLowerCase();
        return lowerTypeName.equals("varchar") || lowerTypeName.equals("char")
                || lowerTypeName.equals("character varying") || lowerTypeName.equals("character")
                || lowerTypeName.equals("numeric") || lowerTypeName.equals("decimal")
                || lowerTypeName.equals("bit") || lowerTypeName.equals("varbit");
    }

    /**
     * PostgreSQL 数据类型修饰符
     */
    protected class PostgresDataTypeModifier implements DBColumnModifier {
        @Override
        public void appendModifier(DBTableColumn column, SqlBuilder sqlBuilder) {
            sqlBuilder.space();
            appendColumnType(column, sqlBuilder);
        }
    }

    /**
     * PostgreSQL NULL/NOT NULL 修饰符
     */
    protected class PostgresNullNotNullModifier implements DBColumnModifier {
        @Override
        public void appendModifier(DBTableColumn column, SqlBuilder sqlBuilder) {
            sqlBuilder.append(column.getNullable() ? " NULL" : " NOT NULL");
        }
    }

    /**
     * PostgreSQL 默认值修饰符
     */
    protected class PostgresDefaultOptionModifier implements DBColumnModifier {
        @Override
        public void appendModifier(DBTableColumn column, SqlBuilder sqlBuilder) {
            if (StringUtils.isNotBlank(column.getDefaultValue())) {
                sqlBuilder.append(" DEFAULT ").append(column.getDefaultValue());
            }
        }
    }
}
