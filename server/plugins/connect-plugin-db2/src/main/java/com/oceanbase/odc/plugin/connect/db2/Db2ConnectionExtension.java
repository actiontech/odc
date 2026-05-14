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

import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import org.apache.commons.lang3.Validate;
import org.pf4j.Extension;

import com.oceanbase.odc.common.util.StringUtils;
import com.oceanbase.odc.core.datasource.ConnectionInitializer;
import com.oceanbase.odc.core.shared.constant.OdcConstants;
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
     * 真实 DB2 连通性测试。
     * <p>
     * T-002 骨架阶段：保留与父类一致的 OB-MySQL 路径，单测以 mock 形式验证不会抛 NPE； <b>T-003</b> 必须改写为：用 IBM JCC DriverManager
     * 建立连接 → executeTestSqls （DB2 推荐 {@code VALUES 1}）→ 翻译 SQLException 错误码到 TestResult； 真实 DB2 实例信息见
     * docs/dev/todo.md（host=10.186.16.126, port=50000, user=db2inst1）。
     */
    @Override
    public TestResult test(String jdbcUrl, Properties properties, int queryTimeout,
            List<ConnectionInitializer> initializers) {
        // TODO(T-003): 替换为真实 DB2 连接 + SQLException 错误码翻译
        if (log.isDebugEnabled()) {
            log.debug("Db2ConnectionExtension.test() skeleton invoked for url={} (T-002 skeleton)",
                    StringUtils.isEmpty(jdbcUrl) ? "<null>" : jdbcUrl);
        }
        return super.test(jdbcUrl, properties, queryTimeout, initializers);
    }
}
