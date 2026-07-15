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
