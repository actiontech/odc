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

/**
 * PostgreSQL 数据库功能特性配置
 * 
 * 定义 PostgreSQL 在 ODC 中支持的功能特性
 */
public class PostgreSQLFeatures extends DefaultFeatures {

    /**
     * PostgreSQL 不支持 OceanBase 的 show trace 命令
     */
    @Override
    public boolean supportsShowTrace() {
        return false;
    }

    /**
     * PostgreSQL 支持视图对象
     */
    @Override
    public boolean supportsViewObject() {
        return true;
    }

    /**
     * PostgreSQL 没有 OceanBase 的租户概念
     */
    @Override
    public boolean supportsOBTenant() {
        return false;
    }

    /**
     * PostgreSQL 没有 show tenant 命令（因为它没有租户概念）
     */
    @Override
    public boolean supportsShowTenant() {
        return false;
    }

    /**
     * PostgreSQL 11+ 支持存储过程（CREATE PROCEDURE）
     */
    @Override
    public boolean supportsProcedure() {
        return true;
    }

    /**
     * PostgreSQL 支持 SQL 中的 schema 前缀（schema.table 格式）
     */
    @Override
    public boolean supportsSchemaPrefixInSql() {
        return true;
    }

    /**
     * PostgreSQL 支持 EXPLAIN 命令查看执行计划
     */
    @Override
    public boolean supportsExplain() {
        return true;
    }

    /**
     * PostgreSQL 不使用 AUTO_INCREMENT，而是使用 SERIAL/BIGSERIAL 或 IDENTITY 列
     */
    @Override
    public boolean supportsAutoIncrement() {
        return false;
    }
}
