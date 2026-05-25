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
package com.oceanbase.odc.plugin.connect.mongodb.bridge;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Collections;

import org.bson.Document;
import org.junit.Assert;
import org.junit.Test;

public class MongoBridgeUtilTest {

    @Test
    public void toResultSet_exposesColumnTypeNameForFrontend() throws Exception {
        MongoTabularResult tabularResult = new MongoResultMapper().mapDocuments(
                Collections.singletonList(new Document("_id", 1).append("name", "alpha")));
        ResultSet resultSet = MongoBridgeUtil.toResultSet(tabularResult);

        Assert.assertTrue(resultSet.next());
        Assert.assertEquals(Types.VARCHAR, resultSet.getMetaData().getColumnType(1));
        Assert.assertEquals("VARCHAR", resultSet.getMetaData().getColumnTypeName(1));
    }

    @Test
    public void connectionProxy_supportsObjectMethods() throws Exception {
        MongoSessionContext context = new MongoSessionContext(null, "appdb", "test");
        Connection connection = MongoBridgeUtil.newConnection(context);

        Assert.assertNotEquals(0, connection.hashCode());
        Assert.assertTrue(connection.equals(connection));
        Assert.assertFalse(connection.equals(new Object()));
        Assert.assertTrue(connection.toString().contains("MongoJdbcConnection"));

        Statement statement = connection.createStatement();
        Assert.assertNotEquals(0, statement.hashCode());
        Assert.assertTrue(statement.equals(statement));
        Assert.assertFalse(statement.equals(new Object()));
        Assert.assertTrue(statement.toString().contains("MongoJdbcStatement"));
    }

    @Test
    public void requireContext_supportsNestedConnectionWrappers() throws Exception {
        MongoSessionContext context = new MongoSessionContext(null, "appdb", "test");
        Connection connection = MongoBridgeUtil.newConnection(context);
        Connection wrapped = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[] {Connection.class}, new NestedConnectionHandler(connection));

        Assert.assertSame(context, MongoBridgeUtil.requireContext(wrapped));
    }

    private static class NestedConnectionHandler implements InvocationHandler {
        private final Connection delegate;

        private NestedConnectionHandler(Connection delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if ("unwrap".equals(name)) {
                if (((Class<?>) args[0]).isInstance(delegate)) {
                    return delegate;
                }
                throw new SQLException("Unsupported unwrap target");
            }
            if ("isWrapperFor".equals(name)) {
                return ((Class<?>) args[0]).isInstance(delegate);
            }
            if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(name)) {
                return proxy == args[0];
            }
            if ("toString".equals(name)) {
                return "NestedConnectionHandler";
            }
            return method.invoke(delegate, args);
        }
    }
}
