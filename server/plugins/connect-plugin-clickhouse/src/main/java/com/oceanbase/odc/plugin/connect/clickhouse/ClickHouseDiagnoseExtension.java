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
package com.oceanbase.odc.plugin.connect.clickhouse;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.pf4j.Extension;

import com.oceanbase.odc.core.shared.model.SqlExecDetail;
import com.oceanbase.odc.plugin.connect.api.SqlDiagnoseExtensionPoint;
import com.oceanbase.odc.plugin.connect.model.diagnose.SqlExplain;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Extension
public class ClickHouseDiagnoseExtension implements SqlDiagnoseExtensionPoint {

    @Override
    public SqlExplain getExplain(Statement statement, @NonNull String sql) throws SQLException {
        SqlExplain sqlExplain = new SqlExplain();
        try {
            ResultSet resultSet = statement.executeQuery("EXPLAIN " + sql);
            StringBuilder builder = new StringBuilder();
            int colCount = resultSet.getMetaData().getColumnCount();
            while (resultSet.next()) {
                for (int i = 1; i <= colCount; i++) {
                    if (i > 1) {
                        builder.append('\t');
                    }
                    builder.append(resultSet.getString(i));
                }
                builder.append('\n');
            }
            sqlExplain.setOriginalText(builder.toString());
            sqlExplain.setShowFormatInfo(false);
        } catch (Exception e) {
            log.warn("Failed to get EXPLAIN from ClickHouse", e);
            sqlExplain.setOriginalText("EXPLAIN not available: " + e.getMessage());
            sqlExplain.setShowFormatInfo(false);
        }
        return sqlExplain;
    }

    @Override
    public SqlExplain getPhysicalPlanBySqlId(Connection connection, @NonNull String sqlId) {
        throw new UnsupportedOperationException("Physical plan by SQL ID is not supported for ClickHouse");
    }

    @Override
    public SqlExplain getPhysicalPlanBySql(Connection connection, @NonNull String sql) {
        throw new UnsupportedOperationException("Physical plan by SQL is not supported for ClickHouse");
    }

    @Override
    public SqlExecDetail getExecutionDetailById(Connection connection, @NonNull String id) {
        throw new UnsupportedOperationException("Execution detail by ID is not supported for ClickHouse");
    }

    @Override
    public SqlExecDetail getExecutionDetailBySql(Connection connection, @NonNull String sql) {
        throw new UnsupportedOperationException("Execution detail by SQL is not supported for ClickHouse");
    }

    @Override
    public SqlExplain getQueryProfileByTraceIdAndSessIds(Connection connection, @NonNull String traceId,
            @NonNull List<String> sessionIds) {
        throw new UnsupportedOperationException("Query profile is not supported for ClickHouse");
    }
}
