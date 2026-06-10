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
package com.oceanbase.odc.plugin.schema.sqlserver;

import java.sql.Connection;
import java.util.List;

import org.pf4j.Extension;

import com.oceanbase.odc.plugin.schema.api.TriggerExtensionPoint;
import com.oceanbase.odc.plugin.schema.sqlserver.utils.DBAccessorUtil;
import com.oceanbase.tools.dbbrowser.model.DBPLObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBTrigger;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;

import lombok.NonNull;

/**
 * SQL Server trigger extension (read-only list/detail).
 */
@Extension
public class SqlServerTriggerExtension implements TriggerExtensionPoint {

    @Override
    public List<DBPLObjectIdentity> list(@NonNull Connection connection, @NonNull String schemaName) {
        return getSchemaAccessor(connection).listTriggers(schemaName);
    }

    @Override
    public DBTrigger getDetail(@NonNull Connection connection, @NonNull String schemaName,
            @NonNull String triggerName) {
        return getSchemaAccessor(connection).getTrigger(schemaName, triggerName);
    }

    @Override
    public void drop(@NonNull Connection connection, String schemaName, @NonNull String triggerName) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public void setEnable(@NonNull Connection connection, @NonNull String schemaName, @NonNull String triggerName,
            @NonNull boolean enable) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public String generateUpdateTemplate(@NonNull DBTrigger oldTrigger, @NonNull DBTrigger newTrigger) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public String generateCreateTemplate(@NonNull DBTrigger trigger) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    protected DBSchemaAccessor getSchemaAccessor(Connection connection) {
        return DBAccessorUtil.getSchemaAccessor(connection);
    }
}
