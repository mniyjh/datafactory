"""加法计算脚本 — 类方法调用模式.

组件类型: COMP_SCRIPT (脚本执行)
类名: Calculator
方法名: execute
参数: a, b (来自上游节点输出参数)
返回格式: {"sum": 两数之和, "product": 两数之积, "detail": 计算明细, "rows": [...], "rowCount": ...}

适用场景:
  开始节点(START) → 脚本执行(SCRIPT) → 结束节点(END)  的最简流水线

调用方式:
  引擎通过 gRPC 将 inputParams 序列化为 JSON 传给 Python 进程的 stdin:
    {"a": "5", "b": "3"}
  Python 进程将计算结果写入 stdout，引擎解析后按 outputParams 白名单提取字段.
"""

import json
import sys
import traceback


class Calculator:
    """加法计算器.

    该类被 ScriptPlugin 通过 className/methodName 反射调用.
    所有输入参数均为字符串类型，需要在 execute 内部做类型转换.
    """

    def execute(self, a, b):
        """执行加法计算.

        Args:
            a: 加数a（字符串或数值）
            b: 加数b（字符串或数值）

        Returns:
            dict: 包含 sum, product, detail, rows, rowCount 五个字段
        """
        try:
            x = float(a)
            y = float(b)
        except (ValueError, TypeError) as e:
            return {
                "success": False,
                "error": f"参数类型错误: a={a}, b={b}, 原因: {str(e)}",
                "sum": 0,
                "product": 0,
                "detail": "",
                "rows": [],
                "rowCount": 0,
            }

        total = x + y
        prod = x * y

        return {
            "success": True,
            "sum": total,
            "product": prod,
            "detail": f"{x} + {y} = {total}，{x} × {y} = {prod}",
            "rows": [
                {"a": x, "b": y, "sum": total, "product": prod}
            ],
            "rowCount": 1,
        }


# ============================================================
# 以下为引擎调用入口（由 ScriptPlugin 通过 subprocess 调用）
# ============================================================
if __name__ == "__main__":
    exit_code = 0
    try:
        raw = sys.stdin.read()
        if not raw.strip():
            # 无输入时使用默认值，确保脚本不会因空输入崩溃
            raw = '{"a": "0", "b": "0"}'

        inputs = json.loads(raw)
        a_val = inputs.get("a", "0")
        b_val = inputs.get("b", "0")

        calculator = Calculator()
        result = calculator.execute(a_val, b_val)

        print(json.dumps(result, ensure_ascii=False))
    except Exception as e:
        print(json.dumps({
            "success": False,
            "error": str(e),
            "traceback": traceback.format_exc(),
        }, ensure_ascii=False))
        exit_code = 1
    sys.exit(exit_code)
