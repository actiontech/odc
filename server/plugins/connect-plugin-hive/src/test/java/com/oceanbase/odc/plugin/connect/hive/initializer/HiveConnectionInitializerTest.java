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
package com.oceanbase.odc.plugin.connect.hive.initializer;

import org.junit.Test;

/**
 * The current contract for {@link HiveConnectionInitializer} is "no default SET statements" (design
 * §4.1.4). The pinning test below verifies that {@code init(null)} returns without executing
 * anything — if a future change accidentally adds a default SET, the call will NPE on the null
 * connection and this test will fail loudly so the change author has to revisit the design.
 */
public class HiveConnectionInitializerTest {

    @Test
    public void init_emptyDefaults_doesNotTouchConnection() throws Exception {
        // With the default SET list empty, init must early-return before invoking
        // connection.createStatement() — so passing null is a deliberate canary.
        new HiveConnectionInitializer().init(null);
    }
}
