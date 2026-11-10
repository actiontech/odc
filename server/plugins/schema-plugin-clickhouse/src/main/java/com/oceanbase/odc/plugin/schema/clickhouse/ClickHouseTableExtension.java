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
package com.oceanbase.odc.plugin.schema.clickhouse;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.pf4j.Extension;

import com.oceanbase.odc.common.util.JdbcOperationsUtil;
import com.oceanbase.odc.common.util.StringUtils;
import com.oceanbase.odc.plugin.schema.api.TableExtensionPoint;
import com.oceanbase.tools.dbbrowser.model.DBObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;
import com.oceanbase.tools.dbbrowser.model.DBTable;

import lombok.extern.slf4j.Slf4j;

/**
 * Lists ClickHouse tables via {@code SHOW TABLES FROM <database>}.
 */
@Slf4j
@Extension
public class ClickHouseTableExtension implements TableExtensionPoint {

    @Override
    public List<DBObjectIdentity> list(Connection connection, String schemaName, DBObjectType tableType) {
        List<DBObjectIdentity> result = new ArrayList<>();
        for (String name : showNamesLike(connection, schemaName, "")) {
            result.add(DBObjectIdentity.of(schemaName, DBObjectType.TABLE, name));
        }
        return result;
    }

    @Override
    public List<String> showNamesLike(Connection connection, String schemaName, String tableNameLike) {
        try {
            String sql = "SHOW TABLES FROM `" + schemaName + "`";
            List<String> names = JdbcOperationsUtil.getJdbcOperations(connection)
                    .query(sql, (rs, rowNum) -> rs.getString(1));
            if (StringUtils.isBlank(tableNameLike)) {
                return names;
            }
            return names.stream().filter(n -> n != null && n.contains(tableNameLike)).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to list tables for ClickHouse database {}", schemaName, e);
            return Collections.emptyList();
        }
    }

    @Override
    public DBTable getDetail(Connection connection, String schemaName, String tableName) {
        DBTable table = new DBTable();
        table.setName(tableName);
        table.setSchemaName(schemaName);
        table.setOwner(schemaName);
        table.setType(DBObjectType.TABLE);
        try {
            String ddl = JdbcOperationsUtil.getJdbcOperations(connection).query(
                    "SHOW CREATE TABLE `" + schemaName + "`.`" + tableName + "`",
                    rs -> {
                        if (rs.next()) {
                            int cols = rs.getMetaData().getColumnCount();
                            return rs.getString(cols);
                        }
                        return null;
                    });
            table.setDDL(ddl);
        } catch (Exception e) {
            log.warn("Failed to get CREATE TABLE for {}.{}", schemaName, tableName, e);
        }
        return table;
    }

    @Override
    public void drop(Connection connection, String schemaName, String tableName) {
        throw new UnsupportedOperationException("ClickHouse drop table is out of scope for this slice");
    }

    @Override
    public String generateCreateDDL(Connection connection, DBTable table) {
        throw new UnsupportedOperationException("ClickHouse generateCreateDDL is out of scope for this slice");
    }

    @Override
    public String generateUpdateDDL(Connection connection, DBTable oldTable, DBTable newTable) {
        throw new UnsupportedOperationException("ClickHouse generateUpdateDDL is out of scope for this slice");
    }

    @Override
    public boolean syncExternalTableFiles(Connection connection, String schemaName, String tableName) {
        return false;
    }
}
