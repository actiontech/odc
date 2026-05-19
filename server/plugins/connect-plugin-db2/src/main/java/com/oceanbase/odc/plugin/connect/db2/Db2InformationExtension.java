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

import java.sql.Connection;
import java.sql.SQLException;

import org.pf4j.Extension;

import com.oceanbase.odc.core.shared.constant.ErrorCodes;
import com.oceanbase.odc.core.shared.exception.BadRequestException;
import com.oceanbase.odc.plugin.connect.api.InformationExtensionPoint;

import lombok.extern.slf4j.Slf4j;

/**
 * DB2 information extension. Returns the database product version via JDBC metadata (design.md
 * §2.3). For finer fixpack-level info we would query
 * {@code SELECT SERVICE_LEVEL FROM SYSIBMADM.ENV_INST_INFO}; that is out of scope for this
 * iteration.
 *
 * @author actiontech-zihan
 * @since 4.3.4
 */
@Slf4j
@Extension
public class Db2InformationExtension implements InformationExtensionPoint {

    @Override
    public String getDBVersion(Connection connection) {
        try {
            return connection.getMetaData().getDatabaseProductVersion();
        } catch (SQLException e) {
            log.warn("DB2 getDBVersion failed: {}", e.getMessage());
            throw new BadRequestException(ErrorCodes.QueryDBVersionFailed,
                    new Object[] {e.getMessage()}, e.getMessage());
        }
    }

}
