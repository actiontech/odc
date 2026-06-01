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

import org.pf4j.Extension;

import com.oceanbase.odc.common.util.JdbcOperationsUtil;
import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.odc.plugin.schema.db2.utils.DBAccessorUtil;
import com.oceanbase.odc.plugin.schema.obmysql.OBMySQLViewExtension;
import com.oceanbase.tools.dbbrowser.DBBrowser;
import com.oceanbase.tools.dbbrowser.editor.DBObjectOperator;
import com.oceanbase.tools.dbbrowser.editor.db2.Db2ObjectOperator;
import com.oceanbase.tools.dbbrowser.model.DBView;
import com.oceanbase.tools.dbbrowser.schema.DBSchemaAccessor;
import com.oceanbase.tools.dbbrowser.template.DBObjectTemplate;

/**
 * DB2 view extension (fix-I).
 *
 * <p>
 * Registers the {@code ViewExtensionPoint} pf4j extension so the v1 view controller
 * ({@code /api/v1/view/list/{sid}}) stops returning
 * {@code "Feature extension point is not supported for DB2"} and the front-end renders the "视图"
 * category node under each DB2 schema (case 2.4 view-list regression).
 *
 * <p>
 * Inherits from {@link OBMySQLViewExtension} to reuse the {@code list}/{@code listSystemViews}/
 * {@code getDetail}/{@code drop}/{@code generateCreateTemplate} method bodies that delegate to
 * {@code SchemaAccessor}/{@code Operator}/{@code Template}, and overrides three protected hooks:
 *
 * <ol>
 * <li>{@link #getSchemaAccessor(Connection)} — routes via
 * {@code DBAccessorUtil.getSchemaAccessor(connection)} to the DB2-dialect schema accessor
 * (initially added by feat-839 commit-B; see
 * {@code Db2SchemaAccessor#listViews/listAllUserViews/showSystemViews
 * /getView}).
 * <li>{@link #getOperator(Connection)} — uses {@link Db2ObjectOperator}, which emits DB2-style
 * double-quoted identifiers (DB2 11.5 requires {@code "} not the MySQL backtick); reuses the
 * already-implemented {@code drop(DBObjectType.VIEW, schema, name)} path.
 * <li>{@link #getTemplate()} — keeps the OB-MySQL view template factory because the
 * {@code MySQLViewTemplate} produces a generic SELECT scaffold the front-end uses for the "create
 * view" wizard. Routing through {@code buildForDB2()} on the factory would throw
 * {@code UnsupportedOperationException} (DB2 has its own DDL we don't yet emit). The scaffold is
 * literally a {@code "select * from ..."} starter — DB2-compatible by construction — but DDL-driven
 * features (view create / view DDL) remain out of scope per design.md §6.
 * </ol>
 *
 * @author actiontech-zihan
 * @since 4.3.4 (Issue dms-ee#839, fix-I)
 */
@Extension
public class Db2ViewExtension extends OBMySQLViewExtension {

    @Override
    protected DBSchemaAccessor getSchemaAccessor(Connection connection) {
        return DBAccessorUtil.getSchemaAccessor(connection);
    }

    @Override
    protected DBObjectOperator getOperator(Connection connection) {
        return new Db2ObjectOperator(JdbcOperationsUtil.getJdbcOperations(connection));
    }

    @Override
    protected DBObjectTemplate<DBView> getTemplate() {
        // The DB-Browser view template factory throws UnsupportedOperationException for
        // buildForDB2(); fall back to the generic MySQL view scaffold (a plain "select * from ..."
        // starter) so the "create view" UI surface — if reachable — gets a syntactically valid
        // skeleton instead of crashing. Real DB2 view DDL is out of scope per design.md §6.
        return DBBrowser.objectTemplate().viewTemplate()
                .setType(DialectType.OB_MYSQL.getDBBrowserDialectTypeName()).create();
    }

}
