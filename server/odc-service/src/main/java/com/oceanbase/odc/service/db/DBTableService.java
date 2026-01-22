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
package com.oceanbase.odc.service.db;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Service;

import com.oceanbase.odc.core.authority.util.SkipAuthorize;
import com.oceanbase.odc.core.session.ConnectionSession;
import com.oceanbase.odc.core.session.ConnectionSessionConstants;
import com.oceanbase.odc.core.session.ConnectionSessionUtil;
import com.oceanbase.odc.core.shared.PreConditions;
import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.odc.core.shared.constant.OdcConstants;
import com.oceanbase.odc.core.shared.constant.ResourceType;
import com.oceanbase.odc.core.shared.exception.UnexpectedException;
import com.oceanbase.odc.core.shared.model.TableIdentity;
import com.oceanbase.odc.plugin.schema.api.TableExtensionPoint;
import com.oceanbase.odc.service.common.util.SqlUtils;
import com.oceanbase.odc.service.db.browser.DBSchemaAccessors;
import com.oceanbase.odc.service.db.model.DatabaseAndTables;
import com.oceanbase.odc.service.db.model.GenerateTableDDLResp;
import com.oceanbase.odc.service.db.model.GenerateUpdateTableDDLReq;
import com.oceanbase.odc.service.db.model.UpdateTableDdlCheck;
import com.oceanbase.odc.service.plugin.SchemaPluginUtil;
import com.oceanbase.odc.service.sqlcheck.SqlCheckUtil;
import com.oceanbase.tools.dbbrowser.DBBrowser;
import com.oceanbase.tools.dbbrowser.model.DBObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;
import com.oceanbase.tools.dbbrowser.model.DBTable;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;
import com.oceanbase.tools.sqlparser.statement.Statement;
import com.oceanbase.tools.sqlparser.statement.alter.table.AlterTable;
import com.oceanbase.tools.sqlparser.statement.alter.table.AlterTableAction;
import com.oceanbase.tools.sqlparser.statement.createindex.CreateIndex;
import com.oceanbase.tools.sqlparser.statement.dropindex.DropIndex;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@SkipAuthorize("inside connect session")
public class DBTableService {
    /**
     * show tables from schemaName like tableName
     *
     * @param session session
     * @param schemaName use session current database if null
     * @param fuzzyTableName show all tables if null or blank
     */
    public List<String> showTablesLike(@NotNull ConnectionSession session, String schemaName, String fuzzyTableName) {
        String tableNameLike = SqlUtils.anyLike(fuzzyTableName);
        List<String> tableNames = session.getSyncJdbcExecutor(
                ConnectionSessionConstants.BACKEND_DS_KEY)
                .execute((ConnectionCallback<List<String>>) con -> getTableExtensionPoint(session)
                        .showNamesLike(con, schemaName, tableNameLike).stream()
                        .filter(name -> !StringUtils.endsWith(name.toUpperCase(),
                                OdcConstants.VALIDATE_DDL_TABLE_POSTFIX))
                        .collect(Collectors.toList()));
        log.debug("showTablesLike, schemaName={}, tableNameLike={}, tableNamesCount={}",
                schemaName, tableNameLike, tableNames.size());
        return tableNames;
    }

    public DBTable getTable(@NotNull ConnectionSession connectionSession, String schemaName,
            @NotBlank String tableName, @NotNull DBObjectType type) {
        DBSchemaAccessor schemaAccessor = DBSchemaAccessors.create(connectionSession);
        if (type == DBObjectType.TABLE) {
            PreConditions.validExists(ResourceType.OB_TABLE, "tableName", tableName,
                    () -> schemaAccessor.showTables(schemaName).stream().filter(name -> name.equals(tableName))
                            .collect(Collectors.toList()).size() > 0);
        }
        if (type == DBObjectType.EXTERNAL_TABLE) {
            PreConditions.validExists(ResourceType.OB_TABLE, "tableName", tableName,
                    () -> schemaAccessor.showExternalTables(schemaName).stream().filter(name -> name.equals(tableName))
                            .collect(Collectors.toList()).size() > 0);
        }
        try {
            return connectionSession.getSyncJdbcExecutor(
                    ConnectionSessionConstants.CONSOLE_DS_KEY)
                    .execute((ConnectionCallback<DBTable>) con -> getTableExtensionPoint(connectionSession)
                            .getDetail(con, schemaName, tableName));
        } catch (Exception e) {
            log.warn("Query table information failed, table name=%s.", e);
            throw new UnexpectedException(String
                    .format("Query table information failed, table name=%s, error massage=%s", tableName,
                            e.getMessage()));
        }
    }

    /**
     * get all table details in a schema
     */
    public Map<String, DBTable> getTables(@NotNull ConnectionSession connectionSession, String schemaName) {
        return DBSchemaAccessors.create(connectionSession).getTables(schemaName, null);
    }

    public List<DBTable> listTables(@NotNull ConnectionSession connectionSession, String schemaName) {
        return connectionSession.getSyncJdbcExecutor(ConnectionSessionConstants.BACKEND_DS_KEY)
                .execute((ConnectionCallback<List<DBObjectIdentity>>) con -> getTableExtensionPoint(connectionSession)
                        .list(con, schemaName, DBObjectType.TABLE))
                .stream().map(item -> {
                    DBTable table = new DBTable();
                    table.setName(item.getName());
                    table.setSchemaName(schemaName);
                    return table;
                }).collect(Collectors.toList());
    }

    public GenerateTableDDLResp generateCreateDDL(@NotNull ConnectionSession session, @NotNull DBTable table) {
        String ddl;
        String schemaName = table.getSchemaName();
        String tableName = table.getName();
        if (ConnectionSessionUtil.isLogicalSession(session)) {
            /**
             * when creating a logical table, we assume that the table expression is the table name, and the
             * schema will be ignored.
             */
            table.setSchema(null);
            table.setSchemaName(null);
            ddl = DBBrowser.objectEditor().tableEditor()
                    .setDbVersion("4.0.0")
                    .setType(session.getDialectType().getDBBrowserDialectTypeName()).create()
                    .generateCreateObjectDDL(table);
        } else {
            ddl = session.getSyncJdbcExecutor(
                    ConnectionSessionConstants.BACKEND_DS_KEY)
                    .execute((ConnectionCallback<String>) con -> getTableExtensionPoint(session).generateCreateDDL(con,
                            table));
        }
        return GenerateTableDDLResp.builder()
                .sql(ddl)
                .currentIdentity(TableIdentity.of(schemaName, tableName))
                .previousIdentity(TableIdentity.of(schemaName, tableName))
                .build();
    }

    /**
     * 生成表更新DDL语句 根据会话类型（逻辑会话或物理会话）采用不同的DDL生成策略
     * 
     * @param session 数据库连接会话
     * @param req 包含表修改前后状态的请求对象（previous: 修改前的表结构, current: 修改后的表结构）
     * @return 包含生成的DDL语句、表标识信息和提示信息的响应对象
     */
    public GenerateTableDDLResp generateUpdateDDL(@NotNull ConnectionSession session,
            @NotNull GenerateUpdateTableDDLReq req) {
        String ddl; // 用于存储生成的DDL语句

        // ========== 分支1: 逻辑会话处理 ==========
        // 逻辑会话是指不直接连接真实数据库的会话，通常用于离线DDL生成或逻辑建模场景
        if (ConnectionSessionUtil.isLogicalSession(session)) {
            // 使用DBBrowser工具链式构建表编辑器，生成更新DDL
            ddl = DBBrowser.objectEditor().tableEditor()
                    .setDbVersion("4.0.0") // 设置数据库版本为4.0.0
                    .setType(session.getDialectType().getDBBrowserDialectTypeName()) // 设置数据库方言类型（如MySQL、Oracle等）
                    .create() // 创建表编辑器实例
                    .generateUpdateObjectDDL(req.getPrevious(), req.getCurrent()); // 对比前后表结构差异，生成ALTER TABLE等更新DDL
        }
        // ========== 分支2: 物理会话处理 ==========
        // 物理会话是指直接连接真实数据库的会话，需要通过数据库扩展点生成DDL
        else {
            // 通过JDBC执行器获取数据库连接，调用表扩展点生成DDL
            ddl = session.getSyncJdbcExecutor(
                    ConnectionSessionConstants.BACKEND_DS_KEY) // 使用后端数据源执行器
                    .execute((ConnectionCallback<String>) con ->
                    // 获取对应数据库类型的表扩展点，执行generateUpdateDDL方法
                    getTableExtensionPoint(session).generateUpdateDDL(con,
                            req.getPrevious(), req.getCurrent()));
        }

        // ========== 构建响应对象 ==========
        return GenerateTableDDLResp.builder()
                .sql(ddl) // 设置生成的DDL SQL语句
                // 设置当前表标识（修改后的表）：包含schema名称和表名
                .currentIdentity(TableIdentity.of(req.getCurrent().getSchemaName(), req.getCurrent().getName()))
                // 设置之前表标识（修改前的表）：包含schema名称和表名
                .previousIdentity(TableIdentity.of(req.getPrevious().getSchemaName(), req.getPrevious().getName()))
                // 检查DDL中是否包含索引操作（创建/删除），并返回相应的提示信息
                .tip(checkUpdateDDL(session.getDialectType(), ddl))
                .build();
    }

    public boolean syncExternalTableFiles(@NotNull ConnectionSession connectionSession, String schemaName,
            @NotBlank String externalTableName) {
        DBSchemaAccessor schemaAccessor = DBSchemaAccessors.create(connectionSession);
        PreConditions.validExists(ResourceType.OB_TABLE, "tableName", externalTableName,
                () -> schemaAccessor.showExternalTables(schemaName).stream()
                        .filter(name -> name.equals(externalTableName))
                        .collect(Collectors.toList()).size() > 0);
        return connectionSession.getSyncJdbcExecutor(ConnectionSessionConstants.BACKEND_DS_KEY)
                .execute((ConnectionCallback<Boolean>) con -> getTableExtensionPoint(connectionSession)
                        .syncExternalTableFiles(con, schemaName, externalTableName));
    }

    public String checkUpdateDDL(DialectType dialectType, String ddl) {
        boolean createIndex = false;
        boolean dropIndex = false;
        for (String s : SqlUtils.split(dialectType, ddl, ";")) {
            Statement stmt = SqlCheckUtil.parseSingleSql(dialectType, s);
            if (stmt == null) {
                continue;
            }
            if (stmt instanceof CreateIndex) {
                createIndex = true;
            } else if (stmt instanceof DropIndex) {
                dropIndex = true;
            } else if (stmt instanceof AlterTable) {
                for (AlterTableAction tableAction : ((AlterTable) stmt).getAlterTableActions()) {
                    if (Objects.nonNull(tableAction.getAddIndex())) {
                        createIndex = true;
                    } else if (Objects.nonNull(tableAction.getDropIndexName())) {
                        dropIndex = true;
                    }
                }
            }
        }
        if (dropIndex && createIndex) {
            return UpdateTableDdlCheck.DROP_AND_CREATE_INDEX.getLocalizedMessage();
        } else if (dropIndex) {
            return UpdateTableDdlCheck.DROP_INDEX.getLocalizedMessage();
        } else if (createIndex) {
            return UpdateTableDdlCheck.CREATE_INDEX.getLocalizedMessage();
        }
        return null;
    }

    public Boolean isLowerCaseTableName(@NotNull ConnectionSession connectionSession) {
        DBSchemaAccessor schemaAccessor = DBSchemaAccessors.create(connectionSession);
        return schemaAccessor.isLowerCaseTableName();
    }

    public List<DatabaseAndTables> generateDatabaseAndTables(@NotNull DBSchemaAccessor accessor,
            @NotNull String tableNameLike,
            @NotNull List<String> existedDatabases) {
        List<DBObjectIdentity> existedTablesIdentities = accessor.listTables(null, tableNameLike).stream()
                .filter(identity -> !StringUtils.endsWithIgnoreCase(identity.getName(),
                        OdcConstants.VALIDATE_DDL_TABLE_POSTFIX)
                        && !StringUtils.startsWithIgnoreCase(identity.getName(), OdcConstants.CONTAINER_TABLE_PREFIX)
                        && !StringUtils.startsWithIgnoreCase(identity.getName(),
                                OdcConstants.MATERIALIZED_VIEW_LOG_PREFIX))
                .collect(Collectors.toList());
        Map<String, List<String>> schema2ExistedTables = new HashMap<>();
        existedTablesIdentities.forEach(item -> {
            schema2ExistedTables.computeIfAbsent(item.getSchemaName(), t -> new ArrayList<>()).add(item.getName());
        });
        return existedDatabases.stream()
                .map(schema -> new DatabaseAndTables(schema, Optional.ofNullable(schema2ExistedTables.get(schema))
                        .orElse(Collections.emptyList())))
                .collect(Collectors.toList());
    }

    private TableExtensionPoint getTableExtensionPoint(@NotNull ConnectionSession connectionSession) {
        return SchemaPluginUtil.getTableExtension(connectionSession.getDialectType());
    }
}
