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
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.pf4j.Extension;

import com.oceanbase.odc.core.shared.constant.ErrorCodes;
import com.oceanbase.odc.core.shared.exception.BadRequestException;
import com.oceanbase.odc.plugin.connect.api.InformationExtensionPoint;

import lombok.extern.slf4j.Slf4j;

/**
 * DB2 information extension. Returns the database product version via JDBC metadata (design.md
 * §2.3).
 *
 * <p>
 * Important: IBM Data Server Driver for JDBC returns the product version as the IBM <em>internal
 * version code</em> (for example {@code "SQL110580"} for DB2 v11.5.8.0) instead of a dotted decimal
 * string. ODC core consumes the returned value via
 * {@code com.oceanbase.odc.common.util.VersionUtils#compareVersions} which performs
 * {@code Integer.parseInt} on every dot-separated segment — passing the raw {@code "SQL110580"}
 * triggers {@link NumberFormatException} and blocks {@code POST
 * /api/v2/datasource/databases/{id}/sessions} (createSessionByDatabase), cascading through
 * table/view/index/constraint metadata loads (bug A in fix-G; see
 * {@code docs/dev/fix_reports/fix-G-db2-tree-meta.md}).
 *
 * <p>
 * Normalization strategy (in order, first match wins):
 * <ol>
 * <li>Already dotted decimal like {@code "11.5.8.0"} → return as-is (no change).</li>
 * <li>Free-form text containing a dotted decimal (e.g. {@code "DB2 v11.5.9.0"} returned by
 * {@code SYSIBMADM.ENV_INST_INFO.SERVICE_LEVEL}) → extract the first dotted run.</li>
 * <li>IBM internal code {@code "SQL"} + 6-to-8 digits → decode the trailing digits as
 * {@code V.R.M.F} (e.g. {@code "SQL110580"} → {@code "11.5.8.0"}).</li>
 * <li>Anything else → return a safe sentinel {@code "0.0.0"} so the caller can still run
 * {@code compareVersions} without throwing. A warning is logged to surface the unexpected format
 * upstream.</li>
 * </ol>
 *
 * @author actiontech-zihan
 * @since 4.3.4
 */
@Slf4j
@Extension
public class Db2InformationExtension implements InformationExtensionPoint {

    /**
     * IBM internal version code regex. The IBM Data Server Driver for JDBC reports the product version
     * as the IBM internal version code "SQL" + 6-to-8 digits. Empirical observation on DB2 v11.5.8.0
     * yields {@code "SQL110580"} — 6 digits decoded as V(2)=11, R(2)=05, M(1)=8, F(1)=0. Older / newer
     * builds may report 7 or 8 digits if the modification or fixpack levels widen past 9. We accept the
     * 6/7/8-digit widths and decode positionally with the last two digits being the fixpack (F). The
     * leading SQL prefix is matched case-insensitively to tolerate any future lowercase variants
     * emitted by IBM.
     */
    private static final Pattern IBM_INTERNAL_CODE = Pattern.compile("(?i)^SQL(\\d{6,8})$");

    /**
     * Free-form dotted decimal extractor. Matches the first run of dotted decimals so we tolerate
     * decoration around the version (e.g. {@code "DB2 v11.5.9.0"}, {@code "11.5.9 LUW"}).
     */
    private static final Pattern DOTTED_DECIMAL = Pattern.compile("(\\d+(?:\\.\\d+)+)");

    /**
     * Fallback when the driver-reported string cannot be parsed. Lets {@code VersionUtils} run without
     * throwing; downstream comparisons that gate features by version will degrade gracefully (treat
     * connection as oldest).
     */
    static final String UNKNOWN_VERSION = "0.0.0";

    @Override
    public String getDBVersion(Connection connection) {
        try {
            String raw = connection.getMetaData().getDatabaseProductVersion();
            return normalizeVersion(raw);
        } catch (SQLException e) {
            log.warn("DB2 getDBVersion failed: {}", e.getMessage());
            throw new BadRequestException(ErrorCodes.QueryDBVersionFailed,
                    new Object[] {e.getMessage()}, e.getMessage());
        }
    }

    /**
     * Visible-for-test helper that converts whatever the IBM JDBC driver returns into a dotted decimal
     * version string consumable by {@code VersionUtils.compareVersions}. See class-level Javadoc for
     * the strategy.
     */
    static String normalizeVersion(String raw) {
        if (raw == null || raw.isEmpty()) {
            log.warn("DB2 getDBVersion got null/empty version string, using {}", UNKNOWN_VERSION);
            return UNKNOWN_VERSION;
        }
        String trimmed = raw.trim();
        // Pre-emptive dotted decimal embedded in the string (covers "11.5.8.0",
        // "DB2 v11.5.9.0", "11.5.9 LUW", etc.).
        Matcher dottedMatcher = DOTTED_DECIMAL.matcher(trimmed);
        if (dottedMatcher.find()) {
            return dottedMatcher.group(1);
        }
        // IBM internal code like SQL110580 (v11.5.8.0). The IBM JDBC driver returns this when
        // there is no embedded dotted form to extract. Positional decode is anchored from both
        // ends:
        // V (major) = 2 leading digits
        // R (release) = next 2 digits
        // F (fixpack) = 1 trailing digit (6-width) or 2 trailing digits (7/8-width)
        // M (modification) = remaining middle digits (1 or 2)
        // Empirical sample observed in production logs:
        // "SQL110580" (6 digits 110580) → V=11 R=05 M=8 F=0 → "11.5.8.0"
        // The 7/8-digit forms are accepted for forward-compat against hypothetical future builds
        // that bump M or F past 9 (e.g. "SQL10050599" → V=10 R=05 M=05 F=99 → "10.5.5.99").
        Matcher internalMatcher = IBM_INTERNAL_CODE.matcher(trimmed);
        if (internalMatcher.matches()) {
            String digits = internalMatcher.group(1);
            int len = digits.length();
            // For 6-digit codes fixpack is 1 digit; for 7/8-digit codes fixpack is 2 digits.
            int fixpackWidth = (len == 6) ? 1 : 2;
            int v = Integer.parseInt(digits.substring(0, 2));
            int r = Integer.parseInt(digits.substring(2, 4));
            int m = Integer.parseInt(digits.substring(4, len - fixpackWidth));
            int f = Integer.parseInt(digits.substring(len - fixpackWidth, len));
            return v + "." + r + "." + m + "." + f;
        }
        log.warn("DB2 getDBVersion returned unrecognized format '{}', falling back to {}",
                raw, UNKNOWN_VERSION);
        return UNKNOWN_VERSION;
    }

}
