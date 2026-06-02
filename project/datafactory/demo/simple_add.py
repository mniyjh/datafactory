"""加法脚本 — 类方法调用模式.

类名: Calculator
方法名: execute
参数: a, b (来自上游输入参数)
返回: {"sum": a+b, "rows": [{...}], "rowCount": 1}
"""


class Calculator:
    def execute(self, a, b):
        x = float(a)
        y = float(b)
        s = x + y
        return {
            "sum": s,
            "rows": [{"sum": s, "a": x, "b": y}],
            "rowCount": 1,
        }
