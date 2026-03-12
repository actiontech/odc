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
package com.oceanbase.odc.plugin.schema.postgres;

import java.sql.Connection;

import org.pf4j.Extension;

import com.oceanbase.odc.common.unit.BinarySizeUnit;
import com.oceanbase.odc.plugin.schema.obmysql.OBMySQLTableExtension;
import com.oceanbase.odc.plugin.schema.postgres.utils.DBAccessorUtil;
import com.oceanbase.tools.dbbrowser.editor.DBTableEditor;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;
import com.oceanbase.tools.dbbrowser.model.DBTable;
import com.oceanbase.tools.dbbrowser.model.DBTableStats;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;
import com.oceanbase.tools.dbbrowser.stats.DBStatsAccessor;

import lombok.NonNull;

/**
 * PostgreSQL 数据库表扩展实现
 * 
 * <p>
 * 继承 {@link OBMySQLTableExtension} 基类，覆写 PostgreSQL 特有的表操作方法。 PostgreSQL 与 MySQL 在表详情获取和 DDL
 * 生成方面存在差异：
 * <ul>
 * <li>PostgreSQL 无内置 SHOW CREATE TABLE 命令，需通过 schemaAccessor 程序化获取表详情</li>
 * <li>PostgreSQL 使用 COMMENT ON 语句添加注释</li>
 * </ul>
 *
 * @author ODC Team
 * @since ODC_release_4.3.5
 */
@Extension
public class PostgresTableExtension extends OBMySQLTableExtension {

    /**
     * 获取表详情
     * 
     * <p>
     * PostgreSQL 不像 MySQL 有 SHOW CREATE TABLE 命令，因此不能使用 {@code OBMySQLGetDBTableByParser} 解析 DDL。需要直接通过
     * schemaAccessor 查询各个子对象（列、约束、索引、分区等）来组装表详情。
     *
     * @param connection 数据库连接
     * @param schemaName schema 名称
     * @param tableName 表名
     * @return 表详情对象
     */
    @Override
    public DBTable getDetail(@NonNull Connection connection, @NonNull String schemaName,
            @NonNull String tableName) {
        DBSchemaAccessor schemaAccessor = getSchemaAccessor(connection);
        DBTable table = new DBTable();
        table.setSchemaName(schemaName);
        table.setOwner(schemaName);
        table.setName(tableName);
        // 获取列信息
        table.setColumns(schemaAccessor.listTableColumns(schemaName, tableName));
        // 获取分区信息
        table.setPartition(schemaAccessor.getPartition(schemaName, tableName));
        // 检查是否为外部表
        if (!schemaAccessor.isExternalTable(schemaName, tableName)) {
            table.setConstraints(schemaAccessor.listTableConstraints(schemaName, tableName));
            table.setIndexes(schemaAccessor.listTableIndexes(schemaName, tableName));
            table.setType(DBObjectType.TABLE);
        } else {
            table.setType(DBObjectType.EXTERNAL_TABLE);
        }
        // 获取 DDL（由 PostgresSchemaAccessor.getTableDDL() 程序化拼装）
        table.setDDL(schemaAccessor.getTableDDL(schemaName, tableName));
        // 获取表选项（如注释）
        table.setTableOptions(schemaAccessor.getTableOptions(schemaName, tableName));
        // 获取统计信息
        table.setStats(getTableStats(connection, schemaName, tableName));
        return table;
    }

    /**
     * 生成表创建 DDL
     * 
     * <p>
     * 委托 {@link DBTableEditor} 生成 CREATE TABLE 语句， 包括列定义、约束、索引注释等。
     *
     * @param connection 数据库连接
     * @param table 表对象
     * @return 生成的 DDL 语句
     */
    @Override
    public String generateCreateDDL(@NonNull Connection connection, @NonNull DBTable table) {
        return getTableEditor(connection).generateCreateObjectDDL(table);
    }

    /**
     * 生成表更新 DDL
     * 
     * <p>
     * 对比新旧表结构，委托 {@link DBTableEditor} 生成 ALTER TABLE 语句。
     *
     * @param connection 数据库连接
     * @param oldTable 修改前的表对象
     * @param newTable 修改后的表对象
     * @return 生成的 DDL 语句
     */
    @Override
    public String generateUpdateDDL(@NonNull Connection connection, @NonNull DBTable oldTable,
            @NonNull DBTable newTable) {
        return getTableEditor(connection).generateUpdateObjectDDL(oldTable, newTable);
    }

    /**
     * 获取表统计信息
     * 
     * <p>
     * 通过 {@link DBStatsAccessor} 查询表的行数、数据大小等统计信息。 PostgreSQL 使用 pg_stat_user_tables 和
     * pg_total_relation_size() 获取统计信息。
     *
     * @param connection 数据库连接
     * @param schemaName schema 名称
     * @param tableName 表名
     * @return 表统计信息对象
     */
    @Override
    protected DBTableStats getTableStats(@NonNull Connection connection, @NonNull String schemaName,
            @NonNull String tableName) {
        DBStatsAccessor statsAccessor = getStatsAccessor(connection);
        DBTableStats tableStats = statsAccessor.getTableStats(schemaName, tableName);
        if (tableStats == null) {
            return new DBTableStats();
        }
        Long dataSizeInBytes = tableStats.getDataSizeInBytes();
        if (dataSizeInBytes == null || dataSizeInBytes < 0) {
            tableStats.setTableSize(null);
        } else {
            tableStats.setTableSize(BinarySizeUnit.B.of(dataSizeInBytes).toString());
        }
        return tableStats;
    }

    /**
     * 获取表编辑器
     * 
     * <p>
     * 返回 PostgreSQL 专用的表编辑器，用于生成 DDL。
     *
     * @param connection 数据库连接
     * @return 表编辑器实例
     */
    @Override
    protected DBTableEditor getTableEditor(Connection connection) {
        return DBAccessorUtil.getTableEditor(connection);
    }

    /**
     * 获取 schema 访问器
     *
     * @param connection 数据库连接
     * @return schema 访问器实例
     */
    @Override
    protected DBSchemaAccessor getSchemaAccessor(Connection connection) {
        return DBAccessorUtil.getSchemaAccessor(connection);
    }

    /**
     * 获取统计信息访问器
     *
     * @param connection 数据库连接
     * @return 统计信息访问器实例
     */
    @Override
    protected DBStatsAccessor getStatsAccessor(Connection connection) {
        return DBAccessorUtil.getStatsAccessor(connection);
    }

    /**
     * 同步外部表文件
     * 
     * <p>
     * PostgreSQL 暂不支持此功能。
     *
     * @param connection 数据库连接
     * @param schemaName schema 名称
     * @param tableName 表名
     * @return 不支持
     */
    @Override
    public boolean syncExternalTableFiles(Connection connection, String schemaName, String tableName) {
        throw new UnsupportedOperationException("PostgreSQL does not support external table file sync");
    }
}
