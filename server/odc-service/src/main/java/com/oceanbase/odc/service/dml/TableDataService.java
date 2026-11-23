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
package com.oceanbase.odc.service.dml;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import com.oceanbase.odc.common.util.Lazy;
import com.oceanbase.odc.common.util.StringUtils;
import com.oceanbase.odc.core.authority.util.SkipAuthorize;
import com.oceanbase.odc.core.session.ConnectionSession;
import com.oceanbase.odc.core.session.ConnectionSessionUtil;
import com.oceanbase.odc.core.shared.PreConditions;
import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.odc.core.shared.constant.ErrorCodes;
import com.oceanbase.odc.core.shared.exception.BadRequestException;
import com.oceanbase.odc.core.shared.exception.UnexpectedException;
import com.oceanbase.odc.service.common.model.ResourceSql;
import com.oceanbase.odc.service.connection.model.ConnectionConfig;
import com.oceanbase.odc.service.db.browser.DBSchemaAccessors;
import com.oceanbase.odc.service.dml.model.BatchDataModifyReq;
import com.oceanbase.odc.service.dml.model.BatchDataModifyReq.Operate;
import com.oceanbase.odc.service.dml.model.BatchDataModifyReq.Row;
import com.oceanbase.odc.service.dml.model.BatchDataModifyResp;
import com.oceanbase.odc.service.dml.model.DataModifyUnit;
import com.oceanbase.odc.service.feature.AllFeatures;
import com.oceanbase.odc.service.feature.Features;
import com.oceanbase.tools.dbbrowser.model.DBTableColumn;
import com.oceanbase.tools.dbbrowser.model.DBTableConstraint;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Validated
@Service
@SkipAuthorize("inside connect session")
public class TableDataService {

    public BatchDataModifyResp batchGetModifySql(@NotNull ConnectionSession connectionSession,
            @NotNull @Valid BatchDataModifyReq req) {
        if (connectionSession.getDialectType().isMongoDB()) {
            return batchGetMongoModifySql(connectionSession, req);
        }
        List<Row> sortedRows = req.getRows().stream()
                .sorted(Comparator.comparingInt(row -> row.getOperate().ordinal()))
                .collect(Collectors.toList());

        ConnectionConfig config = (ConnectionConfig) ConnectionSessionUtil.getConnectionConfig(connectionSession);
        Features features = AllFeatures.getByConnectType(config.getType());
        if (!features.supportsSchemaPrefixInSql()) {
            req.setSchemaName(null);
        }
        String tableName = req.getTableName();
        String schemaName = req.getSchemaName();
        BatchDataModifyResp resp = new BatchDataModifyResp();
        resp.setTableName(tableName);
        resp.setSchemaName(schemaName);
        Lazy<List<DBTableConstraint>> constraints = new Lazy<>(
                () -> BaseDMLBuilder.getConstraints(schemaName, tableName, connectionSession));
        Map<String, DBTableColumn> columnName2Column = getColumnName2Column(connectionSession, schemaName, tableName);
        StringBuilder sqlBuilder = new StringBuilder();
        for (Row row : sortedRows) {
            Operate operate = row.getOperate();
            boolean hasModifiedUnit = false;
            for (DataModifyUnit unit : row.getUnits()) {
                unit.setSchemaName(schemaName);
                unit.setTableName(tableName);
                if (!StringUtils.equals(unit.getOldData(), unit.getNewData())) {
                    hasModifiedUnit = true;
                }
            }
            if (!hasModifiedUnit && !operate.equals(Operate.DELETE)) {
                continue;
            }
            DMLBuilder dmlBuilder;
            DialectType dialectType = connectionSession.getDialectType();
            if (dialectType.isMysql() || dialectType.isDoris() || dialectType.isTidb()
                    || dialectType.isClickHouse()) {
                // ClickHouse: backticks + MySQL-family literals (S4 §10.1); UPDATE uses
                // ClickHouseUpdateGenerator (ALTER TABLE ... UPDATE), not MySQL UPDATE.
                dmlBuilder = new MySQLDMLBuilder(row.getUnits(), req.getWhereColumns(), connectionSession, constraints);
            } else if (dialectType.isOracle() || dialectType.isDm() || dialectType.isHana()) {
                dmlBuilder =
                        new OracleDMLBuilder(row.getUnits(), req.getWhereColumns(), connectionSession, constraints);
            } else if (dialectType.isDb2()) {
                // fix-L commit-2 (Issue dms-ee#839, bug N2): DB2 now has its own DML builder that
                // emits ANSI double-quoted identifiers (DB2 native) instead of MySQL backticks.
                // Previously DB2 was routed through MySQLDMLBuilder which produced backtick SQL
                // (`SCHEMA`.`TABLE`) that DB2 rejects with SQLCODE=-7 / SQLSTATE=42601.
                dmlBuilder = new Db2DMLBuilder(row.getUnits(), req.getWhereColumns(), connectionSession, constraints);
            } else {
                throw new IllegalArgumentException("Illegal dialect type, " + dialectType);
            }

            if (operate == Operate.DELETE || operate == Operate.UPDATE) {
                PreConditions.validArgumentState(
                        dmlBuilder.containsPrimaryKeyOrRowId() || dmlBuilder.containsUniqueKeys(),
                        ErrorCodes.ObUpdateKeyRequired, null,
                        "Primary key or unique constraint is required to generate update condition");
            }

            DMLGenerator generator = getGenerator(operate, dmlBuilder, columnName2Column, dialectType);
            String sql = generator.generate();

            ResourceSql resourceSql = new ResourceSql();
            resourceSql.setSql(sql);
            if (generator.isAffectMultiRows()) {
                resourceSql.setAffectMultiRows(true);
                resourceSql.setTip("This modification will affect multiple rows");
            }
            Consumer<ResourceSql> postAction = getPostAction(operate, resp);
            postAction.accept(resourceSql);
            sqlBuilder.append(resourceSql.getSql()).append("\n");
            if (StringUtils.isEmpty(resp.getTip()) && StringUtils.isNotBlank(resourceSql.getTip())) {
                resp.setTip(resourceSql.getTip());
            }
        }
        resp.setSql(sqlBuilder.toString());
        return resp;
    }

    private BatchDataModifyResp batchGetMongoModifySql(@NotNull ConnectionSession connectionSession,
            @NotNull BatchDataModifyReq req) {
        String tableName = req.getTableName();
        String schemaName = req.getSchemaName();
        if (StringUtils.isBlank(schemaName)) {
            schemaName = ConnectionSessionUtil.getCurrentSchema(connectionSession);
        }
        BatchDataModifyResp resp = new BatchDataModifyResp();
        resp.setTableName(tableName);
        resp.setSchemaName(schemaName);
        resp.setCreateRows(0);
        resp.setDeleteRows(0);
        resp.setUpdateAffectedMultiRows(false);
        StringBuilder sqlBuilder = new StringBuilder();
        for (Row row : req.getRows()) {
            Operate operate = row.getOperate();
            if (operate != Operate.UPDATE) {
                throw new BadRequestException("MongoDB 结果集编辑仅支持 UPDATE，不支持增删行");
            }
            for (DataModifyUnit unit : row.getUnits()) {
                unit.setSchemaName(schemaName);
                unit.setTableName(tableName);
            }
            String sql = MongoUpdateGenerator.generate(tableName, row.getUnits());
            if (StringUtils.isBlank(sql)) {
                continue;
            }
            ResourceSql resourceSql = new ResourceSql();
            resourceSql.setSql(sql);
            resourceSql.setAffectMultiRows(false);
            getPostAction(Operate.UPDATE, resp).accept(resourceSql);
            // Join with '\n'; each command already ends with ';' for multi-statement split (BE-S1-09/10).
            sqlBuilder.append(sql).append("\n");
        }
        resp.setSql(sqlBuilder.toString());
        return resp;
    }

    private Consumer<ResourceSql> getPostAction(Operate operate, BatchDataModifyResp resp) {
        switch (operate) {
            case INSERT:
                return result -> {
                    resp.setCreateRows(resp.getCreateRows() + 1);
                };
            case UPDATE:
                return result -> {
                    resp.setUpdateRows(resp.getUpdateRows() + 1);
                    resp.setUpdateAffectedMultiRows(result.isAffectMultiRows());
                };
            case DELETE:
                return result -> {
                    resp.setDeleteRows(resp.getDeleteRows() + 1);
                    resp.setDeleteAffectedMultiRows(result.isAffectMultiRows());
                };
            default:
                throw new UnexpectedException("Unexpected operate value:" + operate);
        }
    }

    private DMLGenerator getGenerator(Operate operate, DMLBuilder builder,
            Map<String, DBTableColumn> columnName2Column, DialectType dialectType) {
        switch (operate) {
            case INSERT:
                return new InsertGenerator(builder);
            case UPDATE:
                if (dialectType != null && dialectType.isClickHouse()) {
                    return new ClickHouseUpdateGenerator(builder, columnName2Column);
                }
                return new UpdateGenerator(builder, columnName2Column);
            case DELETE:
                return new DeleteGenerator(builder);
            default:
                throw new UnexpectedException("Unexpected operate value:" + operate);
        }
    }

    private Map<String, DBTableColumn> getColumnName2Column(@NotNull ConnectionSession session, String schema,
            @NotNull String tableName) {
        DBSchemaAccessor accessor = DBSchemaAccessors.create(session);
        if (schema == null) {
            schema = ConnectionSessionUtil.getCurrentSchema(session);
        }
        return accessor.listTableColumns(schema, tableName).stream()
                .collect(Collectors.toMap(DBTableColumn::getName, c -> c));
    }

}
