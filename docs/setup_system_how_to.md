# ODC 开发环境配置指南 - Cursor/VSCode

本文档提供在 Cursor/VSCode 上配置和启动 ODC 后端项目的完整指南。

## 📋 目录

1. [环境要求](#环境要求)
2. [快速开始](#快速开始)
3. [详细配置步骤](#详细配置步骤)
4. [常见问题解决](#常见问题解决)
5. [验证和测试](#验证和测试)

---

## 环境要求

### 必需环境
- **Java 8**：用于项目编译和运行
- **Java 21**：用于 Java Language Server（IDE 功能）
- **Maven**：项目使用 Maven Wrapper（`mvnw`），无需单独安装
- **MySQL 8.0+**：用于元数据库
- **Node.js 16+**（可选）：仅当需要构建前端资源时

### IDE 扩展
- **Extension Pack for Java**（Cursor/VSCode Java 扩展包）

---

## 快速开始

### 一键启动（如果已完成所有配置）

1. 按 `F5` 启动调试
2. 选择 "Launch OdcServer" 配置
3. 等待服务启动（约 1-2 分钟）
4. 访问 `http://localhost:8989/` 验证

---

## 详细配置步骤

### 阶段一：基础环境准备 ✅

#### 1.1 配置 Java 环境

确保系统已安装 Java 8 和 Java 21：

```bash
# 检查 Java 8（项目编译使用）
java -version  # 应该显示 1.8.x

# 检查 Java 21（Language Server 使用）
/usr/lib/jvm/java-21-openjdk-amd64/bin/java -version
```

#### 1.2 配置 Cursor/VSCode 设置

配置文件：`.vscode/settings.json`

关键配置：
- `java.configuration.runtimes`：配置 Java 8 路径
- `java.jdt.ls.java.home`：配置 Java 21 路径（Language Server 需要）
- `java.project.sourcePaths` 和 `java.project.referencedLibraries`：已移除（使用 Maven 标准识别）

#### 1.3 创建启动配置

配置文件：`.vscode/launch.json`

已配置：
- ✅ 主类：`com.oceanbase.odc.server.OdcServer`
- ✅ 端口：8989
- ✅ 数据库环境变量
- ✅ VM 参数（日志、插件路径等）

#### 1.4 创建构建任务

配置文件：`.vscode/tasks.json`

可用任务：
- `Build Libs`：构建依赖组件
- `Build JAR`：构建完整 JAR 包
- `Maven: Compile`：编译项目
- `Build SQL Console (Frontend)`：构建前端资源

---

### 阶段二：项目构建（必需）✅

#### 2.1 构建依赖组件

```bash
./script/build_libs.sh
```

**作用**：
- 构建 `ob-sql-parser` 和 `db-browser`
- 安装 `pty4j` 和 `purejavacomm` 到本地 Maven 仓库

**状态**: ✅ 已完成

#### 2.2 编译整个项目

```bash
./mvnw clean compile -DskipTests
```

或使用 VSCode 任务：`Tasks: Run Task` -> `Maven: Compile`

**状态**: ✅ 已完成

#### 2.3 构建完整 JAR 包

```bash
./script/build_jar.sh
```

或使用 VSCode 任务：`Tasks: Run Task` -> `Build JAR`

**构建结果**：
- ✅ JAR 包：`server/odc-server/target/odc-server-4.3.4-SNAPSHOT-executable.jar`
- ✅ Plugins：`distribution/plugins/`（21 个插件 JAR）
- ✅ Starters：`distribution/starters/`（2 个 starter JAR）
- ✅ Modules：`distribution/modules/`（1 个 module JAR）

**状态**: ✅ 已完成

---

### 阶段三：前端资源构建 ✅

#### 3.1 配置 Node.js 环境（如果未安装）

```bash
./script/init_node_env.sh
```

**状态**: ✅ Node.js 16.14.0 已安装

#### 3.2 更新前端子模块

```bash
./script/update_submodule.sh
```

**状态**: ✅ 前端子模块已更新（client 目录已存在）

#### 3.3 配置 npm/pnpm 镜像源（国内用户必需）

```bash
# 配置 npm 镜像源
npm config set registry https://registry.npmmirror.com

# 配置 pnpm 镜像源
/root/.nvs/default/bin/pnpm config set registry https://registry.npmmirror.com

# 创建项目级配置
cd client
echo "registry=https://registry.npmmirror.com" > .npmrc
```

**状态**: ✅ 已配置淘宝镜像源

#### 3.4 安装前端依赖

```bash
cd /root/ODC/odc/client
/root/.nvs/default/bin/pnpm install
```

**预计时间**: 5-10 分钟（使用国内源）

**状态**: ✅ 已完成

#### 3.5 构建前端资源

```bash
/root/.nvs/default/bin/pnpm run build:odc
```

**预计时间**: 5-10 分钟

**状态**: ✅ 已完成

#### 3.6 复制文件到后端

```bash
cd /root/ODC/odc
mkdir -p server/odc-server/src/main/resources/static/
rm -rf server/odc-server/src/main/resources/static/*
cp -r client/dist/renderer/* server/odc-server/src/main/resources/static/

# 验证
ls -lh server/odc-server/src/main/resources/static/index.html
```

**状态**: ✅ 已完成

**验证结果**：
- ✅ `index.html` 已存在（12K）
- ✅ 所有静态资源文件已复制

---

### 阶段四：数据库配置 ✅

#### 4.1 配置 MySQL SQL 模式

**问题**：MySQL 8.0 严格模式会导致 TIMESTAMP 字段报错

**解决方案**：修改 MySQL SQL 模式（开发环境）

```sql
-- 连接 MySQL
mysql -h 10.186.58.3 -P 3306 -u odc -p

-- 全局修改 SQL 模式（移除 NO_ZERO_DATE 和 STRICT_TRANS_TABLES）
SET GLOBAL sql_mode = 'ONLY_FULL_GROUP_BY,NO_ZERO_IN_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';

-- 验证
SELECT @@GLOBAL.sql_mode;
```

**状态**: ✅ 已解决

---

## 常见问题解决

### 问题 1：pty4j 依赖解析失败

**错误信息**：
```
Could not resolve dependencies: org.jetbrains.pty4j:pty4j:jar:0.11.4
```

**原因**：`pty4j` 和 `purejavacomm` 不在 Maven 中央仓库，需要手动安装

**解决方案**：
```bash
# 1. 执行构建脚本安装依赖
./script/build_libs.sh

# 2. 清理失败缓存
rm -rf ~/.m2/repository/org/jetbrains/pty4j/

# 3. 重新构建
./script/build_jar.sh
```

**状态**: ✅ 已解决

---

### 问题 2：MySQL TIMESTAMP 字段错误

**错误信息**：
```
Invalid default value for 'gmt_modify'
```

**原因**：MySQL 8.0 严格模式要求 TIMESTAMP 字段必须有默认值

**解决方案**：修改 MySQL SQL 模式（见阶段四）

**状态**: ✅ 已解决

---

### 问题 3：pnpm corepack 错误

**错误信息**：
```
Error: globalThis.fetch is not a function
```

**原因**：Node.js 16.14.0 通过 corepack 调用 pnpm 时，corepack 使用了不支持的 API

**解决方案**：
```bash
# 方法 1：使用 npm 安装的 pnpm 完整路径
/root/.nvs/default/bin/pnpm install

# 方法 2：使用 npx（推荐）
npx pnpm@8 install

# 方法 3：禁用 corepack
corepack disable
```

**状态**: ✅ 已解决（使用完整路径）

---

### 问题 4：前端资源下载慢

**问题**：`pnpm install` 从官方源下载很慢

**解决方案**：配置国内镜像源（见阶段三 3.3）

**状态**: ✅ 已解决

---

### 问题 5：前端页面模板错误

**错误信息**：
```
Error resolving template [index], template might not exist
```

**原因**：前端资源未构建或未复制到后端

**解决方案**：
1. 构建前端资源（见阶段三）
2. 确保 `ODC_INDEX_PAGE_URI` 为空（使用本地资源）
3. 重启 ODC 服务

**状态**: ✅ 已解决

---

## 验证和测试

### 5.1 验证构建结果

```bash
# 检查 JAR 包
ls -lh server/odc-server/target/odc-server-*-executable.jar

# 检查插件和启动器
ls -la distribution/plugins/ | wc -l  # 应该显示 21+
ls -la distribution/starters/ | wc -l  # 应该显示 2+

# 检查前端资源
ls -lh server/odc-server/src/main/resources/static/index.html
```

### 5.2 启动 ODC 服务

1. **在 Cursor 中启动**：
   - 按 `F5` 或点击调试按钮
   - 选择 "Launch OdcServer" 配置
   - 等待服务启动（查看控制台输出）

2. **验证启动日志**：
   - 查看 `log/odc.log` 文件
   - 确认看到：`Tomcat started on port(s): 8989`
   - 确认看到：`Started OdcServer in XX seconds`

### 5.3 验证服务可用性

```bash
# 健康检查
curl http://localhost:8989/api/v1/heartbeat/isHealthy

# 应该返回：{"code":null,"data":true,...}
```

### 5.4 验证前端页面

访问以下地址，应该能看到 ODC 登录页面：

- `http://localhost:8989/`
- `http://localhost:8989/index.html`
- `http://10.186.58.4:8989/`（如果从其他机器访问）

---

## 当前配置状态

### ✅ 已完成

1. **基础环境**
   - ✅ Java 8 和 Java 21 已配置
   - ✅ Cursor/VSCode 配置已完成
   - ✅ 启动配置已创建

2. **项目构建**
   - ✅ 依赖组件已构建
   - ✅ 项目已编译
   - ✅ JAR 包已生成

3. **前端资源**
   - ✅ Node.js 环境已配置
   - ✅ 前端依赖已安装
   - ✅ 前端资源已构建
   - ✅ 文件已复制到后端

4. **数据库配置**
   - ✅ MySQL SQL 模式已调整
   - ✅ 数据库连接配置已设置

### 📝 当前配置

**数据库配置**（`.vscode/launch.json`）：
- `ODC_DATABASE_HOST`: 10.186.58.3
- `ODC_DATABASE_PORT`: 33060
- `ODC_DATABASE_NAME`: odc_metadb
- `ODC_DATABASE_USERNAME`: odc
- `ODC_DATABASE_PASSWORD`: odcpass
- `ODC_PROFILE_MODE`: alipay
- `ODC_SERVER_PORT`: 8989
- `ODC_INDEX_PAGE_URI`: ""（使用本地前端资源）

---

## 下一步操作

### 日常开发

1. **启动服务**：按 `F5` 启动调试
2. **查看日志**：`log/odc.log`
3. **访问前端**：`http://localhost:8989/`

### 重新构建

如果修改了代码，需要重新编译：

```bash
# 仅编译
./mvnw compile -DskipTests

# 完整构建（包括 JAR）
./script/build_jar.sh
```

### 前端资源更新

如果修改了前端代码：

```bash
cd client
/root/.nvs/default/bin/pnpm run build:odc
cd ..
rm -rf server/odc-server/src/main/resources/static/*
cp -r client/dist/renderer/* server/odc-server/src/main/resources/static/
```

---

## 注意事项

1. **构建顺序**：
   - 必须先构建 libs → 然后编译项目 → 最后构建 JAR

2. **数据库 SQL 模式**：
   - 开发环境已调整 SQL 模式
   - 生产环境建议修复 SQL 脚本而不是修改数据库配置

3. **前端资源**：
   - 如果只是后端开发，可以不构建前端
   - 可以通过 `ODC_INDEX_PAGE_URI` 环境变量引用远程前端资源

4. **Maven 源**：
   - 如果下载依赖慢，可以配置阿里云镜像
   - 创建 `~/.m2/settings.xml` 配置镜像源

5. **端口冲突**：
   - 默认端口 8989，可在 `launch.json` 中修改
   - 确保端口未被占用

---

## 参考文档

- [ODC 开发指南](../docs/zh-CN/DEVELOPER_GUIDE.md)
- [VSCode 配置说明](./README.md)

---

## 更新记录

- **2026-01-13**：完成前端资源构建，文档整理完成
- **2026-01-13**：解决 MySQL SQL 模式问题
- **2026-01-13**：解决 pnpm corepack 问题
- **2026-01-13**：完成项目构建和配置
