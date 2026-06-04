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
package com.oceanbase.odc.plugin.connect.redis.bridge;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class RedisClient implements Closeable {
    private final Socket socket;
    private final BufferedInputStream input;
    private final BufferedOutputStream output;

    public RedisClient(String host, int port, int timeoutMillis) throws IOException {
        this.socket = new Socket();
        this.socket.connect(new InetSocketAddress(host, port), timeoutMillis);
        this.socket.setSoTimeout(timeoutMillis);
        this.input = new BufferedInputStream(socket.getInputStream());
        this.output = new BufferedOutputStream(socket.getOutputStream());
    }

    public Object command(String command, String... args) throws IOException {
        List<String> parts = new ArrayList<>();
        parts.add(command);
        if (args != null) {
            for (String arg : args) {
                parts.add(arg == null ? "" : arg);
            }
        }
        return command(parts);
    }

    public Object command(List<String> parts) throws IOException {
        output.write(("*" + parts.size() + "\r\n").getBytes(StandardCharsets.UTF_8));
        for (String part : parts) {
            byte[] payload = part.getBytes(StandardCharsets.UTF_8);
            output.write(("$" + payload.length + "\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(payload);
            output.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        output.flush();
        return readReply();
    }

    private Object readReply() throws IOException {
        int prefix = input.read();
        if (prefix < 0) {
            throw new IOException("Redis connection closed");
        }
        switch (prefix) {
            case '+':
                return readLine();
            case '-':
                throw new IOException(readLine());
            case ':':
                return Long.parseLong(readLine());
            case '$':
                return readBulkString();
            case '*':
                return readArray();
            default:
                throw new IOException("Unsupported Redis reply prefix: " + (char) prefix);
        }
    }

    private String readBulkString() throws IOException {
        int length = Integer.parseInt(readLine());
        if (length < 0) {
            return null;
        }
        byte[] data = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(data, offset, length - offset);
            if (read < 0) {
                throw new IOException("Redis bulk string truncated");
            }
            offset += read;
        }
        expectCrLf();
        return new String(data, StandardCharsets.UTF_8);
    }

    private List<Object> readArray() throws IOException {
        int size = Integer.parseInt(readLine());
        if (size < 0) {
            return null;
        }
        List<Object> result = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            result.add(readReply());
        }
        return result;
    }

    private String readLine() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int previous = -1;
        int current;
        while ((current = input.read()) >= 0) {
            if (previous == '\r' && current == '\n') {
                byte[] data = buffer.toByteArray();
                return new String(data, 0, data.length - 1, StandardCharsets.UTF_8);
            }
            buffer.write(current);
            previous = current;
        }
        throw new IOException("Redis line truncated");
    }

    private void expectCrLf() throws IOException {
        if (input.read() != '\r' || input.read() != '\n') {
            throw new IOException("Invalid Redis bulk string terminator");
        }
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
