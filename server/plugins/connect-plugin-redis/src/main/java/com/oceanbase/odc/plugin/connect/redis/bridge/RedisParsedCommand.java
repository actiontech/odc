package com.oceanbase.odc.plugin.connect.redis.bridge;

import java.util.Collections;
import java.util.List;

public class RedisParsedCommand {
    private final String command;
    private final List<String> args;
    private final boolean write;

    public RedisParsedCommand(String command, List<String> args, boolean write) {
        this.command = command;
        this.args = args == null ? Collections.emptyList() : Collections.unmodifiableList(args);
        this.write = write;
    }

    public String getCommand() {
        return command;
    }

    public List<String> getArgs() {
        return args;
    }

    public boolean isWrite() {
        return write;
    }
}
