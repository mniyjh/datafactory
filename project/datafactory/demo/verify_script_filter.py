"""
验证性脚本 - 用于测试 COMP_SCRIPT + COMP_FILTER 组件联动
功能：接收输入参数，生成模拟订单数据，输出 rows 供 FILTER 组件过滤

输入(stdin JSON):
  {
    "dataCount": 10,         # 生成的数据条数
    "minAmount": 100,        # 最低金额
    "maxAmount": 5000,       # 最高金额
    "categoryList": "食品,电子,服装,家居,运动"   # 商品类别列表
  }

输出(stdout JSON):
  {
    "rows": [  {商品名, 类别, 金额, 数量, 总价, 日期}, ...  ],
    "rowCount": 10,
    "summary": {总金额, 平均金额, 最高金额, 最低金额}
  }
"""
import sys, json, random
from datetime import datetime, timedelta


def generate_orders(data_count, min_amount, max_amount, categories):
    products = {
        "食品": ["薯片", "牛奶", "面包", "巧克力", "坚果", "饼干", "果汁"],
        "电子": ["耳机", "鼠标", "键盘", "U盘", "数据线", "充电宝", "手机壳"],
        "服装": ["T恤", "牛仔裤", "卫衣", "运动鞋", "帽子", "袜子", "围巾"],
        "家居": ["台灯", "抱枕", "地毯", "窗帘", "收纳盒", "花瓶", "闹钟"],
        "运动": ["瑜伽垫", "哑铃", "跳绳", "护膝", "运动水壶", "臂包", "速干衣"],
    }

    rows = []
    base_date = datetime.now()
    for i in range(data_count):
        cat = random.choice(categories)
        product = random.choice(products.get(cat, ["商品X"]))
        price = round(random.uniform(min_amount, max_amount), 2)
        qty = random.randint(1, 5)
        total = round(price * qty, 2)
        order_date = (base_date - timedelta(days=random.randint(0, 60))).strftime("%Y-%m-%d")

        rows.append({
            "id": i + 1,
            "product": product,
            "category": cat,
            "unitPrice": price,
            "quantity": qty,
            "totalAmount": total,
            "orderDate": order_date,
        })

    amounts = [r["totalAmount"] for r in rows]
    summary = {
        "totalAmount": round(sum(amounts), 2),
        "avgAmount": round(sum(amounts) / len(amounts), 2),
        "maxAmount": max(amounts),
        "minAmount": min(amounts),
    }

    output = {
        "rows": sorted(rows, key=lambda r: r["totalAmount"], reverse=True),
        "rowCount": len(rows),
        "summary": summary,
    }
    print(json.dumps(output, ensure_ascii=False))


def main():
    try:
        raw = sys.stdin.read().strip()
        data = json.loads(raw) if raw else {}
    except Exception:
        data = {}

    def safe_int(val, default=10):
        if val is None or val == '' or val == 'null':
            return default
        return int(val)

    def safe_float(val, default=100.0):
        if val is None or val == '' or val == 'null':
            return default
        return float(val)

    def safe_str(val, default=''):
        if val is None or val == 'null':
            return default
        return str(val)

    data_count = safe_int(data.get("dataCount"), 10)
    min_amount = safe_float(data.get("minAmount"), 50.0)
    max_amount = safe_float(data.get("maxAmount"), 3000.0)
    cat_str = safe_str(data.get("categoryList"), "食品,电子,服装,家居,运动")
    categories = [c.strip() for c in cat_str.split(",") if c.strip()]

    generate_orders(data_count, min_amount, max_amount, categories)


if __name__ == "__main__":
    main()
