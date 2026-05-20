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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Blob;
import java.sql.SQLException;

/**
 * Minimal {@link Blob} stub used by {@code GeneralLobMapperTest}; reports a fixed length and
 * otherwise returns empty content. We can't reuse {@code java.sql.rowset.serial.SerialBlob} because
 * it doesn't let tests set a length larger than 2GB and it eagerly allocates the byte array, which
 * is wasteful for size-based assertions.
 */
public class TestBlob implements Blob {

    private final long length;

    public TestBlob(long length) {
        this.length = length;
    }

    @Override
    public long length() {
        return length;
    }

    @Override
    public byte[] getBytes(long pos, int length) {
        return new byte[length];
    }

    @Override
    public InputStream getBinaryStream() {
        return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public long position(byte[] pattern, long start) {
        return -1;
    }

    @Override
    public long position(Blob pattern, long start) {
        return -1;
    }

    @Override
    public int setBytes(long pos, byte[] bytes) {
        throw new UnsupportedOperationException("read-only stub");
    }

    @Override
    public int setBytes(long pos, byte[] bytes, int offset, int len) {
        throw new UnsupportedOperationException("read-only stub");
    }

    @Override
    public OutputStream setBinaryStream(long pos) {
        throw new UnsupportedOperationException("read-only stub");
    }

    @Override
    public void truncate(long len) {
        throw new UnsupportedOperationException("read-only stub");
    }

    @Override
    public void free() throws SQLException {}

    @Override
    public InputStream getBinaryStream(long pos, long length) {
        return new ByteArrayInputStream(new byte[0]);
    }
}
