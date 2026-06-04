package com.oceanbase.odc.plugin.connect.redis.bridge;

import org.junit.Assert;
import org.junit.Test;

class RedisCommandParserTest {
    @Test
    void parse_whenQuotedArgs_thenKeepSpaces() {
        RedisParsedCommand command = new RedisCommandParser().parse("set user:1 'hello world'");
        Assert.assertEquals("SET", command.getCommand());
        Assert.assertTrue(command.isWrite());
        Assert.assertEquals("hello world", command.getArgs().get(1));
    }

    @Test
    void parse_whenScan_thenReadCommand() {
        RedisParsedCommand command = new RedisCommandParser().parse("SCAN 0 MATCH user:* COUNT 100");
        Assert.assertEquals("SCAN", command.getCommand());
        Assert.assertFalse(command.isWrite());
        Assert.assertEquals(6, command.getArgs().size());
    }

    @Test
    void parse_whenDangerous_thenDeny() {
        Assert.assertThrows(IllegalArgumentException.class, () -> new RedisCommandParser().parse("FLUSHALL"));
    }
}
