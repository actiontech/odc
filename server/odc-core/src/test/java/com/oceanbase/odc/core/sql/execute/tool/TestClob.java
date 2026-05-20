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

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.sql.Clob;
import java.sql.SQLException;

/**
 * fix-K: minimal {@link Clob} stub used by {@code GeneralLobMapperTest} — it only needs to report a
 * length so we can assert that the mapper goes through {@link Clob#length()} rather than calling
 * {@code getBinaryStream()} (which DB2 jcc rejects on CLOB columns with ERRORCODE=-4461).
 */
public class TestClob implements Clob {

    private final long length;

    public TestClob(long length) {
        this.length = length;
    }

    @Override
    public long length() {
        return length;
    }

    @Override
    public String getSubString(long pos, int length) {
        return "";
    }

    @Override
    public Reader getCharacterStream() {
        return new StringReader("");
    }

    @Override
    public InputStream getAsciiStream() {
        throw new UnsupportedOperationException(
                "fix-K: DB2 jcc rejects getAsciiStream on CLOB; tests should not call this");
    }

    @Override
    public long position(String searchstr, long start) {
        return -1;
    }

    @Override
    public long position(Clob searchstr, long start) {
        return -1;
    }

    @Override
    public int setString(long pos, String str) {
        throw new UnsupportedOperationException("read-only stub");
    }

    @Override
    public int setString(long pos, String str, int offset, int len) {
        throw new UnsupportedOperationException("read-only stub");
    }

    @Override
    public OutputStream setAsciiStream(long pos) {
        throw new UnsupportedOperationException("read-only stub");
    }

    @Override
    public Writer setCharacterStream(long pos) {
        throw new UnsupportedOperationException("read-only stub");
    }

    @Override
    public void truncate(long len) {
        throw new UnsupportedOperationException("read-only stub");
    }

    @Override
    public void free() throws SQLException {}

    @Override
    public Reader getCharacterStream(long pos, long length) {
        return new StringReader("");
    }
}
