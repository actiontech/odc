# ODC-Server 部署文档（Ubuntu 系统）
## 一、部署前提
1. 已获取 ODC-Server 安装包：`odc-server-xxxxxx.x86_64.rpm`
2. 以 `root` 用户执行所有命令（确保权限足够）
3. 服务器已安装基础依赖：`rpm2cpio`、`cpio`（Ubuntu 可通过 `apt install rpm2cpio cpio` 安装）
4. 目标部署目录：`/opt/odc`

## 二、部署步骤
### 步骤1：创建 ODC 运行所需的 admin 用户/组
ODC 包要求文件权限归属 `admin:admin`，需先创建对应用户/组（系统级，不可登录，保障安全）：
```bash
# 1. 创建 admin 系统用户组（-r 标识为系统组）
groupadd -r admin

# 2. 创建 admin 系统用户，关联到 admin 组，设置为不可登录
useradd -r -g admin -s /sbin/nologin admin

# 3. 验证用户/组创建成功（输出 uid/gid 信息即成功）
id admin
```
**预期输出**：`uid=999(admin) gid=115(admin) groups=115(admin)`

### 步骤2：提取 RPM 包文件到目标目录并配置权限
直接提取 RPM 包内容到 `/opt/odc`，并配置正确权限：
```bash
# 1. 清空目标目录（避免旧文件干扰）
rm -rf /opt/odc && mkdir -p /opt/odc

# 2. 提取 RPM 包所有文件到 /opt/odc（需确保 rpm 包在当前目录）
rpm2cpio odc-server-4.3.4-20260123.x86_64.rpm | cpio -idmv -D /opt/odc

# 3. 递归设置 /opt/odc 目录权限为 admin:admin
chown -R admin:admin /opt/odc

# 4. 验证权限（输出文件所有者为 admin:admin 即成功）
ls -l /opt/odc | head -5
```

### 步骤3：配置环境变量并启动 ODC-Server
ODC 为 Java 程序，需配置数据库连接、JVM 等核心参数，严格对齐官方文档规范：
```bash
# 1. 进入 ODC 启动脚本目录（提取后实际路径，嵌套一层 opt）
cd /opt/odc/opt/odc/bin

# 2. 清空残留环境变量，避免冲突
unset DATABASE_HOST DATABASE_PORT DATABASE_NAME DATABASE_USERNAME DATABASE_PASSWORD
unset ODC_PROFILE_MODE ODC_SERVER_PORT ODC_ADMIN_INITIAL_PASSWORD ODC_INDEX_PAGE_URI

# 3. 配置核心环境变量（数据库连接，必填）
export DATABASE_HOST="10.186.xx.xx"         # 元数据库地址
export DATABASE_PORT="33060"                # 元数据库端口
export DATABASE_NAME="odc_metadb"           # 元数据库名
export DATABASE_USERNAME="odc"              # 元数据库用户名
export DATABASE_PASSWORD="odcpass"          # 元数据库密码
export ODC_ADMIN_INITIAL_PASSWORD="Admin@123#Test"  # ODC 管理员初始密码（带 ODC 前缀）

# 4. 配置可选环境变量（和官方文档一致）
export ODC_PROFILE_MODE="alipay"            # 运行模式，默认 alipay
export ODC_SERVER_PORT="8989"               # ODC 服务端口，默认 8989
export ODC_INDEX_PAGE_URI=""                # 前端静态资源地址（开发联调时可替换为实际地址）

# 5. 配置 JVM 参数（兼容 VSCode 调试配置）
export ODC_JVM_HEAP_OPTIONS="-XX:MaxRAMPercentage=45.0 -XX:InitialRAMPercentage=45.0"
export ODC_JVM_GC_OPTIONS="-XX:+UseG1GC"
export ODC_JVM_OOM_OPTIONS="-XX:+ExitOnOutOfMemoryError"
export ODC_JVM_EXTRA_OPTIONS="-Dlog4j.configurationFile=/opt/odc/opt/odc/conf/log4j2.xml -Dodc.log.directory=/opt/odc/opt/odc/log -Duser.dir=/opt/odc/opt/odc -Dplugin.dir=/opt/odc/opt/odc/plugins -Dstarter.dir=/opt/odc/opt/odc/starters"

# 6. 启动 ODC（前台模式，调试用，可直接查看启动日志）
./start-odc.sh

# 【可选】生产环境后台启动（日志输出到指定文件，不占用终端）
# nohup ./start-odc.sh > /opt/odc/opt/odc/log/odc-start.log 2>&1 &
```

## 三、启动验证
### 1. 前台启动验证
启动后控制台无 `FATAL ERROR` 报错，且输出 `Started OdcServer in xxx seconds` 即启动成功。

### 2. 端口访问验证
```bash
# 访问 ODC 服务端口，返回前端页面/接口响应即成功
curl http://localhost:8989
```

### 3. 后台启动日志验证
若使用 `nohup` 后台启动，可通过日志查看启动状态：
```bash
tail -f /opt/odc/opt/odc/log/odc-start.log
```