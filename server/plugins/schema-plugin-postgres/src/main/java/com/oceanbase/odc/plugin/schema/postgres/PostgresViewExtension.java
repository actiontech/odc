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
import com.oceanbase.odc.plugin.schema.obmysql.OBMySQLViewExtension;
import com.oceanbase.odc.plugin.schema.postgres.utils.DBAccessorUtil;
import com.oceanbase.tools.dbbrowser.DBBrowser;
import com.oceanbase.tools.dbbrowser.editor.DBObjectOperator;
import com.oceanbase.tools.dbbrowser.editor.postgre.PostgresObjectOperator;
import com.oceanbase.tools.dbbrowser.model.DBView;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;
import com.oceanbase.tools.dbbrowser.template.DBObjectTemplate;

import lombok.NonNull;

/**
 * PostgreSQL 数据库视图扩展实现
 * 
 * <p>
 * 继承 {@link OBMySQLViewExtension} 基类，覆写 PostgreSQL 特有的视图操作方法。 PostgreSQL 视图功能与 MySQL 类似，主要差异在于：
 * <ul>
 * <li>使用双引号包裹标识符</li>
 * <li>CREATE VIEW 模板使用 PostgreSQL 方言</li>
 * </ul>
 *
 * @author ODC Team
 * @since ODC_release_4.3.5
 */
@Extension
public class PostgresViewExtension extends OBMySQLViewExtension {

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
     * 返回 PostgreSQL 专用的对象操作器，用于执行 DROP VIEW 等操作。
     *
     * @param connection 数据库连接
     * @return PostgreSQL 对象操作器实例
     */
    @Override
    protected DBObjectOperator getOperator(Connection connection) {
        return new PostgresObjectOperator(JdbcOperationsUtil.getJdbcOperations(connection));
    }

    /**
     * 生成视图创建模板
     * 
     * <p>
     * 使用 PostgreSQL 模板生成 CREATE VIEW 语句，支持：
     * <ul>
     * <li>schema 前缀（"schema"."view" 格式）</li>
     * <li>双引号标识符</li>
     * </ul>
     *
     * @param view 视图对象
     * @return 生成的 CREATE VIEW 语句模板
     */
    @Override
    public String generateCreateTemplate(@NonNull DBView view) {
        return getTemplate().generateCreateObjectTemplate(view);
    }

    /**
     * 获取视图模板
     * 
     * <p>
     * 返回 PostgreSQL 专用的视图模板。
     *
     * @return PostgreSQL 视图模板实例
     */
    @Override
    protected DBObjectTemplate<DBView> getTemplate() {
        return DBBrowser.objectTemplate().viewTemplate()
                .setType(DialectType.POSTGRESQL.getDBBrowserDialectTypeName()).create();
    }

}
