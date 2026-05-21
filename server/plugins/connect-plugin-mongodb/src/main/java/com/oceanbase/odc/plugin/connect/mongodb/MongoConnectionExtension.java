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
package com.oceanbase.odc.plugin.connect.mongodb;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang3.Validate;
import org.pf4j.Extension;

import com.mongodb.MongoSecurityException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.oceanbase.odc.common.util.ExceptionUtils;
import com.oceanbase.odc.common.util.StringUtils;
import com.oceanbase.odc.core.datasource.ConnectionInitializer;
import com.oceanbase.odc.core.shared.constant.OdcConstants;
import com.oceanbase.odc.plugin.connect.api.ConnectionExtensionPoint;
import com.oceanbase.odc.plugin.connect.api.JdbcUrlParser;
import com.oceanbase.odc.plugin.connect.api.TestResult;
import com.oceanbase.odc.plugin.connect.model.JdbcUrlProperty;
import com.oceanbase.odc.plugin.connect.mongodb.bridge.MongoJdbcDriver;

import lombok.NonNull;

@Extension
public class MongoConnectionExtension implements ConnectionExtensionPoint {
    @Override
    public String generateJdbcUrl(@NonNull JdbcUrlProperty properties) {
        Validate.notEmpty(properties.getHost(), "host can not be empty");
        Validate.notNull(properties.getPort(), "port can not be null");

        StringBuilder builder = new StringBuilder("jdbc:mongodb://")
                .append(properties.getHost())
                .append(":")
                .append(properties.getPort());
        if (StringUtils.isNotBlank(properties.getDefaultSchema())) {
            builder.append("/").append(properties.getDefaultSchema());
        }
        Map<String, String> parameters = appendDefaultJdbcUrlParameters(properties.getJdbcParameters());
        if (!parameters.isEmpty()) {
            builder.append("?");
            boolean first = true;
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                if (!first) {
                    builder.append("&");
                }
                builder.append(entry.getKey()).append("=").append(entry.getValue());
                first = false;
            }
        }
        return builder.toString();
    }

    private Map<String, String> appendDefaultJdbcUrlParameters(Map<String, String> source) {
        Map<String, String> result = source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
        result.putIfAbsent("appName", "odc-mongodb");
        return result;
    }

    @Override
    public String getDriverClassName() {
        return OdcConstants.MONGODB_DRIVER_CLASS_NAME;
    }

    @Override
    public List<ConnectionInitializer> getConnectionInitializers() {
        return Collections.emptyList();
    }

    @Override
    public JdbcUrlParser getConnectionInfo(@NonNull String jdbcUrl, String userName) throws SQLException {
        return new MongoJdbcUrlParser(jdbcUrl, userName);
    }

    @Override
    public TestResult test(String jdbcUrl, Properties properties, int queryTimeout,
            List<ConnectionInitializer> initializers) {
        try {
            String mongoUri = MongoJdbcDriver.toMongoUri(jdbcUrl, properties);
            try (MongoClient client = MongoClients.create(mongoUri)) {
                client.getDatabase("admin").runCommand(new org.bson.Document("ping", 1));
            }
            return TestResult.success();
        } catch (Exception e) {
            Throwable rootCause = ExceptionUtils.getRootCause(e);
            if (rootCause instanceof MongoSecurityException) {
                return TestResult.accessDenied(rootCause.getLocalizedMessage());
            }
            if (rootCause instanceof UnknownHostException) {
                return TestResult.unknownHost(rootCause.getLocalizedMessage());
            }
            if (rootCause instanceof ConnectException) {
                return TestResult.unknownPort(0);
            }
            if (rootCause instanceof SocketTimeoutException || rootCause instanceof MongoTimeoutException) {
                return TestResult.hostUnreachable(rootCause.getLocalizedMessage());
            }
            return TestResult.unknownError(rootCause == null ? e : rootCause);
        }
    }
}
