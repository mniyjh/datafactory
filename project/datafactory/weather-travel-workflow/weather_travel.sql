-- ============================================================
-- 天气出游判断流程 — 数据库初始化脚本
-- 使用所有 9 种组件：START / API_CALL / FILTER / PYTHON /
--   DB_QUERY / BRANCH / SHELL / SUB_TASK / END
-- ============================================================
-- 业务数据独立存放在 datafactory_weather 库
-- 配置元数据（API/脚本/任务）写入 datafactory 库
-- ============================================================

-- ============================================================
-- A. 业务数据库：datafactory_weather
-- ============================================================
CREATE DATABASE IF NOT EXISTS datafactory_weather
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE datafactory_weather;

-- ============================================================
-- 1. 数据源：旅行历史记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS travel_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    city VARCHAR(64) NOT NULL COMMENT '城市',
    travel_date DATE COMMENT '出行日期',
    weather_desc VARCHAR(128) COMMENT '当天天气',
    temperature INT COMMENT '温度(°C)',
    suitable_flag TINYINT DEFAULT 1 COMMENT '是否适合出游 1=是 0=否',
    remark VARCHAR(256) COMMENT '备注',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP
) COMMENT '旅行历史记录';

INSERT IGNORE INTO travel_history (city, travel_date, weather_desc, temperature, suitable_flag, remark) VALUES
('北京', '2026-05-01', '晴', 22, 1, '五一出行，天气很好'),
('上海', '2026-05-15', '小雨', 18, 0, '下雨取消'),
('三亚', '2026-04-20', '多云', 30, 1, '海边度假'),
('哈尔滨', '2026-06-01', '晴', 25, 1, '凉爽宜人');

-- ============================================================
-- B. 配置元数据：切换回 datafactory 库
-- ============================================================
USE datafactory;

-- ============================================================
-- 2. 数据源配置：连接 datafactory_weather 业务库
-- ============================================================
INSERT INTO datasource_db (tenant_id, db_code, db_name, db_type, description, status) VALUES
(1, 'TRAVEL_DB', '旅行数据库', 'MySQL', '本地MySQL旅行历史数据', 1);

INSERT INTO datasource_db_version (tenant_id, db_id, version, environment, dsl_content, db_type, db_name,
    jdbc_url, username, password, change_log, is_current, publish_status, created_by)
SELECT 1, id, '1.0.0', 'DEV', '{}', 'MySQL', 'datafactory',
    'jdbc:mysql://127.0.0.1:3306/datafactory_weather?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai',
    'root', '123456', '初始化天气旅行数据源', 1, 0, 'admin'
FROM datasource_db WHERE db_code = 'TRAVEL_DB';

-- ============================================================
-- 3. SQL 脚本：查询旅行历史
-- ============================================================
INSERT INTO script (tenant_id, script_code, script_name, script_type, description, status) VALUES
(1, 'SQL_TRAVEL_HISTORY', '查询旅行历史记录', 'SQL', '根据城市查询历史出行记录', 1);

INSERT INTO script_version (tenant_id, script_id, version, environment,
    script_code_content, timeout, retry_count, is_current, publish_status, created_by)
SELECT 1, id, '1.0.0', 'DEV',
    'SELECT city, travel_date, weather_desc, temperature, suitable_flag, remark FROM travel_history WHERE city = ''${city}'' ORDER BY travel_date DESC LIMIT 5',
    30, 0, 1, 0, 'admin'
FROM script WHERE script_code = 'SQL_TRAVEL_HISTORY';

-- ============================================================
-- 4. 外部 API：天气查询接口
-- ============================================================
INSERT INTO external_api (tenant_id, api_code, api_name, api_type, description, status) VALUES
(1, 'WEATHER_API', '天气查询接口', 'REST', '调用wttr.in免费天气API获取城市实时天气数据', 1);

-- API 版本配置 (URL 中 ${city} 会在运行时由上游参数替换)
INSERT INTO external_api_version (tenant_id, api_id, version, environment, dsl_content,
    request_method, request_url, content_type, request_headers, auth_type,
    timeout, retry_count, is_current, publish_status, change_log, created_by)
SELECT 1, id, '1.0.0', 'DEV', '{}',
    'GET', 'https://wttr.in/${city}?format=j1', 'application/json',
    '{"Accept":"application/json"}', 'None',
    30, 1, 1, 0, '初始化天气API配置', 'admin'
FROM external_api WHERE api_code = 'WEATHER_API';

-- ============================================================
-- 5. Python 脚本：天气数据分析
-- ============================================================
INSERT INTO script (tenant_id, script_code, script_name, script_type, description, status) VALUES
(1, 'PY_WEATHER_ANALYZE', '天气出游分析脚本', 'PYTHON', '解析天气JSON数据，综合评分判断是否适合出游', 1);

INSERT INTO script_version (tenant_id, script_id, version, environment,
    script_code_content, timeout, retry_count, dependencies,
    work_dir, interpreter_path, max_memory, is_current, publish_status, change_log, created_by)
SELECT 1, id, '1.0.0', 'DEV',
    'import json
import sys

def analyze(raw_data):
    """解析天气数据并给出出游建议"""
    weather = json.loads(raw_data) if isinstance(raw_data, str) else raw_data

    # 当前天气
    current = weather.get("current_condition", [{}])[0]
    temp_c = int(current.get("temp_C", 0))
    desc = current.get("weatherDesc", [{}])[0].get("value", "未知")
    wind_kmph = int(current.get("windspeedKmph", 0))
    humidity = int(current.get("humidity", 0))
    precip_mm = float(current.get("precipMM", 0))
    uv = int(current.get("uvIndex", 0))
    feels_like = int(current.get("FeelsLikeC", temp_c))

    # 综合评分（满分 100）
    score = 100
    warnings = []

    desc_lower = desc.lower()
    if any(w in desc_lower for w in ["rain", "drizzle", "shower", "雨"]):
        score -= 30; warnings.append("有降水")
    if any(w in desc_lower for w in ["snow", "sleet", "hail", "雪", "冰雹"]):
        score -= 40; warnings.append("有降雪/冰雹")
    if any(w in desc_lower for w in ["fog", "mist", "霾", "雾"]):
        score -= 15; warnings.append("能见度低")
    if temp_c < 5:
        score -= 25; warnings.append("温度过低")
    elif temp_c < 12:
        score -= 10; warnings.append("温度偏凉")
    elif temp_c > 37:
        score -= 30; warnings.append("温度过高")
    elif temp_c > 32:
        score -= 10; warnings.append("温度偏高")
    if wind_kmph > 40:
        score -= 20; warnings.append("风力过大")
    elif wind_kmph > 25:
        score -= 10; warnings.append("风力较大")
    if precip_mm > 5:
        score -= 20; warnings.append("降水量大")
    if uv > 7:
        score -= 10; warnings.append("紫外线强")

    score = max(0, score)
    suitable = "是" if score >= 60 else "否"
    suggestion = "非常适合出游，祝旅途愉快！" if score >= 80 else \
                 "适合出游，建议做好防护措施" if score >= 60 else \
                 "不太适合出游，建议改期或选择室内活动"

    result = {
        "城市": weather.get("request", [{}])[0].get("query", "未知"),
        "温度_摄氏度": temp_c,
        "体感温度_摄氏度": feels_like,
        "天气状况": desc,
        "风速_kmh": wind_kmph,
        "湿度_百分比": humidity,
        "降水量_mm": precip_mm,
        "紫外线指数": uv,
        "综合评分": score,
        "是否适合出游": suitable,
        "出游建议": suggestion,
        "风险提示": "；".join(warnings) if warnings else "无明显风险"
    }

    print(json.dumps(result, ensure_ascii=False))
    return result

if __name__ == "__main__":
    input_data = sys.stdin.read()
    analyze(input_data)
',
    60, 0,
    '[]',
    '/tmp', '/usr/bin/python3', 512, 1, 0, '初始化天气分析脚本', 'admin'
FROM script WHERE script_code = 'PY_WEATHER_ANALYZE';

-- ============================================================
-- 6. Shell 脚本：出行通知生成
-- ============================================================
INSERT INTO script (tenant_id, script_code, script_name, script_type, description, status) VALUES
(1, 'SH_TRAVEL_NOTIFY', '出行通知脚本', 'SHELL', '生成出行通知摘要文件', 1);

INSERT INTO script_version (tenant_id, script_id, version, environment,
    script_code_content, timeout, retry_count,
    work_dir, interpreter_path, is_current, publish_status, change_log, created_by)
SELECT 1, id, '1.0.0', 'DEV',
    '#!/bin/bash
# 出行通知生成脚本
CITY="${1:-未知城市}"
SCORE="${2:-0}"
SUGGESTION="${3:-暂无建议}"

OUTPUT_DIR="/tmp/travel_notify"
mkdir -p "$OUTPUT_DIR"

NOTIFY_FILE="$OUTPUT_DIR/$(date +%Y%m%d_%H%M%S)_${CITY}.txt"

cat > "$NOTIFY_FILE" << EOF
==========================================
  出游通知
  生成时间: $(date "+%Y-%m-%d %H:%M:%S")
==========================================
  城市:     $CITY
  综合评分: $SCORE
  建议:     $SUGGESTION
==========================================
EOF

echo "通知文件已生成: $NOTIFY_FILE"
cat "$NOTIFY_FILE"
',
    30, 0,
    '/tmp', '/bin/bash', 1, 0, '初始化出行通知脚本', 'admin'
FROM script WHERE script_code = 'SH_TRAVEL_NOTIFY';

-- ============================================================
-- 7. 子任务：室内活动推荐（简易占位任务）
-- ============================================================
INSERT INTO task (tenant_id, task_code, task_name, description, version, status, created_by) VALUES
(1, 'INDOOR_PLAN', '室内活动推荐', '天气不适合出游时推荐的室内活动方案', '1.0.0', 1, 'admin');

-- 子任务的 DEV 版本（仅包含开始→结束的最小结构）
INSERT INTO task_dsl (tenant_id, task_id, version, environment, dsl_content,
    is_current, env_status, publish_status, change_log, created_by)
SELECT 1, id, '1.0.0', 'DEV',
    '{
  "schemaVersion": 3,
  "nodes": [
    {
      "id": "n_indoor_start",
      "name": "开始",
      "type": "START",
      "componentCode": "COMP_START",
      "componentId": 1,
      "x": 100, "y": 100,
      "fieldValues": {},
      "inputParams": [
        {"paramCode": "city", "paramName": "城市", "dataType": "STRING", "sourceType": "CONST", "sourceValue": "", "requiredFlag": 0, "paramSpace": "NODE"}
      ],
      "outputParams": [
        {"paramCode": "city", "paramName": "城市", "dataType": "STRING"}
      ]
    },
    {
      "id": "n_indoor_end",
      "name": "结束",
      "type": "END",
      "componentCode": "COMP_END",
      "componentId": 2,
      "x": 400, "y": 100,
      "fieldValues": {},
      "inputParams": [],
      "outputParams": [
        {"paramCode": "suggestion", "paramName": "建议", "dataType": "STRING"},
        {"paramCode": "activities", "paramName": "推荐活动", "dataType": "STRING"}
      ]
    }
  ],
  "edges": [
    {"id": "e_indoor_1", "source": {"nodeId": "n_indoor_start", "port": "out"}, "target": {"nodeId": "n_indoor_end", "port": "in"}}
  ],
  "viewport": {"scale": 1, "pan": {"x": 0, "y": 0}}
}',
    1, 1, 1, '初始化室内活动子任务', 'admin'
FROM task WHERE task_code = 'INDOOR_PLAN';

-- 插入室内活动的内容（硬编码返回值）
UPDATE task_dsl SET dsl_content = '{
  "schemaVersion": 3,
  "nodes": [
    {
      "id": "n_indoor_start",
      "name": "开始",
      "type": "START",
      "componentCode": "COMP_START",
      "componentId": 1,
      "x": 100, "y": 100,
      "fieldValues": {},
      "inputParams": [
        {"paramCode": "city", "paramName": "城市", "dataType": "STRING", "sourceType": "CONST", "sourceValue": "", "requiredFlag": 0, "paramSpace": "NODE"}
      ],
      "outputParams": [
        {"paramCode": "city", "paramName": "城市", "dataType": "STRING"}
      ]
    },
    {
      "id": "n_indoor_end",
      "name": "结束",
      "type": "END",
      "componentCode": "COMP_END",
      "componentId": 2,
      "x": 400, "y": 100,
      "fieldValues": {},
      "inputParams": [],
      "outputParams": [
        {"paramCode": "suggestion", "paramName": "建议", "dataType": "STRING"},
        {"paramCode": "activities", "paramName": "推荐活动", "dataType": "STRING"}
      ]
    }
  ],
  "edges": [
    {"id": "e_indoor_1", "source": {"nodeId": "n_indoor_start", "port": "out"}, "target": {"nodeId": "n_indoor_end", "port": "in"}}
  ],
  "viewport": {"scale": 1, "pan": {"x": 0, "y": 0}}
}' WHERE task_id = (SELECT id FROM task WHERE task_code = 'INDOOR_PLAN') AND environment = 'DEV';

-- ============================================================
-- 8. 主任务：天气出游判断流程
-- ============================================================
INSERT INTO task (tenant_id, task_code, task_name, description, version, status, created_by) VALUES
(1, 'WEATHER_TRAVEL_CHECK', '天气出游判断流程', '根据城市天气综合判断是否适合出游，包含天气查询、数据分析、历史对比、分支决策、通知生成等完整流程', '1.0.0', 1, 'admin');

-- ============================================================
-- 9. 主任务 DSL：天气出游判断流程完整 DAG
-- ============================================================
INSERT INTO task_dsl (tenant_id, task_id, version, environment, dsl_content,
    is_current, env_status, publish_status, change_log, created_by)
SELECT 1, id, '1.0.0', 'DEV',
    '{
  "schemaVersion": 3,
  "nodes": [
    {
      "id": "n_start",
      "name": "开始节点",
      "type": "START",
      "componentCode": "COMP_START",
      "componentId": 1,
      "x": 100, "y": 300,
      "fieldValues": {},
      "inputParams": [
        {"paramCode": "city", "paramName": "城市名称", "dataType": "STRING", "sourceType": "CONST", "sourceValue": "Beijing", "requiredFlag": 1, "paramSpace": "TASK"}
      ],
      "outputParams": [
        {"paramCode": "city", "paramName": "城市名称", "dataType": "STRING"}
      ]
    },
    {
      "id": "n_api",
      "name": "获取天气数据",
      "type": "数据处理",
      "componentCode": "COMP_API_CALL",
      "componentId": 4,
      "x": 350, "y": 200,
      "fieldValues": {
        "apiCode": "WEATHER_API",
        "url": "https://wttr.in/${city}?format=j1",
        "method": "GET",
        "headers": "{\"Accept\":\"application/json\"}",
        "body": "",
        "result_var": "weather_raw"
      },
      "inputParams": [
        {"paramCode": "city", "paramName": "城市名称", "dataType": "STRING", "sourceType": "UPSTREAM_OUTPUT", "sourceValue": "{\"nodeId\":\"n_start\",\"paramCode\":\"city\"}", "requiredFlag": 1, "paramSpace": "NODE"}
      ],
      "outputParams": [
        {"paramCode": "weather_raw", "paramName": "原始天气数据", "dataType": "STRING"}
      ]
    },
    {
      "id": "n_filter",
      "name": "数据过滤清洗",
      "type": "数据处理",
      "componentCode": "COMP_FILTER",
      "componentId": 6,
      "x": 600, "y": 200,
      "fieldValues": {
        "filterMode": "COLUMN",
        "sourceNodeId": "n_api",
        "columns": "weather_raw"
      },
      "inputParams": [
        {"paramCode": "weather_raw", "paramName": "原始天气数据", "dataType": "STRING", "sourceType": "UPSTREAM_OUTPUT", "sourceValue": "{\"nodeId\":\"n_api\",\"paramCode\":\"weather_raw\"}", "requiredFlag": 1, "paramSpace": "NODE"}
      ],
      "outputParams": [
        {"paramCode": "weather_raw", "paramName": "过滤后天气数据", "dataType": "STRING"}
      ]
    },
    {
      "id": "n_python",
      "name": "天气数据分析",
      "type": "数据处理",
      "componentCode": "COMP_PYTHON_EXECUTOR",
      "componentId": 5,
      "x": 850, "y": 200,
      "fieldValues": {
        "scriptCode": "PY_WEATHER_ANALYZE",
        "result_var": "analysis_result"
      },
      "inputParams": [
        {"paramCode": "weather_raw", "paramName": "天气数据", "dataType": "STRING", "sourceType": "UPSTREAM_OUTPUT", "sourceValue": "{\"nodeId\":\"n_filter\",\"paramCode\":\"weather_raw\"}", "requiredFlag": 1, "paramSpace": "NODE"}
      ],
      "outputParams": [
        {"paramCode": "analysis_result", "paramName": "分析结果JSON", "dataType": "STRING"}
      ]
    },
    {
      "id": "n_db",
      "name": "查询历史记录",
      "type": "数据接入",
      "componentCode": "COMP_DB_QUERY",
      "componentId": 3,
      "x": 850, "y": 400,
      "fieldValues": {
        "scriptCode": "SQL_TRAVEL_HISTORY",
        "datasource": "TRAVEL_DB",
        "result_var": "history_data"
      },
      "inputParams": [
        {"paramCode": "city", "paramName": "城市名称", "dataType": "STRING", "sourceType": "UPSTREAM_OUTPUT", "sourceValue": "{\"nodeId\":\"n_start\",\"paramCode\":\"city\"}", "requiredFlag": 1, "paramSpace": "NODE"}
      ],
      "outputParams": [
        {"paramCode": "history_data", "paramName": "历史出行记录", "dataType": "ARRAY"}
      ]
    },
    {
      "id": "n_branch",
      "name": "判断是否适合出游",
      "type": "流程控制",
      "componentCode": "COMP_BRANCH",
      "componentId": 7,
      "x": 1100, "y": 200,
      "fieldValues": {
        "branches": "[{\"expression\":\"${analysis_result.综合评分} >= 60\",\"label\":\"适合出游\"},{\"expression\":\"${analysis_result.综合评分} < 60\",\"label\":\"不适合出游\"}]"
      },
      "inputParams": [
        {"paramCode": "analysis_result", "paramName": "分析结果", "dataType": "STRING", "sourceType": "UPSTREAM_OUTPUT", "sourceValue": "{\"nodeId\":\"n_python\",\"paramCode\":\"analysis_result\"}", "requiredFlag": 1, "paramSpace": "NODE"}
      ],
      "outputParams": []
    },
    {
      "id": "n_shell",
      "name": "生成出行通知",
      "type": "数据处理",
      "componentCode": "COMP_SHELL_EXECUTOR",
      "componentId": 9,
      "x": 1350, "y": 100,
      "fieldValues": {
        "scriptCode": "SH_TRAVEL_NOTIFY",
        "result_var": "notify_result"
      },
      "inputParams": [
        {"paramCode": "city", "paramName": "城市名称", "dataType": "STRING", "sourceType": "UPSTREAM_OUTPUT", "sourceValue": "{\"nodeId\":\"n_start\",\"paramCode\":\"city\"}", "requiredFlag": 1, "paramSpace": "NODE"},
        {"paramCode": "score", "paramName": "综合评分", "dataType": "STRING", "sourceType": "UPSTREAM_OUTPUT", "sourceValue": "{\"nodeId\":\"n_python\",\"paramCode\":\"analysis_result\"}", "requiredFlag": 1, "paramSpace": "NODE"}
      ],
      "outputParams": [
        {"paramCode": "notify_result", "paramName": "通知生成结果", "dataType": "STRING"}
      ]
    },
    {
      "id": "n_subtask",
      "name": "室内活动推荐",
      "type": "流程控制",
      "componentCode": "COMP_SUB_TASK",
      "componentId": 8,
      "x": 1350, "y": 300,
      "fieldValues": {
        "subTaskId": "INDOOR_PLAN",
        "result_var": "indoor_plan"
      },
      "inputParams": [
        {"paramCode": "city", "paramName": "城市名称", "dataType": "STRING", "sourceType": "UPSTREAM_OUTPUT", "sourceValue": "{\"nodeId\":\"n_start\",\"paramCode\":\"city\"}", "requiredFlag": 1, "paramSpace": "NODE"}
      ],
      "outputParams": [
        {"paramCode": "indoor_plan", "paramName": "室内活动方案", "dataType": "STRING"}
      ]
    },
    {
      "id": "n_end",
      "name": "结束节点",
      "type": "END",
      "componentCode": "COMP_END",
      "componentId": 2,
      "x": 1600, "y": 200,
      "fieldValues": {},
      "inputParams": [],
      "outputParams": [
        {"paramCode": "city", "paramName": "城市名称", "dataType": "STRING"},
        {"paramCode": "weather_raw", "paramName": "原始天气数据", "dataType": "STRING"},
        {"paramCode": "analysis_result", "paramName": "天气分析结果", "dataType": "STRING"},
        {"paramCode": "history_data", "paramName": "历史出行记录", "dataType": "ARRAY"},
        {"paramCode": "final_suggestion", "paramName": "最终建议", "dataType": "STRING"},
        {"paramCode": "notify_file", "paramName": "通知文件路径", "dataType": "STRING"}
      ]
    }
  ],
  "edges": [
    {"id": "e_start_to_api",     "source": {"nodeId": "n_start",  "port": "out"}, "target": {"nodeId": "n_api",    "port": "in"}},
    {"id": "e_api_to_filter",    "source": {"nodeId": "n_api",    "port": "out"}, "target": {"nodeId": "n_filter", "port": "in"}},
    {"id": "e_filter_to_python", "source": {"nodeId": "n_filter", "port": "out"}, "target": {"nodeId": "n_python", "port": "in"}},
    {"id": "e_start_to_db",     "source": {"nodeId": "n_start",  "port": "out"}, "target": {"nodeId": "n_db",     "port": "in"}},
    {"id": "e_python_to_branch", "source": {"nodeId": "n_python", "port": "out"}, "target": {"nodeId": "n_branch", "port": "in"}},
    {"id": "e_db_to_branch",    "source": {"nodeId": "n_db",     "port": "out"}, "target": {"nodeId": "n_branch", "port": "in"}},
    {"id": "e_branch_yes",      "source": {"nodeId": "n_branch", "port": "out_0"}, "target": {"nodeId": "n_shell",  "port": "in"}},
    {"id": "e_branch_no",       "source": {"nodeId": "n_branch", "port": "out_1"}, "target": {"nodeId": "n_subtask","port": "in"}},
    {"id": "e_shell_to_end",    "source": {"nodeId": "n_shell",  "port": "out"}, "target": {"nodeId": "n_end",    "port": "in"}},
    {"id": "e_subtask_to_end",  "source": {"nodeId": "n_subtask","port": "out"}, "target": {"nodeId": "n_end",    "port": "in"}}
  ],
  "viewport": {"scale": 1, "pan": {"x": 0, "y": 0}}
}',
    1, 1, 0, '初始化天气出游判断流程DEV版本', 'admin'
FROM task WHERE task_code = 'WEATHER_TRAVEL_CHECK';
