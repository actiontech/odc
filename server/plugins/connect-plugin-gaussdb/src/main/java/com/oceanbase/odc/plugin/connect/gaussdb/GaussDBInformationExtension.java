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
package com.oceanbase.odc.plugin.connect.gaussdb;

import java.sql.Connection;

import org.pf4j.Extension;

import com.oceanbase.odc.common.util.JdbcOperationsUtil;
import com.oceanbase.odc.core.shared.constant.ErrorCodes;
import com.oceanbase.odc.core.shared.exception.BadRequestException;
import com.oceanbase.odc.plugin.connect.api.InformationExtensionPoint;

/**
 * Information extension for GaussDB / openGauss.
 * <p>
 * Both products speak the PostgreSQL wire protocol and expose
 * {@code current_setting('server_version')} which returns a pure numeric version string such as
 * {@code "9.2.4"} on both GaussDB commercial (`122.9.71.90:8000`) and openGauss
 * (`10.186.16.126:5432`) clusters. The pure numeric form is required by
 * {@code com.oceanbase.odc.common.util.VersionUtils#compareVersions} which is invoked from
 * {@code VersionDiffConfigService#getSupportFeatures} / {@code #getDatatypeList} during
 * {@code ConnectSessionController#createSessionByDatabase}.
 * <p>
 * The historical {@code SELECT version()} returned a human readable banner like
 * {@code "gaussdb (GaussDB Kernel 505.2.1.SPC0800 ...)"} or
 * {@code "(openGauss 6.0.0 build aee4abd5) ..."}; when VersionUtils tried to
 * {@code Integer.parseInt} the leading {@code "gaussdb (GaussDB Kernel 505"} fragment it threw
 * {@code NumberFormatException} and made the entire {@code POST
 * /api/v2/datasource/databases/{id}/sessions} call return 400, blocking the workbench tables view
 * (see fix report task_004_fix_2_*.md). The matching
 * {@link com.oceanbase.odc.plugin.connect.postgres.PostgresInformationExtension} on PostgreSQL
 * already uses this approach.
 */
@Extension
public class GaussDBInformationExtension implements InformationExtensionPoint {
    @Override
    public String getDBVersion(Connection connection) {
        String querySql = "SELECT current_setting('server_version');";
        try {
            return JdbcOperationsUtil.getJdbcOperations(connection).queryForObject(querySql, String.class);
        } catch (Exception e) {
            throw new BadRequestException(ErrorCodes.QueryDBVersionFailed,
                    new Object[] {"Result set is empty"}, "Result set is empty");
        }
    }
}
