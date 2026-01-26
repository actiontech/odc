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
package com.oceanbase.tools.dbbrowser.editor;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcOperations;

import com.oceanbase.tools.dbbrowser.editor.sqlserver.SqlServerObjectOperator;
import com.oceanbase.tools.dbbrowser.model.DBObjectType;

/**
 * @description: all tests for {@link SqlServerObjectOperator}
 * @author: yizhou.xw
 * @date: 2024/12
 * @since: ODC_release_4.3.4
 */
public class SqlServerObjectOperatorTest {

    private JdbcOperations syncJdbcExecutor;
    private SqlServerObjectOperator operator;

    @Before
    public void setUp() {
        syncJdbcExecutor = mock(JdbcOperations.class);
        operator = new SqlServerObjectOperator(syncJdbcExecutor);
    }

    @Test
    public void drop_dropTable_dropSucceed() {
        operator.drop(DBObjectType.TABLE, "dbo", "test_table");
        verify(syncJdbcExecutor).execute("DROP TABLE [dbo].[test_table]");
    }

    @Test
    public void drop_dropView_dropSucceed() {
        operator.drop(DBObjectType.VIEW, "dbo", "test_view");
        verify(syncJdbcExecutor).execute("DROP VIEW [dbo].[test_view]");
    }

    @Test
    public void drop_dropIndex_dropSucceed() {
        operator.drop(DBObjectType.INDEX, "dbo", "test_index");
        verify(syncJdbcExecutor).execute("DROP INDEX [dbo].[test_index]");
    }

}
