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
package com.oceanbase.odc.plugin.schema.hive.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.odc.plugin.schema.hive.utils.HiveDescribeParser.ParsedDescribe;
import com.oceanbase.odc.plugin.schema.hive.utils.HiveDescribeParser.Row;
import com.oceanbase.tools.dbbrowser.model.DBTableColumn;

/**
 * Map-case driven coverage of {@link HiveDescribeParser#parse(List)}. This parser is the
 * load-bearing piece behind {@code HiveTableExtension.getDetail} (DESCRIBE FORMATTED -> structured
 * columns), so the cases below pin both the happy path and the resilience contract demanded by
 * NFR-4.2.3 / design §4.1.5: malformed / blank / section-marker rows must never raise — they switch
 * state or are skipped.
 *
 * <p>
 * Covers compat-RISK R-11 (parsing resilience for varied Hive 4.x DESCRIBE outputs).
 */
public class HiveDescribeParserTest {

    @Test
    public void parse_nullRows_returnsEmptyResult() {
        ParsedDescribe result = HiveDescribeParser.parse(null);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.columns.isEmpty());
        Assert.assertTrue(result.partitionColumns.isEmpty());
        Assert.assertNull(result.tableType);
        Assert.assertNull(result.owner);
        Assert.assertNull(result.comment);
    }

    @Test
    public void parse_emptyRows_returnsEmptyResult() {
        ParsedDescribe result = HiveDescribeParser.parse(Collections.emptyList());
        Assert.assertNotNull(result);
        Assert.assertTrue(result.columns.isEmpty());
        Assert.assertTrue(result.partitionColumns.isEmpty());
    }

    @Test
    public void parse_mapCaseMatrix_meetsParsingContract() {
        Map<String, Scenario> cases = buildScenarios();
        for (Map.Entry<String, Scenario> entry : cases.entrySet()) {
            String label = entry.getKey();
            Scenario s = entry.getValue();
            ParsedDescribe result = HiveDescribeParser.parse(s.rows);
            Assert.assertNotNull(label + ": parse should never return null", result);
            Assert.assertEquals(label + ": columns size",
                    s.expectedColumnNames.size(), result.columns.size());
            for (int i = 0; i < s.expectedColumnNames.size(); i++) {
                Assert.assertEquals(label + ": column[" + i + "] name",
                        s.expectedColumnNames.get(i), result.columns.get(i).getName());
            }
            Assert.assertEquals(label + ": partition columns size",
                    s.expectedPartitionNames.size(), result.partitionColumns.size());
            for (int i = 0; i < s.expectedPartitionNames.size(); i++) {
                DBTableColumn pc = result.partitionColumns.get(i);
                Assert.assertEquals(label + ": partition[" + i + "] name",
                        s.expectedPartitionNames.get(i), pc.getName());
                Assert.assertEquals(label + ": partition[" + i + "] extraInfo",
                        "PARTITION", pc.getExtraInfo());
            }
            Assert.assertEquals(label + ": tableType", s.expectedTableType, result.tableType);
            Assert.assertEquals(label + ": owner", s.expectedOwner, result.owner);
        }
    }

    @Test
    public void parse_columnTypeAndCommentFlowsThroughTypeMapper() {
        // a deeper assertion than the matrix above: verify a column produced from a typed row carries
        // both the parsed typeName / precision and the comment forwarded from the third column.
        List<Row> rows = Arrays.asList(
                new Row("id", "decimal(10,2)", "primary key"),
                new Row("name", "varchar(255)", null));
        ParsedDescribe r = HiveDescribeParser.parse(rows);
        Assert.assertEquals(2, r.columns.size());

        DBTableColumn id = r.columns.get(0);
        Assert.assertEquals("id", id.getName());
        Assert.assertEquals("DECIMAL", id.getTypeName());
        Assert.assertEquals(Long.valueOf(10), id.getPrecision());
        Assert.assertEquals(Integer.valueOf(2), id.getScale());
        Assert.assertEquals("primary key", id.getComment());
        Assert.assertEquals(Boolean.TRUE, id.getNullable());

        DBTableColumn name = r.columns.get(1);
        Assert.assertEquals("name", name.getName());
        Assert.assertEquals("VARCHAR", name.getTypeName());
        Assert.assertEquals(Long.valueOf(255), name.getMaxLength());
    }

    @Test
    public void parse_skipsRowsWithBlankColumnNameAndNullRows() {
        // Defensive: rows with null col_name or blank col_name must not contribute columns; null Row
        // entries are skipped entirely.
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("real_col", "int", null));
        rows.add(new Row(null, "string", "ignored"));
        rows.add(new Row("   ", "bigint", "ignored"));
        rows.add(null);
        ParsedDescribe r = HiveDescribeParser.parse(rows);
        Assert.assertEquals(1, r.columns.size());
        Assert.assertEquals("real_col", r.columns.get(0).getName());
    }

    private static Map<String, Scenario> buildScenarios() {
        Map<String, Scenario> map = new LinkedHashMap<>();

        // 1. plain table — only regular columns
        map.put("regular_columns_only", new Scenario(
                Arrays.asList(
                        new Row("id", "int", "pk"),
                        new Row("name", "string", null)),
                Arrays.asList("id", "name"),
                Collections.emptyList(),
                null, null));

        // 2. partitioned table — partition header switches section, partition rows mark PARTITION.
        // Per parser contract: any "#"-prefixed row classifies the next section. The Hive output
        // standard inserts a "# col_name" pseudo-header right after "# Partition Information";
        // that pseudo-header re-classifies into Section.OTHER and the following partition rows are
        // silently dropped. So this case rejects the "# col_name" line and pins the simpler shape
        // where partition columns follow the partition header directly. The dropped-pseudo-header
        // behaviour is itself a "# Some Future Hive Section" variant pinned by case 6.
        map.put("with_partition_information", new Scenario(
                Arrays.asList(
                        new Row("id", "int", "pk"),
                        new Row("", null, null),
                        new Row("# Partition Information", null, null),
                        new Row("dt", "string", "date partition"),
                        new Row("region", "string", null)),
                Arrays.asList("id"),
                Arrays.asList("dt", "region"),
                null, null));

        // 3. detailed-table-information section captures Table Type + Owner
        map.put("detailed_metadata_captures_type_and_owner", new Scenario(
                Arrays.asList(
                        new Row("c1", "int", null),
                        new Row("# Detailed Table Information", null, null),
                        new Row("Owner:", "hive_user", null),
                        new Row("Table Type:", "EXTERNAL_TABLE", null)),
                Arrays.asList("c1"),
                Collections.emptyList(),
                "EXTERNAL_TABLE",
                "hive_user"));

        // 4. detailed-information section without partition section — only METADATA captured
        map.put("only_detailed_no_partition", new Scenario(
                Arrays.asList(
                        new Row("# Detailed Table Information", null, null),
                        new Row("Owner:", "alice", null),
                        new Row("Table Type:", "MANAGED_TABLE", null)),
                Collections.emptyList(),
                Collections.emptyList(),
                "MANAGED_TABLE",
                "alice"));

        // 5. blank rows in the middle do not break the column section; multiple blanks are ignored
        map.put("blank_rows_are_skipped", new Scenario(
                Arrays.asList(
                        new Row("a", "int", null),
                        new Row("", "", ""),
                        new Row("", null, null),
                        new Row("b", "string", null)),
                Arrays.asList("a", "b"),
                Collections.emptyList(),
                null, null));

        // 6. unknown "#" header routes to OTHER section, subsequent rows dropped silently — proves
        // the parser does not throw on unrecognised section markers (R-11 resilience)
        map.put("unknown_section_marker_is_silently_dropped", new Scenario(
                Arrays.asList(
                        new Row("first", "int", null),
                        new Row("# Some Future Hive Section", null, null),
                        new Row("stray_value", "anything", null)),
                Arrays.asList("first"),
                Collections.emptyList(),
                null, null));

        return map;
    }

    private static final class Scenario {
        final List<Row> rows;
        final List<String> expectedColumnNames;
        final List<String> expectedPartitionNames;
        final String expectedTableType;
        final String expectedOwner;

        Scenario(List<Row> rows, List<String> expectedColumnNames,
                List<String> expectedPartitionNames, String expectedTableType,
                String expectedOwner) {
            this.rows = rows;
            this.expectedColumnNames = expectedColumnNames;
            this.expectedPartitionNames = expectedPartitionNames;
            this.expectedTableType = expectedTableType;
            this.expectedOwner = expectedOwner;
        }
    }
}
