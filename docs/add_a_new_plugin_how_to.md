# 如何新增一个数据库插件 (以 SQLServer 为例)

本文档旨在指导开发者如何在 ODC 中新增一个数据库插件，并以 SQLServer 的实现作为参考。

## 1. 架构概述

ODC 的数据库适配遵循“逻辑下沉、接口隔离”原则，主要分为以下几个层次：

- **`libs/db-browser` (基础适配层)**：提供数据库方言的原子操作，如元数据查询（Schema）、SQL 生成（Editor）、SQL 模板（Template）等。
- **`server/plugins` (插件扩展层)**：基于 PF4J 机制，将 `db-browser` 的能力封装为 ODC Server 可用的扩展点（Extension Point）。
- **`server/odc-core` (核心逻辑层)**：处理 SQL 拆分（SqlSplitter）等跨模块的基础能力。

---

## 2. 基础适配层：`libs/db-browser`

在该层中，我们需要实现 SQLServer 的方言适配逻辑。

### 2.1 目录结构与文件说明

```text
libs/db-browser/src/main/java/com/oceanbase/tools/dbbrowser/
├── schema/sqlserver/
│   ├── SqlServerSchemaAccessor.java
│   └── SqlServerSchemaUtil.java
├── editor/sqlserver/
│   ├── SqlServerColumnEditor.java
│   ├── SqlServerConstraintEditor.java
│   ├── SqlServerIndexEditor.java
│   ├── SqlServerMViewEditor.java
│   ├── SqlServerObjectOperator.java
│   ├── SqlServerPartitionEditor.java
│   ├── SqlServerSequenceEditor.java
│   ├── SqlServerSynonymEditor.java
│   ├── SqlServerTableEditor.java
│   ├── SqlServerTriggerEditor.java
│   └── SqlServerViewEditor.java
├── template/sqlserver/
│   ├── SqlServerFunctionTemplate.java
│   ├── SqlServerProcedureTemplate.java
│   ├── SqlServerTriggerTemplate.java
│   └── SqlServerViewTemplate.java
└── util/
    ├── SqlServerSqlBuilder.java
    └── StringUtils.java (工具类增强)
```

| 文件名 | 作用说明 |
| :--- | :--- |
| `SqlServerSchemaAccessor.java` | 实现 `DBSchemaAccessor` 接口，通过系统视图查询表、列、索引、约束等元数据。 |
| `SqlServerSchemaUtil.java` | 封装 SQLServer 特有的元数据处理工具方法。 |
| `SqlServerTableEditor.java` | 实现 `DBTableEditor`，负责生成表的 `CREATE`, `ALTER`, `DROP`, `RENAME` 等 DDL。 |
| `SqlServerColumnEditor.java` | 负责生成列相关的 DDL 语句。 |
| `SqlServerIndexEditor.java` | 负责生成索引相关的 DDL 语句。 |
| `SqlServerConstraintEditor.java` | 负责生成约束（主键、外键、唯一键、Check）相关的 DDL 语句。 |
| `SqlServerObjectOperator.java` | 实现 `DBObjectOperator`，执行通用的数据库对象操作（如删除、统计等）。 |
| `SqlServerFunctionTemplate.java` | 提供新建函数时的初始 SQL 模板。 |
| `SqlServerSqlBuilder.java` | 继承 `SqlBuilder`，处理 SQLServer 特有的标识符引用（`[]`）和值转义。 |
| `StringUtils.java` | 增加 `quoteSqlServerIdentifier` 和 `quoteSqlServerValue` 等工具方法。 |

### 2.2 核心逻辑实现

- **元数据访问**: SQLServer 支持 `database.schema` 结构。为了兼容 ODC 的单层 Schema 模型，我们将 `database.schema` 组合作为一个统一的 `schemaName` 返回。
- **SQL 生成**: 使用 `SqlServerSqlBuilder` 确保生成的 SQL 符合 SQLServer 语法规范。

---

## 3. 业务扩展层：`server/plugins`

该层负责将 `db-browser` 的能力注册到 ODC 插件系统中。

### 3.1 连接插件 (Connect Plugin)

**目录结构**: `server/plugins/connect-plugin-sqlserver/src/main/java/com/oceanbase/odc/plugin/connect/sqlserver/`

| 文件名 | 作用说明 |
| :--- | :--- |
| `SqlServerConnectionPlugin.java` | 插件入口类，定义插件 ID 和扩展点。 |
| `SqlServerConnectionExtension.java` | 实现 `ConnectionExtensionPoint`，处理驱动加载、连接测试。 |
| `SqlServerJdbcUrlParser.java` | 实现 `JdbcUrlParser`，解析 SQLServer JDBC URL 属性。 |
| `SqlServerSessionExtension.java` | 实现 `SessionExtensionPoint`，处理会话初始化、Schema 切换（`USE`）、获取连接 ID（`@@SPID`）。 |
| `SqlServerInformationExtension.java` | 提供数据库版本、特性支持等信息。 |
| `SqlServerDiagnoseExtensionPoint.java` | 实现 SQL 诊断、执行计划获取等功能。 |

### 3.2 Schema 插件 (Schema Plugin)

**目录结构**: `server/plugins/schema-plugin-sqlserver/src/main/java/com/oceanbase/odc/plugin/schema/sqlserver/`

| 文件名 | 作用说明 |
| :--- | :--- |
| `SqlServerSchemaPlugin.java` | 实现 `BaseSchemaPlugin`，注册 SQLServer 的 Schema 访问能力。 |
| `SqlServerTableExtension.java` | 将 `db-browser` 的 `SqlServerSchemaAccessor` 桥接到 ODC 的表管理接口。 |
| `SqlServerDatabaseExtension.java` | 处理数据库/Schema 列表的获取。 |
| `SqlServerViewExtension.java` | 处理视图元数据的获取。 |
| `SqlServerFunctionExtension.java` | 处理函数元数据的获取。 |

---

## 4. 核心逻辑增强：`server/odc-core`

### 4.1 SQL 拆分器 (SqlSplitter)

**路径**: `server/odc-core/src/main/java/com/oceanbase/odc/core/sql/split/SqlServerSqlSplitter.java`

| 文件名 | 作用说明 |
| :--- | :--- |
| `SqlServerSqlSplitter.java` | 实现 SQLServer 脚本拆分逻辑，支持 `GO` 分隔符，识别 `BEGIN...END` 块以避免错误切分。 |

---

## 5. 全局集成与注册

除了实现插件本身的逻辑外，还需要在 ODC 核心模块中进行注册，使系统能够识别并调用新插件。

### 5.1 `libs/db-browser` 工厂注册

| 工厂类 | 注册操作 |
| :--- | :--- |
| `AbstractDBBrowserFactory.java` | 在 `create()` 方法的 switch 中增加 `SQL_SERVER` 分支，并添加 `buildForSqlServer()` 抽象方法。 |
| `DBSchemaAccessorFactory.java` | 实现 `buildForSqlServer()`，返回 `SqlServerSchemaAccessor` 实例。 |
| `DBTableEditorFactory.java` | 实现 `buildForSqlServer()`，返回 `SqlServerTableEditor` 实例。 |
| `DBObjectOperatorFactory.java` | 实现 `buildForSqlServer()`，返回 `SqlServerObjectOperator` 实例。 |
| `DBFunctionTemplateFactory.java` | 实现 `buildForSqlServer()`，返回 `SqlServerFunctionTemplate` 实例。 |

### 5.2 `server/odc-core` 枚举与常量

| 文件路径 | 修改内容 |
| :--- | :--- |
| `DialectType.java` | 增加 `SQL_SERVER` 枚举值。 |
| `ConnectType.java` | 增加 `SQL_SERVER(DialectType.SQL_SERVER)` 枚举值。 |
| `OdcConstants.java` | 增加 `SQL_SERVER_DRIVER_CLASS_NAME` 和 `SQL_SERVER_DEFAULT_SCHEMA` 常量。 |
| `SqlCommentProcessor.java` | 在 `split()` 方法中增加对 `SQL_SERVER` 的处理逻辑。 |

### 5.3 `server/odc-service` 业务集成

| 文件路径 | 修改内容 |
| :--- | :--- |
| `ConnectTypeUtil.java` | 在 `getConnectType()` 方法中增加对 `SQL_SERVER` 的识别逻辑。 |
| `ConnectionTesting.java` | 在 `test()` 方法中增加对 SQLServer 默认 Schema 的处理逻辑。 |
| `DruidDataSourceFactory.java` | 配置 SQLServer 特有的连接池参数（如 `validationQuery`）。 |

### 5.4 `server/odc-migrate` 数据库初始化

| 文件路径 | 修改内容 |
| :--- | :--- |
| `R_2_0_0__initialize_version_diff_config.sql` | 增加 SQLServer 的版本差异配置（如 `column_data_type`、`support_view` 等）。 |

---

## 6. 配置与注册 (插件层)

1. **Plugin 注册**:
   - 在插件模块的 `pom.xml` 中指定 `plugin.class`。
   - 在 `META-INF/MANIFEST.MF` 或通过注解确保 PF4J 能够识别插件。

---

## 7. 总结与原则

- **逻辑下沉**: 尽量在 `db-browser` 层实现方言逻辑，保持 `server/plugins` 层的轻量化。
- **TDD 开发**: 优先编写单元测试，通过测试驱动方言适配的完善。
- **解耦设计**: 避免在公共模块中编写特定数据库的业务代码，利用工厂模式和接口实现多数据库适配。

---

## 8. 待完善功能：SQL 语法解析器 (Parser)

由于 SQL 语法解析（如 DDL 解析、SQL 检查）的实现成本较高，目前 SQLServer 插件暂未实现专用的语法解析器。如果后续需要支持如“影子表”、“回滚方案”或“SQL 检查”等深度依赖语法分析的功能，可参考以下路径进行实现：

1. **定义 Antlr4 语法**: 在底层的 SQL 解析库中增加 SQLServer 的 Antlr4 语法文件。
2. **实现插件层解析器**:
   - **路径**: `server/plugins/schema-plugin-sqlserver/src/main/java/com/oceanbase/odc/plugin/schema/sqlserver/parser/`
   - **操作**: 参照 `OBMySQLGetDBTableByParser.java`，实现 `SqlServerGetDBTableByParser`，通过语法树解析 DDL 字符串并还原为 `DBTable` 等模型对象。
3. **集成到扩展点**: 将解析器集成到 `TableExtensionPoint` 等相关扩展点中，以支持通过 SQL 脚本反向生成元数据。

