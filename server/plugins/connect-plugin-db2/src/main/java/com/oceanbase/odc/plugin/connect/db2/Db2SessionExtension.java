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

import org.pf4j.Extension;

import com.oceanbase.odc.plugin.connect.model.DBClientInfo;
import com.oceanbase.odc.plugin.connect.obmysql.OBMySQLSessionExtension;

/**
 * DB2 Session Extension（T-002 骨架，蓝本 {@code PostgresSessionExtension}）。
 * <p>
 * T-002 仅提供骨架；DB2 的 {@code listAllSessions} / {@code killSession} 等动作 由 T-003 接入：
 * <ul>
 * <li>{@code SELECT APPLICATION_HANDLE, APPLICATION_NAME, SESSION_AUTH_ID, ... FROM SYSIBMADM.APPLICATIONS}</li>
 * <li>{@code CALL SYSPROC.ADMIN_CMD('FORCE APPLICATION (<handle>)')} 用于 kill。</li>
 * </ul>
 */
@Extension
public class Db2SessionExtension extends OBMySQLSessionExtension {

    /**
     * DB2 setClientInfo 在不同版本（10.5 / 11.1 / 11.5）行为不一致，MVP 阶段不打开 （compat-RISK-10：行为差异统一在 default 路径
     * return false）。
     */
    @Override
    public boolean setClientInfo(Connection connection, DBClientInfo clientInfo) {
        return false;
    }
}
