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
package com.oceanbase.odc.plugin.connect.hive.initializer;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;

import com.oceanbase.odc.core.datasource.ConnectionInitializer;

import lombok.extern.slf4j.Slf4j;

/**
 * Initializer executed after a Hive JDBC connection is acquired. Currently runs no default
 * {@code SET} statements: design §4.1.4 leaves the SET list empty so that the worker classroom
 * decides execution engine (mr / tez / spark) explicitly. Reserved for future tuning hooks.
 *
 * @since ODC_release_4.3.4
 */
@Slf4j
public class HiveConnectionInitializer implements ConnectionInitializer {

    /**
     * Default {@code SET} statements applied to a fresh Hive session. Override / extend here when a
     * tuning knob becomes necessary; keeping the list empty avoids surprising upstream tools.
     */
    private static final List<String> DEFAULT_SETS = Collections.emptyList();

    @Override
    public void init(Connection connection) throws SQLException {
        if (DEFAULT_SETS.isEmpty()) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            for (String sql : DEFAULT_SETS) {
                try {
                    statement.execute(sql);
                } catch (Exception e) {
                    log.warn("Hive connection initializer failed to apply SET, sql={}, errMsg={}",
                            sql, e.getMessage());
                }
            }
        }
    }
}
