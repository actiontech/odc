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
package com.oceanbase.tools.dbbrowser.editor.sqlserver;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javax.validation.constraints.NotNull;

import com.oceanbase.tools.dbbrowser.editor.DBTableColumnEditor;
import com.oceanbase.tools.dbbrowser.model.DBTableColumn;
import com.oceanbase.tools.dbbrowser.model.datatype.DataTypeUtil;
import com.oceanbase.tools.dbbrowser.util.SqlBuilder;
import com.oceanbase.tools.dbbrowser.util.SqlServerSqlBuilder;
import com.oceanbase.tools.dbbrowser.util.StringUtils;

/**
 * @author yizhou.xw
 * @date 2024/12
 * @since ODC_release_4.3.4
 */
public class SqlServerColumnEditor extends DBTableColumnEditor {

    @Override
    protected SqlBuilder sqlBuilder() {
        return new SqlServerSqlBuilder();
    }

    @Override
    protected boolean appendColumnKeyWord() {
        return false;
    }

    @Override
    protected List<DBColumnModifier> getSupportColumnModifiers() {
        return Arrays.asList(new SqlServerDataTypeModifier(),
                new SqlServerNullNotNullModifier(),
                new DefaultOptionModifier());
    }

    /**
     * SQL Server 自定义数据类型修饰符，用于格式化数据类型定义 与基类 DataTypeModifier 的区别：decimal(10,2) 格式没有空格
     */
    protected class SqlServerDataTypeModifier implements DBColumnModifier {
        @Override
        public void appendModifier(DBTableColumn column, SqlBuilder sqlBuilder) {
            String typeName = column.getTypeName();
            Long precision = column.getPrecision();
            Integer scale = column.getScale();
            sqlBuilder.space().append(typeName);
            if (Objects.isNull(scale)) {
                if (Objects.nonNull(precision)) {
                    sqlBuilder.append("(").append(String.valueOf(precision)).append(")");
                }
            } else {
                if (Objects.isNull(precision)) {
                    sqlBuilder.append("(").append(String.valueOf(scale)).append(")");
                } else {
                    // SQL Server 格式：decimal(10,2) 没有空格
                    sqlBuilder.append("(").append(String.valueOf(precision))
                            .append(",").append(String.valueOf(scale)).append(")");
                }
            }
        }
    }

    /**
     * 生成重命名列的 DDL 语句
     * 
     * 该方法使用 SQL Server 的 sp_rename 存储过程来重命名列。
     * 
     * 注意： - sp_rename 使用 '[schema].[table].[column]' 格式（使用方括号确保兼容性，特别是对于包含下划线的表名） - sp_rename
     * 只能在当前数据库中执行，不能跨数据库重命名 - 如果需要在跨数据库的情况下重命名，应该先切换到目标数据库
     * 
     * 生成的 SQL 示例： EXEC sp_rename @objname = '[schema].[table].[oldColumnName]', @newname =
     * 'newColumnName', @objtype = 'COLUMN';
     * 
     * @param oldColumn 重命名前的列定义
     * @param newColumn 重命名后的列定义
     * @return 生成的 DDL 语句字符串
     */
    @Override
    public String generateRenameObjectDDL(@NotNull DBTableColumn oldColumn,
            @NotNull DBTableColumn newColumn) {
        SqlBuilder sqlBuilder = sqlBuilder();

        // 解析数据库名和 schema 名
        String[] dbAndSchema = parseDatabaseAndSchema(oldColumn.getSchemaName());
        String databaseName = dbAndSchema[0];
        String schema = dbAndSchema[1];
        String table = oldColumn.getTableName();
        String column = oldColumn.getName();

        // sp_rename 只能在当前数据库中执行，需要先切换到目标数据库
        // 如果有数据库名，添加 USE [database] 语句
        if (StringUtils.isNotBlank(databaseName)) {
            sqlBuilder.append("USE ").identifier(databaseName).append(";").line();
        }

        // sp_rename 使用命名参数格式以避免参数歧义
        // 格式为 '[schema].[table].[column]'，使用方括号确保兼容性（特别是对于包含下划线的表名如 test_table）
        String objectName = StringUtils.quoteSqlServerIdentifier(schema) + "."
                + StringUtils.quoteSqlServerIdentifier(table) + "."
                + StringUtils.quoteSqlServerIdentifier(column);
        sqlBuilder.append("EXEC sp_rename @objname = ").value(objectName)
                .append(", @newname = ").value(newColumn.getName())
                .append(", @objtype = ").value("COLUMN").append(";");
        return sqlBuilder.toString();
    }

    /**
     * 生成添加新列的 DDL 语句
     * 
     * 该方法会生成以下 SQL 语句： 1. ALTER TABLE ... ADD 语句：添加列定义（包括列名、数据类型、精度、是否可空、默认值等） 2. 列注释添加语句（如果列有注释）：使用
     * sp_addextendedproperty 添加注释
     * 
     * 注意： - SQL Server 使用三部分名称：database.schema.table（通过 getFullyQualifiedTableName 生成） - SQL Server 的
     * ALTER TABLE ADD 语句中，默认值可以直接在列定义中指定 - 列注释通过扩展属性（extended properties）存储，使用 MS_Description 属性名
     * 
     * 生成的 SQL 示例： ALTER TABLE [database].[schema].[table] ADD [columnName] int NOT NULL DEFAULT 0; EXEC
     * sp_addextendedproperty @name=N'MS_Description', @value=N'列注释', ...;
     * 
     * @param column 要添加的列定义
     * @return 生成的 DDL 语句字符串，可能包含多条语句，用分号分隔
     */
    @Override
    public String generateCreateObjectDDL(@NotNull DBTableColumn column) {
        SqlBuilder sqlBuilder = sqlBuilder();

        // 步骤1：生成 ALTER TABLE ... ADD 语句
        // 格式：ALTER TABLE [database].[schema].[table] ADD [columnName] type(precision,scale) NULL/NOT NULL
        // DEFAULT value
        sqlBuilder.append("ALTER TABLE ").append(getFullyQualifiedTableName(column))
                .append(" ADD ");

        // SQL Server 不需要 COLUMN 关键字（appendColumnKeyWord 返回 false）
        if (appendColumnKeyWord()) {
            sqlBuilder.append("COLUMN ");
        }

        // 添加列 definition：列名 + 数据类型 + 精度/小数位数 + 是否可空 + 默认值
        // 这些信息通过 appendColumnDefinition 方法添加，它会调用各个 DBColumnModifier
        appendColumnDefinition(column, sqlBuilder);
        sqlBuilder.append(";").line();

        // 步骤1.1：生成 unsigned 约束语句（如果需要）
        // 在列定义生成后，如果 unsigned 为 true，添加 CHECK 约束
        if (Boolean.TRUE.equals(column.getUnsigned()) && isNumericType(column.getTypeName())) {
            sqlBuilder.append("ALTER TABLE ").append(getFullyQualifiedTableName(column))
                    .append(" ADD CONSTRAINT CK_").append(column.getTableName())
                    .append("_").append(column.getName())
                    .append("_unsigned CHECK (").identifier(column.getName())
                    .append(" >= 0);").line();
        }

        // 步骤2：生成列注释语句（如果列有注释）
        // 使用 sp_addextendedproperty 存储列注释到扩展属性中
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
     * 该方法会对比旧列和新列的属性，按顺序生成以下 SQL 语句（如果属性有变化）： 1. 列名重命名语句（如果列名改变） 2. 列注释更新语句（如果注释改变） 3.
     * 列定义修改语句（如果类型、精度、是否可空等改变） 4. 默认值修改语句（如果默认值改变，以注释形式提供模板）
     * 
     * 注意：SQL Server 的 ALTER COLUMN 不支持直接修改 DEFAULT 值，需要通过删除旧约束 再添加新约束来实现，但需要知道旧约束的名称，因此这里只提供注释模板。
     * 
     * @param oldColumn 修改前的列定义
     * @param newColumn 修改后的列定义
     * @return 生成的 DDL 语句字符串，可能包含多条语句，用分号分隔
     */
    @Override
    public String generateUpdateObjectDDL(@NotNull DBTableColumn oldColumn,
            @NotNull DBTableColumn newColumn) {
        SqlBuilder sqlBuilder = sqlBuilder();

        // 步骤1：检查列名是否改变
        // 如果列名改变了，生成重命名语句：EXEC sp_rename 'schema.table.oldName', 'newName', 'COLUMN';
        if (!StringUtils.equals(oldColumn.getName(), newColumn.getName())) {
            sqlBuilder.append(generateRenameObjectDDL(oldColumn, newColumn)).append("\n");
        }

        // 步骤2：检查列定义是否改变（类型、精度、小数位数、是否可空）
        // SQL Server 的 ALTER COLUMN 不支持在同一个语句中修改 DEFAULT 值
        // DEFAULT 值必须通过约束（constraint）单独处理
        if (!isColumnDefinitionEqual(oldColumn, newColumn)) {
            // 生成 ALTER TABLE ... ALTER COLUMN 语句
            // 格式：ALTER TABLE [database].[schema].[table] ALTER COLUMN [columnName] type(precision,scale)
            // NULL/NOT NULL
            sqlBuilder.append("ALTER TABLE ").append(getFullyQualifiedTableName(oldColumn))
                    .append(" ALTER COLUMN ").identifier(newColumn.getName()).space();

            // 添加数据类型
            sqlBuilder.append(newColumn.getTypeName());

            // 添加精度和小数位数（根据类型判断是否需要）
            // 例如：decimal(10,2) 或 varchar(100) 或 int（不需要精度）
            String typeName = newColumn.getTypeName();
            if (needsPrecisionAndScale(typeName)) {
                // 需要精度和小数位数：decimal(10,2)
                if (newColumn.getPrecision() != null && newColumn.getScale() != null) {
                    sqlBuilder.append("(").append(newColumn.getPrecision().toString())
                            .append(",").append(newColumn.getScale().toString()).append(")");
                }
            } else if (needsPrecision(typeName)) {
                // 只需要精度（长度）：varchar(100)
                if (newColumn.getPrecision() != null) {
                    sqlBuilder.append("(").append(newColumn.getPrecision().toString()).append(")");
                }
            } else if (needsScaleOnly(typeName)) {
                // 只需要小数位数：datetime2(7)
                if (newColumn.getScale() != null) {
                    sqlBuilder.append("(").append(newColumn.getScale().toString()).append(")");
                }
            }
            // 对于 int, bigint, smallint, tinyint 等类型，即使有 precision 和 scale 也不输出

            // 添加是否可空约束
            sqlBuilder.append(newColumn.getNullable() ? " NULL" : " NOT NULL");
            sqlBuilder.append(";\n");
        }

        // 步骤3：检查列注释是否改变
        // 如果注释改变了，生成更新注释的语句（使用 sp_updateextendedproperty 或 sp_addextendedproperty）
        if (!Objects.equals(oldColumn.getComment(), newColumn.getComment())) {
            generateColumnComment(newColumn, sqlBuilder);
        }

        // 步骤4：检查默认值和 zerofill 是否改变
        // 注意：修改默认值需要先删除旧的 DEFAULT 约束，再添加新的约束
        // 但删除约束需要知道约束的名称，ODC 可能没有这个信息
        // 因此这里只提供注释形式的模板，供用户手动修改
        String oldDefault = oldColumn.getDefaultValue();
        String newDefault = newColumn.getDefaultValue();
        Boolean oldZerofill = oldColumn.getZerofill();
        Boolean newZerofill = newColumn.getZerofill();

        // 确定最终需要的默认值
        String effectiveOldDefault = oldDefault;
        if (StringUtils.isBlank(effectiveOldDefault) && Boolean.TRUE.equals(oldZerofill)
                && isNumericType(oldColumn.getTypeName())) {
            effectiveOldDefault = "0";
        }

        String effectiveNewDefault = newDefault;
        if (StringUtils.isBlank(effectiveNewDefault) && Boolean.TRUE.equals(newZerofill)
                && isNumericType(newColumn.getTypeName())) {
            effectiveNewDefault = "0";
        }

        if (!Objects.equals(effectiveOldDefault, effectiveNewDefault)) {
            // 如果最终需要的默认值改变了，生成注释形式的添加约束语句
            if (StringUtils.isNotBlank(effectiveNewDefault)) {
                sqlBuilder.append("-- ALTER TABLE ").append(getFullyQualifiedTableName(oldColumn))
                        .append(" ADD CONSTRAINT DF_").append(newColumn.getTableName())
                        .append("_").append(newColumn.getName())
                        .append(" DEFAULT ").append(effectiveNewDefault)
                        .append(" FOR ").identifier(newColumn.getName()).append(";\n");
            } else {
                // 如果新默认值为空，提示删除旧约束
                sqlBuilder.append("-- ALTER TABLE ").append(getFullyQualifiedTableName(oldColumn))
                        .append(" DROP CONSTRAINT DF_").append(oldColumn.getTableName())
                        .append("_").append(oldColumn.getName()).append(";\n");
            }
        }

        // 步骤5：检查 unsigned 是否改变
        Boolean oldUnsigned = oldColumn.getUnsigned();
        Boolean newUnsigned = newColumn.getUnsigned();
        if (!Objects.equals(oldUnsigned, newUnsigned) && isNumericType(newColumn.getTypeName())) {
            if (Boolean.TRUE.equals(newUnsigned)) {
                // 添加 CHECK 约束
                sqlBuilder.append("ALTER TABLE ").append(getFullyQualifiedTableName(newColumn))
                        .append(" ADD CONSTRAINT CK_").append(newColumn.getTableName())
                        .append("_").append(newColumn.getName())
                        .append("_unsigned CHECK (").identifier(newColumn.getName())
                        .append(" >= 0);\n");
            } else {
                // 删除 CHECK 约束
                sqlBuilder.append("-- ALTER TABLE ").append(getFullyQualifiedTableName(oldColumn))
                        .append(" DROP CONSTRAINT CK_").append(oldColumn.getTableName())
                        .append("_").append(oldColumn.getName()).append("_unsigned;\n");
            }
        }

        return sqlBuilder.toString();
    }

    /**
     * 判断两个列的列定义是否相等
     * 
     * 比较以下属性： - 数据类型名称（typeName） - 精度（precision） - 小数位数（scale） - 是否可空（nullable）
     * 
     * 注意：此方法不比较列名、注释和默认值，这些属性由其他逻辑单独处理 对于不支持 precision 的类型（如 int, bigint 等），忽略 precision 的比较
     * 
     * @param oldColumn 旧列定义
     * @param newColumn 新列定义
     * @return 如果列定义相同返回 true，否则返回 false
     */
    private boolean isColumnDefinitionEqual(DBTableColumn oldColumn, DBTableColumn newColumn) {
        if (!Objects.equals(oldColumn.getTypeName(), newColumn.getTypeName())) {
            return false;
        }
        if (!Objects.equals(oldColumn.getNullable(), newColumn.getNullable())) {
            return false;
        }
        // 对于不支持 precision 的类型，忽略 precision 的比较
        String typeName = newColumn.getTypeName();
        if (needsPrecision(typeName) || needsPrecisionAndScale(typeName) || needsScaleOnly(typeName)) {
            // 这些类型需要比较 precision 和 scale
            if (!Objects.equals(oldColumn.getPrecision(), newColumn.getPrecision())) {
                return false;
            }
            if (!Objects.equals(oldColumn.getScale(), newColumn.getScale())) {
                return false;
            }
        }
        // 对于 int, bigint, smallint, tinyint 等类型，即使 precision 不同也认为定义相同
        return true;
    }

    @Override
    protected String getFullyQualifiedTableName(@NotNull DBTableColumn column) {
        SqlBuilder sqlBuilder = sqlBuilder();
        String schemaName = column.getSchemaName();

        // 解析数据库名和 schema 名
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        String databaseName = dbAndSchema[0];
        String actualSchemaName = dbAndSchema[1];

        // SQL Server 三部分名称：database.schema.table
        if (StringUtils.isNotBlank(databaseName)) {
            sqlBuilder.identifier(databaseName).append(".");
        }
        if (StringUtils.isNotBlank(actualSchemaName)) {
            sqlBuilder.identifier(actualSchemaName).append(".");
        }
        if (StringUtils.isNotBlank(column.getTableName())) {
            sqlBuilder.identifier(column.getTableName());
        }
        return sqlBuilder.toString();
    }

    /**
     * 解析 schemaName，支持两种格式： 1. "database" - 只有数据库名，默认使用 dbo schema 2. "database.schema" - 数据库名和 schema
     * 名
     *
     * @param schemaName 可能是数据库名或 database.schema 格式
     * @return [数据库名, schema名] 数组
     */
    private String[] parseDatabaseAndSchema(String schemaName) {
        if (StringUtils.isBlank(schemaName)) {
            return new String[] {null, "dbo"};
        }

        if (schemaName.contains(".")) {
            String[] parts = schemaName.split("\\.", 2);
            if (parts.length == 2 && StringUtils.isNotBlank(parts[0]) && StringUtils.isNotBlank(parts[1])) {
                return new String[] {parts[0], parts[1]};
            }
            // 如果格式不正确，返回默认值
            return new String[] {schemaName, "dbo"};
        } else {
            // 只有数据库名，默认使用 dbo schema
            return new String[] {schemaName, "dbo"};
        }
    }

    /**
     * 解析 schemaName，支持两种格式： 1. "database" - 只有数据库名，默认使用 dbo schema 2. "database.schema" - 数据库名和 schema
     * 名
     *
     * @param schemaName 可能是数据库名或 database.schema 格式
     * @return 实际的 schema 名
     */
    private String parseSchemaName(String schemaName) {
        String[] dbAndSchema = parseDatabaseAndSchema(schemaName);
        return dbAndSchema[1];
    }

    @Override
    protected void generateColumnComment(DBTableColumn column, SqlBuilder sqlBuilder) {
        if (StringUtils.isBlank(column.getComment())) {
            return;
        }
        String schema = parseSchemaName(column.getSchemaName());
        String table = column.getTableName();
        String name = column.getName();
        String comment = column.getComment();

        sqlBuilder.append("IF EXISTS (SELECT 1 FROM sys.extended_properties WHERE name = N'MS_Description' "
                + "AND major_id = OBJECT_ID(N'").append(schema).append(".").append(table).append("') "
                        + "AND minor_id = (SELECT column_id FROM sys.columns WHERE name = N'")
                .append(name)
                .append("' AND object_id = OBJECT_ID(N'").append(schema).append(".").append(table).append("')))").line()
                .append("  EXEC sp_updateextendedproperty @name=N'MS_Description', @value=N")
                .value(comment)
                .append(", @level0type=N'SCHEMA', @level0name=N").value(schema)
                .append(", @level1type=N'TABLE', @level1name=N").value(table)
                .append(", @level2type=N'COLUMN', @level2name=N").value(name).append(";").line()
                .append("ELSE").line()
                .append("  EXEC sp_addextendedproperty @name=N'MS_Description', @value=N")
                .value(comment)
                .append(", @level0type=N'SCHEMA', @level0name=N").value(schema)
                .append(", @level1type=N'TABLE', @level1name=N").value(table)
                .append(", @level2type=N'COLUMN', @level2name=N").value(name).append(";\n");
    }

    /**
     * 生成删除列的 DDL 语句
     * 
     * 该方法会生成 ALTER TABLE ... DROP COLUMN 语句来删除指定的列。
     * 
     * 注意： - SQL Server 使用三部分名称：database.schema.table（通过 getFullyQualifiedTableName 生成） - SQL Server 的
     * DROP COLUMN 语法：ALTER TABLE [database].[schema].[table] DROP COLUMN [columnName]
     * 
     * 生成的 SQL 示例： ALTER TABLE [database].[schema].[table] DROP COLUMN [columnName];
     * 
     * @param column 要删除的列定义
     * @return 生成的 DDL 语句字符串
     */
    @Override
    public String generateDropObjectDDL(@NotNull DBTableColumn column) {
        SqlBuilder sqlBuilder = sqlBuilder();
        // 生成 ALTER TABLE ... DROP COLUMN 语句
        // 格式：ALTER TABLE [database].[schema].[table] DROP COLUMN [columnName]
        sqlBuilder.append("ALTER TABLE ").append(getFullyQualifiedTableName(column))
                .append(" DROP COLUMN ").identifier(column.getName()).append(";\n");
        return sqlBuilder.toString();
    }

    /**
     * 检查 SQL Server 数据类型是否需要精度参数
     * 
     * @param typeName 数据类型名称（不区分大小写）
     * @return 如果需要精度返回 true，否则返回 false
     */
    private boolean needsPrecision(String typeName) {
        if (StringUtils.isBlank(typeName)) {
            return false;
        }
        String lowerTypeName = typeName.toLowerCase();
        // 需要精度的类型：字符串类型、二进制类型、浮点类型
        return lowerTypeName.equals("varchar") || lowerTypeName.equals("char")
                || lowerTypeName.equals("nvarchar") || lowerTypeName.equals("nchar")
                || lowerTypeName.equals("binary") || lowerTypeName.equals("varbinary")
                || lowerTypeName.equals("float") || lowerTypeName.equals("real");
    }

    /**
     * 检查 SQL Server 数据类型是否需要精度和小数位数（precision, scale）
     * 
     * @param typeName 数据类型名称（不区分大小写）
     * @return 如果需要精度和小数位数返回 true，否则返回 false
     */
    private boolean needsPrecisionAndScale(String typeName) {
        if (StringUtils.isBlank(typeName)) {
            return false;
        }
        String lowerTypeName = typeName.toLowerCase();
        // 需要精度和小数位数的类型：decimal, numeric
        return lowerTypeName.equals("decimal") || lowerTypeName.equals("numeric");
    }

    /**
     * 检查 SQL Server 数据类型是否只需要小数位数（scale）
     * 
     * @param typeName 数据类型名称（不区分大小写）
     * @return 如果只需要小数位数返回 true，否则返回 false
     */
    private boolean needsScaleOnly(String typeName) {
        if (StringUtils.isBlank(typeName)) {
            return false;
        }
        String lowerTypeName = typeName.toLowerCase();
        // 只需要小数位数的类型：datetime2, time, datetimeoffset
        return lowerTypeName.equals("datetime2") || lowerTypeName.equals("time")
                || lowerTypeName.equals("datetimeoffset");
    }

    private boolean isNumericType(String typeName) {
        if (StringUtils.isBlank(typeName)) {
            return false;
        }
        String lowerTypeName = typeName.toLowerCase();
        // SQL Server 数值类型：整数类型、小数类型、浮点类型、货币类型
        return DataTypeUtil.isIntegerType(typeName)
                || lowerTypeName.equals("decimal") || lowerTypeName.equals("numeric")
                || lowerTypeName.equals("float") || lowerTypeName.equals("real")
                || lowerTypeName.equals("money") || lowerTypeName.equals("smallmoney");
    }

    /**
     * SQL Server 自定义 NULL/NOT NULL 修饰符 与基类 NullNotNullModifier 的区别：末尾不添加空格，避免在分号前产生多余空格
     */
    protected class SqlServerNullNotNullModifier implements DBColumnModifier {
        @Override
        public void appendModifier(DBTableColumn column, SqlBuilder sqlBuilder) {
            sqlBuilder.append(column.getNullable() ? " NULL" : " NOT NULL");
        }
    }

    protected class DefaultOptionModifier implements DBColumnModifier {
        @Override
        public void appendModifier(DBTableColumn column, SqlBuilder sqlBuilder) {
            String defaultValue = column.getDefaultValue();
            Boolean zerofill = column.getZerofill();

            // 优先级：如果默认值字段有数值，则优先使用，忽略 zerofill
            if (StringUtils.isNotEmpty(defaultValue)) {
                // 用户显式设置了默认值，使用该值
                sqlBuilder.append(" DEFAULT ").append(defaultValue);
            } else if (Boolean.TRUE.equals(zerofill) && isNumericType(column.getTypeName())) {
                // zerofill 为 true 且是数值类型，且没有显式默认值，设置默认值为 0
                sqlBuilder.append(" DEFAULT 0");
            }
        }
    }

}
