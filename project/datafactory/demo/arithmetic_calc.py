"""四则运算脚本 — 类方法调用模式.

组件类型: COMP_PYTHON_EXECUTOR (PYTHON执行器)
类名: Calculator
方法名: execute
参数: a, b, op (来自上游节点)
   a  - 左操作数
   b  - 右操作数
   op - 运算符: + - * /

返回格式: {"result": 计算结果, "detail": 计算明细, "rows": [...], "rowCount": 1}

适用场景:
  START(传a,b,op) → SCRIPT(计算) → BRANCH(按结果分路) → END
"""

import json
import sys
import traceback


class Calculator:
    def execute(self, a, b, op):
        try:
            x = float(a)
            y = float(b)
        except (ValueError, TypeError) as e:
            return {
                "success": False,
                "error": f"参数类型错误: a={a}, b={b}",
                "result": 0,
                "detail": str(e),
                "rows": [],
                "rowCount": 0,
            }

        op_map = {
            "+": ("add", x + y),
            "-": ("sub", x - y),
            "*": ("mul", x * y),
            "/": ("div", x / y if y != 0 else float('inf')),
        }

        if op not in op_map:
            return {
                "success": False,
                "error": f"不支持的运算符: {op}",
                "result": 0,
                "detail": f"仅支持 + - * /",
                "rows": [],
                "rowCount": 0,
            }

        op_name, value = op_map[op]

        return {
            "success": True,
            "result": value,
            "detail": f"{x} {op} {y} = {value}",
            "rows": [{"a": x, "b": y, "op": op, "result": value}],
            "rowCount": 1,
        }


if __name__ == "__main__":
    exit_code = 0
    try:
        raw = sys.stdin.read()
        if not raw.strip():
            raw = '{"a": "10", "b": "5", "op": "+"}'

        inputs = json.loads(raw)
        a_val = inputs.get("a", "0")
        b_val = inputs.get("b", "0")
        op_val = inputs.get("op", "+")

        calculator = Calculator()
        result = calculator.execute(a_val, b_val, op_val)

        print(json.dumps(result, ensure_ascii=False))
    except Exception as e:
        print(json.dumps({
            "success": False,
            "error": str(e),
            "traceback": traceback.format_exc(),
        }, ensure_ascii=False))
        exit_code = 1
    sys.exit(exit_code)
