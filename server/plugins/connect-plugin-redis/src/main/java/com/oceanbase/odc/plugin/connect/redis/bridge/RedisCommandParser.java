package com.oceanbase.odc.plugin.connect.redis.bridge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class RedisCommandParser {
    private static final Set<String> DENYLIST = new HashSet<>(Arrays.asList(
            "AUTH", "ACL", "CONFIG", "DEBUG", "FLUSHALL", "FLUSHDB", "MIGRATE", "MODULE", "REPLICAOF", "SHUTDOWN", "SLAVEOF"));
    private static final Set<String> WRITE_COMMANDS = new HashSet<>(Arrays.asList(
            "SET", "DEL", "HSET", "LPUSH", "RPUSH", "SADD", "ZADD", "XADD", "EXPIRE", "PEXPIRE", "INCR", "DECR"));

    public RedisParsedCommand parse(String text) {
        List<String> tokens = tokenize(text);
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("redis command is empty");
        }
        String command = tokens.get(0).toUpperCase(Locale.ROOT);
        if (DENYLIST.contains(command)) {
            throw new IllegalArgumentException("unsupported redis command: " + command);
        }
        return new RedisParsedCommand(command, tokens.subList(1, tokens.size()), WRITE_COMMANDS.contains(command));
    }

    static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null) {
            return tokens;
        }
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (quoted) {
                if (c == quote) {
                    quoted = false;
                } else {
                    current.append(c);
                }
                continue;
            }
            if (c == '\'' || c == '"') {
                quoted = true;
                quote = c;
                continue;
            }
            if (Character.isWhitespace(c)) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(c);
        }
        if (escaped || quoted) {
            throw new IllegalArgumentException("invalid redis command quoting");
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}
