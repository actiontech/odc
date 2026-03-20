# ODC 构建镜像配置说明

本文档说明构建镜像中的配置与构建依赖的对应关系。

## 镜像信息

- **镜像名称**: `reg.actiontech.com/actiontech-dev/odc/build-env:1.0`
- **基础镜像**: Ubuntu 22.04
- **用途**: ODC RPM 包构建流水线

## 构建依赖配置对照表

| 构建依赖 | 镜像配置 | 版本 | 用途说明 |
| :--- | :--- | :--- | :--- |
| **Java 环境** | `openjdk-8-jdk` (apt 安装) | OpenJDK 8 | 后端代码编译 |
| **Maven** | 从华为云镜像下载安装 | 3.6.3 | Java 项目构建工具 |
| **Maven 仓库** | 配置阿里云镜像源 (`/root/.m2/settings.xml`) | - | 加速 Maven 依赖下载 |
| **Node.js** | 从清华镜像下载安装 | 18.20.0 | 前端构建运行时 |
| **pnpm** | 通过 npm 全局安装 | 10.28.1 | 前端包管理工具 |
| **pnpm 仓库** | 配置 npmmirror 镜像源 | - | 加速前端依赖下载 |
| **RPM 工具** | `rpm` (apt 安装) | - | RPM 包构建 |
| **Git** | `git` (apt 安装) | - | 代码版本管理 |
| **系统工具** | `file`, `curl`, `zip`, `unzip`, `make`, `gcc`, `g++`, `xz-utils`, `ca-certificates` | - | 构建过程所需的基础工具 |

## 环境变量配置

| 环境变量 | 值 | 说明 |
| :--- | :--- | :--- |
| `JAVA_HOME` | `/usr/lib/jvm/java-8-openjdk-amd64` | Java 安装路径 |
| `MAVEN_HOME` | `/usr/local/maven` | Maven 安装路径 |
| `NODE_VERSION` | `18.20.0` | Node.js 版本 |
| `PATH` | `$MAVEN_HOME/bin:$PATH` | 包含 Maven 可执行文件路径 |
| `CI` | `true` | 启用非交互模式 |

## 镜像源配置

| 工具 | 镜像源 | 说明 |
| :--- | :--- | :--- |
| **Ubuntu APT** | `mirrors.aliyun.com` | 系统包管理器镜像源 |
| **Maven 仓库** | `https://maven.aliyun.com/repository/public` | Maven 依赖下载镜像源 |
| **Node.js** | `mirrors.tuna.tsinghua.edu.cn` | Node.js 二进制下载镜像源 |
| **npm/pnpm** | `https://registry.npmmirror.com` | npm 包注册表镜像源 |

## 工作目录

- **工作目录**: `/root/odc`
- **默认命令**: `/bin/bash`
