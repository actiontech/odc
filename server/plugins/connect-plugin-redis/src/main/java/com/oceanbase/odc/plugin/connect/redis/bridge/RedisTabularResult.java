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
