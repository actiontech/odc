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
import java.io.InputStream;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import com.oceanbase.tools.dbbrowser.model.datatype.DataType;

import lombok.NonNull;

/**
 * {@link JdbcColumnMapper} for data type
 *
 * <pre>
 *     {@code blob}
 *     {@code clob}
 *     {@code nclob}
 *     {@code dbclob}
 *     {@code tinyblob}
 *     {@code longblob}
 *     {@code mediumblob}
 * </pre>
 *
 * <p>
 * fix-K: DB2 jcc 对 CLOB / DBCLOB / NCLOB 列调用 {@link java.sql.ResultSet#getBinaryStream(int)} 抛
 * {@code ERRORCODE=-4461, SQLSTATE=42815}（数据转换无效：所请求转换的结果列类型错误）。本 mapper 现在按列的实际类别（character LOB vs
 * binary LOB）分别走 {@link Clob#length()} / {@link Blob#length()}（或对应 stream），保证与 jcc 类型系统兼容；MySQL /
 * Oracle / OB 的 BLOB 行为不变。
 * </p>
 */
public class GeneralLobMapper implements JdbcColumnMapper {

    private final static List<String> CANDIDATE_TYPES =
            Arrays.asList("BLOB", "CLOB", "NCLOB", "DBCLOB", "TINYBLOB", "MEDIUMBLOB", "LONGBLOB");
    /**
     * fix-K: character-LOB types whose JDBC drivers MAY reject {@code getBinaryStream}. For these we go
     * through {@link Clob} so DB2 jcc stays happy and the size reported to the UI reflects the
     * character (not byte) length, matching the column semantics.
     */
    private final static List<String> CHARACTER_LOB_TYPES =
            Arrays.asList("CLOB", "NCLOB", "DBCLOB");
    private final static int KB = 1024;
    private final static int MB = KB * 1024;
    private final static int GB = MB * 1024;

    @Override
    public Object mapCell(@NonNull CellData data) throws SQLException, IOException {
        String typeName = data.getDataType().getDataTypeName();
        long size;
        if (isCharacterLob(typeName)) {
            // fix-K: DB2 jcc rejects getBinaryStream() on CLOB / DBCLOB columns
            // (ERRORCODE=-4461, SQLSTATE=42815). Use Clob#length() instead.
            Clob clob = data.getClob();
            if (clob == null) {
                return null;
            }
            size = clob.length();
        } else {
            // Binary LOBs: prefer Blob#length() when available (cheaper and safer),
            // fall back to InputStream#available() if the driver returns no Blob handle.
            Blob blob = data.getBlob();
            if (blob != null) {
                size = blob.length();
            } else {
                InputStream inputStream = data.getBinaryStream();
                if (inputStream == null) {
                    return null;
                }
                size = inputStream.available();
            }
        }
        return formatSize(typeName, size);
    }

    @Override
    public boolean supports(@NonNull DataType dataType) {
        for (String type : CANDIDATE_TYPES) {
            if (type.equalsIgnoreCase(dataType.getDataTypeName())) {
                return true;
            }
        }
        return false;
    }

    private boolean isCharacterLob(String dataTypeName) {
        if (dataTypeName == null) {
            return false;
        }
        for (String type : CHARACTER_LOB_TYPES) {
            if (type.equalsIgnoreCase(dataTypeName)) {
                return true;
            }
        }
        return false;
    }

    private static String formatSize(String dataTypeName, long size) {
        long available = size;
        String unit = "B";
        if (available > GB) {
            available = available >> 30;
            unit = "GB";
        } else if (available > MB) {
            available = available >> 20;
            unit = "MB";
        } else if (available > KB) {
            available = available >> 10;
            unit = "KB";
        }
        return String.format("(%s) %d %s", dataTypeName, available, unit);
    }

}
