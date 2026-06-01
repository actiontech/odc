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

import com.oceanbase.odc.core.sql.execute.tool.LobCellData;
import com.oceanbase.tools.dbbrowser.model.datatype.CommonDataTypeFactory;
import com.oceanbase.tools.dbbrowser.model.datatype.DataType;
import com.oceanbase.tools.dbbrowser.model.datatype.DataTypeFactory;

/**
 * Test cases for {@link GeneralLobMapper}
 *
 * @author yh263208
 * @date 2022-07-14 12:11
 */
public class GeneralLobMapperTest {

    @Test
    public void mapCell_nonNullInputStream_returnRightValue() throws IOException, SQLException {
        DataTypeFactory factory = new CommonDataTypeFactory("blob");
        DataType dataType = factory.generate();
        GeneralLobMapper mapper = new GeneralLobMapper();
        // No Blob handle exposed → falls back to InputStream#available()
        Assert.assertEquals("(blob) 12 B", mapper.mapCell(new LobCellData(12, dataType)));
    }

    @Test
    public void mapCell_nullInputStream_returnNull() throws IOException, SQLException {
        DataTypeFactory factory = new CommonDataTypeFactory("blob");
        DataType dataType = factory.generate();
        GeneralLobMapper mapper = new GeneralLobMapper();
        Assert.assertNull(mapper.mapCell(new LobCellData(-1, dataType)));
    }

    @Test
    public void mapCell_blobWithHandle_returnsBlobLength() throws IOException, SQLException {
        // fix-K: prefer Blob#length() over InputStream#available() when the driver gives one.
        DataTypeFactory factory = new CommonDataTypeFactory("blob");
        DataType dataType = factory.generate();
        GeneralLobMapper mapper = new GeneralLobMapper();
        Assert.assertEquals("(blob) 7 B", mapper.mapCell(new LobCellData(7L, dataType, true)));
    }

    @Test
    public void mapCell_clob_usesClobLengthInsteadOfBinaryStream() throws IOException, SQLException {
        // fix-K: DB2 jcc throws ERRORCODE=-4461 (SQLSTATE=42815) when binary stream is requested
        // for a CLOB column. The mapper must go through Clob#length() instead.
        DataTypeFactory factory = new CommonDataTypeFactory("clob");
        DataType dataType = factory.generate();
        GeneralLobMapper mapper = new GeneralLobMapper();
        Assert.assertEquals("(clob) 33 B", mapper.mapCell(new LobCellData(33L, dataType, false)));
    }

    @Test
    public void mapCell_dbclob_db2DoubleByteCharacterLob_usesClobLength() throws IOException, SQLException {
        // fix-K: DB2-only DBCLOB (double-byte CLOB) must also avoid getBinaryStream(); jcc returns
        // the same -4461 / 42815 error code for any character-LOB type.
        DataTypeFactory factory = new CommonDataTypeFactory("dbclob");
        DataType dataType = factory.generate();
        GeneralLobMapper mapper = new GeneralLobMapper();
        Assert.assertEquals("(dbclob) 9 B", mapper.mapCell(new LobCellData(9L, dataType, false)));
    }

    @Test
    public void mapCell_clobZeroLength_returnsZeroByteText() throws IOException, SQLException {
        DataTypeFactory factory = new CommonDataTypeFactory("clob");
        DataType dataType = factory.generate();
        GeneralLobMapper mapper = new GeneralLobMapper();
        Assert.assertEquals("(clob) 0 B", mapper.mapCell(new LobCellData(0L, dataType, false)));
    }

    @Test
    public void supports_blob_supports() throws IOException, SQLException {
        GeneralLobMapper mapper = new GeneralLobMapper();
        DataTypeFactory factory = new CommonDataTypeFactory("blob");
        Assert.assertTrue(mapper.supports(factory.generate()));
    }

    @Test
    public void supports_clob_supports() throws IOException, SQLException {
        GeneralLobMapper mapper = new GeneralLobMapper();
        DataTypeFactory factory = new CommonDataTypeFactory("clob");
        Assert.assertTrue(mapper.supports(factory.generate()));
    }

    @Test
    public void supports_dbclob_supports() throws IOException, SQLException {
        // fix-K: DB2 DBCLOB now recognized so the data tab handles double-byte LOBs.
        GeneralLobMapper mapper = new GeneralLobMapper();
        DataTypeFactory factory = new CommonDataTypeFactory("dbclob");
        Assert.assertTrue(mapper.supports(factory.generate()));
    }

    @Test
    public void supports_nclob_supports() throws IOException, SQLException {
        GeneralLobMapper mapper = new GeneralLobMapper();
        DataTypeFactory factory = new CommonDataTypeFactory("nclob");
        Assert.assertTrue(mapper.supports(factory.generate()));
    }

    @Test
    public void supports_mediumblob_supports() throws IOException, SQLException {
        GeneralLobMapper mapper = new GeneralLobMapper();
        DataTypeFactory factory = new CommonDataTypeFactory("mediumblob");
        Assert.assertTrue(mapper.supports(factory.generate()));
    }

    @Test
    public void supports_tinyblob_supports() throws IOException, SQLException {
        GeneralLobMapper mapper = new GeneralLobMapper();
        DataTypeFactory factory = new CommonDataTypeFactory("tinyblob");
        Assert.assertTrue(mapper.supports(factory.generate()));
    }

    @Test
    public void supports_longblob_supports() throws IOException, SQLException {
        GeneralLobMapper mapper = new GeneralLobMapper();
        DataTypeFactory factory = new CommonDataTypeFactory("longblob");
        Assert.assertTrue(mapper.supports(factory.generate()));
    }

    @Test
    public void supports_timestamp_notSupports() throws IOException, SQLException {
        GeneralLobMapper mapper = new GeneralLobMapper();
        DataTypeFactory factory = new CommonDataTypeFactory("timetamp(2) with local time zone");
        Assert.assertFalse(mapper.supports(factory.generate()));
    }

}
