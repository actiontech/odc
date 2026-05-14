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

import java.sql.Connection;

import org.pf4j.Extension;

import com.oceanbase.odc.plugin.connect.api.InformationExtensionPoint;

/**
 * DB2 Information Extension（T-002 骨架，蓝本 {@code PostgresInformationExtension}）。
 * <p>
 * T-002 占位返回 {@code "DB2"} 字符串；T-003 接入真实 SQL：
 * 
 * <pre>
 *     SELECT service_level FROM TABLE(sysproc.env_get_inst_info()) AS A
 * </pre>
 * 
 * 返回示例：{@code DB2 v11.5.8.0}。
 */
@Extension
public class Db2InformationExtension implements InformationExtensionPoint {

    /**
     * Placeholder DB version string returned when the real SQL is not yet wired in. T-003 will replace
     * the body with a real JDBC query.
     */
    static final String PLACEHOLDER_DB_VERSION = "DB2";

    @Override
    public String getDBVersion(Connection connection) {
        // TODO(T-003): SELECT service_level FROM TABLE(sysproc.env_get_inst_info()) AS A
        return PLACEHOLDER_DB_VERSION;
    }
}
