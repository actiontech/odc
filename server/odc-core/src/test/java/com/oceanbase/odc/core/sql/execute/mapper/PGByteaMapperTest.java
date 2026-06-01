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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.SQLException;

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.odc.core.sql.execute.tool.PGTestCellData;
import com.oceanbase.tools.dbbrowser.model.datatype.CommonDataTypeFactory;
import com.oceanbase.tools.dbbrowser.model.datatype.DataType;
import com.oceanbase.tools.dbbrowser.model.datatype.DataTypeFactory;

/**
 * Test cases for {@link PGByteaMapper}
 *
 * @author ODC Team
 * @date 2026-03-12
 */
public class PGByteaMapperTest {

    @Test
    public void mapCell_smallBinary_returnBytes() throws IOException, SQLException {
        DataTypeFactory factory = new CommonDataTypeFactory("bytea");
        DataType dataType = factory.generate();
        PGByteaMapper mapper = new PGByteaMapper();
        PGTestCellData cellData = new PGTestCellData(dataType);
        byte[] bytes = new byte[] {0x01, 0x02, 0x03, 0x04, 0x05};
        cellData.setBinaryStreamValue(new ByteArrayInputStream(bytes));
        Assert.assertEquals("(bytea) 5 B", mapper.mapCell(cellData));
    }

    @Test
    public void mapCell_largeBinaryKb_returnKb() throws IOException, SQLException {
        DataTypeFactory factory = new CommonDataTypeFactory("bytea");
        DataType dataType = factory.generate();
        PGByteaMapper mapper = new PGByteaMapper();
        PGTestCellData cellData = new PGTestCellData(dataType);
        // 2048 bytes = 2 KB
        byte[] bytes = new byte[2048];
        cellData.setBinaryStreamValue(new ByteArrayInputStream(bytes));
        Assert.assertEquals("(bytea) 2 KB", mapper.mapCell(cellData));
    }

    @Test
    public void mapCell_nullInput_returnNull() throws IOException, SQLException {
        DataTypeFactory factory = new CommonDataTypeFactory("bytea");
        DataType dataType = factory.generate();
        PGByteaMapper mapper = new PGByteaMapper();
        PGTestCellData cellData = new PGTestCellData(dataType);
        cellData.setBinaryStreamValue(null);
        Assert.assertNull(mapper.mapCell(cellData));
    }

    @Test
    public void supports_bytea_supports() throws IOException, SQLException {
        PGByteaMapper mapper = new PGByteaMapper();
        DataTypeFactory factory = new CommonDataTypeFactory("bytea");
        Assert.assertTrue(mapper.supports(factory.generate()));
    }

    @Test
    public void supports_blob_notSupports() throws IOException, SQLException {
        PGByteaMapper mapper = new PGByteaMapper();
        DataTypeFactory factory = new CommonDataTypeFactory("blob");
        Assert.assertFalse(mapper.supports(factory.generate()));
    }

}
