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
package com.oceanbase.odc.plugin.connect.clickhouse;

import java.sql.Connection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.pf4j.Extension;
import org.springframework.jdbc.core.JdbcOperations;

import com.oceanbase.odc.common.util.JdbcOperationsUtil;
import com.oceanbase.odc.plugin.connect.api.InformationExtensionPoint;

import lombok.extern.slf4j.Slf4j;

/**
 * Returns a numeric-dot version string for session init ({@code VersionUtils}).
 */
@Slf4j
@Extension
public class ClickHouseInformationExtension implements InformationExtensionPoint {

    private static final String DEFAULT_VERSION = "26.3.0";
    private static final Pattern NUMERIC_VERSION = Pattern.compile("(\\d+(?:\\.\\d+)*)");

    @Override
    public String getDBVersion(Connection connection) {
        try {
            JdbcOperations jdbc = JdbcOperationsUtil.getJdbcOperations(connection);
            String rawVersion = jdbc.queryForObject("SELECT version()", String.class);
            if (rawVersion != null && !rawVersion.isEmpty()) {
                Matcher matcher = NUMERIC_VERSION.matcher(rawVersion);
                if (matcher.find()) {
                    return matcher.group(1);
                }
                return rawVersion;
            }
        } catch (Exception e) {
            log.warn("Failed to get ClickHouse version, returning default", e);
        }
        return DEFAULT_VERSION;
    }
}
