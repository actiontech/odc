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

import com.oceanbase.tools.dbbrowser.model.datatype.DataType;

import lombok.NonNull;

/**
 * {@link JdbcColumnMapper} for PostgreSQL data type {@code boolean}/{@code bool}
 *
 * <p>
 * Maps PostgreSQL boolean values to string representation "true" or "false". PostgreSQL has native
 * boolean type with values TRUE/FALSE/NULL.
 *
 * <p>
 * Uses {@code getObject()} instead of {@code getBoolean()} to properly distinguish between null
 * values and false, since {@code getBoolean()} returns primitive boolean which cannot represent
 * null.
 *
 * @author ODC Team
 * @date 2026-03-12
 * @since ODC_release_4.3.0
 * @see JdbcColumnMapper
 */
public class PGBooleanMapper implements JdbcColumnMapper {

    @Override
    public Object mapCell(@NonNull CellData data) throws SQLException {
        // Use getObject() to properly handle null values
        // getBoolean() returns primitive boolean which returns false for null
        Object value = data.getObject();
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value ? "true" : "false";
        }
        // Fallback for other representations
        return String.valueOf(value);
    }

    @Override
    public boolean supports(@NonNull DataType dataType) {
        String typeName = dataType.getDataTypeName();
        return "BOOLEAN".equalsIgnoreCase(typeName) || "BOOL".equalsIgnoreCase(typeName);
    }

}
