#!/bin/bash
# ============================================
# BlossomGW: Hystrix → Resilience4j 迁移验证脚本
# 请在项目根目录执行: ./verify-migration.sh
# 前置条件: Maven 3.x + JDK 11+
# ============================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

PASS=0
FAIL=0

check() {
    local desc=$1
    local result=$2
    if [ "$result" = "0" ]; then
        echo -e "  ${GREEN}✓${NC} $desc"
        PASS=$((PASS+1))
    else
        echo -e "  ${RED}✗${NC} $desc"
        FAIL=$((FAIL+1))
    fi
}

echo ""
echo "=========================================="
echo " BlossomGW Migration Verification"
echo " Hystrix 1.5.12 → Resilience4j 2.1.0"
echo "=========================================="
echo ""

# ── Test 1: 依赖检查 ──
echo "📦 [1/5] Dependency Check"

# Hystrix 应该完全移除
hystrix_count=$(grep -ci "hystrix" gateway-http-core/pom.xml 2>/dev/null; true)
check "No Hystrix in pom.xml (found: $hystrix_count)" "$( [ "$hystrix_count" = "0" ] && echo 0 || echo 1 )"

# Resilience4j 依赖应该存在
r4j_cb=$(grep -c "resilience4j-circuitbreaker" gateway-http-core/pom.xml 2>/dev/null || echo "0")
check "resilience4j-circuitbreaker dependency present" "$( [ "$r4j_cb" -gt "0" ] && echo 0 || echo 1 )"

r4j_tl=$(grep -c "resilience4j-timelimiter" gateway-http-core/pom.xml 2>/dev/null || echo "0")
check "resilience4j-timelimiter dependency present" "$( [ "$r4j_tl" -gt "0" ] && echo 0 || echo 1 )"

r4j_ver=$(grep -c "resilience4j.version" gateway-http-core/pom.xml 2>/dev/null || echo "0")
check "resilience4j version property defined" "$( [ "$r4j_ver" -gt "0" ] && echo 0 || echo 1 )"

echo ""

# ── Test 2: Java Import 检查 ──
echo "☕ [2/5] Java Import Check"

hystrix_imports=$(grep -r "import com.netflix.hystrix" gateway-http-core/src/ --include="*.java" 2>/dev/null | wc -l)
check "No Hystrix imports in Java source" "$( [ "$hystrix_imports" = "0" ] && echo 0 || echo 1 )"

r4j_imports=$(grep -r "import io.github.resilience4j" gateway-http-core/src/ --include="*.java" 2>/dev/null | wc -l)
check "Resilience4j imports present ($r4j_imports found)" "$( [ "$r4j_imports" -gt "0" ] && echo 0 || echo 1 )"

cb_import=$(grep -r "import io.github.resilience4j.circuitbreaker.CircuitBreaker;" gateway-http-core/src/ --include="*.java" 2>/dev/null | wc -l)
check "CircuitBreaker import in RouterFilter" "$( [ "$cb_import" -gt "0" ] && echo 0 || echo 1 )"

tl_import=$(grep -r "import io.github.resilience4j.timelimiter.TimeLimiter;" gateway-http-core/src/ --include="*.java" 2>/dev/null | wc -l)
check "TimeLimiter import in RouterFilter" "$( [ "$tl_import" -gt "0" ] && echo 0 || echo 1 )"

cnpe_import=$(grep -r "CallNotPermittedException" gateway-http-core/src/ --include="*.java" 2>/dev/null | wc -l)
check "CallNotPermittedException handling present ($cnpe_import refs)" "$( [ "$cnpe_import" -gt "0" ] && echo 0 || echo 1 )"

echo ""

# ── Test 3: 功能逻辑检查 ──
echo "🔧 [3/5] Functional Logic Check"

# CircuitBreaker.of() 应该存在
cb_of=$(grep -c "CircuitBreaker.of(" gateway-http-core/src/main/java/blossom/project/core/filter/router/RouterFilter.java 2>/dev/null || echo "0")
check "CircuitBreaker.of() instantiation" "$( [ "$cb_of" -gt "0" ] && echo 0 || echo 1 )"

# TimeLimiter.of() 应该存在
tl_of=$(grep -c "TimeLimiter.of(" gateway-http-core/src/main/java/blossom/project/core/filter/router/RouterFilter.java 2>/dev/null || echo "0")
check "TimeLimiter.of() instantiation" "$( [ "$tl_of" -gt "0" ] && echo 0 || echo 1 )"

# tryAcquirePermission() 快速失败检查
try_acquire=$(grep -c "tryAcquirePermission" gateway-http-core/src/main/java/blossom/project/core/filter/router/RouterFilter.java 2>/dev/null || echo "0")
check "tryAcquirePermission() fast-fail check" "$( [ "$try_acquire" -gt "0" ] && echo 0 || echo 1 )"

# onSuccess/onError 手动记录
on_success=$(grep -c "circuitBreaker.onSuccess" gateway-http-core/src/main/java/blossom/project/core/filter/router/RouterFilter.java 2>/dev/null || echo "0")
check "circuitBreaker.onSuccess() recording" "$( [ "$on_success" -gt "0" ] && echo 0 || echo 1 )"

on_error=$(grep -c "circuitBreaker.onError" gateway-http-core/src/main/java/blossom/project/core/filter/router/RouterFilter.java 2>/dev/null || echo "0")
check "circuitBreaker.onError() recording" "$( [ "$on_error" -gt "0" ] && echo 0 || echo 1 )"

# 非阻塞检查: 不应该有 .join() 在 executeSupplier 内
blocking_join=$(grep "executeSupplier.*join" gateway-http-core/src/main/java/blossom/project/core/filter/router/RouterFilter.java 2>/dev/null | wc -l)
check "No blocking .join() inside executeSupplier" "$( [ "$blocking_join" = "0" ] && echo 0 || echo 1 )"

# fallback 逻辑
fallback=$(grep -c "handleCircuitBreakerFailure" gateway-http-core/src/main/java/blossom/project/core/filter/router/RouterFilter.java 2>/dev/null || echo "0")
check "Fallback handler present ($fallback call sites)" "$( [ "$fallback" -gt "2" ] && echo 0 || echo 1 )"

echo ""

# ── Test 4: Config 模型检查 ──
echo "⚙️  [4/5] Config Model Check"

failure_threshold=$(grep -c "failureRateThreshold" gateway-common/src/main/java/blossom/project/common/config/Rule.java 2>/dev/null || echo "0")
check "failureRateThreshold field in config" "$( [ "$failure_threshold" -gt "0" ] && echo 0 || echo 1 )"

wait_duration=$(grep -c "waitDurationInOpenState" gateway-common/src/main/java/blossom/project/common/config/Rule.java 2>/dev/null || echo "0")
check "waitDurationInOpenState field in config" "$( [ "$wait_duration" -gt "0" ] && echo 0 || echo 1 )"

echo ""

# ── Test 5: Maven 编译 ──
echo "🏗️  [5/5] Maven Compile Test"

if command -v mvn &> /dev/null; then
    echo "  Running: mvn clean compile -DskipTests -q ..."
    if mvn clean compile -DskipTests -q 2>&1; then
        check "Maven compile SUCCESS" "0"
    else
        check "Maven compile FAILED" "1"
        echo -e "  ${YELLOW}Run 'mvn clean compile -DskipTests' manually to see errors${NC}"
    fi
else
    echo -e "  ${YELLOW}⚠ Maven not found, skipping compile test${NC}"
    echo -e "  ${YELLOW}  Please run manually: mvn clean compile -DskipTests${NC}"
fi

echo ""
echo "=========================================="
echo -e " Results: ${GREEN}${PASS} passed${NC}, ${RED}${FAIL} failed${NC}"
echo "=========================================="

if [ "$FAIL" -gt "0" ]; then
    echo -e " ${RED}Some checks failed. Please review above.${NC}"
    exit 1
else
    echo -e " ${GREEN}All checks passed! Migration looks correct.${NC}"
    echo ""
    echo " Next steps:"
    echo "   1. mvn clean compile -DskipTests  (if not done above)"
    echo "   2. mvn test                        (run unit tests)"
    echo "   3. Start Nacos + gateway for integration test"
    echo "   4. curl http://localhost:9999/... to verify routing"
    exit 0
fi
