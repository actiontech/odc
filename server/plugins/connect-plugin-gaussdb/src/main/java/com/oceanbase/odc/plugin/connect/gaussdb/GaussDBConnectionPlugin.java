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
package com.oceanbase.odc.plugin.connect.gaussdb;

import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.odc.plugin.connect.api.BaseConnectionPlugin;

/**
 * Plugin entry for GaussDB (Huawei Cloud GaussDB / openGauss) data sources.
 * <p>
 * Mirrors {@code PostgresConnectionPlugin} but reports {@link DialectType#GAUSSDB} so the pf4j
 * loader can pick this plugin instead of the postgres one when the data source is registered as
 * GaussDB.
 */
public class GaussDBConnectionPlugin extends BaseConnectionPlugin {
    @Override
    public DialectType getDialectType() {
        return DialectType.GAUSSDB;
    }
}
