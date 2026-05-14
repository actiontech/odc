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

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.pf4j.Extension;

import com.oceanbase.odc.common.util.StringUtils;
import com.oceanbase.odc.core.datasource.ConnectionInitializer;
import com.oceanbase.odc.core.shared.constant.OdcConstants;
import com.oceanbase.odc.plugin.connect.api.HostAddress;
import com.oceanbase.odc.plugin.connect.api.JdbcUrlParser;
import com.oceanbase.odc.plugin.connect.api.TestResult;
import com.oceanbase.odc.plugin.connect.model.JdbcUrlProperty;
import com.oceanbase.odc.plugin.connect.obmysql.OBMySQLConnectionExtension;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * DB2 连接 Extension（T-002 骨架，蓝本 PostgresConnectionExtension + SqlServerConnectionExtension）。
 * <p>
 * <b>本任务（T-002）完成的部分</b>：
 * <ul>
 * <li>{@link #generateJdbcUrl(JdbcUrlProperty)}：完整实现 {@code jdbc:db2://host:port/catalog:k=v;k=v;}
 * 形式的 JDBC URL 构造（DB2 特殊点：参数串以 {@code :} 起始、{@code ;} 分隔）。</li>
 * <li>{@link #getDriverClassName()}：返回 {@link OdcConstants#DB2_DRIVER_CLASS_NAME}。</li>
 * <li>{@link #getConnectionInitializers()}：返回 {@link Collections#emptyList()}，DB2 无需特殊 session
 * 初始化。</li>
 * <li>{@link #appendDefaultJdbcUrlParameters(Map)}：默认追加
 * {@code retrieveMessagesFromServerOnGetMessage=true}（拿可读 SQLException）。</li>
 * </ul>
 * <b>T-003 接力完成</b>：
 * <ul>
 * <li>{@link #test(String, Properties, int, List)} 真实连通性（连接 IBM DB2 实例）；</li>
 * <li>{@link #getConnectionInfo(String, String)} 返回 {@link Db2JdbcUrlParser}（已在 T-002 提供）。</li>
 * </ul>
 *
 * @see Db2JdbcUrlParser
 */
@Slf4j
@Extension
public class Db2ConnectionExtension extends OBMySQLConnectionExtension {

    /**
     * DB2 JDBC URL 前缀：{@code jdbc:db2://}。
     */
    public static final String DB2_JDBC_URL_PREFIX = "jdbc:db2://";

    /**
     * 构造 DB2 JDBC URL：
     * 
     * <pre>
     *     jdbc:db2://&lt;host&gt;:&lt;port&gt;/&lt;catalogName&gt;[:k=v;k=v;]
     * </pre>
     * 
     * 注意：
     * <ul>
     * <li>DB2 catalog 必填（IBM JCC Type 4 driver 要求）；与 PostgreSQL 类似。</li>
     * <li>参数串与 PG / MySQL 的 {@code ?k=v&k=v} 不同，使用 {@code :k=v;k=v;}。</li>
     * <li>{@code defaultSchema} 透传为 {@code currentSchema=...}；常用做 DBA 切 schema。</li>
     * </ul>
     */
    @Override
    public String generateJdbcUrl(@NonNull JdbcUrlProperty properties) {
        String host = properties.getHost();
        Validate.notEmpty(host, "host can not be empty");
        Integer port = properties.getPort();
        Validate.notNull(port, "port can not be null");
        String catalogName = properties.getCatalogName();
        Validate.notEmpty(catalogName, "catalogName can not be empty for DB2");
        String defaultSchema = properties.getDefaultSchema();

        StringBuilder jdbcUrl = new StringBuilder(DB2_JDBC_URL_PREFIX)
                .append(host).append(":").append(port).append("/").append(catalogName);

        Map<String, String> jdbcUrlParams = appendDefaultJdbcUrlParameters(properties.getJdbcParameters());
        if (StringUtils.isNotBlank(defaultSchema)) {
            // currentSchema 通过 jdbcParameters 透传；若调用方已传则尊重调用方
            if (jdbcUrlParams == null) {
                jdbcUrlParams = new HashMap<>();
            }
            jdbcUrlParams.putIfAbsent("currentSchema", defaultSchema);
        }

        if (jdbcUrlParams != null && !jdbcUrlParams.isEmpty()) {
            jdbcUrl.append(":");
            for (Map.Entry<String, String> entry : jdbcUrlParams.entrySet()) {
                jdbcUrl.append(entry.getKey()).append("=").append(entry.getValue()).append(";");
            }
        }
        return jdbcUrl.toString();
    }

    /**
     * 返回 IBM JCC driver 类名。
     * <p>
     * 实际类由 pf4j PluginClassLoader 在装载 plugin jar 时从
     * {@code distribution/plugins/connect-plugin-db2/lib/db2jcc4.jar} 中加载（compat-RISK-6 / RISK-8）。
     */
    @Override
    public String getDriverClassName() {
        return OdcConstants.DB2_DRIVER_CLASS_NAME;
    }

    @Override
    public List<ConnectionInitializer> getConnectionInitializers() {
        return Collections.emptyList();
    }

    @Override
    public JdbcUrlParser getConnectionInfo(@NonNull String jdbcUrl, String userName) throws SQLException {
        return new Db2JdbcUrlParser(jdbcUrl, userName);
    }

    /**
     * 默认 JDBC 参数：
     * <ul>
     * <li>{@code retrieveMessagesFromServerOnGetMessage=true}：让 IBM JCC 在
     * {@code SQLException#getMessage} 时 从服务端取可读消息，便于 ODC 把 DB2 错误码翻译为业务错误（compat-RISK-10）。</li>
     * </ul>
     */
    @Override
    protected Map<String, String> appendDefaultJdbcUrlParameters(Map<String, String> jdbcUrlParams) {
        if (Objects.isNull(jdbcUrlParams)) {
            jdbcUrlParams = new HashMap<>();
        }
        jdbcUrlParams.putIfAbsent("retrieveMessagesFromServerOnGetMessage", "true");
        return jdbcUrlParams;
    }

    /**
     * Real DB2 connectivity test (T-003 commit-3). Builds on the OB-MySQL parent's {@code internalTest}
     * which already wraps {@link java.sql.DriverManager#getConnection} + {@code executeTestSqls} +
     * standard SQLException classification (unknownHost, unknownPort, socketTimeout → hostUnreachable,
     * accessDenied). For DB2 we additionally:
     *
     * <ul>
     * <li>override {@link #executeTestSqls(Statement)} to run {@code SELECT 1 FROM
     * SYSIBM.SYSDUMMY1} so the test SQL is dialect-correct;</li>
     * <li>preserve the standard JCC error-message keyword ("Connection refused" / "communication" /
     * "authorization" / "timed out") so the parent's classifier maps them to the right ODC error
     * codes;</li>
     * <li>fail fast and explicitly when the IBM JCC driver class is missing — this is the "deploy-side
     * jar not placed" path (compat-RISK-6).</li>
     * </ul>
     *
     * <p>
     * Real DB2 endpoint for manual smoke (NOT for unit tests): host=10.186.16.126, port=50000,
     * user=db2inst1, db=testdb. Unit tests stub {@link java.sql.DriverManager} via a mock driver so no
     * real network connection is made (compat-RISK-8 / compat-RISK-13).
     */
    /**
     * Effective socket / connect timeout (ms) applied to the DB2 test connection. Mirrors the value
     * used by {@link OBMySQLConnectionExtension} (10 s). Exposed as a package constant so unit tests
     * can short-circuit if needed.
     */
    static final int REACHABLE_TIMEOUT_MILLIS = 10_000;

    @Override
    public TestResult test(String jdbcUrl, Properties properties, int queryTimeout,
            List<ConnectionInitializer> initializers) {
        // Fail-fast when the IBM JCC driver class is missing on the classpath (compat-RISK-6).
        // The plugin pom uses <scope>provided</scope> so jcc must be in
        // distribution/plugins/connect-plugin-db2/lib/; if it is not, the user gets a readable
        // error rather than a generic SQLException buried inside DriverManager.
        if (!isDriverClassAvailable()) {
            log.warn("DB2 connect plugin: missing IBM JCC driver (com.ibm.db2.jcc.DB2Driver) - "
                    + "place db2jcc4.jar in distribution/plugins/connect-plugin-db2/lib/");
            return TestResult.unknownError(new ClassNotFoundException(
                    "DB2 connect plugin: IBM JCC driver com.ibm.db2.jcc.DB2Driver not found - "
                            + "please install db2jcc4.jar to "
                            + "distribution/plugins/connect-plugin-db2/lib/"));
        }
        if (properties == null) {
            properties = new Properties();
        }
        // IBM JCC honors loginTimeout (seconds) and reads queryTimeout from the Statement, not
        // from properties. Set socketTimeout / connectTimeout for hosts that reach the TCP layer
        // but never respond.
        properties.setProperty("loginTimeout", String.valueOf(REACHABLE_TIMEOUT_MILLIS / 1000));
        properties.setProperty("socketTimeout", String.valueOf(REACHABLE_TIMEOUT_MILLIS));
        properties.setProperty("connectTimeout", String.valueOf(REACHABLE_TIMEOUT_MILLIS));

        if (log.isDebugEnabled()) {
            log.debug("Db2ConnectionExtension.test() invoked for url={}",
                    StringUtils.isEmpty(jdbcUrl) ? "<null>" : jdbcUrl);
        }

        // Resolve hostAddress for downstream classification (UnknownHost / HostUnreachable).
        HostAddress hostAddress;
        try {
            hostAddress = getConnectionInfo(jdbcUrl, null).getHostAddresses().get(0);
        } catch (SQLException ex) {
            return TestResult.unknownError(ex);
        }

        try (Connection connection = openConnection(jdbcUrl, properties)) {
            try (Statement statement = connection.createStatement()) {
                if (queryTimeout >= 0) {
                    statement.setQueryTimeout(queryTimeout);
                }
                if (initializers != null && !initializers.isEmpty()) {
                    try {
                        for (ConnectionInitializer initializer : initializers) {
                            initializer.init(connection);
                        }
                    } catch (Exception e) {
                        return TestResult.initScriptFailed(e);
                    }
                }
                executeTestSqls(statement);
                return TestResult.success();
            }
        } catch (Exception e) {
            return classifyTestException(e, hostAddress);
        }
    }

    /**
     * Open a JDBC connection. Extracted as a separate method so unit tests can stub it without
     * registering a JDBC driver with the global {@link DriverManager}.
     */
    protected Connection openConnection(String jdbcUrl, Properties properties) throws SQLException {
        return DriverManager.getConnection(jdbcUrl, properties);
    }

    /**
     * Map a {@link Throwable} thrown while opening or testing the connection to a {@link TestResult}.
     * Implements DB2-aware classification on top of the standard MySQL-style mapping so callers see a
     * stable error code regardless of whether the immediate exception has a populated root cause (IBM
     * JCC frequently throws bare {@link SQLException} with the descriptive text in the message body).
     */
    protected TestResult classifyTestException(Throwable t, HostAddress hostAddress) {
        log.warn("Failed to test DB2 connection", t);
        Throwable rootCause = ExceptionUtils.getRootCause(t);
        Throwable effective = rootCause == null ? t : rootCause;
        String host = hostAddress == null ? null : hostAddress.getHost();
        Integer port = hostAddress == null ? null : hostAddress.getPort();
        if (effective instanceof ConnectException) {
            return port != null ? TestResult.unknownPort(port) : TestResult.unknownError(effective);
        }
        if (effective instanceof SocketTimeoutException) {
            return host != null ? TestResult.hostUnreachable(host) : TestResult.unknownError(effective);
        }
        if (effective instanceof UnknownHostException) {
            return host != null ? TestResult.unknownHost(host) : TestResult.unknownError(effective);
        }
        // Best-effort text matches for IBM JCC SQLException messages (it rarely populates causes).
        String msg = effective.getMessage();
        if (msg == null) {
            msg = t.getMessage();
        }
        if (msg == null) {
            msg = "";
        }
        if (StringUtils.containsIgnoreCase(msg, "Access denied")
                || StringUtils.containsIgnoreCase(msg, "authorization")
                || (effective instanceof SQLException
                        && "28000".equals(((SQLException) effective).getSQLState()))) {
            return TestResult.accessDenied(msg);
        }
        if (StringUtils.containsIgnoreCase(msg, "timed out")
                || StringUtils.containsIgnoreCase(msg, "communication link failure")) {
            return host != null ? TestResult.hostUnreachable(host) : TestResult.unknownError(effective);
        }
        if (StringUtils.containsIgnoreCase(msg, "nodename nor servname")
                || StringUtils.containsIgnoreCase(msg, "name or service not known")
                || StringUtils.containsIgnoreCase(msg, "unknown host")) {
            return host != null ? TestResult.unknownHost(host) : TestResult.unknownError(effective);
        }
        return TestResult.unknownError(effective);
    }

    /**
     * Run the DB2 connectivity probe SQL: {@code SELECT 1 FROM SYSIBM.SYSDUMMY1}. This is the IBM-
     * documented "no-op" SELECT (analogous to MySQL's {@code SELECT 1}). Executed against a real IBM
     * Db2 LUW 11.5 / 12.x instance returns a single 1-row result; failures bubble up as SQLExceptions
     * and the parent's classifier maps the root cause to a {@link TestResult}.
     */
    @Override
    protected void executeTestSqls(Statement statement) throws SQLException {
        statement.execute("SELECT 1 FROM SYSIBM.SYSDUMMY1");
    }

    /**
     * Check whether the IBM JCC driver class is available on the current ClassLoader. Used by
     * {@link #test(String, Properties, int, List)} to fail fast with a readable error when the
     * deploy-side IPLA jar is missing (compat-RISK-6). Exposed as protected so unit tests can override
     * to simulate the "jar missing" path without actually removing jcc from the test classpath.
     */
    protected boolean isDriverClassAvailable() {
        try {
            Class.forName(getDriverClassName());
            return true;
        } catch (ClassNotFoundException ignore) {
            return false;
        } catch (LinkageError linkErr) {
            // partial classpath (rare): treat as available — DriverManager will surface the
            // actual LinkageError from a connection attempt.
            log.warn("DB2 driver class found but failed to link", linkErr);
            return true;
        }
    }
}
