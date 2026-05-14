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
package com.oceanbase.odc.core.sql.execute.mapper;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import com.oceanbase.tools.dbbrowser.model.datatype.DataType;

import lombok.NonNull;

/**
 * {@link JdbcColumnMapper} for DB2-specific large-object / specialty data types not covered by
 * {@link GeneralLobMapper} (which only knows BLOB / CLOB / TINY/MEDIUM/LONGBLOB).
 *
 * <p>
 * Covered DB2 types:
 * <ul>
 * <li>{@code DBCLOB} — double-byte character LOB</li>
 * <li>{@code XML} — DB2 native XML (IBM JCC returns SQLXML / InputStream)</li>
 * </ul>
 *
 * <p>
 * BLOB / CLOB are intentionally NOT claimed here so {@link GeneralLobMapper} remains the single
 * handler for those types (no double-mapping). DECFLOAT is intentionally NOT claimed because IBM
 * JCC returns it as {@link java.math.BigDecimal} which Spring JDBC already string-formats via
 * {@link MySQLNumberMapper} (claims DECIMAL / NUMBER / FIXED; DECFLOAT goes through default
 * Object→toString path). TIMESTAMP / TIMESTAMP WITH TIME ZONE are claimed by
 * {@link MySQLDatetimeMapper} / {@link MySQLTimestampMapper} per design.md §3.6.
 *
 * <p>
 * See design.md §3.6 and compat_risks.md compat-RISK-13.
 */
public class DB2LobMapper implements JdbcColumnMapper {

    private static final List<String> CANDIDATE_TYPES = Arrays.asList("DBCLOB", "XML");
    private static final int KB = 1024;
    private static final int MB = KB * 1024;
    private static final int GB = MB * 1024;

    @Override
    public Object mapCell(@NonNull CellData data) throws SQLException, IOException {
        InputStream inputStream = data.getBinaryStream();
        if (inputStream == null) {
            return null;
        }
        int available = inputStream.available();
        String unit = "B";
        if (available > GB) {
            available = available >> 30;
            unit = "GB";
        } else if (available > MB) {
            available = available >> 20;
            unit = "MB";
        } else if (available > KB) {
            available = available >> 10;
            unit = "KB";
        }
        return String.format("(%s) %d %s", data.getDataType().getDataTypeName(), available, unit);
    }

    @Override
    public boolean supports(@NonNull DataType dataType) {
        for (String type : CANDIDATE_TYPES) {
            if (type.equalsIgnoreCase(dataType.getDataTypeName())) {
                return true;
            }
        }
        return false;
    }

}
