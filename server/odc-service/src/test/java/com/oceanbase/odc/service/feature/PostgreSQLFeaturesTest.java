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

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.odc.core.shared.constant.ConnectType;

/**
 * Unit tests for PostgreSQLFeatures
 */
public class PostgreSQLFeaturesTest {

    private static final Features FEATURES = new PostgreSQLFeatures();

    /**
     * Test case 1: PostgreSQL 不支持 show trace 设计文档 Section 3.5.1: supportsShowTrace() = false
     */
    @Test
    public void testSupportsShowTrace_returnsFalse() {
        Assert.assertFalse("PostgreSQL should not support show trace", FEATURES.supportsShowTrace());
    }

    /**
     * Test case 2: PostgreSQL 支持视图对象 设计文档 Section 3.5.1: supportsViewObject() = true
     */
    @Test
    public void testSupportsViewObject_returnsTrue() {
        Assert.assertTrue("PostgreSQL should support view object", FEATURES.supportsViewObject());
    }

    /**
     * Test case 3: PostgreSQL 没有 OceanBase 的租户概念 设计文档 Section 3.5.1: supportsOBTenant() = false
     */
    @Test
    public void testSupportsOBTenant_returnsFalse() {
        Assert.assertFalse("PostgreSQL should not support OB tenant", FEATURES.supportsOBTenant());
    }

    /**
     * Test case 4: PostgreSQL 没有 show tenant 命令 设计文档 Section 3.5.1: supportsShowTenant() = false
     */
    @Test
    public void testSupportsShowTenant_returnsFalse() {
        Assert.assertFalse("PostgreSQL should not support show tenant", FEATURES.supportsShowTenant());
    }

    /**
     * Test case 5: PostgreSQL 11+ 支持存储过程 设计文档 Section 3.5.1: supportsProcedure() = true
     */
    @Test
    public void testSupportsProcedure_returnsTrue() {
        Assert.assertTrue("PostgreSQL should support procedure", FEATURES.supportsProcedure());
    }

    /**
     * Test case 6: PostgreSQL 支持 SQL 中的 schema 前缀 设计文档 Section 3.5.1: supportsSchemaPrefixInSql() =
     * true
     */
    @Test
    public void testSupportsSchemaPrefixInSql_returnsTrue() {
        Assert.assertTrue("PostgreSQL should support schema prefix in SQL", FEATURES.supportsSchemaPrefixInSql());
    }

    /**
     * Test case 7: PostgreSQL 支持 EXPLAIN 命令 设计文档 Section 3.5.1: supportsExplain() = true
     */
    @Test
    public void testSupportsExplain_returnsTrue() {
        Assert.assertTrue("PostgreSQL should support explain", FEATURES.supportsExplain());
    }

    /**
     * Test case 8: PostgreSQL 不使用 AUTO_INCREMENT 设计文档 Section 3.5.1: supportsAutoIncrement() = false
     * (PG 用 SERIAL/IDENTITY)
     */
    @Test
    public void testSupportsAutoIncrement_returnsFalse() {
        Assert.assertFalse("PostgreSQL should not support auto_increment (uses SERIAL/IDENTITY)",
                FEATURES.supportsAutoIncrement());
    }

    /**
     * Test case 9: 验证 AllFeatures.getByConnectType() 对 POSTGRESQL 类型返回正确的 Features
     */
    @Test
    public void testAllFeatures_getByConnectType_postgresql() {
        Features features = AllFeatures.getByConnectType(ConnectType.POSTGRESQL);
        Assert.assertNotNull("Features should not be null for POSTGRESQL connect type", features);
        Assert.assertTrue("Should return PostgreSQLFeatures instance",
                features instanceof PostgreSQLFeatures);
    }

    /**
     * Test case 10: 验证通过 AllFeatures 获取的 PostgreSQLFeatures 各方法返回正确值
     */
    @Test
    public void testAllFeatures_postgresqlFeatures_allMethods() {
        Features features = AllFeatures.getByConnectType(ConnectType.POSTGRESQL);

        Assert.assertFalse("show trace should be false", features.supportsShowTrace());
        Assert.assertTrue("view object should be true", features.supportsViewObject());
        Assert.assertFalse("OB tenant should be false", features.supportsOBTenant());
        Assert.assertFalse("show tenant should be false", features.supportsShowTenant());
        Assert.assertTrue("procedure should be true", features.supportsProcedure());
        Assert.assertTrue("schema prefix should be true", features.supportsSchemaPrefixInSql());
        Assert.assertTrue("explain should be true", features.supportsExplain());
        Assert.assertFalse("auto increment should be false", features.supportsAutoIncrement());
    }

    /**
     * Test case 11: 验证 PostgreSQLFeatures 继承自 DefaultFeatures 确保 PostgreSQLFeatures 可以正确覆写父类方法
     */
    @Test
    public void testPostgreSQLFeatures_extendsDefaultFeatures() {
        Assert.assertTrue("PostgreSQLFeatures should extend DefaultFeatures",
                DefaultFeatures.class.isAssignableFrom(PostgreSQLFeatures.class));
    }
}
