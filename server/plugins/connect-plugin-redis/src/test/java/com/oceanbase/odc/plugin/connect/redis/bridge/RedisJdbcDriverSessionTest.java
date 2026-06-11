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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.odc.plugin.connect.redis.RedisSessionExtension;

public class RedisJdbcDriverSessionTest {
    @Test
    public void connect_whenUrlContainsDatabase_thenSessionUsesDefaultDatabase() throws Exception {
        FakeRedisServer server = new FakeRedisServer();
        try {
            RedisJdbcDriver driver = new RedisJdbcDriver();
            Connection connection =
                    driver.connect("jdbc:redis://127.0.0.1:" + server.getPort() + "/2", new Properties());

            Assert.assertEquals("2", new RedisSessionExtension().getCurrentSchema(connection));
            Assert.assertTrue(server.getCommands().contains("SELECT 2"));
            Assert.assertTrue(server.getCommands().contains("INFO server"));
            connection.close();
        } finally {
            server.close();
        }
    }

    private static class FakeRedisServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final List<String> commands = new ArrayList<>();
        private volatile boolean closed;

        private FakeRedisServer() throws IOException {
            this.serverSocket = new ServerSocket(0);
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    serve();
                }
            });
            thread.setDaemon(true);
            thread.start();
        }

        private int getPort() {
            return serverSocket.getLocalPort();
        }

        private List<String> getCommands() {
            return commands;
        }

        private void serve() {
            try {
                Socket socket = serverSocket.accept();
                try {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    while (!closed) {
                        List<String> parts = readCommand(reader);
                        if (parts.isEmpty()) {
                            return;
                        }
                        commands.add(join(parts));
                        String command = parts.get(0).toUpperCase();
                        if ("INFO".equals(command)) {
                            socket.getOutputStream()
                                    .write("$21\r\nredis_version:7.0.0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                        } else {
                            socket.getOutputStream().write("+OK\r\n".getBytes(StandardCharsets.UTF_8));
                        }
                        socket.getOutputStream().flush();
                    }
                } finally {
                    socket.close();
                }
            } catch (IOException ignored) {
            }
        }

        private List<String> readCommand(BufferedReader reader) throws IOException {
            String line = reader.readLine();
            if (line == null) {
                return new ArrayList<>();
            }
            int count = Integer.parseInt(line.substring(1));
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String bulkHeader = reader.readLine();
                int size = Integer.parseInt(bulkHeader.substring(1));
                char[] payload = new char[size];
                int read = reader.read(payload, 0, size);
                if (read != size) {
                    throw new IOException("Unexpected Redis payload length");
                }
                reader.readLine();
                parts.add(new String(payload));
            }
            return parts;
        }

        private String join(List<String> parts) {
            StringBuilder builder = new StringBuilder();
            for (String part : parts) {
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(part);
            }
            return builder.toString();
        }

        @Override
        public void close() throws IOException {
            closed = true;
            serverSocket.close();
        }
    }
}
