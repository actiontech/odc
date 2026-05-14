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

import org.junit.Assert;
import org.junit.Test;

import com.oceanbase.odc.core.shared.constant.DialectType;

public class Db2ConnectionPluginTest {

    @Test
    public void getDialectType_returnsDB2() {
        Db2ConnectionPlugin plugin = new Db2ConnectionPlugin();
        Assert.assertEquals(DialectType.DB2, plugin.getDialectType());
    }

    /**
     * compat-RISK-8 / RISK-5：
     * <p>
     * 生产环境下 pf4j 会用独立的 PluginClassLoader 装载 connect-plugin-db2， 与主程 (odc-core) 的系统 ClassLoader 隔离；在本单测的
     * Maven 进程内，所有 类都由同一 AppClassLoader 装载，因此本用例只校验"plugin 类与 plugin-api / obmysql / odc-core 中的类来自相同
     * ClassLoader 链顶端不会抛 LinkageError" 这一弱不变量。一旦未来 jcc / driver 走独立类加载，须通过 pf4j 集成测试再验证。
     */
    @Test
    public void classloader_pluginClass_loadableWithoutLinkageError() {
        ClassLoader pluginCl = Db2ConnectionPlugin.class.getClassLoader();
        ClassLoader dialectCl = DialectType.class.getClassLoader();
        Assert.assertNotNull("Db2ConnectionPlugin should be loaded by some ClassLoader", pluginCl);
        Assert.assertNotNull("DialectType should be loaded by some ClassLoader", dialectCl);
        // 弱不变量：plugin 类的 ClassLoader 与 DialectType 共享同一 parent 链顶端
        Assert.assertSame(getRoot(pluginCl), getRoot(dialectCl));
    }

    /**
     * compat-RISK-8：禁止把 IBM JCC driver 类硬链接到本测试 classpath （JCC scope=provided；运行时由 plugin/<id>/lib/
     * 提供）。本用例验证 在没有 jcc jar 时 plugin 加载本身仍能成功（只要不触发 driver 加载）。
     */
    @Test
    public void pluginClass_loadable_evenWhenJccDriverMissing() {
        try {
            Class.forName("com.ibm.db2.jcc.DB2Driver");
            // 测试 classpath 上无 jcc 时进入 catch；如果有 jcc 则放过
        } catch (ClassNotFoundException expected) {
            // 期望命中：测试 classpath 上没有 jcc
            Assert.assertEquals("com.ibm.db2.jcc.DB2Driver", expected.getMessage());
        }
        // 不引用 driver 的情况下 plugin 类仍可成功 new
        Db2ConnectionPlugin plugin = new Db2ConnectionPlugin();
        Assert.assertNotNull(plugin);
        Assert.assertEquals(DialectType.DB2, plugin.getDialectType());
    }

    private static ClassLoader getRoot(ClassLoader cl) {
        ClassLoader cur = cl;
        while (cur != null && cur.getParent() != null) {
            cur = cur.getParent();
        }
        return cur;
    }
}
