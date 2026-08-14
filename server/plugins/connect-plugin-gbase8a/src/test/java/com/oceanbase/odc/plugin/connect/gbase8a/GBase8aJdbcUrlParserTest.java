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
package com.oceanbase.odc.plugin.connect.gbase8a;

import java.sql.SQLException;

import org.junit.Assert;
import org.junit.Test;

public class GBase8aJdbcUrlParserTest {

    @Test
    public void parse_withSchemaAndVcName() throws SQLException {
        String url = "jdbc:gbase://10.186.16.126:5258/gbase?vcName=vc1&useSSL=false";
        GBase8aJdbcUrlParser parser = new GBase8aJdbcUrlParser(url);
        Assert.assertEquals("10.186.16.126", parser.getHostAddresses().get(0).getHost());
        Assert.assertEquals(5258, parser.getHostAddresses().get(0).getPort().intValue());
        Assert.assertEquals("gbase", parser.getSchema());
        Assert.assertEquals("vc1", parser.getParameters().get("vcName"));
    }
}
