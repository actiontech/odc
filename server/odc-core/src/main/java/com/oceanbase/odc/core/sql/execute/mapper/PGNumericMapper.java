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

import java.math.BigDecimal;
import java.sql.SQLException;

import org.apache.commons.lang3.StringUtils;

import com.oceanbase.tools.dbbrowser.model.datatype.DataType;

import lombok.NonNull;

/**
 * {@link JdbcColumnMapper} for PostgreSQL data type {@code numeric}/{@code decimal}
 *
 * <p>
 * Ensures NUMERIC/DECIMAL precision is preserved in string representation. PostgreSQL NUMERIC and
 * DECIMAL types preserve exact precision and scale. Using BigDecimal.toString() ensures the full
 * precision is maintained.
 *
 * @author ODC Team
 * @date 2026-03-12
 * @since ODC_release_4.3.0
 * @see JdbcColumnMapper
 */
public class PGNumericMapper implements JdbcColumnMapper {

    @Override
    public Object mapCell(@NonNull CellData data) throws SQLException {
        BigDecimal value = data.getBigDecimal();
        if (value == null) {
            return null;
        }
        return value.toPlainString();
    }

    @Override
    public boolean supports(@NonNull DataType dataType) {
        String typeName = dataType.getDataTypeName();
        return StringUtils.containsAnyIgnoreCase(typeName, "NUMERIC", "DECIMAL");
    }

}
