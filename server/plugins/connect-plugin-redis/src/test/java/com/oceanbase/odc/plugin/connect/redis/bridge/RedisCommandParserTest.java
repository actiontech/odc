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

import org.junit.Assert;
import org.junit.Test;

public class RedisCommandParserTest {
    @Test
    public void parse_whenQuotedArgs_thenKeepSpaces() {
        RedisParsedCommand command = new RedisCommandParser().parse("set user:1 'hello world'");
        Assert.assertEquals("SET", command.getCommand());
        Assert.assertTrue(command.isWrite());
        Assert.assertEquals("hello world", command.getArgs().get(1));
    }

    @Test
    public void parse_whenScan_thenReadCommand() {
        RedisParsedCommand command = new RedisCommandParser().parse("SCAN 0 MATCH user:* COUNT 100");
        Assert.assertEquals("SCAN", command.getCommand());
        Assert.assertFalse(command.isWrite());
        Assert.assertEquals(5, command.getArgs().size());
    }

    @Test
    public void parse_whenDangerous_thenDeny() {
        Assert.assertThrows(IllegalArgumentException.class, () -> new RedisCommandParser().parse("FLUSHALL"));
    }
}
