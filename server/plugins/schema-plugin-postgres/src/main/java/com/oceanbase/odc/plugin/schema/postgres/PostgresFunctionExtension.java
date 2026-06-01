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

import com.oceanbase.odc.common.util.JdbcOperationsUtil;
import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.odc.plugin.schema.obmysql.OBMySQLFunctionExtension;
import com.oceanbase.odc.plugin.schema.postgres.utils.DBAccessorUtil;
import com.oceanbase.tools.dbbrowser.DBBrowser;
import com.oceanbase.tools.dbbrowser.editor.DBObjectOperator;
import com.oceanbase.tools.dbbrowser.editor.postgre.PostgresObjectOperator;
import com.oceanbase.tools.dbbrowser.model.DBFunction;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;
import com.oceanbase.tools.dbbrowser.template.DBObjectTemplate;

import lombok.NonNull;

/**
 * PostgreSQL 数据库函数扩展实现
 * 
 * <p>
 * 继承 {@link OBMySQLFunctionExtension} 基类，覆写 PostgreSQL 特有的函数操作方法。 PostgreSQL 函数特性：
 * <ul>
 * <li>使用 pg_get_functiondef(oid) 获取函数定义</li>
 * <li>支持 PL/pgSQL、PL/python、PL/perl 等多种过程语言</li>
 * <li>使用 dollar-quoting（$$...$$）作为函数体定界符</li>
 * </ul>
 *
 * @author ODC Team
 * @since ODC_release_4.3.5
 */
@Extension
public class PostgresFunctionExtension extends OBMySQLFunctionExtension {

    /**
     * 获取 schema 访问器
     *
     * @param connection 数据库连接
     * @return PostgreSQL schema 访问器实例
     */
    @Override
    protected DBSchemaAccessor getSchemaAccessor(Connection connection) {
        return DBAccessorUtil.getSchemaAccessor(connection);
    }

    /**
     * 获取对象操作器
     * 
     * <p>
     * 返回 PostgreSQL 专用的对象操作器，用于执行 DROP FUNCTION 等操作。
     *
     * @param connection 数据库连接
     * @return PostgreSQL 对象操作器实例
     */
    @Override
    protected DBObjectOperator getOperator(Connection connection) {
        return new PostgresObjectOperator(JdbcOperationsUtil.getJdbcOperations(connection));
    }

    /**
     * 生成函数创建模板
     * 
     * <p>
     * 使用 PostgreSQL 模板生成 CREATE FUNCTION 语句，支持：
     * <ul>
     * <li>schema 前缀（"schema"."function" 格式）</li>
     * <li>参数模式：IN、OUT、INOUT、VARIADIC</li>
     * <li>返回类型</li>
     * <li>PL/pgSQL dollar-quoting（$$...$$）</li>
     * </ul>
     *
     * @param function 函数对象
     * @return 生成的 CREATE FUNCTION 语句模板
     */
    @Override
    public String generateCreateTemplate(@NonNull DBFunction function) {
        return getTemplate().generateCreateObjectTemplate(function);
    }

    /**
     * 获取函数模板
     * 
     * <p>
     * 返回 PostgreSQL 专用的函数模板。
     *
     * @return PostgreSQL 函数模板实例
     */
    @Override
    protected DBObjectTemplate<DBFunction> getTemplate() {
        return DBBrowser.objectTemplate().functionTemplate()
                .setType(DialectType.POSTGRESQL.getDBBrowserDialectTypeName()).create();
    }

}
