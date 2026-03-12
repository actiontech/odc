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
import java.sql.Array;
import java.sql.SQLException;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.oceanbase.odc.core.sql.execute.tool.PGTestCellData;
import com.oceanbase.tools.dbbrowser.model.datatype.CommonDataTypeFactory;
import com.oceanbase.tools.dbbrowser.model.datatype.DataType;
import com.oceanbase.tools.dbbrowser.model.datatype.DataTypeFactory;

/**
 * Test cases for {@link PGArrayMapper}
 *
 * @author ODC Team
 * @date 2026-03-12
 */
public class PGArrayMapperTest {

    @Test
    public void mapCell_intArray_returnArrayString() throws IOException, SQLException {
        DataTypeFactory factory = new CommonDataTypeFactory("_int4");
        DataType dataType = factory.generate();
        PGArrayMapper mapper = new PGArrayMapper();
        PGTestCellData cellData = new PGTestCellData(dataType);

        Array mockArray = Mockito.mock(Array.class);
        Mockito.when(mockArray.toString()).thenReturn("{1,2,3}");
        cellData.setArrayValue(mockArray);

        Assert.assertEquals("{1,2,3}", mapper.mapCell(cellData));
    }

    @Test
    public void mapCell_textArray_returnArrayString() throws IOException, SQLException {
        DataTypeFactory factory = new CommonDataTypeFactory("_text");
        DataType dataType = factory.generate();
        PGArrayMapper mapper = new PGArrayMapper();
        PGTestCellData cellData = new PGTestCellData(dataType);

        Array mockArray = Mockito.mock(Array.class);
        Mockito.when(mockArray.toString()).thenReturn("{a,b,c}");
        cellData.setArrayValue(mockArray);

        Assert.assertEquals("{a,b,c}", mapper.mapCell(cellData));
    }

    @Test
    public void mapCell_nullArray_returnNull() throws IOException, SQLException {
        DataTypeFactory factory = new CommonDataTypeFactory("_int4");
        DataType dataType = factory.generate();
        PGArrayMapper mapper = new PGArrayMapper();
        PGTestCellData cellData = new PGTestCellData(dataType);
        cellData.setArrayValue(null);
        Assert.assertNull(mapper.mapCell(cellData));
    }

    @Test
    public void supports_int4Array_supports() throws IOException, SQLException {
        PGArrayMapper mapper = new PGArrayMapper();
        DataTypeFactory factory = new CommonDataTypeFactory("_int4");
        Assert.assertTrue(mapper.supports(factory.generate()));
    }

    @Test
    public void supports_textArray_supports() throws IOException, SQLException {
        PGArrayMapper mapper = new PGArrayMapper();
        DataTypeFactory factory = new CommonDataTypeFactory("_text");
        Assert.assertTrue(mapper.supports(factory.generate()));
    }

    @Test
    public void supports_int8Array_supports() throws IOException, SQLException {
        PGArrayMapper mapper = new PGArrayMapper();
        DataTypeFactory factory = new CommonDataTypeFactory("_int8");
        Assert.assertTrue(mapper.supports(factory.generate()));
    }

    @Test
    public void supports_integer_notSupports() throws IOException, SQLException {
        PGArrayMapper mapper = new PGArrayMapper();
        DataTypeFactory factory = new CommonDataTypeFactory("integer");
        Assert.assertFalse(mapper.supports(factory.generate()));
    }

}
