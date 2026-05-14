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
 * Unit tests for {@link DB2LobMapper} (T-003 commit-3 / design.md §3.6 / compat-RISK-13).
 *
 * <p>
 * Verifies the DB2-specific LOB mapper:
 * <ul>
 * <li>Claims DB2-only types DBCLOB and XML (which {@link GeneralLobMapper} does NOT claim).</li>
 * <li>Does NOT claim BLOB / CLOB so {@link GeneralLobMapper} remains the single handler for those
 * types (no double-mapping in the chain).</li>
 * <li>Does NOT claim DECFLOAT / TIMESTAMP / DECIMAL.</li>
 * <li>Returns a human-readable size summary in the same format as {@link GeneralLobMapper}.</li>
 * </ul>
 */
public class DB2LobMapperTest {

    private final DB2LobMapper mapper = new DB2LobMapper();

    private static DataType type(String name) {
        try {
            DataTypeFactory factory = new CommonDataTypeFactory(name);
            return factory.generate();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void supports_DBCLOB_true() {
        Assert.assertTrue(mapper.supports(type("DBCLOB")));
    }

    @Test
    public void supports_dbclob_lowercase_true() {
        Assert.assertTrue(mapper.supports(type("dbclob")));
    }

    @Test
    public void supports_XML_true() {
        Assert.assertTrue(mapper.supports(type("XML")));
    }

    @Test
    public void supports_BLOB_false_handledByGeneralLobMapper() {
        Assert.assertFalse(mapper.supports(type("BLOB")));
    }

    @Test
    public void supports_CLOB_false_handledByGeneralLobMapper() {
        Assert.assertFalse(mapper.supports(type("CLOB")));
    }

    @Test
    public void supports_DECFLOAT_false() {
        Assert.assertFalse(mapper.supports(type("DECFLOAT")));
    }

    @Test
    public void supports_DECIMAL_false() {
        Assert.assertFalse(mapper.supports(type("DECIMAL")));
    }

    @Test
    public void supports_TIMESTAMP_false() {
        Assert.assertFalse(mapper.supports(type("TIMESTAMP")));
    }

    @Test
    public void supports_VARCHAR_false() {
        Assert.assertFalse(mapper.supports(type("VARCHAR")));
    }

    @Test
    public void mapCell_DBCLOB_nonNullInputStream_returnsSizeSummary() throws IOException,
            SQLException {
        Assert.assertEquals("(DBCLOB) 12 B",
                mapper.mapCell(new LobCellData(12, type("DBCLOB"))));
    }

    @Test
    public void mapCell_XML_kbSize_returnsKBUnit() throws IOException, SQLException {
        // 1500 bytes → 1 KB (right shift 10)
        Assert.assertEquals("(XML) 1 KB",
                mapper.mapCell(new LobCellData(1500, type("XML"))));
    }

    @Test
    public void mapCell_DBCLOB_nullInputStream_returnsNull() throws IOException, SQLException {
        Assert.assertNull(mapper.mapCell(new LobCellData(-1, type("DBCLOB"))));
    }

    @Test
    public void mapCell_XML_mbSize_returnsMBUnit() throws IOException, SQLException {
        // 5 * 1024 * 1024 + 1 → 5 MB
        Assert.assertEquals("(XML) 5 MB",
                mapper.mapCell(new LobCellData(5 * 1024 * 1024 + 1, type("XML"))));
    }
}
