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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.pf4j.Extension;

import com.oceanbase.odc.common.util.JdbcOperationsUtil;
import com.oceanbase.odc.plugin.connect.api.InformationExtensionPoint;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Extension
public class DB2InformationExtension implements InformationExtensionPoint {

    private static final String DEFAULT_VERSION = "11.5.0";
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)*)");

    @Override
    public String getDBVersion(Connection connection) {
        String querySql = "SELECT service_level FROM TABLE(sysproc.env_get_inst_info())";
        try {
            String raw = JdbcOperationsUtil.getJdbcOperations(connection)
                    .queryForObject(querySql, String.class);
            if (raw == null) {
                return DEFAULT_VERSION;
            }
            // service_level returns strings like "DB2 v11.5.8.0", extract numeric version
            Matcher matcher = VERSION_PATTERN.matcher(raw);
            if (matcher.find()) {
                return matcher.group(1);
            }
            log.warn("Could not extract numeric version from service_level: {}", raw);
            return DEFAULT_VERSION;
        } catch (Exception e) {
            log.warn("Failed to get DB2 version, falling back to default", e);
            return DEFAULT_VERSION;
        }
    }
}
