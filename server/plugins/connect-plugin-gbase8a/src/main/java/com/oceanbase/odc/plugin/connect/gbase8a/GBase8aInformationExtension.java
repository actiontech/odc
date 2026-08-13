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
package com.oceanbase.odc.plugin.connect.gbase8a;

import java.sql.Connection;

import org.pf4j.Extension;

import com.oceanbase.odc.common.util.JdbcOperationsUtil;
import com.oceanbase.odc.core.shared.constant.ErrorCodes;
import com.oceanbase.odc.core.shared.exception.BadRequestException;
import com.oceanbase.odc.plugin.connect.api.InformationExtensionPoint;

import lombok.extern.slf4j.Slf4j;

/**
 * Information extension for GBase-8a.
 */
@Slf4j
@Extension
public class GBase8aInformationExtension implements InformationExtensionPoint {

    @Override
    public String getDBVersion(Connection connection) {
        try {
            String version = JdbcOperationsUtil.getJdbcOperations(connection)
                    .queryForObject("SELECT VERSION()", String.class);
            if (version == null) {
                throw new BadRequestException(ErrorCodes.QueryDBVersionFailed,
                        new Object[] {"Result set is empty"}, "Result set is empty");
            }
            return version.contains("-") ? version.split("-")[0] : version;
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException(ErrorCodes.QueryDBVersionFailed,
                    new Object[] {e.getMessage()}, e.getMessage());
        }
    }
}
