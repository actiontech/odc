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
package com.oceanbase.odc.plugin.connect.db2;

import org.junit.Assert;
import org.junit.Test;

public class Db2SessionExtensionTest {

    /**
     * compat-RISK-10：DB2 setClientInfo 行为版本差异大，MVP 阶段一律返回 false。
     */
    @Test
    public void setClientInfo_returnsFalse_skeleton() {
        Db2SessionExtension ext = new Db2SessionExtension();
        Assert.assertFalse(ext.setClientInfo(null, null));
    }
}
