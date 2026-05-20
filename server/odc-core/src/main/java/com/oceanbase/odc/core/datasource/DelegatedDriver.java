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
package com.oceanbase.odc.core.datasource;

import java.sql.Driver;
import java.sql.DriverManager;

/**
 * Marker contract for a {@link Driver} shim that registers itself with the global
 * {@link DriverManager} on behalf of another driver that lives in a non-host classloader (typically
 * a pf4j {@code PluginClassLoader}).
 *
 * <p>
 * ODC builds its {@code DataSource} instances by name via {@code Class.forName(driverClassName)},
 * which uses the calling class's classloader. Drivers shipped inside a plugin jar are invisible to
 * that loader. The convention is:
 *
 * <ol>
 * <li>The plugin's {@code start()} hook instantiates the real driver through the plugin classloader
 * and wraps it in a shim that implements {@code Driver} and {@code DelegatedDriver}.</li>
 * <li>The shim is registered with {@link DriverManager#registerDriver(Driver)} from within the
 * plugin, so the resulting {@link Driver} object lives in the host-visible registry.</li>
 * <li>When {@code BaseClassBasedDataSource} fails to {@code Class.forName(driverClassName)}, it
 * falls back to scanning {@link DriverManager} for a registered driver whose
 * {@link #getDelegateClassName()} matches the requested class name, and returns that shim instead
 * of failing.</li>
 * </ol>
 *
 * <p>
 * Implementations must be thread-safe and idempotent on {@link #getDelegateClassName()}.
 */
public interface DelegatedDriver {

    /**
     * The fully-qualified class name of the real JDBC driver this shim delegates to. Must equal the
     * value the application passes to {@link BaseClassBasedDataSource#setDriverClassName(String)}.
     */
    String getDelegateClassName();
}
