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

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.odc.common.util.VersionUtils;

/**
 * Mock-only unit tests for {@link Db2InformationExtension#normalizeVersion(String)} (fix-G bug A).
 *
 * <p>
 * Why this exists: the IBM Data Server Driver for JDBC returns the database product version as the
 * IBM internal version code (e.g. {@code "SQL110580"} for DB2 v11.5.8.0), not as a dotted decimal.
 * ODC core consumes the returned value via
 * {@code com.oceanbase.odc.common.util.VersionUtils#compareVersions} which calls
 * {@code Integer.parseInt} on each dot-separated segment — passing the raw {@code "SQL110580"}
 * through triggers {@link NumberFormatException} during {@code POST
 * /api/v2/datasource/databases/{id}/sessions} (createSessionByDatabase) and cascades to block every
 * metadata operation on DB2 datasources (bug A in fix-G; see
 * {@code docs/dev/fix_reports/fix-G-db2-tree-meta.md}).
 *
 * <p>
 * The fix normalises whatever the driver returns into a dotted decimal that
 * {@link VersionUtils#compareVersions} can parse.
 *
 * @author actiontech-zihan
 * @since 4.3.4
 */
public class Db2InformationExtensionTest {

    @Test
    public void normalizeVersion_ibmInternalCode_db2_v11_5_8_0_returnsDottedDecimal() {
        // Empirical raw value observed in
        // .run-odc/logs/odc-server.log at 2026-05-19 16:44 against DB2 v11.5.8.0.
        Assert.assertEquals("11.5.8.0", Db2InformationExtension.normalizeVersion("SQL110580"));
    }

    @Test
    public void normalizeVersion_ibmInternalCode_decodedSegments_compareCorrectly() {
        // Round-trip with VersionUtils to prove the bug A repro is fixed end-to-end:
        // raw "SQL110580" must no longer throw and must compare correctly to known thresholds.
        String normalised = Db2InformationExtension.normalizeVersion("SQL110580");
        Assert.assertTrue(VersionUtils.isGreaterThan(normalised, "11.5.0"));
        Assert.assertTrue(VersionUtils.isGreaterThan(normalised, "11.5.7.99"));
        Assert.assertTrue(VersionUtils.isLessThan(normalised, "11.5.9"));
    }

    @Test
    public void normalizeVersion_mapCases() {
        Map<String, String> cases = new LinkedHashMap<>();
        // Already-dotted decimals pass through as-is.
        cases.put("11.5.8.0", "11.5.8.0");
        cases.put("11.5.9", "11.5.9");
        // Free-form text containing a dotted decimal — e.g. SYSIBMADM.ENV_INST_INFO.SERVICE_LEVEL
        // returns "DB2 v11.5.9.0".
        cases.put("DB2 v11.5.9.0", "11.5.9.0");
        cases.put("DSN11015 (z/OS 11.0.15)", "11.0.15");
        // IBM internal version codes — primary fix-G bug A repros.
        cases.put("SQL110580", "11.5.8.0");
        cases.put("SQL110570", "11.5.7.0");
        // 8-digit form (covers hypothetical builds that bump M past 9). Sample positional decode:
        // V=10, R=05, M=05, F=99 → "10.5.5.99".
        cases.put("SQL10050599", "10.5.5.99");
        // Case-insensitive prefix.
        cases.put("sql110580", "11.5.8.0");
        // Unrecognised input → safe sentinel (caller will not throw and feature gates degrade).
        cases.put("not-a-version", Db2InformationExtension.UNKNOWN_VERSION);
        cases.put("", Db2InformationExtension.UNKNOWN_VERSION);

        for (Map.Entry<String, String> entry : cases.entrySet()) {
            Assert.assertEquals("input=" + entry.getKey(), entry.getValue(),
                    Db2InformationExtension.normalizeVersion(entry.getKey()));
        }
    }

    @Test
    public void normalizeVersion_nullInput_returnsSentinel() {
        Assert.assertEquals(Db2InformationExtension.UNKNOWN_VERSION,
                Db2InformationExtension.normalizeVersion(null));
    }

    @Test
    public void normalizeVersion_resultIsAlwaysVersionUtilsCompatible() {
        // Defense-in-depth: every output, including the sentinel, must parse via VersionUtils
        // without throwing. This is the contract bug A regressed.
        String[] rawInputs =
                {"SQL110580", "SQL110570", "11.5.8.0", "DB2 v11.5.9.0", "not-a-version", "", null};
        for (String raw : rawInputs) {
            String normalised = Db2InformationExtension.normalizeVersion(raw);
            // Should not throw.
            VersionUtils.compareVersions(normalised, "0.0.0");
        }
    }
}
