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
package com.oceanbase.odc.plugin.connect.kingbase;

import org.junit.Assert;
import org.junit.Test;

public class KingBaseInformationExtensionTest {

    @Test
    public void normalizeVersion_kingbaseVrcBanner_returnsDotted() {
        Assert.assertEquals("9.1.10",
                KingBaseInformationExtension.normalizeVersion("KingbaseES V009R001C010"));
    }

    @Test
    public void normalizeVersion_dotted_returnsAsIs() {
        Assert.assertEquals("9.0.1", KingBaseInformationExtension.normalizeVersion("9.0.1"));
    }

    @Test
    public void normalizeVersion_null_returnsNull() {
        Assert.assertNull(KingBaseInformationExtension.normalizeVersion(null));
    }
}
