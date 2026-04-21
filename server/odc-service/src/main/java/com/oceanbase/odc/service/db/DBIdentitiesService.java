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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import com.oceanbase.odc.common.util.ObjectUtil;
import com.oceanbase.odc.common.util.StringUtils;
import com.oceanbase.odc.core.authority.util.SkipAuthorize;
import com.oceanbase.odc.core.session.ConnectionSession;
import com.oceanbase.odc.service.db.browser.DBSchemaAccessors;
import com.oceanbase.odc.service.db.model.SchemaIdentities;
import com.oceanbase.tools.dbbrowser.model.DBObjectIdentity;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;

@Service
@SkipAuthorize("inside connect session")
public class DBIdentitiesService {

    public List<SchemaIdentities> list(ConnectionSession session, List<DBObjectType> types) {
        return list(session, null, null, types);
    }

    public List<SchemaIdentities> list(ConnectionSession session, String schemaName, List<DBObjectType> types) {
        return list(session, schemaName, null, types);
    }

    public List<SchemaIdentities> list(ConnectionSession session, String schemaName, String identityNameLike,
            List<DBObjectType> types) {
        if (CollectionUtils.isEmpty(types)) {
            return Collections.emptyList();
        }
        DBSchemaAccessor schemaAccessor = DBSchemaAccessors.create(session);
        Map<String, SchemaIdentities> all = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        List<String> existedDatabases = schemaAccessor.showDatabases();
        boolean isSqlServer = session.getDialectType().isSqlServer();
        boolean isDb2 = session.getDialectType().isDb2();
        if (StringUtils.isNotBlank(schemaName) && !existedDatabases.contains(schemaName)) {
            // 对于 SQLServer 和 DB2，不预先创建 SchemaIdentities，因为不知道具体的 schema
            // 对于其他数据库，保持原有逻辑
            if (!isSqlServer && !isDb2) {
                existedDatabases.forEach(db -> all.computeIfAbsent(db, SchemaIdentities::of));
            }
            return new ArrayList<>(all.values());
        }
        if (types.contains(DBObjectType.VIEW)) {
            listViews(schemaAccessor, schemaName, identityNameLike, all);
        }
        if (types.contains(DBObjectType.TABLE)) {
            listTables(schemaAccessor, schemaName, identityNameLike, all);
        }
        if (types.contains(DBObjectType.EXTERNAL_TABLE)) {
            listExternalTables(schemaAccessor, schemaName, identityNameLike, all);
        }
        if (types.contains(DBObjectType.MATERIALIZED_VIEW)) {
            listMViews(schemaAccessor, schemaName, identityNameLike, all);
        }
        // 对于 SQLServer 和 DB2，不预先为所有数据库创建空的 SchemaIdentities
        // 因为 SQLServer 使用 database.schema 格式，DB2 使用 schema 格式
        // 只返回实际有对象的 schema
        if (!isSqlServer && !isDb2) {
            existedDatabases.forEach(db -> all.computeIfAbsent(db, SchemaIdentities::of));
        }
        return new ArrayList<>(all.values());
    }

    void listTables(DBSchemaAccessor schemaAccessor, String schemaName, String tableNameLike,
            Map<String, SchemaIdentities> all) {
        schemaAccessor.listTables(schemaName, tableNameLike)
                .forEach(i -> {
                    String key = getSchemaNameKey(i.getSchemaName());
                    all.computeIfAbsent(key, SchemaIdentities::of).add(i);
                });
    }

    void listViews(DBSchemaAccessor schemaAccessor, String schemaName, String viewNameLike,
            Map<String, SchemaIdentities> all) {
        if (StringUtils.isNotBlank(schemaName) && StringUtils.isBlank(viewNameLike)) {
            schemaAccessor.listViews(schemaName)
                    .forEach(s -> {
                        String key = getSchemaNameKey(s.getSchemaName());
                        all.computeIfAbsent(key, SchemaIdentities::of).add(s);
                    });
        } else {
            schemaAccessor.listAllUserViews(viewNameLike)
                    .stream().filter(i -> StringUtils.isBlank(schemaName) || schemaName.equals(i.getSchemaName()))
                    .forEach(i -> {
                        String key = getSchemaNameKey(i.getSchemaName());
                        all.computeIfAbsent(key, SchemaIdentities::of).add(i);
                    });
            schemaAccessor.listAllSystemViews(viewNameLike)
                    .stream().filter(i -> StringUtils.isBlank(schemaName) || schemaName.equals(i.getSchemaName()))
                    .forEach(i -> {
                        String key = getSchemaNameKey(i.getSchemaName());
                        all.computeIfAbsent(key, SchemaIdentities::of).add(i);
                    });
        }
    }

    void listExternalTables(DBSchemaAccessor schemaAccessor, String schemaName, String tableNameLike,
            Map<String, SchemaIdentities> all) {
        schemaAccessor.listExternalTables(schemaName, tableNameLike)
                .forEach(i -> {
                    String key = getSchemaNameKey(i.getSchemaName());
                    all.computeIfAbsent(key, SchemaIdentities::of).add(i);
                });
    }

    void listMViews(DBSchemaAccessor schemaAccessor, String schemaName, String MViewNameLike,
            Map<String, SchemaIdentities> all) {
        if (StringUtils.isNotBlank(schemaName) && StringUtils.isBlank(MViewNameLike)) {
            schemaAccessor.listMViews(schemaName)
                    .forEach(i -> {
                        String key = getSchemaNameKey(i.getSchemaName());
                        all.computeIfAbsent(key, SchemaIdentities::of).add(i);
                    });
        } else {
            List<DBObjectIdentity> identities = schemaAccessor.listAllMViewsLike(
                    ObjectUtil.defaultIfNull(MViewNameLike, StringUtils.EMPTY));
            identities.stream().filter(i -> StringUtils.isBlank(schemaName) || schemaName.equals(i.getSchemaName()))
                    .forEach(i -> {
                        String key = getSchemaNameKey(i.getSchemaName());
                        all.computeIfAbsent(key, SchemaIdentities::of).add(i);
                    });
        }
    }

    /**
     * 获取安全的 schemaName key，如果为 null 则返回空字符串 防止 TreeMap 使用 String.CASE_INSENSITIVE_ORDER 比较器时出现
     * NullPointerException
     */
    private String getSchemaNameKey(String schemaName) {
        return schemaName == null ? "" : schemaName;
    }

}
