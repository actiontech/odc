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
import java.util.Enumeration;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Use classname for database-driven data source loading. Corresponding to this, heterogeneous
 * driver loading can also be implemented in the form of a {@link ClassLoader}, but it has not yet
 * been implemented.
 *
 * @author yh263208
 * @date 2021-11-10 15:45
 * @since ODC_release_3.2.2
 * @see BaseDriverBasedDataSource
 */
@Slf4j
public abstract class BaseClassBasedDataSource extends BaseDriverBasedDataSource {
    /**
     * Default class name
     */
    public static final String DEFAULT_DRIVER_CLASS_NAME = "com.oceanbase.jdbc.Driver";
    /**
     * The default driver class name is the driver of oceanbase
     */
    private String driverClassName = DEFAULT_DRIVER_CLASS_NAME;

    public void setDriverClassName(@NonNull String driverClassName) {
        try {
            Class.forName(driverClassName);
            this.driverClassName = driverClassName;
            return;
        } catch (ClassNotFoundException notVisibleFromHost) {
            // Plugin JDBC drivers (e.g. ODC pf4j connect-plugin-hive) live inside a pf4j
            // PluginClassLoader and are invisible to host classloaders. If such a plugin has
            // already pushed a host-visible shim into DriverManager via its start() hook, we
            // accept the class name and defer driver lookup to getDriver(). This keeps existing
            // host-classloader drivers (oceanbase / mysql / postgres / ...) on the fast path
            // (Class.forName succeeds the first time, no fallback) and only relaxes the check
            // when the driver class genuinely lives in a plugin classloader.
            Driver shim = findRegisteredDriverFor(driverClassName);
            if (shim != null) {
                log.debug("setDriverClassName: host loader cannot see [{}], using registered shim {}",
                        driverClassName, shim.getClass().getName());
                this.driverClassName = driverClassName;
                return;
            }
            throw new IllegalArgumentException(notVisibleFromHost);
        }
    }

    @Override
    protected Driver getDriver() throws ClassNotFoundException {
        try {
            Class<?> clazz = Class.forName(this.driverClassName);
            return (Driver) clazz.newInstance();
        } catch (ClassNotFoundException notVisibleFromHost) {
            // See setDriverClassName for the rationale. The shim itself, registered by the
            // plugin start() hook, runs through DriverManager and delegates to the real driver
            // sitting inside the PluginClassLoader.
            Driver fromRegistry = findRegisteredDriverFor(this.driverClassName);
            if (fromRegistry != null) {
                log.debug("getDriver: host loader cannot see [{}], delegating to registered shim {}",
                        this.driverClassName, fromRegistry.getClass().getName());
                return fromRegistry;
            }
            throw notVisibleFromHost;
        } catch (Exception e) {
            throw new InternalError(e);
        }
    }

    /**
     * Locate a {@link Driver} that was previously registered with {@link DriverManager} and proxies the
     * requested driver class. The match accepts either an exact class name (uncommon, since the class
     * is by definition not visible to the host loader in this fallback path) or a shim that declares an
     * explicit {@link DelegatedDriver delegate target}.
     *
     * <p>
     * Returns {@code null} when no registered driver matches. Callers must treat that as the
     * authoritative "driver not available" signal and surface the original
     * {@link ClassNotFoundException}.
     */
    private static Driver findRegisteredDriverFor(String driverClassName) {
        if (driverClassName == null) {
            return null;
        }
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            if (driverClassName.equals(driver.getClass().getName())) {
                return driver;
            }
            if (driver instanceof DelegatedDriver
                    && driverClassName.equals(((DelegatedDriver) driver).getDelegateClassName())) {
                return driver;
            }
        }
        return null;
    }

}
