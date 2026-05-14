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

import com.oceanbase.odc.plugin.schema.api.SynonymExtensionPoint;
import com.oceanbase.odc.plugin.schema.db2.utils.DBAccessorUtil;
import com.oceanbase.tools.dbbrowser.model.DBObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBSynonym;
import com.oceanbase.tools.dbbrowser.model.DBSynonymType;

/**
 * DB2 Synonym Extension（T-002 骨架）。
 * <p>
 * DB2 通过 ALIAS 提供同义词能力（{@code CREATE ALIAS ... FOR ...}），真实 SQL：
 *
 * <pre>
 *     SELECT tabname FROM SYSCAT.TABLES WHERE tabschema=? AND type='A'
 * </pre>
 *
 * 由 T-004 接入；本任务阶段 {@link #list} 返回空集合便于 ODC UI 渲染不抛错，其它方法 抛
 * {@link UnsupportedOperationException}（compat-RISK-7 / compat-RISK-10）。
 */
@Extension
public class Db2SynonymExtension implements SynonymExtensionPoint {

    @Override
    public List<DBObjectIdentity> list(Connection connection, String schemaName, DBSynonymType synonymType) {
        // ODC UI 期望可空集合渲染"暂无同义词"，避免一进对象树就 500
        return Collections.emptyList();
    }

    @Override
    public DBSynonym getDetail(Connection connection, String schemaName, String synonymName,
            DBSynonymType synonymType) {
        throw new UnsupportedOperationException(DBAccessorUtil.NOT_SUPPORTED_MESSAGE);
    }

    @Override
    public void dropSynonym(Connection connection, String schemaName, String synonymName) {
        throw new UnsupportedOperationException(DBAccessorUtil.NOT_SUPPORTED_MESSAGE);
    }

    @Override
    public void dropPublicSynonym(Connection connection, String schemaName, String synonymName) {
        throw new UnsupportedOperationException(DBAccessorUtil.NOT_SUPPORTED_MESSAGE);
    }

    @Override
    public String generateCreateDDL(DBSynonym synonym) {
        throw new UnsupportedOperationException(DBAccessorUtil.NOT_SUPPORTED_MESSAGE);
    }
}
