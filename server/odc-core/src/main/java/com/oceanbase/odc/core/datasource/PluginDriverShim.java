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

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Host-visible {@link Driver} shim that bridges a plugin-local JDBC driver into the global
 * {@link DriverManager} so it survives ODC's by-name driver lookup ({@code Class.forName}) and so
 * other parts of the JVM that enumerate {@code DriverManager.getDrivers()} from the host
 * classloader can see it.
 *
 * <p>
 * Why this class lives in {@code odc-core}: {@code DriverManager} segregates its registered driver
 * list by caller classloader. When a pf4j {@code PluginClassLoader} registers a {@code Driver}
 * implementation that is itself loaded by the plugin classloader, ODC's host classloader does NOT
 * see it on subsequent {@code getDrivers()} calls. Putting the shim class itself in
 * {@code odc-core} (host-visible) lets {@code DriverManager} accept it for the host loader. The
 * delegated driver instance still lives in the plugin classloader; the shim simply forwards every
 * {@link Driver} call to it.
 *
 * <p>
 * Standard pattern - used by NiFi, Apache Druid, and other pf4j + JDBC ecosystems. Implements
 * {@link DelegatedDriver} so {@link BaseClassBasedDataSource} can match it back by the original
 * driver class name when the host loader cannot {@code Class.forName} the plugin driver directly.
 *
 * @since ODC_release_4.3.4
 */
public final class PluginDriverShim implements Driver, DelegatedDriver {

    private final Driver delegate;
    private final String delegateClassName;

    /**
     * @param delegate the real {@link Driver} instance, loaded by the plugin classloader
     * @param delegateClassName the fully-qualified class name of the underlying driver; callers must
     *        supply this explicitly because {@code delegate.getClass().getName()} reaches the plugin
     *        classloader's name, which is what we want anyway, but recording it as a separate field
     *        avoids invoking the plugin classloader during a host-only match.
     */
    public PluginDriverShim(Driver delegate, String delegateClassName) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        if (delegateClassName == null || delegateClassName.isEmpty()) {
            throw new IllegalArgumentException("delegateClassName must not be empty");
        }
        this.delegate = delegate;
        this.delegateClassName = delegateClassName;
    }

    @Override
    public String getDelegateClassName() {
        return delegateClassName;
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
