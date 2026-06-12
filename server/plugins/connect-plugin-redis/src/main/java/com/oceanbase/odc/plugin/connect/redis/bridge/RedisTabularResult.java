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
package com.oceanbase.odc.plugin.connect.redis.bridge;

import java.util.Collections;
import java.util.List;

public class RedisTabularResult {
    private final List<String> columns;
    private final List<String> columnTypeNames;
    private final List<List<Object>> rows;

    public RedisTabularResult(List<String> columns, List<String> columnTypeNames, List<List<Object>> rows) {
        this.columns = Collections.unmodifiableList(columns);
        this.columnTypeNames = Collections.unmodifiableList(columnTypeNames);
        this.rows = Collections.unmodifiableList(rows);
    }

    public List<String> getColumns() {
        return columns;
    }

    public List<String> getColumnTypeNames() {
        return columnTypeNames;
    }

    public List<List<Object>> getRows() {
        return rows;
    }
}
