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
import java.util.List;

import org.pf4j.Extension;

import com.oceanbase.odc.plugin.schema.api.ViewExtensionPoint;
import com.oceanbase.tools.dbbrowser.model.DBObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;
import com.oceanbase.tools.dbbrowser.model.DBTable;
import com.oceanbase.tools.dbbrowser.model.DBView;

/**
 * Hive views share the {@code SHOW TABLES} namespace with tables; this extension enumerates via
 * {@link HiveTableExtension} and keeps the entries whose detail reports {@link DBObjectType#VIEW}.
 * Create/drop/template generation are not supported in this read-only release (design.md §2.3
 * decision 1).
 */
@Extension
public class HiveViewExtension implements ViewExtensionPoint {

    @Override
    public List<DBObjectIdentity> list(Connection connection, String schemaName) {
        HiveTableExtension tableExt = newTableExtension();
        List<DBObjectIdentity> views = new ArrayList<>();
        for (DBObjectIdentity id : tableExt.list(connection, schemaName, DBObjectType.TABLE)) {
            DBTable detail = tableExt.getDetail(connection, schemaName, id.getName());
            if (detail.getType() == DBObjectType.VIEW) {
                views.add(DBObjectIdentity.of(schemaName, DBObjectType.VIEW, id.getName()));
            }
        }
        return views;
    }

    @Override
    public List<String> listSystemViews(Connection connection, String schemaName) {
        return Collections.emptyList();
    }

    @Override
    public DBView getDetail(Connection connection, String schemaName, String viewName) {
        DBTable table = newTableExtension().getDetail(connection, schemaName, viewName);
        DBView view = new DBView();
        view.setSchemaName(schemaName);
        view.setViewName(viewName);
        view.setColumns(table.getColumns());
        view.setDdl(table.getDDL());
        return view;
    }

    @Override
    public void drop(Connection connection, String schemaName, String viewName) {
        throw new UnsupportedOperationException(
                "Hive view drop is not supported in this release. See design 2.3 decision 1.");
    }

    @Override
    public String generateCreateTemplate(DBView view) {
        throw new UnsupportedOperationException(
                "Hive view template generation is not supported in this release.");
    }

    /** Hook so unit tests can inject a stubbed {@link HiveTableExtension}. */
    HiveTableExtension newTableExtension() {
        return new HiveTableExtension();
    }
}
