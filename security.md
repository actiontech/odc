## 角色

你是一个Java软件系统的安全专家

## 背景

现在我们的系统：odc server，在接受审查后发现了一些漏洞
操作系统 Red Hat Enterprise Linux release 9.6 (Plow)

## 任务

请你协助我一个一个修复这些漏洞，我们协作的流程是这样的：

1. 我会给你一个漏洞的信息，你需要先在漏洞列表中整理这个漏洞，维护一个可读的漏洞列表
2. 根据当前漏洞和系统代码，分析并给出一个修复方案，包括一个简单的验证方法，并提供给我审查
3. 在我下达修复指令后，请你进行漏洞的修复

## 漏洞列表

| 危险程度 | 漏洞名称 | 漏洞类型 | 修复方案 | 验证信息 | 修复情况 |
| --- | --- | --- | --- | --- | --- |
| 高危 | Tomcat 拒绝服务漏洞(CVE-2025-48989) | 拒绝服务攻击 | 升级 Tomcat 嵌入式组件到 `9.0.108+`（当前代码已升级到 `9.0.109`），并重打包部署。 | 历史扫描信息：当前 `tomcat-embed-core` 为 `9.0.99`，位于受影响区间；进程 PID `1603221`，应用包 `odc-server-4.3.4-SNAPSHOT-executable.jar` 内子包 `tomcat-embed-core-9.0.99.jar`。<br>本次验证结果：执行 `mvn -pl server/odc-server -am -DskipTests dependency:tree -Dincludes=org.apache.tomcat.embed`，确认 `tomcat-embed-core/websocket/el` 均为 `9.0.109`。<br>验证方法：1) 部署新包并重启服务；2) 在部署机检查运行包内 Tomcat 子包版本为 `9.0.108+`。 | 已修复（代码层），待部署验证 |
| 危急 | Spring Framework 路径遍历漏洞(CVE-2024-38819) | 目录遍历 | 目标版本为 `5.3.41+`；由于当前环境无法获取该商业支持版本，暂保留在 `5.3.39` 并避免引入不受控文件系统静态资源映射。 | 历史扫描信息：`spring-webmvc` 当前为 `5.3.26`，位于受影响区间；进程 PID `1603221`，运行包内子包 `spring-webmvc-5.3.26.jar`。<br>代码核查：当前代码未发现 `RouterFunctions`；静态资源主要在 `WebMvcConfigurer#addResourceHandlers`。<br>暂未修复原因：`5.3.41+` 为商业支持版本，当前仓库无法获取对应制品。<br>验证方法：1) 执行 `mvn -pl server/odc-server -am -DskipTests dependency:tree -Dincludes=org.springframework:spring-webmvc,org.springframework:spring-webflux`，确认版本不低于修复版本；2) 部署新包并重启服务；3) 对静态资源路径进行 `../` 探测请求，确认无法越权读取文件。 | 暂未修复 |
| 高危 | Spring Framework 安全漏洞(CVE-2024-22243) | 服务端请求伪造 | 升级 Spring Framework 到 `5.3.32+`（当前代码已为 `5.3.39`），覆盖 `spring-web` 受影响区间。 | 历史扫描信息：`spring-web` 当前为 `5.3.26`，位于受影响区间；进程 PID `1603221`，运行包内子包 `spring-web-5.3.26.jar`。<br>本次验证结果：执行 `mvn -pl server/odc-server -am -DskipTests dependency:tree -Dincludes=org.springframework:spring-web`，解析版本为 `spring-web:5.3.39`（已满足 `5.3.32+`）。<br>验证方法：1) 部署新包并重启服务；2) 在部署机核验运行包中 `spring-web` 版本；3) 检查外部 URL 解析相关接口，确认不存在可利用的重定向或 SSRF 路径。 | 已修复（代码层），待部署验证 |
| 高危 | Apache Velocity 远程代码执行漏洞(CVE-2020-13936) | 代码执行 | 通过依赖治理替换脆弱依赖：在 `odc-service` 中对 `spring-security-saml2-service-provider` 排除 `org.apache.velocity:velocity`，并显式引入 `org.apache.velocity:velocity-engine-core:2.3`。 | 历史扫描信息：`velocity` 当前为 `1.7`，位于受影响区间；进程 PID `1603221`，运行包内子包 `velocity-1.7.jar`。<br>本次验证结果：执行 `mvn -pl server/odc-server -am -DskipTests dependency:tree -Dincludes=org.apache.velocity:velocity,org.apache.velocity:velocity-engine-core`，结果仅包含 `velocity-engine-core:2.3`，未再解析 `velocity:1.7`。<br>验证方法：1) 部署新包并重启服务；2) 在部署机核验运行包中不再包含 `velocity-1.7.jar`，且包含 `velocity-engine-core-2.3.jar`。 | 已修复（代码层），待部署验证 |
| 高危 | Tomcat 资源管理错误漏洞(CVE-2025-53506) | 拒绝服务攻击 | 将 `tomcat-embed` 相关组件统一升级到 `9.0.107+`；本次已合并升级到 `9.0.109`。 | 历史扫描信息：`tomcat-embed-core` 当前为 `9.0.99`，进程 PID `1603221`，运行包内子包 `tomcat-embed-core-9.0.99.jar`。<br>本次验证结果：执行 `mvn -pl server/odc-server -am -DskipTests dependency:tree -Dincludes=org.apache.tomcat.embed`，确认 `core/websocket/el` 均为 `9.0.109`。<br>验证方法：1) 部署新包并重启服务；2) 在部署机核验运行包内 Tomcat 子包版本。 | 已修复（代码层），待部署验证 |
| 高危 | Tomcat 目录遍历漏洞(CVE-2025-55752) | 目录遍历 | 将 `tomcat-embed` 相关组件统一升级到 `9.0.109+`；本次已升级到 `9.0.109`。 | 历史扫描信息：`tomcat-embed-core` 当前为 `9.0.99`，进程 PID `1603221`，运行包内子包 `tomcat-embed-core-9.0.99.jar`。<br>本次验证结果：执行 `mvn -pl server/odc-server -am -DskipTests dependency:tree -Dincludes=org.apache.tomcat.embed`，确认 `core/websocket/el` 均为 `9.0.109`。<br>验证方法：1) 部署新包并重启服务；2) 在部署机核验运行包内 Tomcat 子包版本。 | 已修复（代码层），待部署验证 |
| 高危 | Tomcat 竞争条件问题漏洞(CVE-2025-52434) | 拒绝服务攻击 | 将 `tomcat-embed` 相关组件统一升级到 `9.0.107+`；本次已合并升级到 `9.0.109`。 | 历史扫描信息：`tomcat-embed-core` 当前为 `9.0.99`，进程 PID `1603221`，运行包内子包 `tomcat-embed-core-9.0.99.jar`。<br>本次验证结果：执行 `mvn -pl server/odc-server -am -DskipTests dependency:tree -Dincludes=org.apache.tomcat.embed`，确认 `core/websocket/el` 均为 `9.0.109`。<br>验证方法：1) 部署新包并重启服务；2) 在部署机核验运行包内 Tomcat 子包版本。 | 已修复（代码层），待部署验证 |
| 危急 | H2 代码注入漏洞(CVE-2022-23221) | 注入漏洞 | 将 `h2.version` 从 `1.4.200` 升级到 `2.1.214`（满足 `>=2.1.210`），并重点回归 H2 相关 SQL 兼容行为。 | 历史扫描信息：`h2` 当前为 `1.4.200`；进程 PID `1603221`，运行包内子包 `h2-1.4.200.jar`。<br>本次验证结果：执行 `mvn -pl server/odc-server -am -DskipTests dependency:tree -Dincludes=com.h2database:h2`，确认 `odc-server` 实际解析为 `h2:2.1.214`。<br>验证方法：1) 部署新包并重启服务；2) 回归涉及 H2 的初始化/连接场景；3) 在部署机核验运行包中 H2 子包版本。 | 已修复（代码层），待部署验证 |
| 高危 | Spring Security 安全漏洞(CVE-2025-22228) | 未授权访问 | 当前分支为 Spring Security `5.7.12`，公告要求 `5.7.16+`（商业支持版本）。修复路径：优先接入商业仓库后升级到 `5.7.16+`；若走开源免费路线，则需迁移到 Spring Security `6.4.4+`（涉及 Spring Boot 3 / JDK 17 体系升级）。 | 历史扫描信息：`spring-security-crypto` 当前为 `5.7.12`，进程 PID `1603221`，运行包内子包 `spring-security-crypto-5.7.12.jar`。<br>代码核查：`pom.xml` 中 `spring-security.version=5.7.12`，依赖树确认实际解析 `spring-security-crypto:5.7.12`。<br>暂未修复原因：`5.7.16` 在当前仓库不可获取，且直连 `repo.spring.io/release` 返回 `401 Unauthorized`（缺少商业仓库凭证）；`6.4.4` 可获取但与现有技术栈不兼容。<br>验证方法：1) 执行 `mvn -pl server/odc-server -am -DskipTests dependency:tree -Dincludes=org.springframework.security:spring-security-crypto`，确认目标版本已生效；2) 部署后验证认证/授权主流程（登录、鉴权、SSO）。 | 暂未修复 |
| 高危 | Guava 竞争条件漏洞(CVE-2023-2976) | 任意文件上传 | 将 `guava.version` 从 `31.1-jre` 升级到 `32.1.3-jre`（满足 `>=32.0.1-jre`），并统一由根 `pom.xml` 版本属性治理。 | 历史扫描信息：`guava` 当前为 `31.1-jre`，进程 PID `1603221`，运行包内子包 `guava-31.1-jre.jar`。<br>本次验证结果：执行 `mvn -pl server/odc-server -am -DskipTests dependency:tree -Dincludes=com.google.guava:guava`，确认 `odc-server` 实际解析 `guava:32.1.3-jre`。<br>验证方法：1) 部署后回归文件上传及相关任务链路；2) 在部署机核验运行包内 Guava 子包版本。 | 已修复（代码层），待部署验证 |