#!/bin/bash
# 问候脚本 — 接收 name 参数，输出问候语
# 组件: COMP_SHELL_EXECUTOR
# 输入(stdin JSON): {"name": "张三"}
# 输出(stdout JSON): {"greeting": "Hello, 张三!", "timestamp": "2026-06-11 10:30:00"}
INPUT=$(cat)
NAME=$(echo "$INPUT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('name','World'))" 2>/dev/null || echo "World")
TS=$(date '+%Y-%m-%d %H:%M:%S' 2>/dev/null || echo "unknown")
echo "{\"greeting\": \"Hello, ${NAME}!\", \"timestamp\": \"${TS}\"}"
