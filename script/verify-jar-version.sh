#!/bin/bash
set -euo pipefail

# =============================================================================
# verify-jar-version.sh
# Verify that JAR metadata modifications have been correctly applied in the
# Fat JAR (Spring Boot executable JAR).
#
# Usage: verify-jar-version.sh <fat-jar-path>
#
# Checks 4 version locations for each target JAR:
#   1. JAR filename
#   2. META-INF/MANIFEST.MF (Implementation-Version)
#   3. META-INF/maven/.../pom.properties (version=)
#   4. META-INF/maven/.../pom.xml (<version>)
#
# Exit codes:
#   0 - All verifications passed
#   1 - One or more verifications failed
#   2 - Invalid arguments or missing Fat JAR
# =============================================================================

EXPECTED_SPRING_VERSION="5.3.41"
EXPECTED_SECURITY_VERSION="5.7.16"

SPRING_FRAMEWORK_JARS="spring-webmvc spring-web spring-core spring-context spring-beans spring-expression spring-aop spring-jcl spring-tx spring-jdbc spring-oxm spring-messaging spring-context-support spring-websocket spring-aspects"
SPRING_SECURITY_JARS="spring-security-core spring-security-config spring-security-web spring-security-crypto spring-security-oauth2-core spring-security-oauth2-jose spring-security-oauth2-client spring-security-oauth2-resource-server spring-security-ldap spring-security-saml2-service-provider"

TOTAL_CHECKS=0
PASSED_CHECKS=0
FAILED_CHECKS=0
FAIL_DETAILS=""

usage() {
    echo "Usage: $0 <fat-jar-path>"
    echo ""
    echo "Verify JAR metadata versions in a Spring Boot Fat JAR."
    echo ""
    echo "Arguments:"
    echo "  fat-jar-path  Path to the Spring Boot executable JAR"
    exit 2
}

record_pass() {
    local jar_name="$1"
    local check_name="$2"
    TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
    PASSED_CHECKS=$((PASSED_CHECKS + 1))
}

record_fail() {
    local jar_name="$1"
    local check_name="$2"
    local expected="$3"
    local actual="$4"
    TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
    FAILED_CHECKS=$((FAILED_CHECKS + 1))
    FAIL_DETAILS="${FAIL_DETAILS}  FAIL: ${jar_name} - ${check_name}: expected '${expected}', got '${actual}'\n"
}

# Verify a single JAR's metadata
# Arguments: jar_file_path expected_version maven_group_prefix
verify_jar() {
    local jar_file="$1"
    local expected_version="$2"
    local maven_group_prefix="$3"
    local filename
    filename=$(basename "$jar_file")

    # Extract JAR to temp directory for inspection
    local jar_tmp
    jar_tmp=$(mktemp -d)

    unzip -q "$jar_file" -d "$jar_tmp" 2>/dev/null || {
        record_fail "$filename" "unzip" "success" "failed to extract"
        rm -rf "$jar_tmp"
        return
    }

    # Check 1: Filename contains expected version
    if echo "$filename" | grep -qF "${expected_version}"; then
        record_pass "$filename" "filename"
    else
        record_fail "$filename" "filename" "contains ${expected_version}" "$filename"
    fi

    # Check 2: MANIFEST.MF Implementation-Version
    local manifest_file="${jar_tmp}/META-INF/MANIFEST.MF"
    if [ -f "$manifest_file" ]; then
        local impl_ver
        impl_ver=$(grep "^Implementation-Version:" "$manifest_file" | head -1 | tr -d '\r' | sed 's/^Implementation-Version: *//')
        if [ "$impl_ver" = "$expected_version" ]; then
            record_pass "$filename" "MANIFEST.MF Implementation-Version"
        else
            record_fail "$filename" "MANIFEST.MF Implementation-Version" "$expected_version" "$impl_ver"
        fi
    else
        record_fail "$filename" "MANIFEST.MF Implementation-Version" "$expected_version" "MANIFEST.MF not found"
    fi

    # Check 3: pom.properties version
    if [ -d "$jar_tmp/META-INF/maven" ]; then
        local props_file
        props_file=$(find "$jar_tmp/META-INF/maven" -name "pom.properties" 2>/dev/null | head -1)
        if [ -n "$props_file" ]; then
            local prop_ver
            prop_ver=$(grep "^version=" "$props_file" | head -1 | cut -d= -f2 | tr -d '\r')
            if [ "$prop_ver" = "$expected_version" ]; then
                record_pass "$filename" "pom.properties version"
            else
                record_fail "$filename" "pom.properties version" "$expected_version" "$prop_ver"
            fi
        else
            echo "    SKIP: ${filename} - pom.properties not found in META-INF/maven"
        fi
    else
        echo "    SKIP: ${filename} - META-INF/maven directory does not exist, pom.properties check skipped"
    fi

    # Check 4: pom.xml version
    if [ -d "$jar_tmp/META-INF/maven" ]; then
        local pom_file
        pom_file=$(find "$jar_tmp/META-INF/maven" -name "pom.xml" 2>/dev/null | head -1)
        if [ -n "$pom_file" ]; then
            # Extract the version from the project-level <version> tag (direct child of <project>)
            # We look for lines containing <version> and pick the first occurrence
            local pom_ver
            pom_ver=$(grep "<version>" "$pom_file" | head -1 | sed 's/.*<version>//' | sed 's/<\/version>.*//' | tr -d ' \r')
            if [ "$pom_ver" = "$expected_version" ]; then
                record_pass "$filename" "pom.xml version"
            else
                record_fail "$filename" "pom.xml version" "$expected_version" "$pom_ver"
            fi
        else
            echo "    SKIP: ${filename} - pom.xml not found in META-INF/maven"
        fi
    else
        echo "    SKIP: ${filename} - META-INF/maven directory does not exist, pom.xml check skipped"
    fi

    rm -rf "$jar_tmp"
}

# =============================================================================
# Main
# =============================================================================

if [ $# -lt 1 ]; then
    usage
fi

FAT_JAR="$1"

if [ ! -f "$FAT_JAR" ]; then
    echo "ERROR: Fat JAR not found: $FAT_JAR"
    exit 2
fi

FAT_JAR_ABS=$(realpath "$FAT_JAR")
WORK_DIR=$(mktemp -d)
trap 'rm -rf "$WORK_DIR"' EXIT

echo "=== Verifying JAR versions in $(basename "$FAT_JAR_ABS") ==="
echo ""

# Extract Fat JAR
unzip -q "$FAT_JAR_ABS" -d "$WORK_DIR"

# Verify Spring Framework JARs
echo "--- Spring Framework (expected: ${EXPECTED_SPRING_VERSION}) ---"
for name in $SPRING_FRAMEWORK_JARS; do
    jar_file=$(find "$WORK_DIR/BOOT-INF/lib" -name "${name}-*.jar" 2>/dev/null | head -1)
    if [ -z "$jar_file" ]; then
        echo "  WARN: ${name} JAR not found in BOOT-INF/lib/, skipping"
        continue
    fi
    verify_jar "$jar_file" "$EXPECTED_SPRING_VERSION" "org.springframework"
    echo "  Checked: $(basename "$jar_file")"
done

echo ""

# Verify Spring Security JARs
echo "--- Spring Security (expected: ${EXPECTED_SECURITY_VERSION}) ---"
for name in $SPRING_SECURITY_JARS; do
    jar_file=$(find "$WORK_DIR/BOOT-INF/lib" -name "${name}-*.jar" 2>/dev/null | head -1)
    if [ -z "$jar_file" ]; then
        echo "  WARN: ${name} JAR not found in BOOT-INF/lib/, skipping"
        continue
    fi
    verify_jar "$jar_file" "$EXPECTED_SECURITY_VERSION" "org.springframework.security"
    echo "  Checked: $(basename "$jar_file")"
done

echo ""

# Summary
echo "=== Verification Summary ==="
echo "Total checks: ${TOTAL_CHECKS}"
echo "Passed: ${PASSED_CHECKS}"
echo "Failed: ${FAILED_CHECKS}"
echo ""

if [ $FAILED_CHECKS -ne 0 ]; then
    echo "Failed checks:"
    echo -e "$FAIL_DETAILS"
    echo "=== VERIFICATION FAILED ==="
    exit 1
else
    echo "=== VERIFICATION PASSED ==="
    exit 0
fi
