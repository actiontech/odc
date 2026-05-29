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

import com.oceanbase.odc.common.util.tableformat.BorderStyle;
import com.oceanbase.odc.common.util.tableformat.CellStyle;
import com.oceanbase.odc.common.util.tableformat.CellStyle.AbbreviationStyle;
import com.oceanbase.odc.common.util.tableformat.CellStyle.HorizontalAlign;
import com.oceanbase.odc.common.util.tableformat.CellStyle.NullStyle;
import com.oceanbase.odc.common.util.tableformat.Table;
import com.oceanbase.odc.core.shared.model.SqlExecDetail;
import com.oceanbase.odc.plugin.connect.api.SqlDiagnoseExtensionPoint;
import com.oceanbase.odc.plugin.connect.model.diagnose.SqlExplain;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * SQL diagnose extension for Apache Hive. Hive supports {@code EXPLAIN <sql>} which returns a
 * text-based execution plan. Physical plan introspection and execution-detail queries are not
 * supported.
 *
 * @since ODC_release_4.3.4
 */
@Slf4j
@Extension
public class HiveDiagnoseExtension implements SqlDiagnoseExtensionPoint {

    @Override
    public SqlExplain getExplain(Statement statement, @NonNull String sql) throws SQLException {
        SqlExplain sqlExplain = new SqlExplain();
        try {
            String explainSql = "EXPLAIN " + sql;
            ResultSet resultSet = statement.executeQuery(explainSql);
            ResultSetMetaData metaData = resultSet.getMetaData();
            int colCount = metaData.getColumnCount();
            Table table = new Table(colCount, BorderStyle.HORIZONTAL_ONLY);
            CellStyle cs = new CellStyle(HorizontalAlign.LEFT, AbbreviationStyle.DOTS, NullStyle.NULL_TEXT);
            for (int i = 1; i <= colCount; i++) {
                table.setColumnWidth(i - 1, 10, 120);
                table.addCell(metaData.getColumnName(i), cs);
            }
            while (resultSet.next()) {
                for (int i = 1; i <= colCount; i++) {
                    table.addCell(resultSet.getString(i), cs);
                }
            }
            sqlExplain.setOriginalText(table.render().toString());
            sqlExplain.setShowFormatInfo(false);
        } catch (Exception e) {
            log.warn("Failed to get EXPLAIN from Hive", e);
            sqlExplain.setOriginalText("EXPLAIN not available: " + e.getMessage());
            sqlExplain.setShowFormatInfo(false);
        }
        return sqlExplain;
    }

    @Override
    public SqlExplain getPhysicalPlanBySqlId(Connection connection, @NonNull String sqlId) throws SQLException {
        throw new UnsupportedOperationException("Physical plan by SQL ID is not supported for Hive");
    }

    @Override
    public SqlExplain getPhysicalPlanBySql(Connection connection, @NonNull String sql) throws SQLException {
        throw new UnsupportedOperationException("Physical plan by SQL is not supported for Hive");
    }

    @Override
    public SqlExecDetail getExecutionDetailById(Connection connection, @NonNull String id) throws SQLException {
        throw new UnsupportedOperationException("Execution detail by ID is not supported for Hive");
    }

    @Override
    public SqlExecDetail getExecutionDetailBySql(Connection connection, @NonNull String sql) throws SQLException {
        throw new UnsupportedOperationException("Execution detail by SQL is not supported for Hive");
    }

    @Override
    public SqlExplain getQueryProfileByTraceIdAndSessIds(Connection connection, @NonNull String traceId,
            @NonNull List<String> sessionIds) throws SQLException {
        throw new UnsupportedOperationException("Query profile is not supported for Hive");
    }
}
