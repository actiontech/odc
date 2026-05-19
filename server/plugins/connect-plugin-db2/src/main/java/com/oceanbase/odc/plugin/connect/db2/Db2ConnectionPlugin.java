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
 * pf4j entry point for the DB2 connect plugin. No business logic — extensions are wired via
 * {@code @Extension} on the sibling classes.
 *
 * @author actiontech-zihan
 * @since 4.3.4
 */
public class Db2ConnectionPlugin extends BaseConnectionPlugin {
    @Override
    public DialectType getDialectType() {
        return DialectType.DB2;
    }
}
