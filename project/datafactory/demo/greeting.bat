@echo off
REM 问候脚本(Windows) — 接收 name 参数，输出问候语
REM 组件: COMP_SHELL_EXECUTOR
REM 输入(stdin JSON): {"name": "张三"}
REM 输出(stdout JSON): {"greeting": "Hello, 张三!", "timestamp": "2026-06-11 10:30:00"}
python -c "import sys,json; d=json.load(sys.stdin); print(json.dumps({'greeting': f'Hello, {d.get(\"name\",\"World\")}!', 'timestamp': 'ok'}))"
