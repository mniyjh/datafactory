"""
综合数据分析脚本 - 用于数据工厂 COMP_SCRIPT 组件
功能：合并数据库查询结果与外部 API 数据，进行统计分析和数据增强

输入(stdin JSON):
  {
    "dbEmployees": {"rows": [...], "rowCount": N},
    "apiUsers":   {"statusCode": 200, "body": "[...]"}
  }

输出(stdout JSON):
  {
    "rows": [...enriched_employees...],
    "rowCount": N,
    "stats": {...},
    "enriched": true
  }
"""
import sys
import json
from datetime import datetime


def parse_api_users(body_str):
    """解析外部 API 返回的用户数据"""
    if not body_str:
        return []
    try:
        users = json.loads(body_str) if isinstance(body_str, str) else body_str
        return users if isinstance(users, list) else []
    except (json.JSONDecodeError, TypeError):
        return []


def enrich_employees(employees, external_users):
    """将外部用户数据增强到员工数据上"""
    enriched = []
    for i, emp in enumerate(employees):
        enriched_emp = dict(emp)

        if i < len(external_users):
            ext = external_users[i]
            enriched_emp["external_name"] = ext.get("name", "")
            enriched_emp["external_email"] = ext.get("email", "")
            enriched_emp["external_company"] = (
                ext.get("company", {}).get("name", "") if isinstance(ext.get("company"), dict) else ""
            )
            enriched_emp["external_city"] = (
                ext.get("address", {}).get("city", "") if isinstance(ext.get("address"), dict) else ""
            )
            enriched_emp["external_phone"] = ext.get("phone", "")

        enriched_emp["data_source"] = "comprehensive_analysis"
        enriched_emp["processed_at"] = datetime.now().isoformat()
        enriched.append(enriched_emp)

    return enriched


def compute_stats(employees):
    """计算薪资和部门统计"""
    if not employees:
        return {"avg_salary": 0, "max_salary": 0, "min_salary": 0, "total_count": 0}

    salaries = [float(e.get("salary", 0)) for e in employees]
    departments = set(e.get("department", "未知") for e in employees)
    high_salary = sum(1 for s in salaries if s > 12000)

    return {
        "avg_salary": round(sum(salaries) / len(salaries), 2),
        "max_salary": max(salaries),
        "min_salary": min(salaries),
        "total_count": len(employees),
        "department_count": len(departments),
        "departments": sorted(departments),
        "high_salary_count": high_salary,
        "high_salary_ratio": round(high_salary / len(employees), 4),
    }


def main():
    try:
        raw = sys.stdin.read()
        input_data = json.loads(raw) if raw.strip() else {}
    except (json.JSONDecodeError, Exception):
        input_data = {}

    # 提取上游数据: dbEmployees 来自 DB_QUERY 组件，apiUsers 来自 API_CALL 组件
    db_data = input_data.get("dbEmployees", {})
    api_data = input_data.get("apiUsers", {})

    # 也可支持直接取 rows（如果上游未设 result_var）
    employees = db_data.get("rows", []) if isinstance(db_data, dict) else []
    if not employees:
        # fallback: 从根级别查找
        employees = input_data.get("rows", [])

    external_users = parse_api_users(api_data.get("body", ""))

    # 数据增强
    enriched = enrich_employees(employees, external_users)

    # 统计分析
    stats = compute_stats(enriched)

    # 输出（格式兼容 Filter 组件要求的 rows 结构）
    output = {
        "rows": enriched,
        "rowCount": len(enriched),
        "stats": stats,
        "enriched": len(external_users) > 0,
    }

    print(json.dumps(output, ensure_ascii=False, default=str))


if __name__ == "__main__":
    main()
