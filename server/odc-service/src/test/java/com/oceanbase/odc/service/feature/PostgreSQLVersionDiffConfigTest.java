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
package com.oceanbase.odc.service.feature;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for PostgreSQL Version Diff Config
 * 
 * This test verifies that the PostgreSQL configuration is properly added to the
 * odc_version_diff_config table via migration script.
 * 
 * Related issue: E-001 - 视图/函数/存储过程分组不可见 Fix: Add PostgreSQL support_view, support_function,
 * support_procedure configs
 * 
 * 设计文档参考: - 需求文档 AC-005.4: 对象类型分组展示 - 需求文档 AC-005.8: 后端 supportFeature 标志正确反映 PostgreSQL 支持的对象类型 -
 * 任务7描述: VersionDiffConfigService.getSupportFeatures() 从 odc_version_diff_config 表读取配置
 */
public class PostgreSQLVersionDiffConfigTest {

    // The migration script file path relative to odc module directory
    private static final String MIGRATION_SCRIPT_RELATIVE_PATH =
            "server/odc-migrate/src/main/resources/migrate/common/V_4_3_4_13__add_postgresql_version_diff_config.sql";

    /**
     * PostgreSQL config keys that must be present for resource tree to work correctly. These are the
     * minimum required configs to fix E-001: - support_view: enables view group in resource tree -
     * support_function: enables function group in resource tree - support_procedure: enables procedure
     * group in resource tree
     * 
     * Note: support_sequence, support_trigger, support_type are set to false because ODC doesn't
     * implement the corresponding ExtensionPoints yet (same as SQL Server). They are not included in
     * REQUIRED_POSTGRESQL_CONFIGS because they are disabled.
     */
    private static final String[] REQUIRED_POSTGRESQL_CONFIGS = {
            "support_view",
            "support_function",
            "support_procedure",
            "column_data_type",
            "support_constraint",
            "support_constraint_modify",
            "support_partition",
            "support_show_foreign_key",
            "support_kill_session",
            "support_kill_query",
            "support_sql_explain"
    };

    /**
     * Test case 1: Verify migration script file exists
     * 
     * 测试目标：验证迁移脚本文件存在
     */
    @Test
    public void testMigrationScript_fileExists() {
        File scriptFile = getMigrationScriptFile();
        Assert.assertTrue("Migration script file should exist: " + scriptFile.getAbsolutePath(),
                scriptFile.exists());
    }

    /**
     * Test case 2: Verify migration script contains required PostgreSQL support_view config
     * 
     * 测试目标：验证迁移脚本包含 support_view 配置 需求引用：AC-005.4, AC-005.8, E-001 修复
     */
    @Test
    public void testMigrationScript_containsSupportView() throws Exception {
        String content = readMigrationScript();
        Assert.assertTrue("Migration script should contain support_view for POSTGRESQL",
                content.contains("'support_view','POSTGRESQL'"));
    }

    /**
     * Test case 3: Verify migration script contains required PostgreSQL support_function config
     * 
     * 测试目标：验证迁移脚本包含 support_function 配置 需求引用：AC-005.4, AC-005.8, E-001 修复
     */
    @Test
    public void testMigrationScript_containsSupportFunction() throws Exception {
        String content = readMigrationScript();
        Assert.assertTrue("Migration script should contain support_function for POSTGRESQL",
                content.contains("'support_function','POSTGRESQL'"));
    }

    /**
     * Test case 4: Verify migration script contains required PostgreSQL support_procedure config
     * 
     * 测试目标：验证迁移脚本包含 support_procedure 配置 需求引用：AC-005.4, AC-005.8, E-001 修复
     */
    @Test
    public void testMigrationScript_containsSupportProcedure() throws Exception {
        String content = readMigrationScript();
        Assert.assertTrue("Migration script should contain support_procedure for POSTGRESQL",
                content.contains("'support_procedure','POSTGRESQL'"));
    }

    /**
     * Test case 5: Verify migration script contains all required PostgreSQL configs
     * 
     * 测试目标：验证迁移脚本包含所有必需的 PostgreSQL 配置
     */
    @Test
    public void testMigrationScript_containsAllRequiredConfigs() throws Exception {
        String content = readMigrationScript();

        // Extract all config keys for POSTGRESQL from the migration script
        Set<String> actualConfigKeys = extractPostgreSqlConfigKeys(content);

        for (String requiredConfig : REQUIRED_POSTGRESQL_CONFIGS) {
            Assert.assertTrue(
                    "Migration script should contain '" + requiredConfig + "' for POSTGRESQL. "
                            + "Found configs: " + actualConfigKeys,
                    actualConfigKeys.contains(requiredConfig.toLowerCase()));
        }
    }

    /**
     * Test case 6: Verify support_view is set to true for PostgreSQL
     * 
     * 测试目标：验证 support_view 配置值为 true
     */
    @Test
    public void testMigrationScript_supportView_isTrue() throws Exception {
        String content = readMigrationScript();
        // Pattern to match support_view config
        Pattern pattern = Pattern.compile(
                "values\\s*\\(\\s*'support_view'\\s*,\\s*'POSTGRESQL'\\s*,\\s*'([^']+)'",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(content);

        Assert.assertTrue("Should find support_view config for POSTGRESQL", matcher.find());
        Assert.assertEquals("support_view should be 'true' for POSTGRESQL",
                "true", matcher.group(1).toLowerCase());
    }

    /**
     * Test case 7: Verify support_function is set to true for PostgreSQL
     * 
     * 测试目标：验证 support_function 配置值为 true
     */
    @Test
    public void testMigrationScript_supportFunction_isTrue() throws Exception {
        String content = readMigrationScript();
        Pattern pattern = Pattern.compile(
                "values\\s*\\(\\s*'support_function'\\s*,\\s*'POSTGRESQL'\\s*,\\s*'([^']+)'",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(content);

        Assert.assertTrue("Should find support_function config for POSTGRESQL", matcher.find());
        Assert.assertEquals("support_function should be 'true' for POSTGRESQL",
                "true", matcher.group(1).toLowerCase());
    }

    /**
     * Test case 8: Verify support_procedure is set to true for PostgreSQL Note: PostgreSQL 11+ supports
     * CREATE PROCEDURE
     * 
     * 测试目标：验证 support_procedure 配置值为 true
     */
    @Test
    public void testMigrationScript_supportProcedure_isTrue() throws Exception {
        String content = readMigrationScript();
        Pattern pattern = Pattern.compile(
                "values\\s*\\(\\s*'support_procedure'\\s*,\\s*'POSTGRESQL'\\s*,\\s*'([^']+)'",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(content);

        Assert.assertTrue("Should find support_procedure config for POSTGRESQL", matcher.find());
        Assert.assertEquals("support_procedure should be 'true' for POSTGRESQL (PG 11+)",
                "true", matcher.group(1).toLowerCase());
    }

    /**
     * Test case 9: Verify column_data_type config contains PostgreSQL specific types
     * 
     * 测试目标：验证 column_data_type 配置包含 PostgreSQL 特有数据类型
     */
    @Test
    public void testMigrationScript_columnDataType_containsPostgreSqlTypes() throws Exception {
        String content = readMigrationScript();

        // Check for PostgreSQL specific data types
        String[] pgSpecificTypes = {
                "jsonb", // PostgreSQL specific JSON type
                "serial", "bigserial", // PostgreSQL auto-increment types
                "uuid", // PostgreSQL UUID type
                "timestamptz", "timetz", // PostgreSQL timezone-aware types
                "inet", "cidr", "macaddr" // PostgreSQL network address types
        };

        for (String pgType : pgSpecificTypes) {
            Assert.assertTrue(
                    "column_data_type config should contain PostgreSQL type: " + pgType,
                    content.toLowerCase().contains(pgType.toLowerCase()));
        }
    }

    /**
     * Test case 10: Verify db_mode value is POSTGRESQL (matches DialectType.POSTGRESQL.name())
     * 
     * 测试目标：验证 db_mode 值为 POSTGRESQL 重要：VersionDiffConfigService.getDbMode() 返回
     * connectType.getDialectType().name() 即 "POSTGRESQL"，迁移脚本必须使用相同的值
     */
    @Test
    public void testMigrationScript_dbModeIsPostgreSql() throws Exception {
        String content = readMigrationScript();

        // Count occurrences of POSTGRESQL db_mode in migration script
        Pattern pattern = Pattern.compile("'POSTGRESQL'", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(content);
        int count = 0;
        while (matcher.find()) {
            count++;
        }

        Assert.assertTrue(
                "Migration script should contain 'POSTGRESQL' db_mode at least once",
                count > 0);
    }

    /**
     * Test case 11: Verify support_procedure min_version is '11' for PostgreSQL PostgreSQL 11
     * introduced CREATE PROCEDURE syntax
     * 
     * 测试目标：验证 support_procedure 的 min_version 为 '11'
     */
    @Test
    public void testMigrationScript_supportProcedure_minVersionIs11() throws Exception {
        String content = readMigrationScript();
        Pattern pattern = Pattern.compile(
                "'support_procedure'\\s*,\\s*'POSTGRESQL'\\s*,\\s*'true'\\s*,\\s*'([^']+)'",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(content);

        Assert.assertTrue("Should find support_procedure config for POSTGRESQL with min_version",
                matcher.find());
        Assert.assertEquals("support_procedure min_version should be '11' (PG 11 introduced CREATE PROCEDURE)",
                "11", matcher.group(1));
    }

    /**
     * Test case 12: Verify not supported features are set to false Features that PostgreSQL doesn't
     * support natively should be false
     * 
     * 测试目标：验证 PostgreSQL 不支持的特性被设置为 false
     */
    @Test
    public void testMigrationScript_unsupportedFeatures_areFalse() throws Exception {
        String content = readMigrationScript();

        // Features that PostgreSQL doesn't support
        String[] unsupportedFeatures = {
                "support_recycle_bin", // PG doesn't have recycle bin like OceanBase
                "support_package", // PG doesn't have packages like Oracle
                "support_rowid", // PG doesn't have rowid like Oracle
                "support_synonym", // PG doesn't have synonyms like Oracle
                "support_shadowtable", // Not supported for PG
                "support_pl_debug", // Not supported for PG
                "support_sql_trace" // PG uses EXPLAIN ANALYZE instead
        };

        for (String feature : unsupportedFeatures) {
            Assert.assertTrue(
                    feature + " should be set to 'false' for POSTGRESQL",
                    content.toLowerCase().contains(
                            ("'" + feature + "','POSTGRESQL','false'").toLowerCase()));
        }
    }

    /**
     * Test case 12.1: Verify support_trigger is set to false for PostgreSQL ODC doesn't implement
     * TriggerExtensionPoint for PostgreSQL yet (same as SQL Server)
     * 
     * 测试目标：验证 support_trigger 配置值为 false 原因：ODC 未实现 PostgresTriggerExtension，与 SQL Server 保持一致
     */
    @Test
    public void testMigrationScript_supportTrigger_isFalse() throws Exception {
        String content = readMigrationScript();
        Pattern pattern = Pattern.compile(
                "values\\s*\\(\\s*'support_trigger'\\s*,\\s*'POSTGRESQL'\\s*,\\s*'([^']+)'",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(content);

        Assert.assertTrue("Should find support_trigger config for POSTGRESQL", matcher.find());
        Assert.assertEquals("support_trigger should be 'false' for POSTGRESQL (not implemented)",
                "false", matcher.group(1).toLowerCase());
    }

    /**
     * Test case 12.2: Verify support_trigger_ddl is set to false for PostgreSQL
     * 
     * 测试目标：验证 support_trigger_ddl 配置值为 false
     */
    @Test
    public void testMigrationScript_supportTriggerDdl_isFalse() throws Exception {
        String content = readMigrationScript();
        Pattern pattern = Pattern.compile(
                "values\\s*\\(\\s*'support_trigger_ddl'\\s*,\\s*'POSTGRESQL'\\s*,\\s*'([^']+)'",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(content);

        Assert.assertTrue("Should find support_trigger_ddl config for POSTGRESQL", matcher.find());
        Assert.assertEquals("support_trigger_ddl should be 'false' for POSTGRESQL (not implemented)",
                "false", matcher.group(1).toLowerCase());
    }

    /**
     * Test case 12.3: Verify support_sequence is set to false for PostgreSQL ODC doesn't implement
     * SequenceExtensionPoint for PostgreSQL yet (same as SQL Server)
     * 
     * 测试目标：验证 support_sequence 配置值为 false 原因：ODC 未实现 PostgresSequenceExtension，与 SQL Server 保持一致
     */
    @Test
    public void testMigrationScript_supportSequence_isFalse() throws Exception {
        String content = readMigrationScript();
        Pattern pattern = Pattern.compile(
                "values\\s*\\(\\s*'support_sequence'\\s*,\\s*'POSTGRESQL'\\s*,\\s*'([^']+)'",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(content);

        Assert.assertTrue("Should find support_sequence config for POSTGRESQL", matcher.find());
        Assert.assertEquals("support_sequence should be 'false' for POSTGRESQL (not implemented)",
                "false", matcher.group(1).toLowerCase());
    }

    /**
     * Test case 12.4: Verify support_type is set to false for PostgreSQL ODC doesn't implement
     * TypeExtensionPoint for PostgreSQL yet (same as SQL Server)
     * 
     * 测试目标：验证 support_type 配置值为 false 原因：ODC 未实现 PostgresTypeExtension，与 SQL Server 保持一致
     */
    @Test
    public void testMigrationScript_supportType_isFalse() throws Exception {
        String content = readMigrationScript();
        Pattern pattern = Pattern.compile(
                "values\\s*\\(\\s*'support_type'\\s*,\\s*'POSTGRESQL'\\s*,\\s*'([^']+)'",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(content);

        Assert.assertTrue("Should find support_type config for POSTGRESQL", matcher.find());
        Assert.assertEquals("support_type should be 'false' for POSTGRESQL (not implemented)",
                "false", matcher.group(1).toLowerCase());
    }

    /**
     * Test case 13: Verify the number of PostgreSQL configs is sufficient
     * 
     * 测试目标：验证 PostgreSQL 配置数量充足（至少包含核心配置）
     */
    @Test
    public void testMigrationScript_sufficientConfigCount() throws Exception {
        String content = readMigrationScript();
        Set<String> configKeys = extractPostgreSqlConfigKeys(content);

        // Should have at least 20 configs for a complete PostgreSQL support
        Assert.assertTrue("Should have at least 20 PostgreSQL configs, but found: " + configKeys.size(),
                configKeys.size() >= 20);
    }

    // Helper methods

    private File getMigrationScriptFile() {
        // Find the odc directory by looking for pom.xml
        File currentDir = new File(System.getProperty("user.dir"));
        while (currentDir != null && !new File(currentDir, "pom.xml").exists()) {
            currentDir = currentDir.getParentFile();
        }

        // Navigate to the migration script
        if (currentDir != null) {
            return new File(currentDir, MIGRATION_SCRIPT_RELATIVE_PATH);
        }

        // Fallback: try relative path from current directory
        return new File(MIGRATION_SCRIPT_RELATIVE_PATH);
    }

    private String readMigrationScript() throws Exception {
        File scriptFile = getMigrationScriptFile();
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(scriptFile), StandardCharsets.UTF_8)) {
            return new BufferedReader(reader).lines().collect(Collectors.joining("\n"));
        }
    }

    private Set<String> extractPostgreSqlConfigKeys(String content) {
        Set<String> configKeys = new HashSet<>();
        // Pattern to match config_key for POSTGRESQL
        // Example: values('support_view','POSTGRESQL',...
        Pattern pattern = Pattern.compile(
                "values\\s*\\(\\s*'([^']+)'\\s*,\\s*'POSTGRESQL'",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            configKeys.add(matcher.group(1).toLowerCase());
        }

        return configKeys;
    }
}
