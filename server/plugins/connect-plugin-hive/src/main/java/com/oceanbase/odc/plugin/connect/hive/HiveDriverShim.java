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

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

import com.oceanbase.odc.core.datasource.DelegatedDriver;

/**
 * DriverShim bridges the Apache Hive JDBC {@link Driver} from a pf4j {@code PluginClassLoader} to
 * the host {@link java.sql.DriverManager}.
 *
 * <p>
 * Background: the JDK initializes {@code DriverManager} once at {@code <clinit>} time via
 * {@code ServiceLoader.load(Driver.class, ClassLoader.getSystemClassLoader())}, using the system
 * classloader. Drivers that live inside a pf4j plugin jar are invisible to that scan (the pf4j
 * {@code PluginClassLoader} is child-first and is not a parent of the system classloader).
 * Subsequent {@code DriverManager.getConnection(url, ...)} calls iterate the already-populated
 * registry and never re-scan plugin classloaders, so the plugin's Hive driver is never matched,
 * causing {@code "No suitable driver found for jdbc:hive2://..."}.
 *
 * <p>
 * On top of that, ODC's own {@code BaseClassBasedDataSource.setDriverClassName(String)} performs a
 * {@code Class.forName(driverClassName)} from the host classloader to validate the configured
 * driver name. That call also fails with
 * {@code ClassNotFoundException: org.apache.hive.jdbc.HiveDriver} before
 * {@code DriverManager.getConnection(...)} is ever reached.
 *
 * <p>
 * The standard fix - used by NiFi, Druid and other pf4j + JDBC ecosystems - is to instantiate the
 * plugin-local driver, wrap it in a shim that lives in the host-visible classloader graph, and call
 * {@link java.sql.DriverManager#registerDriver(Driver)} from the plugin's {@code start()} hook. The
 * shim simply delegates every call to the underlying driver, and implements {@link DelegatedDriver}
 * so {@code BaseClassBasedDataSource} can resolve it back from {@link java.sql.DriverManager} when
 * the host classloader cannot see the real driver class.
 *
 * @since ODC_release_4.3.4
 */
public class HiveDriverShim implements Driver, DelegatedDriver {

    /** Class name of the JDBC driver this shim proxies. */
    public static final String HIVE_DRIVER_CLASS_NAME = "org.apache.hive.jdbc.HiveDriver";

    private final Driver delegate;

    public HiveDriverShim(Driver delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getDelegateClassName() {
        return HIVE_DRIVER_CLASS_NAME;
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        return delegate.connect(url, info);
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return delegate.acceptsURL(url);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return delegate.getPropertyInfo(url, info);
    }

    @Override
    public int getMajorVersion() {
        return delegate.getMajorVersion();
    }

    @Override
    public int getMinorVersion() {
        return delegate.getMinorVersion();
    }

    @Override
    public boolean jdbcCompliant() {
        return delegate.jdbcCompliant();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return delegate.getParentLogger();
    }
}
