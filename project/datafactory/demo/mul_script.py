"""乘法脚本 — 类方法调用模式.

组件: COMP_PYTHON_EXECUTOR  |  类名: Calculator  |  方法名: execute
输入: a, b          |  输出: product, detail
"""

import json, sys, traceback

class Calculator:
    def execute(self, a, b):
        try:
            x, y = float(a), float(b)
        except (ValueError, TypeError) as e:
            return {"success": False, "error": str(e), "product": 0, "detail": ""}
        return {
            "success": True,
            "product": x * y,
            "detail": f"{x} × {y} = {x * y}"
        }

if __name__ == "__main__":
    try:
        raw = sys.stdin.read() or '{"a":"0","b":"0"}'
        d = json.loads(raw)
        r = Calculator().execute(d.get("a","0"), d.get("b","0"))
        print(json.dumps(r, ensure_ascii=False))
    except Exception as e:
        print(json.dumps({"success":False,"error":str(e),"traceback":traceback.format_exc()}, ensure_ascii=False))
        sys.exit(1)
