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
package com.oceanbase.odc.plugin.connect.db2;

import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.odc.plugin.connect.api.BaseConnectionPlugin;

/**
 * connect-plugin-db2 入口（T-002 骨架，蓝本 {@code PostgresConnectionPlugin}）。
 * <p>
 * pf4j PluginManager 在装载 plugin jar 时通过 manifest 中的 {@code Plugin-Class} 找到本类；本类的
 * {@link #getDialectType()} 用于注册到 DialectType.DB2 通路（见 compat-RISK-1 / compat-RISK-5）。具体
 * ConnectionExtension / SessionExtension / InformationExtension 等通过 {@code @Extension} 注解暴露给 pf4j。
 */
public class Db2ConnectionPlugin extends BaseConnectionPlugin {

    @Override
    public DialectType getDialectType() {
        return DialectType.DB2;
    }
}
