#!/bin/bash
# DataFactory 功能测试脚本
# 用法: bash test-features.sh
# 前提: Nacos + MySQL + 三服务已启动

BASE="http://127.0.0.1:8081"
echo "============================================"
echo " DataFactory 功能测试"
echo "============================================"
PASS=0
FAIL=0

check() {
  local name="$1"; local expect="$2"; local actual="$3"
  if echo "$actual" | grep -q "$expect"; then
    echo "  ✅ $name"; PASS=$((PASS+1))
  else
    echo "  ❌ $name — 预期含 '$expect'，实际: $(echo "$actual" | head -c 100)"; FAIL=$((FAIL+1))
  fi
}

# ── 1. 脚本管理 CRUD ──
echo; echo "── 1. 脚本管理 ──"

# 新建 Python 脚本
res=$(curl -s -X POST "$BASE/script" -H "Content-Type: application/json" \
  -d '{"code":"TEST_PY_001","name":"test add script","type":"PYTHON","status":"启用","desc":"测试用"}')
script_id=$(echo "$res" | python -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('id',''))" 2>/dev/null)
check "创建 Python 脚本" '"code":0' "$res"

# 新建 Shell 脚本
res=$(curl -s -X POST "$BASE/script" -H "Content-Type: application/json" \
  -d '{"code":"TEST_SH_001","name":"test shell script","type":"SHELL","status":"启用","desc":"测试用"}')
check "创建 Shell 脚本" '"code":0' "$res"

# 新建 SQL 脚本
res=$(curl -s -X POST "$BASE/script" -H "Content-Type: application/json" \
  -d '{"code":"TEST_SQL_001","name":"test sql script","type":"SQL","status":"启用","desc":"测试用"}')
check "创建 SQL 脚本" '"code":0' "$res"

# ── 2. 脚本版本创建（测试 ScriptVersionEditor 各类型字段） ──
echo; echo "── 2. 脚本版本 ──"

# Python 版本
res=$(curl -s -X POST "$BASE/script/version" -H "Content-Type: application/json" \
  -d "{\"scriptId\":$script_id,\"environment\":\"DEV\",\"version\":\"1.0.0\",\"scriptCode\":\"TEST_PY_001\",\"scriptCodeContent\":\"print('hello')\",\"timeout\":10,\"interpreterPath\":\"python\",\"envVars\":\"{\\\"KEY\\\":\\\"val\\\"}\",\"dependencies\":\"[\\\"requests==2.28.0\\\"]\",\"changeLog\":\"init\"}")
check "Python 版本(含 envVars+dependencies)" '"code":0' "$res"

# SQL 版本（不应有 interpreterPath/envVars — 测试前端动态字段）
res=$(curl -s -X POST "$BASE/script/version" -H "Content-Type: application/json" \
  -d '{"scriptId":2,"environment":"DEV","version":"1.0.0","scriptCode":"TEST_SQL_001","scriptCodeContent":"SELECT 1","timeout":5,"changeLog":"init"}')
check "SQL 版本" '"code":0' "$res"

# Shell 版本
res=$(curl -s -X POST "$BASE/script/version" -H "Content-Type: application/json" \
  -d '{"scriptId":3,"environment":"DEV","version":"1.0.0","scriptCode":"TEST_SH_001","scriptCodeContent":"echo hello","timeout":10,"interpreterPath":"/bin/bash","workDir":"/tmp","changeLog":"init"}')
check "Shell 版本" '"code":0' "$res"

# ── 3. 组件管理（COMP_SHELL_EXECUTOR 是否存在） ──
echo; echo "── 3. 组件管理 ──"

res=$(curl -s "$BASE/script/simple")
check "脚本列表(含SQL/Shell/Python)" '"label"' "$res"

res=$(curl -s "$BASE/component/page?current=1&size=20")
check "组件列表含 Shell执行器" "Shell执行器" "$res"

# ── 4. Open API 认证+限流 ──
echo; echo "── 4. Open API ──"

# 创建带认证限流的 API
res=$(curl -s -X POST "$BASE/open-api" -H "Content-Type: application/json" \
  -d '{"code":"TEST_OPENAPI_001","name":"test api","path":"/test","method":"POST","taskId":1,"authType":"AppSecret","limit":2,"status":"启用"}')
check "创建 OpenAPI(带认证限流)" '"code":0' "$res"

# ── 5. OpenAPI 调用（不带 secret 应失败） ──
echo; echo "── 5. OpenAPI 认证 ──"

res=$(curl -s -X POST "$BASE/open-api/invoke/TEST_OPENAPI_001" -H "Content-Type: application/json" -d '{}')
check "不带 secret 调用失败" "失败" "$res"

# ── 6. 执行器状态 ──
executor_url="http://127.0.0.1:8082"
res=$(curl -s -o /dev/null -w "%{http_code}" "$executor_url/tasks/page?current=1&size=1" 2>/dev/null)
if [ "$res" = "200" ]; then
  echo "  ✅ Executor 可达 (8082)"; PASS=$((PASS+1))
else
  echo "  ❌ Executor 不可达 (8082) — 请启动服务"; FAIL=$((FAIL+1))
fi

# ── 7. 网关状态 ──
res=$(curl -s -o /dev/null -w "%{http_code}" "http://127.0.0.1:8080/tasks/page?current=1&size=1" 2>/dev/null)
if [ "$res" = "200" ]; then
  echo "  ✅ Gateway 可达 (8080)"; PASS=$((PASS+1))
else
  echo "  ❌ Gateway 不可达 (8080) — 请启动服务"; FAIL=$((FAIL+1))
fi

# ── 结果 ──
echo
echo "============================================"
echo " 测试结果: $PASS 通过, $FAIL 失败"
echo "============================================"
