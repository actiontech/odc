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

import java.lang.reflect.Method;

import org.junit.Assert;
import org.junit.Test;

/**
 * The {@link HiveInformationExtension#getDBVersion(java.sql.Connection)} method requires a live
 * JDBC connection or a mocked DatabaseMetaData. We deliberately do not introduce mockito or jmockit
 * for the connect-plugin-hive module (per dev hard constraints — keep this test self-contained).
 * The remaining testable surface is the private {@code parseVersion} helper which we exercise via
 * reflection to pin the version-string sanitization logic.
 */
public class HiveInformationExtensionTest {

    @Test
    public void parseVersion_trimsBuildQualifiers() throws Exception {
        Method m = HiveInformationExtension.class.getDeclaredMethod("parseVersion", String.class);
        m.setAccessible(true);
        HiveInformationExtension target = new HiveInformationExtension();

        // "4.0.1 r..." -> "4.0.1"
        Assert.assertEquals("4.0.1", m.invoke(target, "4.0.1 rabcdef"));
        // "4.0.1-incubating" -> "4.0.1"
        Assert.assertEquals("4.0.1", m.invoke(target, "4.0.1-incubating"));
        // already clean
        Assert.assertEquals("4.0.1", m.invoke(target, "4.0.1"));
        // trailing whitespace is trimmed by the trim() call before any split
        Assert.assertEquals("3.1.3", m.invoke(target, "  3.1.3  "));
    }
}
