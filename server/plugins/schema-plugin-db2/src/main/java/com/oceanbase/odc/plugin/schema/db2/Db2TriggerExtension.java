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
package com.oceanbase.odc.plugin.schema.db2;

import java.sql.Connection;
import java.util.Collections;
import java.util.List;

import org.pf4j.Extension;

import com.oceanbase.odc.plugin.schema.api.TriggerExtensionPoint;
import com.oceanbase.odc.plugin.schema.db2.utils.DBAccessorUtil;
import com.oceanbase.tools.dbbrowser.model.DBPLObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBTrigger;

/**
 * DB2 Trigger Extension（T-002 骨架）。
 * <p>
 * 真实 SQL：
 *
 * <pre>
 *     SELECT trigname FROM SYSCAT.TRIGGERS WHERE trigschema=?
 * </pre>
 *
 * 由 T-004 接入；本任务阶段 {@link #list} 返回空集合，其它方法抛 {@link UnsupportedOperationException}（compat-RISK-7）。
 */
@Extension
public class Db2TriggerExtension implements TriggerExtensionPoint {

    @Override
    public List<DBPLObjectIdentity> list(Connection connection, String schemaName) {
        return Collections.emptyList();
    }

    @Override
    public DBTrigger getDetail(Connection connection, String schemaName, String triggerName) {
        throw new UnsupportedOperationException(DBAccessorUtil.NOT_SUPPORTED_MESSAGE);
    }

    @Override
    public void drop(Connection connection, String schemaName, String triggerName) {
        throw new UnsupportedOperationException(DBAccessorUtil.NOT_SUPPORTED_MESSAGE);
    }

    @Override
    public void setEnable(Connection connection, String schemaName, String triggerName, boolean enable) {
        throw new UnsupportedOperationException(DBAccessorUtil.NOT_SUPPORTED_MESSAGE);
    }

    @Override
    public String generateUpdateTemplate(DBTrigger oldTrigger, DBTrigger newTrigger) {
        throw new UnsupportedOperationException(DBAccessorUtil.NOT_SUPPORTED_MESSAGE);
    }

    @Override
    public String generateCreateTemplate(DBTrigger trigger) {
        throw new UnsupportedOperationException(DBAccessorUtil.NOT_SUPPORTED_MESSAGE);
    }
}
