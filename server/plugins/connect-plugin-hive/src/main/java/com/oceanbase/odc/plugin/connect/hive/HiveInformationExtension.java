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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.pf4j.Extension;

import com.oceanbase.odc.plugin.connect.api.InformationExtensionPoint;

import lombok.extern.slf4j.Slf4j;

/**
 * Information extension for Apache Hive. Returns a pure numeric version string (e.g. "2.3.9")
 * suitable for {@code VersionUtils#compareVersions} which splits on "." and calls
 * {@code Integer.parseInt} on each segment.
 * <p>
 * The raw {@link DatabaseMetaData#getDatabaseProductVersion()} may return non-numeric strings such
 * as "2.3.9-SNAPSHOT" or "Apache Hive 2.3.9". This extension extracts the leading numeric-dot
 * portion to avoid {@code NumberFormatException} in downstream version comparisons.
 * <p>
 * This extension is required by {@code ConnectionInfoUtil#initSessionVersion} which is called
 * during {@code DefaultConnectSessionFactory#initSession}. Without it, the PF4J plugin manager
 * throws "Feature extension point is not supported for HIVE", blocking all database-level
 * operations (object tree expansion, SQL console, data browsing).
 *
 * @since ODC_release_4.3.4
 */
@Slf4j
@Extension
public class HiveInformationExtension implements InformationExtensionPoint {

    private static final String DEFAULT_VERSION = "2.3.0";
    private static final Pattern NUMERIC_VERSION = Pattern.compile("(\\d+(?:\\.\\d+)*)");

    @Override
    public String getDBVersion(Connection connection) {
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            String rawVersion = metaData.getDatabaseProductVersion();
            if (rawVersion != null && !rawVersion.isEmpty()) {
                String cleaned = extractNumericVersion(rawVersion);
                log.info("Hive DB version: raw='{}', cleaned='{}'", rawVersion, cleaned);
                return cleaned;
            }
        } catch (Exception e) {
            log.warn("Failed to get Hive version via DatabaseMetaData, returning default", e);
        }
        return DEFAULT_VERSION;
    }

    /**
     * Extracts the first numeric-dot version segment from a raw version string. Examples:
     * "2.3.9-SNAPSHOT" -> "2.3.9", "Apache Hive 2.3.9" -> "2.3.9", "2.3.9.0" -> "2.3.9.0".
     */
    private static String extractNumericVersion(String raw) {
        Matcher matcher = NUMERIC_VERSION.matcher(raw);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return DEFAULT_VERSION;
    }
}
