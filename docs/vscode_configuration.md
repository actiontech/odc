# VSCode 开发环境配置文档

本文档包含 ODC 项目的 VSCode 开发环境完整配置说明。

## 目录结构

VSCode 配置文件位于 `.vscode/` 目录下，包含以下文件：

- `settings.json` - 编辑器和工作区设置
- `launch.json` - 调试和启动配置
- `tasks.json` - 任务配置
- `README.md` - 快速配置说明

---

## 1. settings.json

编辑器和工作区设置配置文件。

```json
{
    "java.configuration.updateBuildConfiguration": "automatic",
    "java.compile.nullAnalysis.mode": "automatic",
    "java.import.maven.enabled": true,
    "java.project.resourceFilters": ["node_modules", ".git"],
    "java.configuration.runtimes": [
        {
            "name": "JavaSE-1.8",
            "path": "/usr/lib/jvm/java-8-openjdk-amd64",
            "default": true
        }
    ],
    "java.jdt.ls.java.home": "/usr/lib/jvm/java-21-openjdk-amd64",
    "java.format.settings.url": "builds/code-style/eclipse-java-oceanbase-style.xml",
    "java.format.settings.profile": "oceanbase-java-format",
    "java.import.gradle.enabled": false,
    "java.project.referencedLibraries": [
        "libs/**/*.jar"
    ],
    "files.eol": "\n",
    "files.encoding": "utf8",
    "files.exclude": {
        "**/.factorypath": true
    },
    "editor.formatOnSave": false,
    "editor.codeActionsOnSave": {
        "source.organizeImports": "never"
    },
    "[java]": {
        "editor.tabSize": 4,
        "editor.insertSpaces": true
    },
    "[xml]": {
        "editor.tabSize": 4,
        "editor.insertSpaces": true
    },
    "[json]": {
        "editor.tabSize": 2,
        "editor.insertSpaces": true
    },
    "[yaml]": {
        "editor.tabSize": 2,
        "editor.insertSpaces": true
    },
    "java.debug.settings.onBuildFailureProceed": true,
    "java.jdt.ls.vmargs": "-XX:+UseParallelGC -XX:GCTimeRatio=4 -XX:AdaptiveSizePolicyWeight=90 -Dsun.zip.disableMemoryMapping=true -Xmx4G -Xms100m -Xlog:disable",
    "git.ignoreLimitWarning": true,
    "java.project.sourcePaths": [
        "ci/script"
    ]
}
```

### 配置说明

#### Java 配置
- **自动构建配置更新**: `java.configuration.updateBuildConfiguration: "automatic"`
- **空值分析模式**: `java.compile.nullAnalysis.mode: "automatic"`
- **Maven 导入**: 已启用
- **Gradle 导入**: 已禁用
- **Java 运行时**: 配置了 Java 8 作为默认运行时，Java 21 作为 JDT Language Server 的运行时
- **代码格式化**: 使用 OceanBase Java 代码风格配置文件
- **引用库路径**: `libs/**/*.jar`

#### 编辑器配置
- **文件换行符**: LF (`\n`)
- **文件编码**: UTF-8
- **保存时格式化**: 已禁用
- **保存时组织导入**: 已禁用

#### 语言特定配置
- **Java/XML**: Tab 大小为 4 空格
- **JSON/YAML**: Tab 大小为 2 空格

#### JDT Language Server 配置
- **JVM 参数**: 使用并行 GC，最大堆内存 4GB，初始堆内存 100MB

---

## 2. launch.json

调试和启动配置文件。

```json
{
    "version": "0.2.0",
    "configurations": [
        {
            "type": "java",
            "name": "Launch OdcServer",
            "request": "launch",
            "mainClass": "com.oceanbase.odc.server.OdcServer",
            "projectName": "odc-server",
            "args": "--server.port=8989",
            "vmArgs": "-XX:MaxRAMPercentage=45.0 -XX:InitialRAMPercentage=45.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -Dlog4j.configurationFile=${workspaceFolder}/server/odc-server/target/classes/log4j2.xml -Dodc.log.directory=${workspaceFolder}/log -Duser.dir=${workspaceFolder} -Dplugin.dir=${workspaceFolder}/distribution/plugins -Dstarter.dir=${workspaceFolder}/distribution/starters",
            "env": {
                "ODC_DATABASE_HOST": "****",
                "ODC_DATABASE_PORT": "33060",
                "ODC_DATABASE_NAME": "odc_metadb",
                "ODC_DATABASE_USERNAME": "****",
                "ODC_DATABASE_PASSWORD": "****",
                "ODC_PROFILE_MODE": "alipay",
                "ODC_SERVER_PORT": "8989",
                "ODC_INDEX_PAGE_URI": "",
                "ODC_ADMIN_INITIAL_PASSWORD": "****"
            },
            "console": "internalConsole",
            "internalConsoleOptions": "openOnSessionStart"
        },
        {
            "type": "java",
            "name": "Launch OdcServer (Remote Debug)",
            "request": "attach",
            "hostName": "localhost",
            "port": 8000
        }
    ]
}
```

### 配置说明

#### Launch OdcServer 配置
- **类型**: Java 应用启动
- **主类**: `com.oceanbase.odc.server.OdcServer`
- **项目名称**: `odc-server`
- **启动参数**: `--server.port=8989`（服务器端口）
- **JVM 参数**:
  - 内存配置: 最大和初始 RAM 百分比为 45%
  - GC 策略: G1GC
  - OOM 处理: 内存溢出时退出
  - 日志配置: 使用 log4j2.xml
  - 目录配置: 工作目录、日志目录、插件目录、启动器目录

- **环境变量**:
  - `ODC_DATABASE_HOST`: 元数据库主机地址（已隐藏）
  - `ODC_DATABASE_PORT`: 元数据库端口 `33060`
  - `ODC_DATABASE_NAME`: 元数据库名称 `odc_metadb`
  - `ODC_DATABASE_USERNAME`: 元数据库用户名（已隐藏）
  - `ODC_DATABASE_PASSWORD`: 元数据库密码（已隐藏）
  - `ODC_PROFILE_MODE`: Profile 模式 `alipay`
  - `ODC_SERVER_PORT`: 服务器端口 `8989`
  - `ODC_INDEX_PAGE_URI`: 前端静态资源地址（空字符串）
  - `ODC_ADMIN_INITIAL_PASSWORD`: 管理员初始密码（已隐藏）

#### Launch OdcServer (Remote Debug) 配置
- **类型**: Java 远程调试附加
- **主机**: `localhost`
- **端口**: `8000`

### 环境变量配置建议

**注意**: 为了安全起见，建议将敏感信息（如数据库密码、IP 地址等）通过以下方式配置：

1. **使用系统环境变量**（推荐）:
   ```bash
   export ODC_DATABASE_HOST="your_host"
   export ODC_DATABASE_PORT="33060"
   export ODC_DATABASE_NAME="odc_metadb"
   export ODC_DATABASE_USERNAME="your_username"
   export ODC_DATABASE_PASSWORD="your_password"
   export ODC_ADMIN_INITIAL_PASSWORD="your_admin_password"
   ```

2. **使用 VSCode 环境变量文件**: 创建 `.env` 文件（需添加到 `.gitignore`）

---

## 3. tasks.json

任务配置文件，定义了常用的构建和测试任务。

```json
{
    "version": "2.0.0",
    "tasks": [
        {
            "label": "Build Libs",
            "type": "shell",
            "command": "./script/build_libs.sh",
            "group": {
                "kind": "build",
                "isDefault": false
            },
            "presentation": {
                "reveal": "always",
                "panel": "shared"
            },
            "problemMatcher": []
        },
        {
            "label": "Build JAR",
            "type": "shell",
            "command": "./script/build_jar.sh",
            "group": {
                "kind": "build",
                "isDefault": true
            },
            "presentation": {
                "reveal": "always",
                "panel": "shared"
            },
            "problemMatcher": "$tsc",
            "dependsOn": "Build Libs"
        },
        {
            "label": "Maven: Clean Install",
            "type": "shell",
            "command": "./mvnw clean install -DskipTests",
            "group": "build",
            "presentation": {
                "reveal": "always",
                "panel": "shared"
            },
            "problemMatcher": "$tsc"
        },
        {
            "label": "Maven: Compile",
            "type": "shell",
            "command": "./mvnw compile -DskipTests",
            "group": "build",
            "presentation": {
                "reveal": "always",
                "panel": "shared",
                "clear": false
            },
            "problemMatcher": "$tsc"
        },
        {
            "label": "Init Node Environment",
            "type": "shell",
            "command": "./script/init_node_env.sh",
            "group": "build",
            "presentation": {
                "reveal": "always",
                "panel": "shared"
            },
            "problemMatcher": []
        },
        {
            "label": "Update Submodule",
            "type": "shell",
            "command": "./script/update_submodule.sh",
            "group": "build",
            "presentation": {
                "reveal": "always",
                "panel": "shared"
            },
            "problemMatcher": []
        },
        {
            "label": "Build SQL Console (Frontend)",
            "type": "shell",
            "command": "./script/build_sqlconsole.sh",
            "group": "build",
            "presentation": {
                "reveal": "always",
                "panel": "shared"
            },
            "problemMatcher": []
        },
        {
            "label": "Test: SQL Server Editors (db-browser)",
            "type": "shell",
            "command": "cd libs/db-browser && mvn test -Dtest=SqlServerColumnEditorTest,SqlServerConstraintEditorTest,SqlServerIndexEditorTest,SqlServerMViewEditorTest,SqlServerObjectOperatorTest,SqlServerPartitionEditorTest,SqlServerSequenceEditorTest,SqlServerSynonymEditorTest,SqlServerTableEditorTest,SqlServerTriggerEditorTest,SqlServerViewEditorTest -DskipTests=false",
            "group": {
                "kind": "test",
                "isDefault": false
            },
            "presentation": {
                "reveal": "always",
                "panel": "shared",
                "clear": false
            },
            "problemMatcher": "$tsc"
        },
        {
            "label": "Test: SQL Server SchemaAccessor (db-browser)",
            "type": "shell",
            "command": "cd libs/db-browser && mvn test -Dtest=SqlServerSchemaAccessorTest -DskipTests=false",
            "group": {
                "kind": "test",
                "isDefault": false
            },
            "presentation": {
                "reveal": "always",
                "panel": "shared",
                "clear": false
            },
            "problemMatcher": "$tsc"
        },
        {
            "label": "Test: SQL Server ViewTemplate (db-browser)",
            "type": "shell",
            "command": "cd libs/db-browser && mvn test -Dtest=SqlServerViewTemplateTest -DskipTests=false",
            "group": {
                "kind": "test",
                "isDefault": false
            },
            "presentation": {
                "reveal": "always",
                "panel": "shared",
                "clear": false
            },
            "problemMatcher": "$tsc"
        }
    ]
}
```

### 任务说明

#### 构建任务

1. **Build Libs**
   - 构建依赖库
   - 命令: `./script/build_libs.sh`
   - 使用方式: `Ctrl+Shift+P` -> `Tasks: Run Task` -> `Build Libs`

2. **Build JAR** (默认构建任务)
   - 构建 JAR 包
   - 命令: `./script/build_jar.sh`
   - 依赖: Build Libs
   - 使用方式: `Ctrl+Shift+B` 或 `Ctrl+Shift+P` -> `Tasks: Run Task` -> `Build JAR`

3. **Maven: Clean Install**
   - Maven 清理并安装（跳过测试）
   - 命令: `./mvnw clean install -DskipTests`

4. **Maven: Compile**
   - Maven 编译（跳过测试）
   - 命令: `./mvnw compile -DskipTests`

5. **Init Node Environment**
   - 初始化 Node.js 环境
   - 命令: `./script/init_node_env.sh`

6. **Update Submodule**
   - 更新 Git 子模块
   - 命令: `./script/update_submodule.sh`

7. **Build SQL Console (Frontend)**
   - 构建 SQL Console 前端
   - 命令: `./script/build_sqlconsole.sh`

#### 测试任务

1. **Test: SQL Server Editors (db-browser)**
   - 运行 SQL Server 编辑器相关测试
   - 测试类: ColumnEditor, ConstraintEditor, IndexEditor, MViewEditor, ObjectOperator, PartitionEditor, SequenceEditor, SynonymEditor, TableEditor, TriggerEditor, ViewEditor

2. **Test: SQL Server SchemaAccessor (db-browser)**
   - 运行 SQL Server SchemaAccessor 测试

3. **Test: SQL Server ViewTemplate (db-browser)**
   - 运行 SQL Server ViewTemplate 测试

### 使用方式

- **运行任务**: `Ctrl+Shift+P` -> `Tasks: Run Task` -> 选择任务名称
- **默认构建**: `Ctrl+Shift+B`（运行 "Build JAR" 任务）

---

## 4. 快速开始

### 首次设置

1. **初始化 Node 环境**:
   ```bash
   # 方式一：使用任务
   Ctrl+Shift+P -> Tasks: Run Task -> Init Node Environment
   
   # 方式二：命令行
   ./script/init_node_env.sh
   ```

2. **更新子模块**:
   ```bash
   Ctrl+Shift+P -> Tasks: Run Task -> Update Submodule
   ```

3. **构建依赖库**:
   ```bash
   Ctrl+Shift+P -> Tasks: Run Task -> Build Libs
   ```

4. **构建项目**:
   ```bash
   Ctrl+Shift+B  # 或使用任务 "Build JAR"
   ```

### 启动开发服务器

1. **配置环境变量**（如果未在 launch.json 中配置）:
   - 在系统环境变量中设置数据库连接信息
   - 或在 `launch.json` 的 `env` 部分配置（不推荐，会暴露敏感信息）

2. **启动 ODC Server**:
   - 按 `F5` 或点击调试按钮
   - 选择 "Launch OdcServer" 配置
   - 服务器将在 `http://localhost:8989` 启动

### 远程调试

1. 确保远程服务器已启动并监听 8000 端口
2. 在 VSCode 中选择 "Launch OdcServer (Remote Debug)" 配置
3. 按 `F5` 开始远程调试

---

## 5. 注意事项

1. **敏感信息保护**: 
   - 不要在 `launch.json` 中直接硬编码密码、IP 地址等敏感信息
   - 建议使用系统环境变量或 `.env` 文件（需添加到 `.gitignore`）

2. **Java 版本要求**:
   - 项目使用 Java 8 作为运行时
   - JDT Language Server 使用 Java 21

3. **内存配置**:
   - JDT Language Server 最大堆内存为 4GB
   - ODC Server 运行时内存为最大 RAM 的 45%

4. **端口配置**:
   - 默认服务器端口: `8989`
   - 远程调试端口: `8000`
   - 可通过 `args` 参数或环境变量 `ODC_SERVER_PORT` 修改

5. **代码格式化**:
   - 保存时自动格式化已禁用
   - 使用 OceanBase Java 代码风格配置文件
   - 可通过 `Ctrl+Shift+P` -> `Format Document` 手动格式化

---

## 6. 相关文档

- [系统设置指南](./setup_system_how_to.md)
- [添加新插件指南](./add_a_new_plugin_how_to.md)
- [清理构建指南](./clean_build.md)
