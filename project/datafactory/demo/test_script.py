"""
脚本测试页面的测试脚本 —— 快速验证脚本管理测试功能是否正常
用法：在脚本管理注册 → 创建版本(解释器路径填 python) → 晋升到TEST → 打开测试页面执行
"""
import json
import sys
import platform
from datetime import datetime


def main():
    raw = sys.stdin.read().strip()
    try:
        inputs = json.loads(raw) if raw else {}
    except (json.JSONDecodeError, ValueError):
        inputs = {}

    name = inputs.get("name", "World")
    repeat = int(inputs.get("repeat", 3))

    lines = []
    for i in range(1, repeat + 1):
        lines.append(f"{i}. Hello, {name}!")

    result = {
        "success": True,
        "server": {
            "platform": platform.system(),
            "python": sys.version.split()[0],
            "time": datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        },
        "inputReceived": inputs,
        "output": lines
    }

    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
