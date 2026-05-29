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

import lombok.extern.slf4j.Slf4j;

/**
 * Trace extension for Apache Hive. Hive does not provide per-statement execution metrics comparable
 * to OceanBase's trace/profile system, so this returns an empty {@link SqlExecTime} stub. The ODC
 * frontend will still display wall-clock elapsed time.
 *
 * @since ODC_release_4.3.4
 */
@Slf4j
@Extension
public class HiveTraceExtension implements TraceExtensionPoint {

    @Override
    public SqlExecTime getExecuteDetail(Statement statement, String version) throws SQLException {
        // Hive does not expose per-statement execution time metrics via JDBC.
        // Return an empty SqlExecTime; the caller (OdcStatementCallBack) will fall
        // back to wall-clock timing.
        return new SqlExecTime();
    }
}
