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
package com.oceanbase.odc.plugin.connect.redis;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;

import org.pf4j.Extension;

import com.oceanbase.odc.plugin.connect.api.InformationExtensionPoint;

@Extension
public class RedisInformationExtension implements InformationExtensionPoint {
    @Override
    public String getDBVersion(Connection connection) {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("INFO server")) {
            while (resultSet.next()) {
                ResultSetMetaData metaData = resultSet.getMetaData();
                if (metaData.getColumnCount() == 1) {
                    String version = parseVersion(resultSet.getString(1));
                    if (version != null) {
                        return version;
                    }
                    continue;
                }
                String section = resultSet.getString(1);
                String key = resultSet.getString(2);
                String value = resultSet.getString(3);
                if ("server".equalsIgnoreCase(section) && "redis_version".equalsIgnoreCase(key)) {
                    return value;
                }
            }
        } catch (Exception ignored) {
        }
        return "0.0.0";
    }

    private String parseVersion(String info) {
        if (info == null) {
            return null;
        }
        for (String line : info.split("\\r?\\n")) {
            if (line.startsWith("redis_version:")) {
                return line.substring("redis_version:".length()).trim();
            }
        }
        return null;
    }
}
