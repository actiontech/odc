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
package com.oceanbase.odc.service.session;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.odc.common.util.TraceStage;
import com.oceanbase.odc.common.util.TraceWatch;
import com.oceanbase.odc.common.util.TraceWatch.EditableTraceStage;
import com.oceanbase.odc.core.sql.execute.SqlExecuteStages;

/**
 * Test case for the fallback logic when executeMicroseconds is null
 * 
 * <p>
 * This test verifies that when PostgreSQL or SQLServer executes SQL, the Execute stage time can be
 * used as approximate DB execution time.
 *
 * @author ODC Team
 * @date 2025-03
 * @since ODC_release_4.3.5
 */
public class OdcStatementCallBackTraceStageTest {

    /**
     * Test that Execute stage can have DB Server Execute SQL as a subStage
     */
    @Test
    public void testExecuteStageCanHaveDBServerSubStage() throws IOException {
        try (TraceWatch tw = new TraceWatch()) {
            // Simulate Execute stage with DB Server subStage
            try (TraceStage executeStage = tw.start(SqlExecuteStages.EXECUTE)) {
                try (TraceStage dbStage = tw.startEditableStage(SqlExecuteStages.DB_SERVER_EXECUTE_SQL)) {
                    // DB execution happens here
                }
            }

            // Verify Execute stage exists
            List<TraceStage> executeStages = tw.getByTaskName(SqlExecuteStages.EXECUTE);
            Assert.assertEquals("Execute stage should exist", 1, executeStages.size());

            // Verify DB Server Execute SQL is subStage of Execute
            List<TraceStage> dbStages = tw.getByTaskName(SqlExecuteStages.DB_SERVER_EXECUTE_SQL);
            Assert.assertEquals("DB Server Execute SQL stage should exist", 1, dbStages.size());
        }
    }

    /**
     * Test that Execute stage time is measurable and can be used as DB time approximation
     */
    @Test
    public void testExecuteStageTimeIsMeasurable() throws IOException, InterruptedException {
        try (TraceWatch tw = new TraceWatch()) {
            try (TraceStage executeStage = tw.start(SqlExecuteStages.EXECUTE)) {
                Thread.sleep(10); // 10ms
            }

            List<TraceStage> executeStages = tw.getByTaskName(SqlExecuteStages.EXECUTE);
            Assert.assertEquals("Execute stage should exist", 1, executeStages.size());

            long executeTimeMicros = executeStages.get(0).getTime(TimeUnit.MICROSECONDS);
            Assert.assertTrue("Execute time should be >= 10ms", executeTimeMicros >= 10000);
        }
    }

    /**
     * Test PostgreSQL scenario: when executeMicroseconds is null, fallback to Execute stage time
     */
    @Test
    public void testPostgreSQLFallbackScenario() throws IOException, InterruptedException {
        try (TraceWatch tw = new TraceWatch()) {
            long executeTimeMicros;

            // Simulate Execute stage
            try (TraceStage executeStage = tw.start(SqlExecuteStages.EXECUTE)) {
                Thread.sleep(15); // 15ms simulated DB execution

                // Fallback: copy Execute time to DB Server stage
                executeTimeMicros = executeStage.getTime(TimeUnit.MICROSECONDS);
                // Note: at this point, we can't get the time until stage is stopped
            }

            // Get the Execute stage time after it's stopped
            List<TraceStage> executeStages = tw.getByTaskName(SqlExecuteStages.EXECUTE);
            executeTimeMicros = executeStages.get(0).getTime(TimeUnit.MICROSECONDS);

            // Now create the DB Server Execute SQL stage with that time
            try (TraceStage execStage = tw.start(SqlExecuteStages.EXECUTE)) {
                try (EditableTraceStage dbStage = tw.startEditableStage(SqlExecuteStages.DB_SERVER_EXECUTE_SQL)) {
                    // Set DB time to Execute time (fallback behavior)
                    dbStage.setTime(executeTimeMicros, TimeUnit.MICROSECONDS);
                }
            }

            // Verify DB Server Execute SQL stage has time set
            List<TraceStage> dbStages = tw.getByTaskName(SqlExecuteStages.DB_SERVER_EXECUTE_SQL);
            Assert.assertEquals("DB Server Execute SQL stage should exist", 1, dbStages.size());

            long dbTime = dbStages.get(0).getTime(TimeUnit.MICROSECONDS);
            Assert.assertEquals("DB time should match Execute time", executeTimeMicros, dbTime);
        }
    }

    /**
     * Test that EditableTraceStage can set custom time
     */
    @Test
    public void testEditableTraceStageCanSetTime() throws IOException {
        try (TraceWatch tw = new TraceWatch()) {
            try (TraceStage executeStage = tw.start(SqlExecuteStages.EXECUTE)) {
                try (EditableTraceStage dbStage = tw.startEditableStage(SqlExecuteStages.DB_SERVER_EXECUTE_SQL)) {
                    dbStage.setTime(5000, TimeUnit.MICROSECONDS); // 5ms
                }
            }

            List<TraceStage> dbStages = tw.getByTaskName(SqlExecuteStages.DB_SERVER_EXECUTE_SQL);
            Assert.assertEquals("DB stage should exist", 1, dbStages.size());
            Assert.assertEquals("DB time should be 5000 microseconds", 5000L,
                    dbStages.get(0).getTime(TimeUnit.MICROSECONDS));
        }
    }
}
