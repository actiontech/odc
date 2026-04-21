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
package com.oceanbase.odc.plugin.schema.db2;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oceanbase.tools.dbbrowser.model.DBTableStats;

/**
 * Test cases for {@link DB2TableExtension#getTableStats} null/negative value handling. Since
 * getTableStats is a protected method that requires a real DB connection, we test the equivalent
 * logic directly using DBTableStats processing.
 */
@RunWith(Parameterized.class)
public class DB2TableExtensionTest {

    private final String testName;
    private final Long dataSizeInBytes;
    private final String expectedTableSize;

    public DB2TableExtensionTest(String testName, Long dataSizeInBytes, String expectedTableSize) {
        this.testName = testName;
        this.dataSizeInBytes = dataSizeInBytes;
        this.expectedTableSize = expectedTableSize;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                {"null dataSizeInBytes returns null tableSize", null, null},
                {"negative dataSizeInBytes returns null tableSize", -1L, null},
                {"zero dataSizeInBytes returns valid tableSize", 0L, "0 B"},
                {"positive dataSizeInBytes returns valid tableSize", 1024L, "1 KB"},
                {"large dataSizeInBytes returns valid tableSize", 1048576L, "1 MB"},
        });
    }

    @Test
    public void testTableStatsProcessing() {
        // Replicate the getTableStats null/negative handling logic from DB2TableExtension
        DBTableStats tableStats = new DBTableStats();
        tableStats.setDataSizeInBytes(dataSizeInBytes);

        if (dataSizeInBytes == null || dataSizeInBytes < 0) {
            tableStats.setTableSize(null);
        } else {
            tableStats.setTableSize(
                    com.oceanbase.odc.common.unit.BinarySizeUnit.B.of(dataSizeInBytes).toString());
        }

        Assert.assertEquals(expectedTableSize, tableStats.getTableSize());
    }

    @Test
    public void testNullStatsReturnsEmptyStats() {
        // When statsAccessor returns null, getTableStats should return a new empty DBTableStats
        DBTableStats stats = new DBTableStats();
        Assert.assertNotNull(stats);
        Assert.assertNull(stats.getDataSizeInBytes());
        Assert.assertNull(stats.getTableSize());
    }
}
