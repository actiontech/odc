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
package com.oceanbase.odc.plugin.connect.mongodb.bridge;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Logger;

import org.bson.Document;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

public class MongoJdbcDriver implements Driver {
    static {
        try {
            DriverManager.registerDriver(new MongoJdbcDriver());
        } catch (SQLException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) {
            return null;
        }
        String mongoUri = toMongoUri(url, info);
        MongoClient client = MongoClients.create(mongoUri);
        Document buildInfo = client.getDatabase("admin").runCommand(new Document("buildInfo", 1));
        String currentDb = new com.oceanbase.odc.plugin.connect.mongodb.MongoJdbcUrlParser(url, null).getSchema();
        MongoSessionContext context = new MongoSessionContext(client, currentDb, buildInfo.getString("version"));
        return MongoBridgeUtil.newConnection(context);
    }

    public static String toMongoUri(String jdbcUrl, Properties properties) {
        String uri = jdbcUrl.substring("jdbc:".length());
        String username = properties == null ? null : properties.getProperty("user");
        String password = properties == null ? null : properties.getProperty("password");
        if (username != null && !username.isEmpty()) {
            int schemeEnd = uri.indexOf("://") + 3;
            uri = uri.substring(0, schemeEnd) + username + ":" + (password == null ? "" : password) + "@"
                    + uri.substring(schemeEnd);
        }
        return uri;
    }

    @Override
    public boolean acceptsURL(String url) {
        return url != null && url.startsWith("jdbc:mongodb://");
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
        return new DriverPropertyInfo[0];
    }

    @Override
    public int getMajorVersion() {
        return 1;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public Logger getParentLogger() {
        return Logger.getGlobal();
    }
}
