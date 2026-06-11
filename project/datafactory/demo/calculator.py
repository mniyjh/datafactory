"""计算器脚本 — 接收两个数字，返回计算结果
组件: COMP_PYTHON_EXECUTOR | 类名: Calculator | 方法名: execute
输入(stdin JSON): {"a": 10, "b": 3}
输出(stdout JSON): {"sum": 13, "product": 30, "difference": 7, "quotient": 3.33, "detail": "..."}
"""
import sys, json

class Calculator:
    def execute(self, a, b):
        a = float(a)
        b = float(b)
        return {
            "sum": round(a + b, 2),
            "product": round(a * b, 2),
            "difference": round(a - b, 2),
            "quotient": round(a / b, 2) if b != 0 else None,
            "detail": f"{a} + {b} = {a + b}, {a} * {b} = {a * b}"
        }
