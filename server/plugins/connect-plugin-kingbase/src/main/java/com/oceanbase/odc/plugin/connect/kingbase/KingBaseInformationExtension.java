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
package com.oceanbase.odc.plugin.connect.kingbase;

import java.sql.Connection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.pf4j.Extension;

import com.oceanbase.odc.common.util.JdbcOperationsUtil;
import com.oceanbase.odc.plugin.connect.api.InformationExtensionPoint;

import lombok.extern.slf4j.Slf4j;

/**
 * Information extension for KingBase.
 * <p>
 * {@code SELECT VERSION()} returns banners such as {@code "KingbaseES V009R001C010"} which
 * {@code VersionUtils.compareVersions} cannot parse (throws {@code NumberFormatException} and
 * blocks session create / SQL console). Normalize to dotted decimals (e.g. {@code "9.1.10"}).
 */
@Slf4j
@Extension
public class KingBaseInformationExtension implements InformationExtensionPoint {

    private static final String DEFAULT_VERSION = "9.0.0";

    /** KingBase product code: V&lt;major&gt;R&lt;release&gt;C&lt;fixpack&gt; (zero-padded). */
    private static final Pattern KINGBASE_VRC =
            Pattern.compile("V(\\d+)R(\\d+)C(\\d+)", Pattern.CASE_INSENSITIVE);

    /** Fallback: any dotted decimal embedded in free-form text. */
    private static final Pattern DOTTED_DECIMAL = Pattern.compile("(\\d+(?:\\.\\d+)+)");

    @Override
    public String getDBVersion(Connection connection) {
        try {
            String version = JdbcOperationsUtil.getJdbcOperations(connection)
                    .queryForObject("SELECT VERSION()", String.class);
            String normalized = normalizeVersion(version);
            if (normalized != null) {
                return normalized;
            }
            log.warn("Failed to normalize KingBase version '{}', using default {}", version, DEFAULT_VERSION);
        } catch (Exception e) {
            log.warn("Failed to get KingBase version via VERSION()", e);
        }
        return DEFAULT_VERSION;
    }

    /**
     * Visible for tests. Maps raw VERSION() banners to VersionUtils-safe dotted decimals.
     */
    static String normalizeVersion(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String trimmed = raw.trim();
        Matcher vrc = KINGBASE_VRC.matcher(trimmed);
        if (vrc.find()) {
            int major = Integer.parseInt(vrc.group(1));
            int release = Integer.parseInt(vrc.group(2));
            int fixpack = Integer.parseInt(vrc.group(3));
            return major + "." + release + "." + fixpack;
        }
        Matcher dotted = DOTTED_DECIMAL.matcher(trimmed);
        if (dotted.find()) {
            return dotted.group(1);
        }
        if (trimmed.matches("\\d+")) {
            return trimmed + ".0.0";
        }
        return null;
    }
}
