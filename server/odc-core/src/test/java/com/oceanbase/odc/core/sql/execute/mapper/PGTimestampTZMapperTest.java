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
import java.sql.Timestamp;

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.odc.core.sql.execute.tool.PGTestCellData;
import com.oceanbase.tools.dbbrowser.model.datatype.CommonDataTypeFactory;
import com.oceanbase.tools.dbbrowser.model.datatype.DataType;
import com.oceanbase.tools.dbbrowser.model.datatype.DataTypeFactory;

/**
 * Test cases for {@link PGTimestampTZMapper}
 *
 * @author ODC Team
 * @date 2026-03-12
 */
public class PGTimestampTZMapperTest {

    @Test
    public void mapCell_normalTimestamp_returnString() throws IOException, SQLException {
        DataTypeFactory factory = new CommonDataTypeFactory("timestamptz");
        DataType dataType = factory.generate();
        PGTimestampTZMapper mapper = new PGTimestampTZMapper();
        PGTestCellData cellData = new PGTestCellData(dataType);
        Timestamp timestamp = Timestamp.valueOf("2026-03-12 10:30:45.123456");
        cellData.setTimestampValue(timestamp);
        Assert.assertNotNull(mapper.mapCell(cellData));
    }

    @Test
    public void mapCell_nullTimestamp_returnNull() throws IOException, SQLException {
        DataTypeFactory factory = new CommonDataTypeFactory("timestamptz");
        DataType dataType = factory.generate();
        PGTimestampTZMapper mapper = new PGTimestampTZMapper();
        PGTestCellData cellData = new PGTestCellData(dataType);
        cellData.setTimestampValue(null);
        Assert.assertNull(mapper.mapCell(cellData));
    }

    @Test
    public void supports_timestamptz_supports() throws IOException, SQLException {
        PGTimestampTZMapper mapper = new PGTimestampTZMapper();
        DataTypeFactory factory = new CommonDataTypeFactory("timestamptz");
        Assert.assertTrue(mapper.supports(factory.generate()));
    }

    @Test
    public void supports_timestampWithTimeZone_supports() throws IOException, SQLException {
        PGTimestampTZMapper mapper = new PGTimestampTZMapper();
        CommonDataTypeFactory factory = new CommonDataTypeFactory("timestamp with time zone");
        Assert.assertTrue(mapper.supports(factory.generate()));
    }

    @Test
    public void supports_timestamp_notSupports() throws IOException, SQLException {
        PGTimestampTZMapper mapper = new PGTimestampTZMapper();
        DataTypeFactory factory = new CommonDataTypeFactory("timestamp");
        Assert.assertFalse(mapper.supports(factory.generate()));
    }

}
