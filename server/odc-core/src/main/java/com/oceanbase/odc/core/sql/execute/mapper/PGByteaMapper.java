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

import com.oceanbase.tools.dbbrowser.model.datatype.DataType;

import lombok.NonNull;

/**
 * {@link JdbcColumnMapper} for PostgreSQL data type {@code bytea}
 *
 * <p>
 * Handles PostgreSQL binary data (bytea) with truncation display. Shows the size and unit
 * (B/KB/MB/GB) instead of full binary content to improve display performance for large binary data.
 *
 * <p>
 * PostgreSQL bytea type stores binary strings. The hex format (introduced in PostgreSQL 9.0) is the
 * default output format: {@code \x followed by hexadecimal digits}
 *
 * @author ODC Team
 * @date 2026-03-12
 * @since ODC_release_4.3.0
 * @see JdbcColumnMapper
 */
public class PGByteaMapper implements JdbcColumnMapper {

    private static final String BYTEA = "BYTEA";
    private static final int KB = 1024;
    private static final int MB = KB * 1024;
    private static final int GB = MB * 1024;

    @Override
    public Object mapCell(@NonNull CellData data) throws SQLException, IOException {
        InputStream inputStream = data.getBinaryStream();
        if (inputStream == null) {
            return null;
        }
        String unit = "B";
        int available = inputStream.available();
        if (available >= GB) {
            available = available >> 30;
            unit = "GB";
        } else if (available >= MB) {
            available = available >> 20;
            unit = "MB";
        } else if (available >= KB) {
            available = available >> 10;
            unit = "KB";
        }
        return String.format("(%s) %d %s", data.getDataType().getDataTypeName(), available, unit);
    }

    @Override
    public boolean supports(@NonNull DataType dataType) {
        return BYTEA.equalsIgnoreCase(dataType.getDataTypeName());
    }

}
