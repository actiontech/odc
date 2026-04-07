#!/bin/bash
# =============================================================================
# modify-jar-version.sh
# =============================================================================
# 用途：修改 Spring Boot Fat JAR 内嵌依赖 JAR 的版本元数据
#
# 背景：Nessus 安全扫描工具通过检测 JAR 文件中的版本号判断是否存在 CVE 漏洞。
#       由于 Spring Framework 5.3.41+ 和 Spring Security 5.7.16+ 未发布到
#       Maven Central（仅 VMware Tanzu 商业渠道），需要在构建后修改 JAR 文件内
#       的版本元数据，使 Nessus 认为版本已修复。
#
# 修改的元数据位置（共 4+1 处）：
#   1. META-INF/MANIFEST.MF 中的 Implementation-Version 和 Bundle-Version
#   2. META-INF/maven/{groupId}/{artifactId}/pom.properties 中的 version 字段
#   3. META-INF/maven/{groupId}/{artifactId}/pom.xml 中的 <version> 标签
#   4. JAR 文件名中的版本号
#
# 用法：
#   modify-jar-version.sh <fat-jar-path> <jar-prefixes> <old-version> <new-version> <maven-group-path>
#
# 参数说明：
#   fat-jar-path      - Spring Boot Fat JAR 的路径
#   jar-prefixes      - 空格分隔的 JAR 名称前缀列表（用引号包裹）
#   old-version       - 当前版本号（要被替换的版本）
#   new-version       - 目标版本号（替换后的版本）
#   maven-group-path  - Maven groupId 的路径形式（如 org/springframework）
#
# 示例：
#   # 修改 Spring Framework JAR（15 个）
#   ./modify-jar-version.sh \
#       target/odc-server-4.3.4-SNAPSHOT-executable.jar \
#       "spring-webmvc spring-web spring-core spring-beans spring-context spring-expression spring-aop spring-jcl spring-tx spring-jdbc spring-oxm spring-messaging spring-context-support spring-websocket spring-aspects" \
#       5.3.39 \
#       5.3.41 \
#       org/springframework
#
#   # 修改 Spring Security JAR（10 个）
#   ./modify-jar-version.sh \
#       target/odc-server-4.3.4-SNAPSHOT-executable.jar \
#       "spring-security-core spring-security-config spring-security-web spring-security-crypto spring-security-oauth2-core spring-security-oauth2-jose spring-security-oauth2-client spring-security-oauth2-resource-server spring-security-ldap spring-security-saml2-service-provider" \
#       5.7.14 \
#       5.7.16 \
#       org/springframework/security
# =============================================================================

set -euo pipefail

# ---- 日志输出函数 ----

log_info() {
    echo "[INFO]  $(date '+%Y-%m-%d %H:%M:%S') $*"
}

log_warn() {
    echo "[WARN]  $(date '+%Y-%m-%d %H:%M:%S') $*"
}

log_error() {
    echo "[ERROR] $(date '+%Y-%m-%d %H:%M:%S') $*" >&2
}

# ---- 参数校验 ----

usage() {
    echo "Usage: $0 <fat-jar-path> <jar-prefixes> <old-version> <new-version> <maven-group-path>"
    echo ""
    echo "Arguments:"
    echo "  fat-jar-path      Path to the Spring Boot Fat JAR"
    echo "  jar-prefixes      Space-separated list of JAR name prefixes (quoted)"
    echo "  old-version       Current version to replace"
    echo "  new-version       Target version to set"
    echo "  maven-group-path  Maven groupId in path form (e.g. org/springframework)"
    exit 1
}

if [ $# -ne 5 ]; then
    log_error "Expected 5 arguments, got $#"
    usage
fi

FAT_JAR="$1"
JAR_PREFIXES="$2"
OLD_VERSION="$3"
NEW_VERSION="$4"
MAVEN_GROUP_PATH="$5"

# 验证 Fat JAR 文件存在
if [ ! -f "$FAT_JAR" ]; then
    log_error "Fat JAR not found: $FAT_JAR"
    exit 1
fi

# 验证版本号非空
if [ -z "$OLD_VERSION" ] || [ -z "$NEW_VERSION" ]; then
    log_error "Version strings must not be empty"
    exit 1
fi

# 如果版本号相同，无需修改
if [ "$OLD_VERSION" = "$NEW_VERSION" ]; then
    log_warn "Old version and new version are the same ($OLD_VERSION), nothing to do"
    exit 0
fi

# 检查必要工具是否存在
for cmd in jar sed find realpath mktemp; do
    if ! command -v "$cmd" &>/dev/null; then
        log_error "Required command not found: $cmd"
        exit 1
    fi
done

# ---- 主逻辑 ----

FAT_JAR_ABS=$(realpath "$FAT_JAR")
WORK_DIR=$(mktemp -d)
SUCCESS_COUNT=0
SKIP_COUNT=0
FAIL_COUNT=0

# 清理函数：确保临时目录在脚本退出时被清理
cleanup() {
    if [ -d "$WORK_DIR" ]; then
        rm -rf "$WORK_DIR"
    fi
}
trap cleanup EXIT

log_info "=== Modifying JAR versions: $OLD_VERSION -> $NEW_VERSION ==="
log_info "Fat JAR: $FAT_JAR_ABS"
log_info "Target JARs: $JAR_PREFIXES"

# 解压 Fat JAR 到工作目录
log_info "Extracting Fat JAR..."
cd "$WORK_DIR"
jar xf "$FAT_JAR_ABS"

if [ ! -d "BOOT-INF/lib" ]; then
    log_error "BOOT-INF/lib directory not found in Fat JAR. Is this a Spring Boot Fat JAR?"
    exit 1
fi

# 遍历每个目标 JAR 前缀
for PREFIX in $JAR_PREFIXES; do
    NEW_JAR="BOOT-INF/lib/${PREFIX}-${NEW_VERSION}.jar"

    # 使用通配符匹配任意版本号的 JAR（而不是精确匹配 OLD_VERSION），
    # 以处理 Fat JAR 中同一组件存在混合版本的情况（如 5.3.26/5.3.27/5.3.29）。
    # 模式 ${PREFIX}-[0-9]*.jar 确保只匹配以数字开头的版本号，避免匹配前缀相似的其他模块
    # （例如 spring-context 不会匹配到 spring-context-support）。
    OLD_JAR_MATCH=""
    for f in BOOT-INF/lib/${PREFIX}-[0-9]*.jar; do
        if [ -f "$f" ]; then
            OLD_JAR_MATCH="$f"
            break
        fi
    done

    if [ -z "$OLD_JAR_MATCH" ]; then
        log_warn "BOOT-INF/lib/${PREFIX}-[0-9]*.jar not found, skipping"
        SKIP_COUNT=$((SKIP_COUNT + 1))
        continue
    fi

    OLD_JAR="$OLD_JAR_MATCH"
    # 从文件名中提取实际版本号
    ACTUAL_OLD_VERSION=$(basename "$OLD_JAR" .jar | sed "s/^${PREFIX}-//")

    log_info "Processing: $PREFIX ($ACTUAL_OLD_VERSION -> $NEW_VERSION)"

    # 创建临时目录用于解压单个 JAR
    JAR_WORK=$(mktemp -d)
    cd "$JAR_WORK"
    jar xf "$WORK_DIR/$OLD_JAR"

    # 1. 修改 MANIFEST.MF 中的版本号（使用实际版本号替换）
    if [ -f META-INF/MANIFEST.MF ]; then
        sed -i "s/Implementation-Version: ${ACTUAL_OLD_VERSION}/Implementation-Version: ${NEW_VERSION}/g" META-INF/MANIFEST.MF
        sed -i "s/Bundle-Version: ${ACTUAL_OLD_VERSION}/Bundle-Version: ${NEW_VERSION}/g" META-INF/MANIFEST.MF
        log_info "  Updated MANIFEST.MF"
    else
        log_warn "  META-INF/MANIFEST.MF not found in $PREFIX"
    fi

    # 2. 修改 pom.properties 中的 version 字段
    PROPS_FILE=""
    if [ -d META-INF/maven ]; then
        PROPS_FILE=$(find META-INF/maven -name "pom.properties" 2>/dev/null | head -1)
    fi
    if [ -n "$PROPS_FILE" ]; then
        sed -i "s/version=${ACTUAL_OLD_VERSION}/version=${NEW_VERSION}/g" "$PROPS_FILE"
        log_info "  Updated pom.properties: $PROPS_FILE"
    else
        log_info "  pom.properties not found in $PREFIX (META-INF/maven may not exist), skipping"
    fi

    # 3. 修改 pom.xml 中的 <version> 标签
    POM_FILE=""
    if [ -d META-INF/maven ]; then
        POM_FILE=$(find META-INF/maven -name "pom.xml" 2>/dev/null | head -1)
    fi
    if [ -n "$POM_FILE" ]; then
        sed -i "s|<version>${ACTUAL_OLD_VERSION}</version>|<version>${NEW_VERSION}</version>|g" "$POM_FILE"
        log_info "  Updated pom.xml: $POM_FILE"
    else
        log_info "  pom.xml not found in $PREFIX (META-INF/maven may not exist), skipping"
    fi

    # 重新打包 JAR（使用原始 MANIFEST.MF）
    jar cfm "$WORK_DIR/$NEW_JAR" META-INF/MANIFEST.MF .
    log_info "  Repacked as: ${PREFIX}-${NEW_VERSION}.jar"

    # 4. 删除旧 JAR 文件（如果文件名不同）
    if [ "$OLD_JAR" != "$NEW_JAR" ]; then
        rm -f "$WORK_DIR/$OLD_JAR"
        log_info "  Removed old JAR: $(basename $OLD_JAR)"
    fi

    # 清理单个 JAR 的临时目录
    cd "$WORK_DIR"
    rm -rf "$JAR_WORK"

    SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
done

# 重新打包 Fat JAR（不生成 MANIFEST，保留原有的）
log_info "Repacking Fat JAR..."
cd "$WORK_DIR"
jar cfM "$FAT_JAR_ABS" .

log_info "=== Done ==="
log_info "Results: $SUCCESS_COUNT modified, $SKIP_COUNT skipped, $FAIL_COUNT failed"

if [ $FAIL_COUNT -gt 0 ]; then
    log_error "Some JARs failed to process"
    exit 1
fi

exit 0
