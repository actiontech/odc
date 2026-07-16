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
package com.oceanbase.odc.service.session.model;

import java.util.Collections;
import java.util.HashMap;

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.odc.core.sql.execute.FutureResult;
import com.oceanbase.odc.core.sql.execute.model.JdbcGeneralResult;
import com.oceanbase.odc.core.sql.execute.model.SqlTuple;

/**
 * Test cases for {@link AsyncExecuteContext}.
 *
 * @author yh263208
 * @date 2026-07-14
 * @since ODC_release_4.3.4
 */
public class AsyncExecuteContextTest {

    @Test
    public void markTerminalFutureConsumedIfAbsent_markTwice_onlyFirstSuccess() {
        AsyncExecuteContext context = new AsyncExecuteContext(Collections.emptyList(), new HashMap<>());

        Assert.assertTrue(context.markTerminalFutureConsumedIfAbsent());
        Assert.assertFalse(context.markTerminalFutureConsumedIfAbsent());
    }

    @Test
    public void getMoreSqlExecutionResults_finishedQueueResult_preventDuplicateTerminalFuture() {
        JdbcGeneralResult result = JdbcGeneralResult.successResult(SqlTuple.newTuple("select 1"));
        AsyncExecuteContext context = new AsyncExecuteContext(Collections.emptyList(), new HashMap<>());
        context.addSqlExecutionResults(Collections.singletonList(result));
        context.setFuture(FutureResult.successResultList(result));

        Assert.assertEquals(1, context.getMoreSqlExecutionResults(0).size());
        Assert.assertFalse(context.markTerminalFutureConsumedIfAbsent());
    }

}
