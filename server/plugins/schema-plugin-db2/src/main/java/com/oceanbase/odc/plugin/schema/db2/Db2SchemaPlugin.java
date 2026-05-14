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
package com.oceanbase.odc.plugin.schema.db2;

import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.odc.plugin.schema.api.BaseSchemaPlugin;

/**
 * schema-plugin-db2 入口（T-002 骨架，蓝本 {@code PostgresSchemaPlugin}）。
 * <p>
 * 注册到 pf4j 后 ODC 根据 {@link #getDialectType()} 把 DB2 数据源派发到本插件。 具体的 Schema/Table/View/... 操作由 T-004
 * 接入；T-002 阶段所有 Extension 方法体 一律落到 {@link com.oceanbase.odc.plugin.schema.db2.utils.DBAccessorUtil}
 * 占位抛出 {@link UnsupportedOperationException}（compat-RISK-7）。
 */
public class Db2SchemaPlugin extends BaseSchemaPlugin {

    @Override
    public DialectType getDialectType() {
        return DialectType.DB2;
    }
}
