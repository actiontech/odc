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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.tools.dbbrowser.model.DBTrigger;

public class Db2TriggerExtensionTest {

    private final Db2TriggerExtension extension = new Db2TriggerExtension();

    @Test
    public void list_returnsEmptyForUiFriendlyRender() {
        Assert.assertTrue(extension.list(null, "DB2INST1").isEmpty());
    }

    /**
     * map case 矩阵：除 list 外的所有方法一律抛 UnsupportedOperationException（compat-RISK-7）。
     */
    @Test
    public void mapCase_unsupportedOps() {
        Map<String, Supplier<Object>> cases = new LinkedHashMap<>();
        cases.put("getDetail", () -> extension.getDetail(null, "S", "T"));
        cases.put("drop", () -> {
            extension.drop(null, "S", "T");
            return null;
        });
        cases.put("setEnable", () -> {
            extension.setEnable(null, "S", "T", true);
            return null;
        });
        cases.put("generateUpdateTemplate",
                () -> extension.generateUpdateTemplate(new DBTrigger(), new DBTrigger()));
        cases.put("generateCreateTemplate", () -> extension.generateCreateTemplate(new DBTrigger()));
        for (Map.Entry<String, Supplier<Object>> entry : cases.entrySet()) {
            try {
                entry.getValue().get();
                Assert.fail("case " + entry.getKey() + " expected UnsupportedOperationException");
            } catch (UnsupportedOperationException expected) {
                // ok
            }
        }
    }
}
