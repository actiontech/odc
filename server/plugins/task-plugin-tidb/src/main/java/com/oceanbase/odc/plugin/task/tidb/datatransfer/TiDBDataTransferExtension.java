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
package com.oceanbase.odc.plugin.task.tidb.datatransfer;

import java.io.File;
import java.net.URL;
import java.util.List;

import org.pf4j.Extension;

import com.oceanbase.odc.plugin.task.api.datatransfer.DataTransferJob;
import com.oceanbase.odc.plugin.task.api.datatransfer.model.DataTransferConfig;
import com.oceanbase.odc.plugin.task.mysql.datatransfer.MySQLDataTransferExtension;
import com.oceanbase.odc.plugin.task.mysql.datatransfer.MySQLDataTransferJob;

import lombok.NonNull;

@Extension
public class TiDBDataTransferExtension extends MySQLDataTransferExtension {
    @Override
    public DataTransferJob generate(@NonNull DataTransferConfig config, @NonNull File workingDir,
            @NonNull File logDir, @NonNull List<URL> inputs) throws Exception {
        return new MySQLDataTransferJob(config, workingDir, logDir, inputs);
    }
}
