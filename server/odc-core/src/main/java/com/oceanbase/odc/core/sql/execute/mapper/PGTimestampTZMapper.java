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

import java.sql.SQLException;
import java.sql.Timestamp;

import org.apache.commons.lang3.StringUtils;

import com.oceanbase.tools.dbbrowser.model.datatype.DataType;

import lombok.NonNull;

/**
 * {@link JdbcColumnMapper} for PostgreSQL data type {@code timestamptz} (timestamp with time zone)
 *
 * <p>
 * Handles PostgreSQL timestamptz type and ensures timezone information is correctly formatted for
 * display.
 *
 * <p>
 * PostgreSQL timestamptz stores timestamp with time zone information. The JDBC driver returns it as
 * {@code java.sql.Timestamp}, shifted to the client's timezone. This mapper uses the string
 * representation from JDBC which includes timezone offset.
 *
 * <p>
 * Note: For proper timezone handling, consider using {@code getTimestamp(Calendar)} with a specific
 * calendar if needed.
 *
 * @author ODC Team
 * @date 2026-03-12
 * @since ODC_release_4.3.0
 * @see JdbcColumnMapper
 */
public class PGTimestampTZMapper implements JdbcColumnMapper {

    private static final String TIMESTAMPTZ = "TIMESTAMPTZ";
    private static final String TIMESTAMP_WITH_TIME_ZONE = "TIMESTAMP WITH TIME ZONE";
    private static final String TIMESTZ = "TIMESTZ";

    @Override
    public Object mapCell(@NonNull CellData data) throws SQLException {
        Timestamp timestamp = data.getTimestamp();
        if (timestamp == null) {
            return null;
        }
        // Use Timestamp.toString() which includes nanoseconds
        // The JDBC driver handles timezone conversion automatically
        return timestamp.toString();
    }

    @Override
    public boolean supports(@NonNull DataType dataType) {
        String typeName = dataType.getDataTypeName();
        if (StringUtils.isEmpty(typeName)) {
            return false;
        }
        String upperTypeName = typeName.toUpperCase();
        return TIMESTAMPTZ.equalsIgnoreCase(typeName)
                || upperTypeName.contains("TIMESTAMP WITH TIME ZONE")
                || TIMESTZ.equalsIgnoreCase(typeName);
    }

}
