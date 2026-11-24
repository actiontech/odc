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
package com.oceanbase.odc.plugin.schema.api;

/**
 * Optional credentials for schema-plugin sync that must open temporary JDBC connections (e.g.
 * PostgreSQL cross-catalog enumeration). Set by the service layer around sync; cleared in
 * {@code finally}. Plugins other than PostgreSQL must ignore this context.
 */
public final class SchemaSyncConnectionContext {

    private static final ThreadLocal<Auth> HOLDER = new ThreadLocal<>();

    private SchemaSyncConnectionContext() {}

    public static void set(String jdbcUrl, String username, String password) {
        HOLDER.set(new Auth(jdbcUrl, username, password));
    }

    public static Auth get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static final class Auth {
        private final String jdbcUrl;
        private final String username;
        private final String password;

        public Auth(String jdbcUrl, String username, String password) {
            this.jdbcUrl = jdbcUrl;
            this.username = username;
            this.password = password;
        }

        public String getJdbcUrl() {
            return jdbcUrl;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }
    }
}
