package com.oceanbase.odc.plugin.connect.redis;

import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.odc.plugin.connect.api.BaseConnectionPlugin;

public class RedisConnectionPlugin extends BaseConnectionPlugin {
    @Override
    public DialectType getDialectType() {
        return DialectType.REDIS;
    }
}
