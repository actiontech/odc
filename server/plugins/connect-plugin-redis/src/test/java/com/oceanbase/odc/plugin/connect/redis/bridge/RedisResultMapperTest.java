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

import java.sql.ResultSet;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

public class RedisResultMapperTest {
    @Test
    public void map_whenStringReply_thenColumnTypeNameNotEmpty() throws Exception {
        RedisParsedCommand command = new RedisCommandParser().parse("GET user:1");
        RedisTabularResult result = new RedisResultMapper().map(command, "alice");
        ResultSet resultSet = RedisBridgeUtil.toResultSet(result);
        Assert.assertEquals("VARCHAR", resultSet.getMetaData().getColumnTypeName(1));
        Assert.assertTrue(resultSet.next());
        Assert.assertEquals("alice", resultSet.getString(1));
    }

    @Test
    public void map_whenScanReply_thenExposeCursorAndKeys() throws Exception {
        RedisParsedCommand command = new RedisCommandParser().parse("SCAN 0 MATCH user:* COUNT 100");
        RedisTabularResult result = new RedisResultMapper().map(command, Arrays.asList("0", Arrays.asList("user:1")));
        ResultSet resultSet = RedisBridgeUtil.toResultSet(result);
        Assert.assertEquals("cursor", resultSet.getMetaData().getColumnName(1));
        Assert.assertEquals("VARCHAR", resultSet.getMetaData().getColumnTypeName(2));
        Assert.assertTrue(resultSet.next());
        Assert.assertEquals("user:1", resultSet.getString(2));
    }

    @Test
    public void map_whenWriteReply_thenExposeAck() {
        RedisParsedCommand command = new RedisCommandParser().parse("SET user:1 alice");
        RedisTabularResult result = new RedisResultMapper().map(command, "OK");
        Assert.assertEquals("ack", result.getColumns().get(0));
        Assert.assertEquals("VARCHAR", result.getColumnTypeNames().get(0));
        Assert.assertEquals("OK", result.getRows().get(0).get(0));
    }
}
