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

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link BaseClassBasedDataSource}, specifically the host-classloader -> DriverManager
 * fallback path that lets pf4j plugin drivers participate in ODC's by-name DataSource construction.
 *
 * <p>
 * Background: a JDBC driver shipped inside a pf4j plugin jar (e.g. Apache Hive's
 * {@code org.apache.hive.jdbc.HiveDriver}) is invisible to the host classloader, so
 * {@code Class.forName(driverClassName)} fails. The fallback walks {@link DriverManager} for a
 * registered {@link Driver} that either matches by class name or implements {@link DelegatedDriver}
 * and advertises the requested class name through {@link DelegatedDriver#getDelegateClassName()}.
 *
 * <p>
 * Tests use {@link SingleConnectionDataSource} as the concrete subclass (it is already
 * test-friendly and the only setter/getter surface we need is inherited from
 * {@link BaseClassBasedDataSource}). They never actually open a JDBC connection.
 */
public class BaseClassBasedDataSourceTest {

    private static final String FAKE_PLUGIN_DRIVER_CLASS_NAME = "org.example.NotOnHostClassPathDriver";

    private TestDelegatingShim registeredShim;

    @Before
    public void setUp() {
        registeredShim = null;
    }

    @After
    public void tearDown() throws SQLException {
        if (registeredShim != null) {
            DriverManager.deregisterDriver(registeredShim);
            registeredShim = null;
        }
    }

    @Test
    public void setDriverClassName_visibleDriver_acceptedOnFastPath() {
        // oceanbase JDBC driver is on the host classpath via odc-core test deps; verify the
        // happy path still works without touching DriverManager fallback.
        SingleConnectionDataSource ds = new SingleConnectionDataSource();
        ds.setDriverClassName(BaseClassBasedDataSource.DEFAULT_DRIVER_CLASS_NAME);
        // No exception is the assertion.
    }

    @Test(expected = IllegalArgumentException.class)
    public void setDriverClassName_invisibleDriverNoShim_throws() {
        // Plugin driver not visible to host AND no shim registered -> IllegalArgumentException.
        new SingleConnectionDataSource().setDriverClassName(FAKE_PLUGIN_DRIVER_CLASS_NAME);
    }

    @Test
    public void setDriverClassName_invisibleDriverWithDelegatingShim_acceptsName() throws Exception {
        // Simulate the pf4j plugin start() hook: register a shim implementing DelegatedDriver
        // that advertises the plugin's driver class name.
        registeredShim = new TestDelegatingShim(FAKE_PLUGIN_DRIVER_CLASS_NAME);
        DriverManager.registerDriver(registeredShim);

        SingleConnectionDataSource ds = new SingleConnectionDataSource();
        ds.setDriverClassName(FAKE_PLUGIN_DRIVER_CLASS_NAME);

        // getDriver() must surface the shim, not throw ClassNotFoundException.
        Driver actual = invokeGetDriver(ds);
        Assert.assertSame(registeredShim, actual);
    }

    @Test
    public void getDriver_visibleDriver_returnsInstantiatedDriver() throws Exception {
        SingleConnectionDataSource ds = new SingleConnectionDataSource();
        ds.setDriverClassName(BaseClassBasedDataSource.DEFAULT_DRIVER_CLASS_NAME);
        Driver driver = invokeGetDriver(ds);
        Assert.assertNotNull(driver);
        Assert.assertEquals(BaseClassBasedDataSource.DEFAULT_DRIVER_CLASS_NAME, driver.getClass().getName());
    }

    @Test
    public void getDriver_invisibleDriverNoShim_throwsClassNotFound() throws Exception {
        // Bypass setDriverClassName so we can drive the getDriver fallback alone.
        SingleConnectionDataSource ds = new SingleConnectionDataSource();
        forceDriverClassName(ds, FAKE_PLUGIN_DRIVER_CLASS_NAME);
        try {
            invokeGetDriver(ds);
            Assert.fail("Expected ClassNotFoundException to propagate from getDriver");
        } catch (java.lang.reflect.InvocationTargetException ite) {
            Assert.assertTrue("Cause should be ClassNotFoundException, was " + ite.getCause(),
                    ite.getCause() instanceof ClassNotFoundException);
        }
    }

    @Test
    public void delegatingShim_classNameMismatch_doesNotMatch() throws SQLException {
        registeredShim = new TestDelegatingShim("org.other.NotMyDriver");
        DriverManager.registerDriver(registeredShim);

        try {
            new SingleConnectionDataSource().setDriverClassName(FAKE_PLUGIN_DRIVER_CLASS_NAME);
            Assert.fail("Expected IllegalArgumentException - shim advertises a different driver class name");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    private static Driver invokeGetDriver(BaseClassBasedDataSource ds) throws Exception {
        Method m = BaseClassBasedDataSource.class.getDeclaredMethod("getDriver");
        m.setAccessible(true);
        return (Driver) m.invoke(ds);
    }

    private static void forceDriverClassName(BaseClassBasedDataSource ds, String name) {
        try {
            java.lang.reflect.Field f = BaseClassBasedDataSource.class.getDeclaredField("driverClassName");
            f.setAccessible(true);
            f.set(ds, name);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Minimal Driver + DelegatedDriver implementation used only to test the BaseClassBasedDataSource
     * fallback. Connect()/etc throw because they should never be invoked by these tests.
     */
    private static class TestDelegatingShim implements Driver, DelegatedDriver {
        private final String delegateClassName;

        TestDelegatingShim(String delegateClassName) {
            this.delegateClassName = delegateClassName;
        }

        @Override
        public String getDelegateClassName() {
            return delegateClassName;
        }

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            throw new SQLException("not implemented in test shim");
        }

        @Override
        public boolean acceptsURL(String url) {
            return false;
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 0;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return Logger.getLogger("test-shim");
        }
    }
}
