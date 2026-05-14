# connect-plugin-db2 / IBM DB2 JDBC 驱动放置说明

本目录承载 ODC 工作台 DB2 数据源所需的 **IBM DB2 JCC**（Java Common Connector）驱动 jar。

> 关联：`docs/spec/design.md` §3.9（IBM JCC 驱动 jar 放置与许可）、`docs/dev/compat_risks.md`
> compat-RISK-6（外置 jar 缺失）、compat-RISK-8（依赖 com.ibm.db2:jcc:11.5.8.0 provided）。

---

## 1. 需要放置的 jar 文件

| 文件 | 用途 | 来源 |
|------|------|------|
| `db2jcc4.jar` | IBM DB2 JCC Type 4 JDBC 驱动核心包 | IBM 官网 / IBM Fix Central / 与 DB2 server 同版本附带 |
| `db2jcc_license_cu.jar` | IPLA 许可证 jar（在 IBM 数据库环境内使用 JCC 必备） | 同上 |

> 推荐版本：`11.5.8.0`（与 `connect-plugin-db2/pom.xml` `ibm.db2.jcc.version` 一致）。
> 较低版本（10.5 / 11.1）也可，但不再受 IBM 官方维护，且 `retrieveMessagesFromServerOnGetMessage`
> 等关键参数的兼容性需要在 docs/dev/compat_risks.md 中重新评估。

放置后目录结构：

```
distribution/plugins/connect-plugin-db2/
├── connect-plugin-db2-<version>.jar      # 由 mvn package 产出
└── lib/
    ├── db2jcc4.jar                        # 部署侧放置
    ├── db2jcc_license_cu.jar              # 部署侧放置
    └── README.md                          # 本文件
```

---

## 2. 许可（IPLA / 法务）

- IBM JCC 驱动遵循 IBM **International Program License Agreement (IPLA)**；
- ActionTech / OceanBase ODC 默认 **不打包** 该 jar（pom 中 `<scope>provided</scope>`），
  由部署侧用户负责接受 IPLA 协议并自行放置；
- ODC Release Notes 已显式说明本约束，部署侧需阅读 IBM 官方 License 条款。

---

## 3. 运维验证

部署完成后，可执行以下命令快速校验：

```bash
# JCC driver class 必须存在
jar tf distribution/plugins/connect-plugin-db2/lib/db2jcc4.jar | grep -E 'com/ibm/db2/jcc/DB2Driver\.class$'

# pf4j 装载 plugin 时日志中应出现：
#   Plugin 'connect-plugin-db2@<version>' loaded
grep 'connect-plugin-db2' /var/log/odc/odc-server.log | head -5
```

---

## 4. 故障排查

| 现象 | 可能原因 | 处理 |
|------|---------|------|
| ODC 启动日志：`ClassNotFoundException: com.ibm.db2.jcc.DB2Driver` | 本目录下未放 `db2jcc4.jar`，或文件名拼写错误 | 重新放置 jar 并 restart-backend |
| 连接 DB2 报 `IBM PROTOCOL VIOLATION` | 驱动版本与 DB2 server 不兼容 | 升级到与 server 同主版本的 JCC（参考 IBM 兼容矩阵） |
| 启动日志：`Plugin 'connect-plugin-db2' failed to load` | `db2jcc_license_cu.jar` 缺失 | 补放 license jar 后重启 |

更多排障流程见 `docs/spec/design.md` §13（回滚 / 故障兜底）。
