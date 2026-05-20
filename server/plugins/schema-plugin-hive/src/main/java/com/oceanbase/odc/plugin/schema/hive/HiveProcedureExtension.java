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
import java.util.List;

import org.pf4j.Extension;

import com.oceanbase.odc.plugin.schema.api.ProcedureExtensionPoint;
import com.oceanbase.tools.dbbrowser.model.DBPLObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBProcedure;

/**
 * Backend backstop for the Hive workbench Procedures tree node (hidden by
 * {@code features.procedureVisible=false}); all methods throw
 * {@link UnsupportedOperationException}, mapped to HTTP 400 by the global exception handler.
 */
@Extension
public class HiveProcedureExtension implements ProcedureExtensionPoint {

    private static final String NOT_SUPPORTED = "Hive procedure metadata is not supported in this release.";

    @Override
    public List<DBPLObjectIdentity> list(Connection connection, String schemaName) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public DBProcedure getDetail(Connection connection, String schemaName, String procedureName) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public void drop(Connection connection, String schemaName, String procedureName) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }

    @Override
    public String generateCreateTemplate(DBProcedure procedure) {
        throw new UnsupportedOperationException(NOT_SUPPORTED);
    }
}
