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
package com.oceanbase.odc.plugin.connect.tidb;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class TiDBInformationExtensionTest {

    private final TiDBInformationExtension extension = new TiDBInformationExtension();

    @Test
    public void parseDBVersion_variousFormats_correctVersionExtracted() {
        Map<String, String> cases = new LinkedHashMap<>();
        // Standard TiDB version format
        cases.put("5.7.25-TiDB-v8.1.0", "8.1.0");
        // Earlier TiDB version
        cases.put("5.7.25-TiDB-v7.5.3", "7.5.3");
        // Minimum supported version
        cases.put("5.7.25-TiDB-v5.0.0", "5.0.0");
        // Double-digit major version
        cases.put("5.7.25-TiDB-v10.0.0", "10.0.0");
        // Pure MySQL version (fallback)
        cases.put("5.7.25", "5.7.25");
        // Non-TiDB suffix (fallback: split by "-" take first segment)
        cases.put("8.0.32-some-build", "8.0.32");

        for (Map.Entry<String, String> entry : cases.entrySet()) {
            String input = entry.getKey();
            String expected = entry.getValue();
            String actual = extension.parseDBVersion(input);
            Assert.assertEquals("parseDBVersion(\"" + input + "\")", expected, actual);
        }
    }
}
