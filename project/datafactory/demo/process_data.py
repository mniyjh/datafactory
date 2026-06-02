"""
数据工厂综合演示 - 主任务数据处理脚本
功能：合并DB员工数据与API外部用户，按部门统计薪资
输入：stdin JSON，上游节点的 result_var 输出
"""
import json
import sys
from datetime import date


def extract_rows(data):
    """兼容两种上游输出：{rows:[...]} 或直接数组"""
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

    db_employees = extract_rows(inputs.get("dbEmployees", []))
    api_users = extract_rows(inputs.get("apiUsers", []))

    merged = []
    for emp in db_employees:
        emp["dataSource"] = "database"
        merged.append(emp)

    for user in api_users:
        merged.append({
            "emp_name": user.get("name", ""),
            "department": user.get("company", {}).get("name", "外部"),
            "salary": None,
            "hire_date": None,
            "status": 1,
            "dataSource": "api",
            "email": user.get("email", ""),
            "phone": user.get("phone", ""),
            "city": user.get("address", {}).get("city", "")
        })

    dept_stats = {}
    for emp in db_employees:
        dept = emp.get("department", "未知")
        salary = emp.get("salary") or 0
        if dept not in dept_stats:
            dept_stats[dept] = {"count": 0, "totalSalary": 0, "avgSalary": 0}
        dept_stats[dept]["count"] += 1
        dept_stats[dept]["totalSalary"] += float(salary)

    for dept, stat in dept_stats.items():
        stat["avgSalary"] = round(stat["totalSalary"] / stat["count"], 2)

    print(json.dumps({
        "success": True,
        "totalRecords": len(merged),
        "dbRecords": len(db_employees),
        "apiRecords": len(api_users),
        "departmentStats": dept_stats,
        "mergedData": merged,
        "processedAt": str(date.today())
    }, ensure_ascii=False, default=str))


if __name__ == "__main__":
    main()
