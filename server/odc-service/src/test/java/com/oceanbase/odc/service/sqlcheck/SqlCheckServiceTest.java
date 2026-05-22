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
package com.oceanbase.odc.service.sqlcheck;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcOperations;

import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.odc.service.regulation.ruleset.model.Rule;
import com.oceanbase.odc.service.regulation.ruleset.model.RuleMetadata;
import com.oceanbase.odc.service.regulation.ruleset.model.RuleType;
import com.oceanbase.odc.service.sqlcheck.rule.SqlCheckRules;

/**
 * Unit tests for {@link SqlCheckService#getRules(List, Supplier, DialectType, JdbcOperations)}.
 * <p>
 * Covers compat_risks CR-5a (SQLE plugin not loaded -> degrade, do not block) and CR-5b (GAUSSDB
 * short-circuit -> empty rule list). Also pins POSTGRESQL / MYSQL regression paths so existing
 * behaviour is unchanged.
 */
public class SqlCheckServiceTest {

    private SqlCheckService service;
    private JdbcOperations jdbc;
    private Supplier<String> dbVersionSupplier;

    @Before
    public void setUp() {
        this.service = new SqlCheckService();
        this.jdbc = mock(JdbcOperations.class);
        this.dbVersionSupplier = () -> "1.0";
    }

    private List<Rule> oneEnabledRule() {
        Rule rule = new Rule();
        RuleMetadata metadata = new RuleMetadata();
        metadata.setType(RuleType.SQL_CHECK);
        metadata.setName("test-rule");
        rule.setMetadata(metadata);
        rule.setEnabled(true);
        return Collections.singletonList(rule);
    }

    @Test
    public void testSqlCheck_GAUSSDB_dialect_returns_empty_rules() {
        // Asserts CR-5b: GAUSSDB short-circuits to emptyList() BEFORE the SQLE
        // rule factory lookup. SqlCheckRules.getAllFactories must NEVER be
        // invoked for GAUSSDB, otherwise we would risk loading the PG rule pack
        // and producing false-positive blocks for valid GaussDB DDL.
        try (MockedStatic<SqlCheckRules> ms = mockStatic(SqlCheckRules.class)) {
            List<com.oceanbase.odc.service.sqlcheck.api.SqlCheckRule> result =
                    service.getRules(oneEnabledRule(), dbVersionSupplier, DialectType.GAUSSDB, jdbc);

            Assert.assertNotNull(result);
            Assert.assertTrue("GAUSSDB must yield empty rule list, got: " + result, result.isEmpty());
            ms.verify(() -> SqlCheckRules.getAllFactories(any(), any()), never());
        }
    }

    @Test
    public void testSqlCheck_POSTGRESQL_dialect_unchanged() {
        // PG 0-regression: behavior must match the legacy code path - the
        // factory lookup is still invoked once and the result list is returned
        // verbatim. We stub the factory candidates to an empty list so the
        // downstream createByRule path is exercised but yields nothing.
        try (MockedStatic<SqlCheckRules> ms = mockStatic(SqlCheckRules.class)) {
            ms.when(() -> SqlCheckRules.getAllFactories(any(), any()))
                    .thenReturn(Collections.emptyList());
            ms.when(() -> SqlCheckRules.createByRule(any(), any(), any(), any()))
                    .thenReturn(null);

            List<com.oceanbase.odc.service.sqlcheck.api.SqlCheckRule> result =
                    service.getRules(oneEnabledRule(), dbVersionSupplier, DialectType.POSTGRESQL, jdbc);

            Assert.assertNotNull(result);
            ms.verify(() -> SqlCheckRules.getAllFactories(DialectType.POSTGRESQL, jdbc), times(1));
        }
    }

    @Test
    public void testSqlCheck_MYSQL_dialect_unchanged() {
        // MYSQL 0-regression: same expectation as PG; factory lookup is hit
        // and the GAUSSDB short-circuit must NOT activate for MYSQL.
        try (MockedStatic<SqlCheckRules> ms = mockStatic(SqlCheckRules.class)) {
            ms.when(() -> SqlCheckRules.getAllFactories(any(), any()))
                    .thenReturn(Collections.emptyList());
            ms.when(() -> SqlCheckRules.createByRule(any(), any(), any(), any()))
                    .thenReturn(null);

            List<com.oceanbase.odc.service.sqlcheck.api.SqlCheckRule> result =
                    service.getRules(oneEnabledRule(), dbVersionSupplier, DialectType.MYSQL, jdbc);

            Assert.assertNotNull(result);
            ms.verify(() -> SqlCheckRules.getAllFactories(DialectType.MYSQL, jdbc), times(1));
        }
    }

    @Test
    public void testSqlCheck_emptyRules_short_circuit_unchanged() {
        // Pre-existing short-circuit (rules.isEmpty()) must remain intact and
        // must execute BEFORE the GAUSSDB short-circuit, matching design.md
        // §3.2.4 ordering.
        try (MockedStatic<SqlCheckRules> ms = mockStatic(SqlCheckRules.class)) {
            List<com.oceanbase.odc.service.sqlcheck.api.SqlCheckRule> result =
                    service.getRules(Arrays.asList(), dbVersionSupplier, DialectType.MYSQL, jdbc);

            Assert.assertNotNull(result);
            Assert.assertTrue(result.isEmpty());
            ms.verify(() -> SqlCheckRules.getAllFactories(any(), any()), never());
        }
    }

    @Test
    public void testSqlCheck_SqleUnavailable_DoesNotBlockExecution() {
        // CR-5a / EARS-6.3 coverage: when the SQLE rule factory layer throws
        // because the gaussdb sqle plugin is not loaded, downstream workbench
        // execution must NOT be blocked.
        //
        // The current SqlCheckService.getRules implementation does NOT yet
        // wrap SqlCheckRules.getAllFactories in a try/catch fallback. Per
        // todo.md Task-D02 §6.4 we only land the assertion here as a regression
        // safety net: GAUSSDB is short-circuited before getAllFactories so a
        // runtime failure inside that method cannot affect GaussDB users. The
        // generic fallback for other dialects is deferred to the fix-phase
        // follow-up (tracked under CR-5a in docs/dev/compat_risks.md).
        try (MockedStatic<SqlCheckRules> ms = mockStatic(SqlCheckRules.class)) {
            ms.when(() -> SqlCheckRules.getAllFactories(any(), any()))
                    .thenThrow(new RuntimeException("sqle plugin not loaded"));

            // GaussDB path must remain insulated from sqle plugin failures.
            List<com.oceanbase.odc.service.sqlcheck.api.SqlCheckRule> result =
                    service.getRules(oneEnabledRule(), dbVersionSupplier, DialectType.GAUSSDB, jdbc);

            Assert.assertNotNull(result);
            Assert.assertTrue("GAUSSDB must degrade to empty rule list even when "
                    + "SQLE rule factory is broken", result.isEmpty());
            // And the factory was never called, which is what makes GaussDB
            // resilient against the unloaded plugin.
            ms.verify(() -> SqlCheckRules.getAllFactories(any(), any()), never());
        }
    }
}
