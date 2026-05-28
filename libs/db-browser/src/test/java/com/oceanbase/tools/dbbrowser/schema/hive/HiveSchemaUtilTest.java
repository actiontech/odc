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
package com.oceanbase.tools.dbbrowser.schema.hive;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.tools.dbbrowser.model.DBTableColumn;
import com.oceanbase.tools.dbbrowser.schema.hive.HiveSchemaUtil.DescribeRow;
import com.oceanbase.tools.dbbrowser.schema.hive.HiveSchemaUtil.HiveTableMetadata;

/**
 * Unit tests for {@link HiveSchemaUtil#parseDescribeFormatted(List)}. Covers all 8 test cases
 * defined in design.md section 5.2.2.
 * <p>
 * Pure in-memory tests using hardcoded DESCRIBE FORMATTED output. No external Hive instance
 * required.
 * </p>
 */
public class HiveSchemaUtilTest {

    // =================== Test Case 1: Non-partitioned TextFile table ===================

    @Test
    public void parseDescribeFormatted_nonPartitionedTextFileTable_parsedCorrectly() {
        List<DescribeRow> rows = new ArrayList<>();
        // COLUMNS section
        rows.add(new DescribeRow("id                  ", "int", null));
        rows.add(new DescribeRow("name                ", "string", null));
        rows.add(new DescribeRow("created_at          ", "timestamp", null));
        // empty separator
        rows.add(new DescribeRow("", null, null));
        // TABLE_INFO section
        rows.add(new DescribeRow("# Detailed Table Information", null, null));
        rows.add(new DescribeRow("Database:           ", "mydb", null));
        rows.add(new DescribeRow("Owner:              ", "hive", null));
        rows.add(new DescribeRow("CreateTime:         ", "Wed May 28 10:00:00 CST 2026", null));
        rows.add(new DescribeRow("LastAccessTime:     ", "UNKNOWN", null));
        rows.add(new DescribeRow("Retention:          ", "0", null));
        rows.add(new DescribeRow("Location:           ",
                "hdfs://namenode:8020/user/hive/warehouse/mydb.db/users", null));
        rows.add(new DescribeRow("Table Type:         ", "MANAGED_TABLE", null));
        rows.add(new DescribeRow("Table Parameters:   ", null, null));
        rows.add(new DescribeRow("  numFiles          ", "2", null));
        rows.add(new DescribeRow("  numRows           ", "1000", null));
        rows.add(new DescribeRow("  totalSize         ", "12345", null));
        rows.add(new DescribeRow("  transient_lastDdlTime", "1716876000", null));
        // empty separator
        rows.add(new DescribeRow("", null, null));
        // STORAGE_INFO section
        rows.add(new DescribeRow("# Storage Information", null, null));
        rows.add(new DescribeRow("SerDe Library:      ",
                "org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe", null));
        rows.add(new DescribeRow("InputFormat:        ",
                "org.apache.hadoop.mapred.TextInputFormat", null));
        rows.add(new DescribeRow("OutputFormat:       ",
                "org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat", null));
        rows.add(new DescribeRow("Compressed:         ", "No", null));
        rows.add(new DescribeRow("Num Buckets:        ", "-1", null));
        rows.add(new DescribeRow("Bucket Columns:     ", "[]", null));
        rows.add(new DescribeRow("Sort Columns:       ", "[]", null));
        rows.add(new DescribeRow("Storage Desc Params:", null, null));
        rows.add(new DescribeRow("  field.delim       ", ",", null));
        rows.add(new DescribeRow("  serialization.format", ",", null));

        HiveTableMetadata metadata = HiveSchemaUtil.parseDescribeFormatted(rows);

        // Verify columns
        Assert.assertEquals(3, metadata.getColumns().size());
        Assert.assertEquals("id", metadata.getColumns().get(0).getName());
        Assert.assertEquals("int", metadata.getColumns().get(0).getTypeName());
        Assert.assertEquals("name", metadata.getColumns().get(1).getName());
        Assert.assertEquals("string", metadata.getColumns().get(1).getTypeName());
        Assert.assertEquals("created_at", metadata.getColumns().get(2).getName());
        Assert.assertEquals("timestamp", metadata.getColumns().get(2).getTypeName());

        // Verify no partition columns
        Assert.assertTrue(metadata.getPartitionColumns().isEmpty());

        // Verify table properties
        Assert.assertEquals("mydb", metadata.getTableProperties().get("Database"));
        Assert.assertEquals("hive", metadata.getTableProperties().get("Owner"));
        Assert.assertEquals("MANAGED_TABLE", metadata.getTableProperties().get("Table Type"));

        // Verify table parameters
        Assert.assertEquals("1000", metadata.getTableParameters().get("numRows"));
        Assert.assertEquals("12345", metadata.getTableParameters().get("totalSize"));

        // Verify storage (TextFile format)
        Assert.assertEquals("org.apache.hadoop.mapred.TextInputFormat",
                metadata.getStorageProperties().get("InputFormat"));
        Assert.assertEquals("org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat",
                metadata.getStorageProperties().get("OutputFormat"));

        // Verify storage desc params
        Assert.assertEquals(",", metadata.getStorageDescParams().get("field.delim"));
    }

    // =================== Test Case 2: Single partition column ORC table ===================

    @Test
    public void parseDescribeFormatted_singlePartitionOrcTable_partitionsSeparated() {
        List<DescribeRow> rows = new ArrayList<>();
        // COLUMNS
        rows.add(new DescribeRow("id                  ", "int", null));
        rows.add(new DescribeRow("name                ", "string", null));
        rows.add(new DescribeRow("amount              ", "double", null));
        // empty separator
        rows.add(new DescribeRow("", null, null));
        // PARTITION_INFO
        rows.add(new DescribeRow("# Partition Information", null, null));
        rows.add(new DescribeRow("# col_name          ", "data_type", "comment"));
        rows.add(new DescribeRow("dt                  ", "string", null));
        // empty separator
        rows.add(new DescribeRow("", null, null));
        // TABLE_INFO
        rows.add(new DescribeRow("# Detailed Table Information", null, null));
        rows.add(new DescribeRow("Database:           ", "sales_db", null));
        rows.add(new DescribeRow("Owner:              ", "admin", null));
        rows.add(new DescribeRow("Table Type:         ", "MANAGED_TABLE", null));
        rows.add(new DescribeRow("Table Parameters:   ", null, null));
        rows.add(new DescribeRow("  numRows           ", "5000", null));
        rows.add(new DescribeRow("  totalSize         ", "98765", null));
        // empty separator
        rows.add(new DescribeRow("", null, null));
        // STORAGE_INFO
        rows.add(new DescribeRow("# Storage Information", null, null));
        rows.add(new DescribeRow("SerDe Library:      ",
                "org.apache.hadoop.hive.ql.io.orc.OrcSerde", null));
        rows.add(new DescribeRow("InputFormat:        ",
                "org.apache.hadoop.hive.ql.io.orc.OrcInputFormat", null));
        rows.add(new DescribeRow("OutputFormat:       ",
                "org.apache.hadoop.hive.ql.io.orc.OrcOutputFormat", null));
        rows.add(new DescribeRow("Compressed:         ", "No", null));

        HiveTableMetadata metadata = HiveSchemaUtil.parseDescribeFormatted(rows);

        // Verify regular columns
        Assert.assertEquals(3, metadata.getColumns().size());
        Assert.assertEquals("id", metadata.getColumns().get(0).getName());
        Assert.assertEquals("name", metadata.getColumns().get(1).getName());
        Assert.assertEquals("amount", metadata.getColumns().get(2).getName());

        // Verify partition columns separated correctly
        Assert.assertEquals(1, metadata.getPartitionColumns().size());
        Assert.assertEquals("dt", metadata.getPartitionColumns().get(0).getName());
        Assert.assertEquals("string", metadata.getPartitionColumns().get(0).getTypeName());

        // Verify ORC storage format
        Assert.assertEquals("org.apache.hadoop.hive.ql.io.orc.OrcInputFormat",
                metadata.getStorageProperties().get("InputFormat"));
        Assert.assertEquals("org.apache.hadoop.hive.ql.io.orc.OrcOutputFormat",
                metadata.getStorageProperties().get("OutputFormat"));
    }

    // =================== Test Case 3: Multiple partition columns Parquet table ===================

    @Test
    public void parseDescribeFormatted_multiPartitionParquetTable_allPartitionsParsed() {
        List<DescribeRow> rows = new ArrayList<>();
        // COLUMNS
        rows.add(new DescribeRow("user_id             ", "bigint", null));
        rows.add(new DescribeRow("event_type          ", "string", null));
        rows.add(new DescribeRow("event_time          ", "timestamp", null));
        // empty separator
        rows.add(new DescribeRow("", null, null));
        // PARTITION_INFO
        rows.add(new DescribeRow("# Partition Information", null, null));
        rows.add(new DescribeRow("# col_name          ", "data_type", "comment"));
        rows.add(new DescribeRow("year                ", "int", null));
        rows.add(new DescribeRow("month               ", "int", null));
        rows.add(new DescribeRow("day                 ", "int", null));
        // empty separator
        rows.add(new DescribeRow("", null, null));
        // TABLE_INFO
        rows.add(new DescribeRow("# Detailed Table Information", null, null));
        rows.add(new DescribeRow("Database:           ", "events_db", null));
        rows.add(new DescribeRow("Table Type:         ", "MANAGED_TABLE", null));
        // empty separator
        rows.add(new DescribeRow("", null, null));
        // STORAGE_INFO
        rows.add(new DescribeRow("# Storage Information", null, null));
        rows.add(new DescribeRow("SerDe Library:      ",
                "org.apache.hadoop.hive.ql.io.parquet.serde.ParquetHiveSerDe", null));
        rows.add(new DescribeRow("InputFormat:        ",
                "org.apache.hadoop.hive.ql.io.parquet.MapredParquetInputFormat", null));
        rows.add(new DescribeRow("OutputFormat:       ",
                "org.apache.hadoop.hive.ql.io.parquet.MapredParquetOutputFormat", null));

        HiveTableMetadata metadata = HiveSchemaUtil.parseDescribeFormatted(rows);

        // Verify regular columns
        Assert.assertEquals(3, metadata.getColumns().size());

        // Verify all 3 partition columns
        Assert.assertEquals(3, metadata.getPartitionColumns().size());
        Assert.assertEquals("year", metadata.getPartitionColumns().get(0).getName());
        Assert.assertEquals("int", metadata.getPartitionColumns().get(0).getTypeName());
        Assert.assertEquals("month", metadata.getPartitionColumns().get(1).getName());
        Assert.assertEquals("int", metadata.getPartitionColumns().get(1).getTypeName());
        Assert.assertEquals("day", metadata.getPartitionColumns().get(2).getName());
        Assert.assertEquals("int", metadata.getPartitionColumns().get(2).getTypeName());

        // Verify Parquet storage
        Assert.assertEquals(
                "org.apache.hadoop.hive.ql.io.parquet.MapredParquetInputFormat",
                metadata.getStorageProperties().get("InputFormat"));
    }

    // =================== Test Case 4: Columns with comments ===================

    @Test
    public void parseDescribeFormatted_columnsWithComments_commentsExtracted() {
        List<DescribeRow> rows = new ArrayList<>();
        // COLUMNS with comments
        rows.add(new DescribeRow("id                  ", "int", "primary key"));
        rows.add(new DescribeRow("name                ", "string", "user full name"));
        rows.add(new DescribeRow("email               ", "string", "user email address"));
        rows.add(new DescribeRow("age                 ", "int", null));
        // empty separator
        rows.add(new DescribeRow("", null, null));
        // TABLE_INFO
        rows.add(new DescribeRow("# Detailed Table Information", null, null));
        rows.add(new DescribeRow("Database:           ", "test_db", null));
        rows.add(new DescribeRow("Table Type:         ", "MANAGED_TABLE", null));
        // empty separator
        rows.add(new DescribeRow("", null, null));
        // STORAGE_INFO
        rows.add(new DescribeRow("# Storage Information", null, null));
        rows.add(new DescribeRow("SerDe Library:      ",
                "org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe", null));
        rows.add(new DescribeRow("InputFormat:        ",
                "org.apache.hadoop.mapred.TextInputFormat", null));
        rows.add(new DescribeRow("OutputFormat:       ",
                "org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat", null));

        HiveTableMetadata metadata = HiveSchemaUtil.parseDescribeFormatted(rows);

        // Verify comments are correctly extracted
        Assert.assertEquals(4, metadata.getColumns().size());
        Assert.assertEquals("primary key", metadata.getColumns().get(0).getComment());
        Assert.assertEquals("user full name", metadata.getColumns().get(1).getComment());
        Assert.assertEquals("user email address", metadata.getColumns().get(2).getComment());
        Assert.assertNull(metadata.getColumns().get(3).getComment()); // no comment -> null
    }

    // =================== Test Case 5: Complex type columns ===================

    @Test
    public void parseDescribeFormatted_complexTypeColumns_typeLiteralsPreserved() {
        List<DescribeRow> rows = new ArrayList<>();
        // COLUMNS with complex types
        rows.add(new DescribeRow("id                  ", "int", null));
        rows.add(new DescribeRow("tags                ", "array<string>", "tag list"));
        rows.add(new DescribeRow("properties          ", "map<string,int>", "property map"));
        rows.add(new DescribeRow("address             ", "struct<street:string,city:string,zip:int>",
                "address struct"));
        // empty separator
        rows.add(new DescribeRow("", null, null));
        // TABLE_INFO
        rows.add(new DescribeRow("# Detailed Table Information", null, null));
        rows.add(new DescribeRow("Database:           ", "complex_db", null));
        rows.add(new DescribeRow("Table Type:         ", "MANAGED_TABLE", null));
        // empty separator
        rows.add(new DescribeRow("", null, null));
        // STORAGE_INFO
        rows.add(new DescribeRow("# Storage Information", null, null));
        rows.add(new DescribeRow("SerDe Library:      ",
                "org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe", null));
        rows.add(new DescribeRow("InputFormat:        ",
                "org.apache.hadoop.mapred.TextInputFormat", null));
        rows.add(new DescribeRow("OutputFormat:       ",
                "org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat", null));

        HiveTableMetadata metadata = HiveSchemaUtil.parseDescribeFormatted(rows);

        // Verify complex type literals are preserved as-is
        Assert.assertEquals(4, metadata.getColumns().size());
        Assert.assertEquals("int", metadata.getColumns().get(0).getTypeName());
        Assert.assertEquals("array<string>", metadata.getColumns().get(1).getTypeName());
        Assert.assertEquals("map<string,int>", metadata.getColumns().get(2).getTypeName());
        Assert.assertEquals("struct<street:string,city:string,zip:int>",
                metadata.getColumns().get(3).getTypeName());

        // Verify fullTypeName also preserved
        Assert.assertEquals("array<string>", metadata.getColumns().get(1).getFullTypeName());
    }

    // =================== Test Case 6: Missing statistics (no numRows/totalSize) ===================

    @Test
    public void parseDescribeFormatted_missingStatistics_noError() {
        List<DescribeRow> rows = new ArrayList<>();
        // COLUMNS
        rows.add(new DescribeRow("id                  ", "int", null));
        rows.add(new DescribeRow("value               ", "string", null));
        // empty separator
        rows.add(new DescribeRow("", null, null));
        // TABLE_INFO
        rows.add(new DescribeRow("# Detailed Table Information", null, null));
        rows.add(new DescribeRow("Database:           ", "stats_db", null));
        rows.add(new DescribeRow("Table Type:         ", "MANAGED_TABLE", null));
        // Table Parameters present but NO numRows or totalSize
        rows.add(new DescribeRow("Table Parameters:   ", null, null));
        rows.add(new DescribeRow("  transient_lastDdlTime", "1716876000", null));
        // empty separator
        rows.add(new DescribeRow("", null, null));
        // STORAGE_INFO
        rows.add(new DescribeRow("# Storage Information", null, null));
        rows.add(new DescribeRow("SerDe Library:      ",
                "org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe", null));
        rows.add(new DescribeRow("InputFormat:        ",
                "org.apache.hadoop.mapred.TextInputFormat", null));
        rows.add(new DescribeRow("OutputFormat:       ",
                "org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat", null));

        HiveTableMetadata metadata = HiveSchemaUtil.parseDescribeFormatted(rows);

        // Parser should not error; numRows and totalSize are simply absent
        Assert.assertNull(metadata.getTableParameters().get("numRows"));
        Assert.assertNull(metadata.getTableParameters().get("totalSize"));
        // But other parameters present
        Assert.assertEquals("1716876000",
                metadata.getTableParameters().get("transient_lastDdlTime"));
        // Verify columns still parsed
        Assert.assertEquals(2, metadata.getColumns().size());
    }

    // =================== Test Case 7: External table ===================

    @Test
    public void parseDescribeFormatted_externalTable_tableTypeCorrect() {
        List<DescribeRow> rows = new ArrayList<>();
        // COLUMNS
        rows.add(new DescribeRow("log_time            ", "timestamp", null));
        rows.add(new DescribeRow("message             ", "string", null));
        rows.add(new DescribeRow("level               ", "string", "log level"));
        // empty separator
        rows.add(new DescribeRow("", null, null));
        // TABLE_INFO
        rows.add(new DescribeRow("# Detailed Table Information", null, null));
        rows.add(new DescribeRow("Database:           ", "logs_db", null));
        rows.add(new DescribeRow("Owner:              ", "etl_user", null));
        rows.add(new DescribeRow("Table Type:         ", "EXTERNAL_TABLE", null));
        rows.add(new DescribeRow("Location:           ",
                "hdfs://namenode:8020/data/external/logs", null));
        // empty separator
        rows.add(new DescribeRow("", null, null));
        // STORAGE_INFO
        rows.add(new DescribeRow("# Storage Information", null, null));
        rows.add(new DescribeRow("SerDe Library:      ",
                "org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe", null));
        rows.add(new DescribeRow("InputFormat:        ",
                "org.apache.hadoop.mapred.TextInputFormat", null));
        rows.add(new DescribeRow("OutputFormat:       ",
                "org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat", null));

        HiveTableMetadata metadata = HiveSchemaUtil.parseDescribeFormatted(rows);

        // Verify Table Type is EXTERNAL_TABLE
        Assert.assertEquals("EXTERNAL_TABLE", metadata.getTableProperties().get("Table Type"));
        Assert.assertEquals("logs_db", metadata.getTableProperties().get("Database"));
        Assert.assertEquals("etl_user", metadata.getTableProperties().get("Owner"));
        Assert.assertEquals("hdfs://namenode:8020/data/external/logs",
                metadata.getTableProperties().get("Location"));

        // Columns still parsed
        Assert.assertEquals(3, metadata.getColumns().size());
        Assert.assertEquals("log level", metadata.getColumns().get(2).getComment());
    }

    // =================== Test Case 8: Empty rows / defensive parsing ===================

    @Test
    public void parseDescribeFormatted_emptyInput_returnsEmptyMetadata() {
        List<DescribeRow> rows = new ArrayList<>();

        HiveTableMetadata metadata = HiveSchemaUtil.parseDescribeFormatted(rows);

        Assert.assertTrue(metadata.getColumns().isEmpty());
        Assert.assertTrue(metadata.getPartitionColumns().isEmpty());
        Assert.assertTrue(metadata.getTableProperties().isEmpty());
        Assert.assertTrue(metadata.getTableParameters().isEmpty());
        Assert.assertTrue(metadata.getStorageProperties().isEmpty());
        Assert.assertTrue(metadata.getStorageDescParams().isEmpty());
    }

    // =================== Additional edge case tests ===================

    @Test
    public void parseDescribeFormatted_ordinalPositionsAssigned() {
        List<DescribeRow> rows = new ArrayList<>();
        rows.add(new DescribeRow("col_a               ", "string", null));
        rows.add(new DescribeRow("col_b               ", "int", null));
        rows.add(new DescribeRow("col_c               ", "bigint", null));
        // empty separator
        rows.add(new DescribeRow("", null, null));
        // PARTITION_INFO
        rows.add(new DescribeRow("# Partition Information", null, null));
        rows.add(new DescribeRow("# col_name          ", "data_type", "comment"));
        rows.add(new DescribeRow("p1                  ", "string", null));
        rows.add(new DescribeRow("p2                  ", "int", null));

        HiveTableMetadata metadata = HiveSchemaUtil.parseDescribeFormatted(rows);

        // Verify ordinal positions for regular columns
        Assert.assertEquals(Integer.valueOf(1), metadata.getColumns().get(0).getOrdinalPosition());
        Assert.assertEquals(Integer.valueOf(2), metadata.getColumns().get(1).getOrdinalPosition());
        Assert.assertEquals(Integer.valueOf(3), metadata.getColumns().get(2).getOrdinalPosition());

        // Verify ordinal positions for partition columns (independent numbering)
        Assert.assertEquals(Integer.valueOf(1),
                metadata.getPartitionColumns().get(0).getOrdinalPosition());
        Assert.assertEquals(Integer.valueOf(2),
                metadata.getPartitionColumns().get(1).getOrdinalPosition());
    }

    @Test
    public void parseDescribeFormatted_tableParametersFollowedByStorageInfo_transitionCorrect() {
        // Tests the edge case where TABLE_PARAMETERS transitions directly to STORAGE_INFO
        // without going through a non-indented TABLE_INFO row first
        List<DescribeRow> rows = new ArrayList<>();
        rows.add(new DescribeRow("id                  ", "int", null));
        // empty separator
        rows.add(new DescribeRow("", null, null));
        // TABLE_INFO
        rows.add(new DescribeRow("# Detailed Table Information", null, null));
        rows.add(new DescribeRow("Database:           ", "test_db", null));
        rows.add(new DescribeRow("Table Type:         ", "MANAGED_TABLE", null));
        rows.add(new DescribeRow("Table Parameters:   ", null, null));
        rows.add(new DescribeRow("  numRows           ", "100", null));
        rows.add(new DescribeRow("  totalSize         ", "500", null));
        // empty separator
        rows.add(new DescribeRow("", null, null));
        // STORAGE_INFO follows - the state machine should transition from TABLE_PARAMETERS
        rows.add(new DescribeRow("# Storage Information", null, null));
        rows.add(new DescribeRow("SerDe Library:      ",
                "org.apache.hadoop.hive.ql.io.orc.OrcSerde", null));

        HiveTableMetadata metadata = HiveSchemaUtil.parseDescribeFormatted(rows);

        Assert.assertEquals("100", metadata.getTableParameters().get("numRows"));
        Assert.assertEquals("500", metadata.getTableParameters().get("totalSize"));
        Assert.assertEquals("org.apache.hadoop.hive.ql.io.orc.OrcSerde",
                metadata.getStorageProperties().get("SerDe Library"));
    }

    @Test
    public void parseDescribeFormatted_columnCommentIsWhitespaceOnly_treatedAsNull() {
        List<DescribeRow> rows = new ArrayList<>();
        rows.add(new DescribeRow("id                  ", "int", "   "));
        rows.add(new DescribeRow("name                ", "string", ""));

        HiveTableMetadata metadata = HiveSchemaUtil.parseDescribeFormatted(rows);

        Assert.assertNull(metadata.getColumns().get(0).getComment());
        Assert.assertNull(metadata.getColumns().get(1).getComment());
    }

    // =================== SQL constant tests ===================

    @Test
    public void sqlConstants_correctValues() {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("SHOW_DATABASES", "SHOW DATABASES");
        expected.put("SHOW_TABLES_IN", "SHOW TABLES IN ");
        expected.put("SHOW_VIEWS_IN", "SHOW VIEWS IN ");
        expected.put("DESCRIBE", "DESCRIBE ");
        expected.put("DESCRIBE_FORMATTED", "DESCRIBE FORMATTED ");
        expected.put("SHOW_CREATE_TABLE", "SHOW CREATE TABLE ");
        expected.put("SHOW_CREATE_VIEW", "SHOW CREATE VIEW ");
        expected.put("SHOW_PARTITIONS", "SHOW PARTITIONS ");

        Assert.assertEquals(expected.get("SHOW_DATABASES"), HiveSchemaUtil.SHOW_DATABASES);
        Assert.assertEquals(expected.get("SHOW_TABLES_IN"), HiveSchemaUtil.SHOW_TABLES_IN);
        Assert.assertEquals(expected.get("SHOW_VIEWS_IN"), HiveSchemaUtil.SHOW_VIEWS_IN);
        Assert.assertEquals(expected.get("DESCRIBE"), HiveSchemaUtil.DESCRIBE);
        Assert.assertEquals(expected.get("DESCRIBE_FORMATTED"), HiveSchemaUtil.DESCRIBE_FORMATTED);
        Assert.assertEquals(expected.get("SHOW_CREATE_TABLE"), HiveSchemaUtil.SHOW_CREATE_TABLE);
        Assert.assertEquals(expected.get("SHOW_CREATE_VIEW"), HiveSchemaUtil.SHOW_CREATE_VIEW);
        Assert.assertEquals(expected.get("SHOW_PARTITIONS"), HiveSchemaUtil.SHOW_PARTITIONS);
    }
}
