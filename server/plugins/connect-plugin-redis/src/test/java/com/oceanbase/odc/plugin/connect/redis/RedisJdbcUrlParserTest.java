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
package com.oceanbase.odc.plugin.connect.redis;

import java.sql.SQLException;

import org.junit.Assert;
import org.junit.Test;

public class RedisJdbcUrlParserTest {
    @Test
    public void parse_whenUrlContainsSchemaAndParameters_thenExposeConnectionInfo() throws Exception {
        RedisJdbcUrlParser parser = new RedisJdbcUrlParser(
                "jdbc:redis://127.0.0.1:6379/3?tls=true&scanCount=200&keySeparator=:", null);

        Assert.assertEquals("127.0.0.1", parser.getHostAddresses().get(0).getHost());
        Assert.assertEquals(Integer.valueOf(6379), parser.getHostAddresses().get(0).getPort());
        Assert.assertEquals("3", parser.getSchema());
        Assert.assertEquals("true", parser.getParameters().get("tls"));
        Assert.assertEquals("200", parser.getParameters().get("scanCount"));
        Assert.assertEquals(":", parser.getParameters().get("keySeparator"));
    }

    @Test
    public void parse_whenUrlHasNoSchema_thenDefaultDatabaseIsZero() throws Exception {
        RedisJdbcUrlParser parser = new RedisJdbcUrlParser("jdbc:redis://localhost:6379", null);

        Assert.assertEquals("0", parser.getSchema());
    }

    @Test(expected = SQLException.class)
    public void parse_whenUrlHasWrongPrefix_thenReject() throws Exception {
        new RedisJdbcUrlParser("jdbc:mysql://localhost:3306/0", null);
    }
}
