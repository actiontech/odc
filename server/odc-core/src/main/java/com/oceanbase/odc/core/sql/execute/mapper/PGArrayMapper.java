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

import java.sql.Array;
import java.sql.SQLException;

import org.apache.commons.lang3.StringUtils;

import com.oceanbase.tools.dbbrowser.model.datatype.DataType;

import lombok.NonNull;

/**
 * {@link JdbcColumnMapper} for PostgreSQL array types
 *
 * <p>
 * Handles PostgreSQL array types and outputs in PostgreSQL array format: {@code {1,2,3}} for
 * integer arrays, {@code {"a","b","c"}} for text arrays.
 *
 * <p>
 * PostgreSQL supports arrays of any built-in, user-defined, or enum type. Common array types
 * include: {@code _int4}, {@code _int8}, {@code _text}, {@code _float8}, {@code _bool},
 * {@code _numeric}, etc.
 *
 * <p>
 * The JDBC driver returns arrays via {@code getArray()}, and calling {@code toString()} on the
 * Array object yields the PostgreSQL array literal format.
 *
 * @author ODC Team
 * @date 2026-03-12
 * @since ODC_release_4.3.0
 * @see JdbcColumnMapper
 */
public class PGArrayMapper implements JdbcColumnMapper {

    @Override
    public Object mapCell(@NonNull CellData data) throws SQLException {
        Array array = data.getArray();
        if (array == null) {
            return null;
        }
        // PostgreSQL JDBC driver returns the array in standard format {elem1,elem2,...}
        return array.toString();
    }

    @Override
    public boolean supports(@NonNull DataType dataType) {
        // PostgreSQL array type names start with underscore (e.g., _int4, _text)
        // Or contain [] suffix (e.g., int[], text[])
        String typeName = dataType.getDataTypeName();
        if (StringUtils.isEmpty(typeName)) {
            return false;
        }
        return typeName.startsWith("_") || typeName.contains("[]");
    }

}
