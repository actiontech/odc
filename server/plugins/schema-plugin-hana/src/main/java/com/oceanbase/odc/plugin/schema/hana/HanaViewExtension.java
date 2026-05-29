/*
 * Copyright (c) 2024 OceanBase.
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
package com.oceanbase.odc.plugin.schema.hana;

import java.sql.Connection;

import org.pf4j.Extension;

import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.odc.plugin.schema.obmysql.OBMySQLViewExtension;
import com.oceanbase.odc.plugin.schema.hana.utils.DBAccessorUtil;
import com.oceanbase.tools.dbbrowser.DBBrowser;
import com.oceanbase.tools.dbbrowser.model.DBView;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;
import com.oceanbase.tools.dbbrowser.template.DBObjectTemplate;

import lombok.NonNull;

/**
 * SAP HANA view extension for view metadata retrieval and DDL template generation.
 *
 * @since ODC_release_4.3.4
 */
@Extension
public class HanaViewExtension extends OBMySQLViewExtension {

    @Override
    protected DBSchemaAccessor getSchemaAccessor(Connection connection) {
        return DBAccessorUtil.getSchemaAccessor(connection);
    }

    @Override
    public String generateCreateTemplate(@NonNull DBView view) {
        return getTemplate().generateCreateObjectTemplate(view);
    }

    @Override
    protected DBObjectTemplate<DBView> getTemplate() {
        return DBBrowser.objectTemplate().viewTemplate()
                .setType(DialectType.HANA.getDBBrowserDialectTypeName()).create();
    }

}
