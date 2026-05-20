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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.pf4j.Extension;

import com.oceanbase.odc.core.shared.model.SqlExecDetail;
import com.oceanbase.odc.plugin.connect.api.SqlDiagnoseExtensionPoint;
import com.oceanbase.odc.plugin.connect.model.diagnose.SqlExplain;

import lombok.NonNull;

/**
 * Hive supports {@code EXPLAIN <sql>} but not OceanBase-style physical plan / execution detail
 * inspection. We surface the textual EXPLAIN output via {@link #getExplain} and short-circuit the
 * OB-specific entry points with {@link UnsupportedOperationException} so the UI can hide them
 * cleanly (compat-RISK R-4.2).
 *
 * @since ODC_release_4.3.4
 */
@Extension
public class HiveDiagnoseExtensionPoint implements SqlDiagnoseExtensionPoint {

    private static final String UNSUPPORTED_MSG = "Hive does not support this diagnose operation";

    @Override
    public SqlExplain getExplain(Statement statement, @NonNull String sql) throws SQLException {
        SqlExplain explain = new SqlExplain();
        StringBuilder text = new StringBuilder();
        try (ResultSet rs = statement.executeQuery("EXPLAIN " + sql)) {
            ResultSetMetaData md = rs.getMetaData();
            int cols = md.getColumnCount();
            while (rs.next()) {
                for (int i = 1; i <= cols; i++) {
                    if (i > 1) {
                        text.append('\t');
                    }
                    String v = rs.getString(i);
                    text.append(v == null ? "" : v);
                }
                text.append('\n');
            }
        }
        explain.setOriginalText(text.toString());
        explain.setShowFormatInfo(false);
        return explain;
    }

    @Override
    public SqlExplain getPhysicalPlanBySqlId(Connection connection, @NonNull String sqlId) {
        throw new UnsupportedOperationException(UNSUPPORTED_MSG);
    }

    @Override
    public SqlExplain getPhysicalPlanBySql(Connection connection, @NonNull String sql) {
        throw new UnsupportedOperationException(UNSUPPORTED_MSG);
    }

    @Override
    public SqlExecDetail getExecutionDetailById(Connection connection, @NonNull String id) {
        throw new UnsupportedOperationException(UNSUPPORTED_MSG);
    }

    @Override
    public SqlExecDetail getExecutionDetailBySql(Connection connection, @NonNull String sql) {
        throw new UnsupportedOperationException(UNSUPPORTED_MSG);
    }

    @Override
    public SqlExplain getQueryProfileByTraceIdAndSessIds(Connection connection, @NonNull String traceId,
            @NonNull List<String> sessionIds) {
        throw new UnsupportedOperationException(UNSUPPORTED_MSG);
    }
}
