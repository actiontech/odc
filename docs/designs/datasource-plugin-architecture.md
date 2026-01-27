# 数据源插件扩展架构

```mermaid
graph TB
    subgraph "Plugin Discovery"
        A[PluginService<br/>启动时加载插件]
        B[PF4J PluginManager<br/>插件管理器]
        C[PluginFinder<br/>插件查找器]
    end
    
    subgraph "Extension Points"
        D[ConnectionExtensionPoint<br/>连接扩展点]
        E[SessionExtensionPoint<br/>会话扩展点]
        F[SchemaExtensionPoint<br/>Schema扩展点]
    end
    
    subgraph "Plugin Implementation"
        G[SqlServerConnectionExtension<br/>generateJdbcUrl<br/>getDriverClassName<br/>test]
        H[SqlServerSessionExtension<br/>switchSchema<br/>getCurrentSchema<br/>getConnectionId]
        I[SqlServerSchemaExtension<br/>list/getDetail]
    end
    
    subgraph "Plugin Usage"
        J[ConnectionPluginUtil<br/>getConnectionExtension<br/>getSessionExtension]
        K[ConnectionSessionFactory<br/>创建会话]
        L[OBConsoleDataSourceFactory<br/>创建数据源]
    end
    
    A --> B
    B --> C
    C --> D
    C --> E
    C --> F
    D --> G
    E --> H
    F --> I
    J --> D
    J --> E
    K --> J
    L --> J
```

## 扩展方法

1. **实现 ConnectionExtensionPoint**
   - generateJdbcUrl: 生成 JDBC URL
   - getDriverClassName: 返回驱动类名
   - test: 测试连接
   - getConnectionInitializers: 连接初始化器

2. **实现 SessionExtensionPoint**
   - switchSchema: 切换Schema
   - getCurrentSchema: 获取当前Schema
   - getConnectionId: 获取连接ID
   - getVariable: 获取变量值

3. **注册插件**
   - 使用 @Extension 注解
   - 在 plugin.properties 中声明 DialectType
   - 插件JAR放入 distribution/plugins 目录
