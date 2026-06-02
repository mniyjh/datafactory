"""
数据工厂综合演示 - 子任务格式化脚本
功能：将过滤后的数据格式化为可读报告
输入：stdin JSON {"filteredData": [...], ...}
"""
import json
import sys
from datetime import datetime


def extract_rows(data):
    if isinstance(data, list):
        return data
    if isinstance(data, dict):
        if "rows" in data:
            return data["rows"]
        if "body" in data:
            body = data["body"]
            if isinstance(body, str):
                try:
                    return json.loads(body)
                except (json.JSONDecodeError, ValueError):
                    return [body]
            return body if isinstance(body, list) else [body]
    return []


def main():
    raw = sys.stdin.read().strip()
    try:
        inputs = json.loads(raw) if raw else {}
    except (json.JSONDecodeError, ValueError):
        inputs = {}

    filtered_data = extract_rows(inputs.get("filteredData", []))

    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    report_lines = [
        "=" * 60,
        f"  数据处理报告 - {now}",
        "=" * 60,
        f"  过滤后记录数: {len(filtered_data)}",
        "-" * 60,
    ]

    for i, record in enumerate(filtered_data, 1):
        report_lines.append(
            f"  {i}. {record.get('emp_name', 'N/A')} "
            f"| {record.get('department', 'N/A')} "
            f"| 薪资: {record.get('salary', 'N/A')}"
        )

    report_lines.append("=" * 60)

    print(json.dumps({
        "report": "\n".join(report_lines),
        "recordCount": len(filtered_data),
        "generatedAt": now
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
