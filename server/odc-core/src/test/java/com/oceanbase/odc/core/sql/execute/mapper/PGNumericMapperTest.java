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
import java.math.BigDecimal;
import java.sql.SQLException;

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.odc.core.sql.execute.tool.PGTestCellData;
import com.oceanbase.tools.dbbrowser.model.datatype.CommonDataTypeFactory;
import com.oceanbase.tools.dbbrowser.model.datatype.DataType;
import com.oceanbase.tools.dbbrowser.model.datatype.DataTypeFactory;

/**
 * Test cases for {@link PGNumericMapper}
 *
 * @author ODC Team
 * @date 2026-03-12
 */
public class PGNumericMapperTest {

    @Test
    public void mapCell_normalValue_returnPlainString() throws IOException, SQLException {
        DataTypeFactory factory = new CommonDataTypeFactory("numeric");
        DataType dataType = factory.generate();
        PGNumericMapper mapper = new PGNumericMapper();
        PGTestCellData cellData = new PGTestCellData(dataType);
        cellData.setBigDecimalValue(new BigDecimal("123.456"));
        Assert.assertEquals("123.456", mapper.mapCell(cellData));
    }

    @Test
    public void mapCell_highPrecisionValue_preservePrecision() throws IOException, SQLException {
        DataTypeFactory factory = new CommonDataTypeFactory("numeric");
        DataType dataType = factory.generate();
        PGNumericMapper mapper = new PGNumericMapper();
        PGTestCellData cellData = new PGTestCellData(dataType);
        cellData.setBigDecimalValue(new BigDecimal("123456789.12345678901234567890"));
        Assert.assertEquals("123456789.12345678901234567890", mapper.mapCell(cellData));
    }

    @Test
    public void mapCell_nullValue_returnNull() throws IOException, SQLException {
        DataTypeFactory factory = new CommonDataTypeFactory("numeric");
        DataType dataType = factory.generate();
        PGNumericMapper mapper = new PGNumericMapper();
        PGTestCellData cellData = new PGTestCellData(dataType);
        cellData.setBigDecimalValue(null);
        Assert.assertNull(mapper.mapCell(cellData));
    }

    @Test
    public void supports_numeric_supports() throws IOException, SQLException {
        PGNumericMapper mapper = new PGNumericMapper();
        DataTypeFactory factory = new CommonDataTypeFactory("numeric");
        Assert.assertTrue(mapper.supports(factory.generate()));
    }

    @Test
    public void supports_decimal_supports() throws IOException, SQLException {
        PGNumericMapper mapper = new PGNumericMapper();
        DataTypeFactory factory = new CommonDataTypeFactory("decimal");
        Assert.assertTrue(mapper.supports(factory.generate()));
    }

    @Test
    public void supports_integer_notSupports() throws IOException, SQLException {
        PGNumericMapper mapper = new PGNumericMapper();
        DataTypeFactory factory = new CommonDataTypeFactory("integer");
        Assert.assertFalse(mapper.supports(factory.generate()));
    }

}
