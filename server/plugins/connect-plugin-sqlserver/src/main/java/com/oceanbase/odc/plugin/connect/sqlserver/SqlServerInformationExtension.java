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
package com.oceanbase.odc.plugin.connect.sqlserver;

import java.sql.Connection;

import org.pf4j.Extension;

import com.oceanbase.odc.common.util.JdbcOperationsUtil;
import com.oceanbase.odc.plugin.connect.api.InformationExtensionPoint;

import lombok.extern.slf4j.Slf4j;

/**
 * @author yizhou.xw
 * @date 2024/12
 * @since ODC_release_4.3.4
 */
@Slf4j
@Extension
public class SqlServerInformationExtension implements InformationExtensionPoint {

    @Override
    public String getDBVersion(Connection connection) {
        String querySql = "SELECT SERVERPROPERTY('ProductVersion')";
        try {
            String version = JdbcOperationsUtil.getJdbcOperations(connection)
                    .queryForObject(querySql, String.class);
            if (version != null) {
                return version;
            }
            // Fallback to @@VERSION if SERVERPROPERTY returns null
            querySql = "SELECT @@VERSION";
            return JdbcOperationsUtil.getJdbcOperations(connection)
                    .queryForObject(querySql, String.class);
        } catch (Exception e) {
            log.warn("Failed to get SQL Server version, will return a default version", e);
            return "14.0.0";
        }
    }
}
