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
package com.oceanbase.tools.dbbrowser.stats.hive;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcOperations;

import com.oceanbase.tools.dbbrowser.model.DBSession;
import com.oceanbase.tools.dbbrowser.model.DBTableStats;
import com.oceanbase.tools.dbbrowser.schema.hive.HiveSchemaUtil;
import com.oceanbase.tools.dbbrowser.schema.hive.HiveSchemaUtil.HiveTableMetadata;
import com.oceanbase.tools.dbbrowser.stats.DBStatsAccessor;
import com.oceanbase.tools.dbbrowser.util.StringUtils;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Stats accessor for Apache Hive.
 * <p>
 * Retrieves table statistics by parsing the {@code DESCRIBE FORMATTED} output. Hive statistics
 * ({@code numRows}, {@code totalSize}) are stored in Table Parameters and may not be accurate or
 * up-to-date unless {@code ANALYZE TABLE} has been run. Missing statistics default to -1.
 * </p>
 * <p>
 * Hive does not have a session management interface comparable to MySQL or SQL Server, so
 * {@link #listAllSessions()} returns an empty list and {@link #currentSession()} returns a minimal
 * {@link DBSession} object.
 * </p>
 */
@Slf4j
public class HiveStatsAccessor implements DBStatsAccessor {

    protected final JdbcOperations jdbcOperations;

    public HiveStatsAccessor(@NonNull JdbcOperations jdbcOperations) {
        this.jdbcOperations = jdbcOperations;
    }

    /**
     * Get table statistics by parsing DESCRIBE FORMATTED output.
     * <p>
     * Extracts {@code numRows} and {@code totalSize} from the Table Parameters section. Returns -1
     * for missing or unparseable values.
     * </p>
     *
     * @param schema the database name
     * @param tableName the table name
     * @return table statistics with row count and data size
     */
    @Override
    public DBTableStats getTableStats(@NonNull String schema, @NonNull String tableName) {
        DBTableStats stats = new DBTableStats();
        stats.setRowCount(-1L);
        stats.setDataSizeInBytes(-1L);
        try {
            String sql = HiveSchemaUtil.DESCRIBE_FORMATTED
                    + new com.oceanbase.tools.dbbrowser.util.HiveSqlBuilder()
                            .identifier(schema).append(".").identifier(tableName).toString();
            List<HiveSchemaUtil.DescribeRow> rows = jdbcOperations.query(sql,
                    (rs, rowNum) -> new HiveSchemaUtil.DescribeRow(
                            rs.getString(1), rs.getString(2), rs.getString(3)));
            HiveTableMetadata metadata = HiveSchemaUtil.parseDescribeFormatted(rows);
            Map<String, String> params = metadata.getTableParameters();

            stats.setRowCount(parseLongOrDefault(params.get("numRows"), -1L));
            stats.setDataSizeInBytes(parseLongOrDefault(params.get("totalSize"), -1L));
        } catch (Exception e) {
            log.warn("Failed to get table stats for {}.{}", schema, tableName, e);
        }
        return stats;
    }

    /**
     * Hive does not have a session listing interface.
     *
     * @return empty list
     */
    @Override
    public List<DBSession> listAllSessions() {
        return Collections.emptyList();
    }

    /**
     * Returns a minimal DBSession for Hive.
     *
     * @return a basic session object with unknown transaction state
     */
    @Override
    public DBSession currentSession() {
        return DBSession.unknown();
    }

    private static long parseLongOrDefault(String value, long defaultValue) {
        if (StringUtils.isBlank(value)) {
            return defaultValue;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            // Hive sometimes reports -1 for missing stats
            return parsed >= 0 ? parsed : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

}
