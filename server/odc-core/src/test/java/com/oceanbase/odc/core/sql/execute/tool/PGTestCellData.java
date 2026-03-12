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
package com.oceanbase.odc.core.sql.execute.tool;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Timestamp;

import com.oceanbase.odc.core.sql.execute.mapper.CellData;
import com.oceanbase.tools.dbbrowser.model.datatype.DataType;

import lombok.NonNull;

/**
 * Test CellData for PostgreSQL mappers that can set various values via reflection.
 *
 * @author ODC Team
 * @date 2026-03-12
 */
public class PGTestCellData extends CellData {

    private Object objectValue;
    private BigDecimal bigDecimalValue;
    private byte[] bytesValue;
    private Array arrayValue;
    private Timestamp timestampValue;
    private java.io.InputStream binaryStreamValue;

    public PGTestCellData(@NonNull DataType dataType) {
        super(new org.h2.tools.SimpleResultSet(), 1, dataType);
    }

    public void setObjectValue(Object value) {
        this.objectValue = value;
    }

    public void setBigDecimalValue(BigDecimal value) {
        this.bigDecimalValue = value;
    }

    public void setBytesValue(byte[] value) {
        this.bytesValue = value;
    }

    public void setArrayValue(Array value) {
        this.arrayValue = value;
    }

    public void setTimestampValue(Timestamp value) {
        this.timestampValue = value;
    }

    public void setBinaryStreamValue(java.io.InputStream value) {
        this.binaryStreamValue = value;
    }

    @Override
    public Object getObject() {
        return objectValue;
    }

    @Override
    public BigDecimal getBigDecimal() {
        return bigDecimalValue;
    }

    @Override
    public byte[] getBytes() {
        return bytesValue;
    }

    @Override
    public Array getArray() {
        return arrayValue;
    }

    @Override
    public Timestamp getTimestamp() {
        return timestampValue;
    }

    @Override
    public java.io.InputStream getBinaryStream() {
        return binaryStreamValue;
    }

}
