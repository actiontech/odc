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
package com.oceanbase.odc.plugin.connect.hive;

import java.sql.SQLException;
import java.sql.Statement;

import org.pf4j.Extension;

import com.oceanbase.odc.core.sql.execute.model.SqlExecTime;
import com.oceanbase.odc.plugin.connect.api.TraceExtensionPoint;

/**
 * Hive does not expose per-statement execution time / profiling via JDBC the way MySQL's
 * {@code SHOW PROFILE} does. Return an empty {@link SqlExecTime} - upstream callers must treat
 * "execute_microseconds == null" as "not reported" rather than zero (compat-RISK R-4.2).
 *
 * @since ODC_release_4.3.4
 */
@Extension
public class HiveTraceExtension implements TraceExtensionPoint {

    @Override
    public SqlExecTime getExecuteDetail(Statement statement, String version) throws SQLException {
        // Hive 4.x exposes operation handle id via beeline, but not in a stable JDBC field.
        // Return empty trace - this is allowed by the contract.
        return new SqlExecTime();
    }
}
