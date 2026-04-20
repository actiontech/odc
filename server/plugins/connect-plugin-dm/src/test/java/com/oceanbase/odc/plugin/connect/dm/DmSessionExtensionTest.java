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
package com.oceanbase.odc.plugin.connect.dm;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Unit tests for {@link DmSessionExtension}.
 * <p>
 * Tests SQL generation for kill session/query and verifies session management behavior.
 * </p>
 *
 * @author
 * @since ODC_release_4.3.4
 */
@RunWith(Parameterized.class)
public class DmSessionExtensionTest {

    private final String description;
    private final String connectionId;
    private final String expectedKillSessionSql;
    private final String expectedKillQuerySql;

    public DmSessionExtensionTest(String description, String connectionId,
            String expectedKillSessionSql, String expectedKillQuerySql) {
        this.description = description;
        this.connectionId = connectionId;
        this.expectedKillSessionSql = expectedKillSessionSql;
        this.expectedKillQuerySql = expectedKillQuerySql;
    }

    @Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                // description, connectionId, expectedKillSessionSql, expectedKillQuerySql
                {"numeric session id",
                        "12345",
                        "CALL SP_CLOSE_SESSION(12345)",
                        "CALL SP_CLOSE_SESSION(12345)"},
                {"single digit session id",
                        "1",
                        "CALL SP_CLOSE_SESSION(1)",
                        "CALL SP_CLOSE_SESSION(1)"},
                {"large session id",
                        "999999999",
                        "CALL SP_CLOSE_SESSION(999999999)",
                        "CALL SP_CLOSE_SESSION(999999999)"},
        });
    }

    @Test
    public void testGetKillSessionSql() {
        DmSessionExtension extension = new DmSessionExtension();
        Assert.assertEquals(expectedKillSessionSql, extension.getKillSessionSql(connectionId));
    }

    @Test
    public void testGetKillQuerySql() {
        DmSessionExtension extension = new DmSessionExtension();
        Assert.assertEquals(expectedKillQuerySql, extension.getKillQuerySql(connectionId));
    }

    /**
     * Non-parameterized tests for other session extension behavior.
     */
    public static class DmSessionExtensionBasicTest {

        @Test
        public void killQuerySql_delegatesToKillSessionSql() {
            DmSessionExtension extension = new DmSessionExtension();
            String connId = "42";
            Assert.assertEquals(
                    extension.getKillSessionSql(connId),
                    extension.getKillQuerySql(connId));
        }

        @Test
        public void setClientInfo_returnsFalse() {
            DmSessionExtension extension = new DmSessionExtension();
            Assert.assertFalse(extension.setClientInfo(null, null));
        }

        @Test
        public void getAlterVariableStatement_returnsCallSPSetParaValue() {
            DmSessionExtension extension = new DmSessionExtension();
            String sql = extension.getAlterVariableStatement("SESSION", "PARAM1", "100");
            Assert.assertEquals("CALL SP_SET_PARA_VALUE(1, 'PARAM1', 100)", sql);
        }
    }
}
