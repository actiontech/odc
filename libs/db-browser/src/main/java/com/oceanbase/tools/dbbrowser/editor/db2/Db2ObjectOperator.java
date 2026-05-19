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
package com.oceanbase.tools.dbbrowser.editor.db2;

import org.springframework.jdbc.core.JdbcOperations;

import com.oceanbase.tools.dbbrowser.editor.DBObjectOperator;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;

import lombok.NonNull;

/**
 * DB2 object operator implementation (B-09 real).
 *
 * <p>
 * 本期仅实现 {@link #drop(DBObjectType, String, String)}（TABLE/VIEW/INDEX）， 生成符合 DB2 SQL 标准的 DROP 语句。
 *
 * @since ODC_release_4.3.4 (Issue dms-ee#839)
 */
public class Db2ObjectOperator implements DBObjectOperator {

    protected final JdbcOperations syncJdbcExecutor;

    public Db2ObjectOperator(@NonNull JdbcOperations syncJdbcExecutor) {
        this.syncJdbcExecutor = syncJdbcExecutor;
    }

    @Override
    public void drop(DBObjectType objectType, String schemaName, String objectName) {
        if (objectType == null || objectName == null || objectName.isEmpty()) {
            throw new IllegalArgumentException("objectType / objectName can not be null or empty");
        }
        StringBuilder sb = new StringBuilder("DROP ");
        sb.append(objectType.getName()).append(' ');
        if (schemaName != null && !schemaName.isEmpty()) {
            sb.append(quoteIdentifier(schemaName)).append('.');
        }
        sb.append(quoteIdentifier(objectName));
        syncJdbcExecutor.execute(sb.toString());
    }

    /**
     * DB2 标准双引号引用 identifier，并转义内部双引号
     */
    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

}
