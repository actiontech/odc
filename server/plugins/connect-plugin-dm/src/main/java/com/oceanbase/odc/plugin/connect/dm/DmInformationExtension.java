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
package com.oceanbase.odc.plugin.connect.dm;

import java.sql.Connection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.pf4j.Extension;

import com.oceanbase.odc.common.util.JdbcOperationsUtil;
import com.oceanbase.odc.plugin.connect.api.InformationExtensionPoint;

import lombok.extern.slf4j.Slf4j;

/**
 * Information extension for DM (Dameng) database.
 * <p>
 * Retrieves database version from {@code V$VERSION} system view and extracts a numeric version
 * string. DM's {@code V$VERSION} BANNER typically returns descriptive text like
 * {@code "DM Database Server 64 V8"} rather than a numeric version, so we parse the major version
 * from the "V<number>" suffix and return it in dotted format (e.g. "8.0.0").
 * </p>
 *
 * @author
 * @since ODC_release_4.3.4
 */
@Slf4j
@Extension
public class DmInformationExtension implements InformationExtensionPoint {

    private static final String DEFAULT_DM_VERSION = "8.0.0";
    /**
     * Pattern to match version identifiers in the DM banner string. Matches "V" followed by digits,
     * optionally followed by dotted sub-versions (e.g. "V8", "V8.1", "V8.1.2.3"). Case-insensitive.
     */
    private static final Pattern DM_VERSION_PATTERN = Pattern.compile("V(\\d+(?:\\.\\d+)*)", Pattern.CASE_INSENSITIVE);

    @Override
    public String getDBVersion(Connection connection) {
        String sql = "SELECT BANNER FROM V$VERSION WHERE ROWNUM = 1";
        try {
            String banner = JdbcOperationsUtil.getJdbcOperations(connection)
                    .queryForObject(sql, String.class);
            if (banner != null) {
                String version = extractVersion(banner);
                if (version != null) {
                    return version;
                }
                log.warn("Failed to extract numeric version from DM banner '{}', using default", banner);
            }
        } catch (Exception e) {
            log.warn("Failed to get DM version from V$VERSION, will return a default version", e);
        }
        return DEFAULT_DM_VERSION;
    }

    /**
     * Extract a numeric version string from the DM banner.
     * <p>
     * Examples:
     * <ul>
     * <li>"DM Database Server 64 V8" -> "8"</li>
     * <li>"DM Database Server 64 V8.1.2.84" -> "8.1.2.84"</li>
     * </ul>
     *
     * @param banner the raw banner string from V$VERSION
     * @return extracted version string, or null if no version pattern found
     */
    static String extractVersion(String banner) {
        if (banner == null || banner.isEmpty()) {
            return null;
        }
        Matcher matcher = DM_VERSION_PATTERN.matcher(banner);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
