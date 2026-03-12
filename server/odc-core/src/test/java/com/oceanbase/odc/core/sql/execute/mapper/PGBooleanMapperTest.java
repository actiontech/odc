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
import java.sql.SQLException;

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.odc.core.sql.execute.tool.PGTestCellData;
import com.oceanbase.tools.dbbrowser.model.datatype.CommonDataTypeFactory;
import com.oceanbase.tools.dbbrowser.model.datatype.DataType;
import com.oceanbase.tools.dbbrowser.model.datatype.DataTypeFactory;

/**
 * Test cases for {@link PGBooleanMapper}
 *
 * @author ODC Team
 * @date 2026-03-12
 */
public class PGBooleanMapperTest {

    @Test
    public void mapCell_trueValue_returnTrueString() throws IOException, SQLException {
        DataTypeFactory factory = new CommonDataTypeFactory("boolean");
        DataType dataType = factory.generate();
        PGBooleanMapper mapper = new PGBooleanMapper();
        PGTestCellData cellData = new PGTestCellData(dataType);
        cellData.setObjectValue(Boolean.TRUE);
        Assert.assertEquals("true", mapper.mapCell(cellData));
    }

    @Test
    public void mapCell_falseValue_returnFalseString() throws IOException, SQLException {
        DataTypeFactory factory = new CommonDataTypeFactory("boolean");
        DataType dataType = factory.generate();
        PGBooleanMapper mapper = new PGBooleanMapper();
        PGTestCellData cellData = new PGTestCellData(dataType);
        cellData.setObjectValue(Boolean.FALSE);
        Assert.assertEquals("false", mapper.mapCell(cellData));
    }

    @Test
    public void mapCell_nullValue_returnNull() throws IOException, SQLException {
        DataTypeFactory factory = new CommonDataTypeFactory("boolean");
        DataType dataType = factory.generate();
        PGBooleanMapper mapper = new PGBooleanMapper();
        PGTestCellData cellData = new PGTestCellData(dataType);
        cellData.setObjectValue(null);
        Assert.assertNull(mapper.mapCell(cellData));
    }

    @Test
    public void supports_boolean_supports() throws IOException, SQLException {
        PGBooleanMapper mapper = new PGBooleanMapper();
        DataTypeFactory factory = new CommonDataTypeFactory("boolean");
        Assert.assertTrue(mapper.supports(factory.generate()));
    }

    @Test
    public void supports_bool_supports() throws IOException, SQLException {
        PGBooleanMapper mapper = new PGBooleanMapper();
        DataTypeFactory factory = new CommonDataTypeFactory("bool");
        Assert.assertTrue(mapper.supports(factory.generate()));
    }

    @Test
    public void supports_varchar_notSupports() throws IOException, SQLException {
        PGBooleanMapper mapper = new PGBooleanMapper();
        DataTypeFactory factory = new CommonDataTypeFactory("varchar");
        Assert.assertFalse(mapper.supports(factory.generate()));
    }

}
