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
package com.oceanbase.odc.plugin.schema.redis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.odc.plugin.connect.redis.bridge.RedisBridgeUtil;
import com.oceanbase.odc.plugin.connect.redis.bridge.RedisClient;
import com.oceanbase.odc.plugin.connect.redis.bridge.RedisSessionContext;
import com.oceanbase.tools.dbbrowser.model.DBObjectIdentity;

public class RedisDatabaseAndTableExtensionTest {
    @Test
    public void listDatabases_shouldExposeAllConfiguredLogicalDatabases() throws Exception {
        FakeRedisServer server = new FakeRedisServer();
        try {
            Connection connection = newConnection(server.getPort(), "3");

            List<DBObjectIdentity> databases = new RedisDatabaseExtension().list(connection);

            Assert.assertEquals(16, databases.size());
            Assert.assertEquals("0", databases.get(0).getName());
            Assert.assertEquals("15", databases.get(15).getName());
            connection.close();
        } finally {
            server.close();
        }
    }

    @Test
    public void showNamesLike_whenScanReturnsKeys_thenGroupByPrefixAndDefaultKeys() throws Exception {
        FakeRedisServer server = new FakeRedisServer();
        try {
            Connection connection = newConnection(server.getPort(), "0");

            List<String> names = new RedisTableExtension().showNamesLike(connection, "0", "");

            Assert.assertTrue(names.contains("codex"));
            Assert.assertTrue(names.contains("keys"));
            connection.close();
        } finally {
            server.close();
        }
    }

    private Connection newConnection(int port, String database) throws Exception {
        RedisClient client = new RedisClient("127.0.0.1", port, 1000);
        return RedisBridgeUtil.newConnection(new RedisSessionContext(client, database, "7.0.0"));
    }

    private static class FakeRedisServer implements AutoCloseable {
        private final ServerSocket serverSocket;
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

        private void serve() {
            while (!closed) {
                try {
                    Socket socket = serverSocket.accept();
                    Thread thread = new Thread(new Handler(socket));
                    thread.setDaemon(true);
                    thread.start();
                } catch (IOException ignored) {
                    return;
                }
            }
        }

        @Override
        public void close() throws IOException {
            closed = true;
            serverSocket.close();
        }
    }

    private static class Handler implements Runnable {
        private final Socket socket;

        private Handler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                while (true) {
                    List<String> parts = readCommand(reader);
                    if (parts.isEmpty()) {
                        return;
                    }
                    String command = parts.get(0).toUpperCase();
                    if ("CONFIG".equals(command) && parts.size() >= 3 && "GET".equalsIgnoreCase(parts.get(1))
                            && "databases".equalsIgnoreCase(parts.get(2))) {
                        socket.getOutputStream().write(
                                "*2\r\n$9\r\ndatabases\r\n$2\r\n16\r\n".getBytes(StandardCharsets.UTF_8));
                    } else if ("SCAN".equals(command)) {
                        socket.getOutputStream().write(
                                "*2\r\n$1\r\n0\r\n*3\r\n$17\r\ncodex:test:string\r\n$15\r\ncodex:test:list\r\n$5\r\nplain\r\n"
                                        .getBytes(StandardCharsets.UTF_8));
                    } else {
                        socket.getOutputStream().write("+OK\r\n".getBytes(StandardCharsets.UTF_8));
                    }
                    socket.getOutputStream().flush();
                }
            } catch (IOException ignored) {
            } finally {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
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
    }
}
