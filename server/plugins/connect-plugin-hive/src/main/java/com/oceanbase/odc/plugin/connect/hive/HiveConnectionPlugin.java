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
package com.oceanbase.odc.plugin.connect.hive;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Enumeration;

import com.oceanbase.odc.core.datasource.PluginDriverShim;
import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.odc.plugin.connect.api.BaseConnectionPlugin;

import lombok.extern.slf4j.Slf4j;

/**
 * pf4j plugin entry for Hive datasource. Returns {@link DialectType#HIVE} so that ODC plugin
 * manager binds all Hive extensions in this jar to the HIVE dialect.
 *
 * <p>
 * On {@link #start()} the plugin loads {@code org.apache.hive.jdbc.HiveDriver} through the plugin
 * classloader and registers a host-visible {@link PluginDriverShim} into the global
 * {@link DriverManager}. {@link PluginDriverShim} itself lives in {@code odc-core}, i.e. the host
 * classloader, which is critical: {@link DriverManager} segregates its registered driver list by
 * caller classloader, and a {@code Driver} whose own class was loaded by the pf4j
 * {@code PluginClassLoader} is invisible to the host classloader on subsequent {@code getDrivers()}
 * calls. Wrapping the plugin-local driver in a host-visible shim lets ODC's sync / data source
 * factories see the registration. See {@link PluginDriverShim} for the long-form explanation.
 *
 * <p>
 * On {@link #stop()} every registered {@link PluginDriverShim} whose delegate is the Hive driver is
 * removed from {@link DriverManager} to avoid leaking driver references on plugin hot-reload.
 *
 * @since ODC_release_4.3.4
 */
@Slf4j
public class HiveConnectionPlugin extends BaseConnectionPlugin {

    private static final String HIVE_DRIVER_CLASS_NAME = "org.apache.hive.jdbc.HiveDriver";

    @Override
    public DialectType getDialectType() {
        return DialectType.HIVE;
    }

    @Override
    public void start() {
        super.start();
        try {
            // The plugin class itself is loaded by the pf4j PluginClassLoader, so its own
            // class loader is exactly the plugin classloader that owns HiveDriver and the
            // META-INF/services/java.sql.Driver SPI. Using getWrapper().getPluginClassLoader()
            // would be equivalent but pf4j only injects the wrapper when the plugin declares a
            // (PluginWrapper) constructor; here we rely on this.getClass().getClassLoader()
            // to keep the no-arg constructor path safe.
            ClassLoader pluginClassLoader = this.getClass().getClassLoader();
            Driver hiveDriver = (Driver) Class.forName(HIVE_DRIVER_CLASS_NAME, true, pluginClassLoader)
                    .getDeclaredConstructor().newInstance();
            PluginDriverShim shim = new PluginDriverShim(hiveDriver, HIVE_DRIVER_CLASS_NAME);
            DriverManager.registerDriver(shim);
            log.info("PluginDriverShim registered to DriverManager for Hive, delegate={}, shim={}",
                    hiveDriver.getClass().getName(), shim);
        } catch (Exception e) {
            // Do not throw - keeping the plugin "started" allows the rest of ODC to boot, and
            // any later JDBC call will surface its own clear error. Throwing here would abort
            // plugin startup and hide the root cause behind an opaque pf4j load failure.
            log.error("Failed to register Hive PluginDriverShim to DriverManager", e);
        }
    }

    @Override
    public void stop() {
        try {
            Enumeration<Driver> drivers = DriverManager.getDrivers();
            while (drivers.hasMoreElements()) {
                Driver driver = drivers.nextElement();
                if (driver instanceof PluginDriverShim
                        && HIVE_DRIVER_CLASS_NAME.equals(((PluginDriverShim) driver).getDelegateClassName())) {
                    try {
                        DriverManager.deregisterDriver(driver);
                        log.info("PluginDriverShim for Hive deregistered from DriverManager, shim={}", driver);
                    } catch (SQLException ex) {
                        log.warn("Failed to deregister PluginDriverShim for Hive from DriverManager, shim={}", driver,
                                ex);
                    }
                }
            }
        } finally {
            super.stop();
        }
    }
}
