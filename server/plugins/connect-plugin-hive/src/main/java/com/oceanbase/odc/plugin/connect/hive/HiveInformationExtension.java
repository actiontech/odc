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
package com.oceanbase.odc.plugin.connect.hive;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

import org.pf4j.Extension;

import com.oceanbase.odc.core.shared.constant.ErrorCodes;
import com.oceanbase.odc.core.shared.exception.BadRequestException;
import com.oceanbase.odc.plugin.connect.api.InformationExtensionPoint;

import lombok.extern.slf4j.Slf4j;

/**
 * Probe Hive server version. Prefers {@code SELECT version()} (HiveServer2 native), falls back to
 * {@link DatabaseMetaData#getDatabaseProductVersion()} from the JDBC driver when the SQL path is
 * not supported.
 *
 * @since ODC_release_4.3.4
 */
@Slf4j
@Extension
public class HiveInformationExtension implements InformationExtensionPoint {

    @Override
    public String getDBVersion(Connection connection) {
        // Prefer "SELECT version()" which is available in Hive 2.x / 3.x / 4.x. The first column
        // is something like "4.0.1 r..." - we only keep the leading SemVer chunk.
        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT version()")) {
            if (rs.next()) {
                String raw = rs.getString(1);
                if (raw != null && !raw.isEmpty()) {
                    return parseVersion(raw);
                }
            }
        } catch (Exception e) {
            log.debug("SELECT version() failed, fallback to JDBC DatabaseMetaData. err={}", e.getMessage());
        }
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            String version = metaData.getDatabaseProductVersion();
            if (version == null || version.isEmpty()) {
                throw new BadRequestException(ErrorCodes.QueryDBVersionFailed,
                        new Object[] {"Empty product version"}, "Empty product version");
            }
            return parseVersion(version);
        } catch (Exception e) {
            throw new BadRequestException(ErrorCodes.QueryDBVersionFailed,
                    new Object[] {e.getMessage()}, e.getMessage());
        }
    }

    private String parseVersion(String version) {
        String trimmed = version.trim();
        // Trim build qualifiers: "4.0.1 r..." or "4.0.1-incubating".
        int sp = trimmed.indexOf(' ');
        if (sp > 0) {
            trimmed = trimmed.substring(0, sp);
        }
        int dash = trimmed.indexOf('-');
        if (dash > 0) {
            trimmed = trimmed.substring(0, dash);
        }
        return trimmed;
    }
}
