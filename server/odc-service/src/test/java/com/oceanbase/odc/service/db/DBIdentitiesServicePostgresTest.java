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
package com.oceanbase.odc.service.db;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.odc.core.shared.constant.DialectType;

/**
 * AC-RV-BE-003: Path A {@code <catalog>.<schema>} must not be rejected by whole-string match
 * against {@code showDatabases()} catalog list.
 */
public class DBIdentitiesServicePostgresTest {

    @Test
    public void isExistingPostgresCatalogSchema_catalogPresent_true() {
        List<String> catalogs = Arrays.asList("db_a", "testdb");
        Assert.assertTrue(DBIdentitiesService.isExistingPostgresCatalogSchema(DialectType.POSTGRESQL,
                "db_a.app_a", catalogs));
    }

    @Test
    public void isExistingPostgresCatalogSchema_catalogMissing_false() {
        List<String> catalogs = Collections.singletonList("testdb");
        Assert.assertFalse(DBIdentitiesService.isExistingPostgresCatalogSchema(DialectType.POSTGRESQL,
                "missing.app_a", catalogs));
    }

    @Test
    public void isExistingPostgresCatalogSchema_plainSchema_false() {
        List<String> catalogs = Arrays.asList("db_a", "public");
        Assert.assertFalse(DBIdentitiesService.isExistingPostgresCatalogSchema(DialectType.POSTGRESQL,
                "public", catalogs));
    }

    @Test
    public void isExistingPostgresCatalogSchema_gaussdb_false() {
        List<String> catalogs = Collections.singletonList("db_a");
        Assert.assertFalse(DBIdentitiesService.isExistingPostgresCatalogSchema(DialectType.GAUSSDB,
                "db_a.app_a", catalogs));
    }

    @Test
    public void isExistingPostgresCatalogSchema_blankOrEmpty_false() {
        Assert.assertFalse(DBIdentitiesService.isExistingPostgresCatalogSchema(DialectType.POSTGRESQL,
                "db_a.app_a", Collections.emptyList()));
        Assert.assertFalse(DBIdentitiesService.isExistingPostgresCatalogSchema(DialectType.POSTGRESQL,
                null, Collections.singletonList("db_a")));
    }
}
