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
package com.oceanbase.odc.plugin.schema.gaussdb;

import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.odc.plugin.schema.api.BaseSchemaPlugin;

/**
 * Plugin entry for GaussDB schema operations. Reports {@link DialectType#GAUSSDB} so the pf4j
 * loader routes table / database extension calls to this plugin rather than the Postgres one when
 * the data source is registered as GaussDB.
 */
public class GaussDBSchemaPlugin extends BaseSchemaPlugin {
    @Override
    public DialectType getDialectType() {
        return DialectType.GAUSSDB;
    }
}
