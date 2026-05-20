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
package com.oceanbase.odc.plugin.schema.hive;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.pf4j.Extension;

import com.oceanbase.odc.plugin.schema.api.ColumnExtensionPoint;
import com.oceanbase.tools.dbbrowser.model.DBObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;
import com.oceanbase.tools.dbbrowser.model.DBTable;
import com.oceanbase.tools.dbbrowser.model.DBTableColumn;

/**
 * Hive column listing for the workbench object-tree. Iterates over each table and reuses
 * {@link HiveTableExtension#getDetail} to obtain its columns; trims to the basic projection
 * (schemaName / tableName / name / comment) expected by the front-end tree.
 *
 * <p>
 * Tables and views share the {@code SHOW TABLES} namespace in Hive; {@code DESCRIBE FORMATTED} is
 * read once per object and the table-type is used to route entries into table vs view buckets.
 *
 * @since ODC_release_4.3.4
 */
@Extension
public class HiveColumnExtension implements ColumnExtensionPoint {

    @Override
    public Map<String, List<DBTableColumn>> listBasicTableColumns(Connection connection, String schemaName) {
        return collect(connection, schemaName, false);
    }

    @Override
    public Map<String, List<DBTableColumn>> listBasicViewColumns(Connection connection, String schemaName) {
        return collect(connection, schemaName, true);
    }

    @Override
    public Map<String, List<DBTableColumn>> listBasicColumnsInfo(Connection connection, String schemaName) {
        Map<String, List<DBTableColumn>> all = new LinkedHashMap<>(listBasicTableColumns(connection, schemaName));
        all.putAll(listBasicViewColumns(connection, schemaName));
        return all;
    }

    private Map<String, List<DBTableColumn>> collect(Connection connection, String schemaName, boolean wantView) {
        HiveTableExtension tableExt = newTableExtension();
        Map<String, List<DBTableColumn>> result = new LinkedHashMap<>();
        for (DBObjectIdentity id : tableExt.list(connection, schemaName, DBObjectType.TABLE)) {
            String name = id.getName();
            DBTable detail;
            try {
                detail = tableExt.getDetail(connection, schemaName, name);
            } catch (RuntimeException e) {
                // a single malformed table should not abort the listing
                continue;
            }
            boolean isView = detail.getType() == DBObjectType.VIEW;
            if (isView != wantView) {
                continue;
            }
            List<DBTableColumn> raw = detail.getColumns();
            if (raw == null) {
                result.put(name, Collections.emptyList());
                continue;
            }
            List<DBTableColumn> basic = new ArrayList<>(raw.size());
            for (DBTableColumn full : raw) {
                DBTableColumn b = new DBTableColumn();
                b.setSchemaName(schemaName);
                b.setTableName(name);
                b.setName(full.getName());
                b.setComment(full.getComment());
                basic.add(b);
            }
            result.put(name, basic);
        }
        return result;
    }

    /** Hook so unit tests can inject a stubbed {@link HiveTableExtension}. */
    HiveTableExtension newTableExtension() {
        return new HiveTableExtension();
    }
}
