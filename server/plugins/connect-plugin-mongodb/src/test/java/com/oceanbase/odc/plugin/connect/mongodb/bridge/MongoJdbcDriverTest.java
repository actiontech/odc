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

import java.util.Properties;

import org.junit.Assert;
import org.junit.Test;

import com.mongodb.ConnectionString;

public class MongoJdbcDriverTest {
    private static final String JDBC_URL = "jdbc:mongodb://10.2.20.15:27017/admin?authSource=admin";

    @Test
    public void toMongoUri_plainCredentials() {
        Properties properties = new Properties();
        properties.setProperty("user", "root");
        properties.setProperty("password", "Km9sPfNaW");

        String mongoUri = MongoJdbcDriver.toMongoUri(JDBC_URL, properties);

        Assert.assertEquals("mongodb://root:Km9sPfNaW@10.2.20.15:27017/admin?authSource=admin", mongoUri);
        assertParsableByMongoDriver(mongoUri);
    }

    @Test
    public void toMongoUri_passwordWithAtSign() {
        Properties properties = new Properties();
        properties.setProperty("user", "user");
        properties.setProperty("password", "pass@word");

        String mongoUri = MongoJdbcDriver.toMongoUri(JDBC_URL, properties);

        Assert.assertEquals("mongodb://user:pass%40word@10.2.20.15:27017/admin?authSource=admin", mongoUri);
        assertParsableByMongoDriver(mongoUri);
    }

    @Test
    public void toMongoUri_passwordWithColon() {
        Properties properties = new Properties();
        properties.setProperty("user", "user");
        properties.setProperty("password", "pa:ss");

        String mongoUri = MongoJdbcDriver.toMongoUri(JDBC_URL, properties);

        Assert.assertEquals("mongodb://user:pa%3Ass@10.2.20.15:27017/admin?authSource=admin", mongoUri);
        assertParsableByMongoDriver(mongoUri);
    }

    @Test
    public void toMongoUri_passwordWithSlashAndHash() {
        Properties properties = new Properties();
        properties.setProperty("user", "user");
        properties.setProperty("password", "p/a#s?");

        String mongoUri = MongoJdbcDriver.toMongoUri(JDBC_URL, properties);

        Assert.assertEquals("mongodb://user:p%2Fa%23s%3F@10.2.20.15:27017/admin?authSource=admin", mongoUri);
        assertParsableByMongoDriver(mongoUri);
    }

    @Test
    public void toMongoUri_usernameWithSpecialCharacters() {
        Properties properties = new Properties();
        properties.setProperty("user", "u@ser");
        properties.setProperty("password", "secret");

        String mongoUri = MongoJdbcDriver.toMongoUri(JDBC_URL, properties);

        Assert.assertEquals("mongodb://u%40ser:secret@10.2.20.15:27017/admin?authSource=admin", mongoUri);
        assertParsableByMongoDriver(mongoUri);
    }

    @Test
    public void toMongoUri_withoutCredentials() {
        String mongoUri = MongoJdbcDriver.toMongoUri(JDBC_URL, null);

        Assert.assertEquals("mongodb://10.2.20.15:27017/admin?authSource=admin", mongoUri);
        assertParsableByMongoDriver(mongoUri);
    }

    @Test
    public void toMongoUri_usernameOnly() {
        Properties properties = new Properties();
        properties.setProperty("user", "root");

        String mongoUri = MongoJdbcDriver.toMongoUri(JDBC_URL, properties);

        Assert.assertEquals("mongodb://root:@10.2.20.15:27017/admin?authSource=admin", mongoUri);
        assertParsableByMongoDriver(mongoUri);
    }

    private void assertParsableByMongoDriver(String mongoUri) {
        ConnectionString connectionString = new ConnectionString(mongoUri);
        Assert.assertNotNull(connectionString.getHosts());
        Assert.assertFalse(connectionString.getHosts().isEmpty());
    }
}
