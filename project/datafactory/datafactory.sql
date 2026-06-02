CREATE TABLE IF NOT EXISTS task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_code VARCHAR(64) NOT NULL UNIQUE,
  task_name VARCHAR(128) NOT NULL,
  description VARCHAR(500) DEFAULT NULL,
  version VARCHAR(32) DEFAULT '1.0.0',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '0-禁用 1-启用',
  created_by VARCHAR(64) DEFAULT 'admin',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64) DEFAULT 'admin',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_task_name (task_name),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS task_dsl (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  version VARCHAR(50) NOT NULL,
  environment VARCHAR(20) NOT NULL COMMENT 'DEV/TEST/PROD',
  dsl_content LONGTEXT NOT NULL,
  change_log VARCHAR(500) DEFAULT NULL,
  is_current TINYINT NOT NULL DEFAULT 0,
  env_status TINYINT NOT NULL DEFAULT 1,
  publish_status TINYINT NOT NULL DEFAULT 0 COMMENT '0-待发布 1-已发布',
  created_by VARCHAR(64) DEFAULT 'admin',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64) DEFAULT 'admin',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_task_dsl_task_id FOREIGN KEY (task_id) REFERENCES task(id),
  KEY idx_task_id (task_id),
  KEY idx_env (environment),
  KEY idx_current (is_current)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS datasource_db (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  db_code VARCHAR(64) NOT NULL UNIQUE,
  db_name VARCHAR(128) NOT NULL,
  db_type VARCHAR(32) NOT NULL,
  description VARCHAR(500) DEFAULT NULL,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '0-禁用 1-启用',
  created_by VARCHAR(64) DEFAULT 'admin',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64) DEFAULT 'admin',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_db_name (db_name),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS datasource_db_version (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  db_id BIGINT NOT NULL,
  version VARCHAR(50) NOT NULL,
  environment VARCHAR(20) NOT NULL COMMENT 'DEV/TEST/PROD',
  dsl_content LONGTEXT NOT NULL,
  db_type VARCHAR(32) DEFAULT NULL,
  db_name VARCHAR(128) DEFAULT NULL COMMENT '数据库名称(Schema)',
  jdbc_url VARCHAR(512) DEFAULT NULL,
  username VARCHAR(128) DEFAULT NULL,
  password VARCHAR(128) DEFAULT NULL,
  change_log VARCHAR(500) DEFAULT NULL,
  is_current TINYINT NOT NULL DEFAULT 0,
  publish_status TINYINT NOT NULL DEFAULT 0 COMMENT '0-待发布 1-已发布',
  created_by VARCHAR(64) DEFAULT 'admin',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64) DEFAULT 'admin',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_datasource_db_version_db_id FOREIGN KEY (db_id) REFERENCES datasource_db(id),
  KEY idx_db_id (db_id),
  KEY idx_env (environment),
  KEY idx_current (is_current)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS task_test_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  version_id BIGINT NULL,
  name VARCHAR(128) NOT NULL,
  config_mode VARCHAR(20) NOT NULL COMMENT 'JSON/INPUT/OUTPUT',
  config_data LONGTEXT NOT NULL,
  is_default TINYINT NOT NULL DEFAULT 0,
  created_by VARCHAR(64) DEFAULT 'admin',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64) DEFAULT 'admin',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS external_api (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  api_code VARCHAR(64) NOT NULL UNIQUE,
  api_name VARCHAR(128) NOT NULL,
  api_type VARCHAR(32) NOT NULL COMMENT 'REST/SOAP/GraphQL/Other',
  description VARCHAR(500) DEFAULT NULL,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '0-禁用 1-启用',
  created_by VARCHAR(64) DEFAULT 'admin',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64) DEFAULT 'admin',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_api_name (api_name),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS external_api_version (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  api_id BIGINT NOT NULL,
  version VARCHAR(50) NOT NULL,
  environment VARCHAR(20) NOT NULL COMMENT 'DEV/TEST/PROD',
  dsl_content LONGTEXT NOT NULL,
  request_method VARCHAR(16) DEFAULT NULL,
  request_url VARCHAR(512) DEFAULT NULL,
  content_type VARCHAR(64) DEFAULT NULL,
  request_headers LONGTEXT DEFAULT NULL,
  query_params LONGTEXT DEFAULT NULL,
  request_body LONGTEXT DEFAULT NULL,
  auth_type VARCHAR(32) DEFAULT NULL,
  auth_config LONGTEXT DEFAULT NULL,
  timeout INT DEFAULT 30,
  retry_count INT DEFAULT 0,
  change_log VARCHAR(500) DEFAULT NULL,
  is_current TINYINT NOT NULL DEFAULT 0,
  publish_status TINYINT NOT NULL DEFAULT 0 COMMENT '0-待发布 1-已发布',
  created_by VARCHAR(64) DEFAULT 'admin',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64) DEFAULT 'admin',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_external_api_version_api_id FOREIGN KEY (api_id) REFERENCES external_api(id),
  KEY idx_api_id (api_id),
  KEY idx_env (environment),
  KEY idx_current (is_current)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS open_api (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  api_code VARCHAR(64) NOT NULL UNIQUE,
  api_name VARCHAR(128) NOT NULL,
  api_path VARCHAR(256) NOT NULL,
  api_method VARCHAR(16) NOT NULL DEFAULT 'POST',
  task_id BIGINT DEFAULT NULL,
  input_schema LONGTEXT DEFAULT NULL,
  output_schema LONGTEXT DEFAULT NULL,
  auth_type VARCHAR(32) DEFAULT 'None',
  limit_count INT DEFAULT 0,
  app_secret VARCHAR(64) DEFAULT NULL,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '0-禁用 1-启用',
  description VARCHAR(500) DEFAULT NULL,
  created_by VARCHAR(64) DEFAULT 'admin',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64) DEFAULT 'admin',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_task_id (task_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS open_api_invoke_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  api_code VARCHAR(64) NOT NULL,
  task_id BIGINT DEFAULT NULL,
  execution_id VARCHAR(64) DEFAULT NULL,
  request_payload LONGTEXT DEFAULT NULL,
  duration_ms BIGINT DEFAULT 0,
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_api_code (api_code),
  KEY idx_execution_id (execution_id),
  KEY idx_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS script (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  script_code VARCHAR(64) NOT NULL UNIQUE,
  script_name VARCHAR(128) NOT NULL,
  script_type VARCHAR(32) NOT NULL COMMENT 'PYTHON/SHELL/SQL/Other',
  description VARCHAR(500) DEFAULT NULL,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '0-禁用 1-启用',
  created_by VARCHAR(64) DEFAULT 'admin',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64) DEFAULT 'admin',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_script_name (script_name),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS component_definition (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  component_code VARCHAR(64) NOT NULL UNIQUE,
  component_name VARCHAR(128) NOT NULL,
  component_type VARCHAR(32) NOT NULL COMMENT '数据接入/数据处理/流程控制',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '0-禁用 1-启用',
  is_system TINYINT NOT NULL DEFAULT 0 COMMENT '1-系统内置 0-自定义',
  default_config LONGTEXT DEFAULT NULL,
  description VARCHAR(500) DEFAULT NULL,
  created_by VARCHAR(64) DEFAULT 'admin',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64) DEFAULT 'admin',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_status (status),
  KEY idx_is_system (is_system)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS component (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  component_code VARCHAR(64) NOT NULL UNIQUE,
  component_name VARCHAR(128) NOT NULL,
  component_type VARCHAR(32) NOT NULL DEFAULT '数据处理' COMMENT '数据接入/数据处理/流程控制',
  category VARCHAR(32) DEFAULT NULL COMMENT '数据接入/数据处理/流程控制',
  version VARCHAR(32) DEFAULT '1.0.0',
  description VARCHAR(500) DEFAULT NULL,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '0-禁用 1-启用',
  created_by VARCHAR(64) DEFAULT 'admin',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64) DEFAULT 'admin',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_component_name (component_name),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS component_field;
CREATE TABLE component_field (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  component_id BIGINT NOT NULL,
  field_code VARCHAR(64) NOT NULL,
  field_name VARCHAR(128) NOT NULL,
  value_type VARCHAR(32) NOT NULL COMMENT 'STRING/INT/BIGINT/DECIMAL/BOOLEAN/DATE/DATETIME/TEXT/JSON/ARRAY/OBJECT',
  widget_type VARCHAR(32) NOT NULL COMMENT 'INPUT/TEXTAREA/NUMBER/SWITCH/SELECT/MULTI_SELECT/RADIO/CHECKBOX/DATE_PICKER/DATETIME_PICKER/JSON_EDITOR/PASSWORD',
  widget_props LONGTEXT DEFAULT NULL,
  default_value LONGTEXT DEFAULT NULL,
  required_flag TINYINT NOT NULL DEFAULT 0,
  sort_order INT NOT NULL DEFAULT 1,
  description VARCHAR(500) DEFAULT NULL,
  created_by VARCHAR(64) DEFAULT 'admin',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64) DEFAULT 'admin',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_component_field_code (component_id, field_code),
  KEY idx_component_id (component_id),
  KEY idx_sort_order (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS component_io_param (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  component_id BIGINT NOT NULL,
  io_type VARCHAR(8) NOT NULL DEFAULT 'INPUT' COMMENT 'INPUT/OUTPUT',
  param_code VARCHAR(64) NOT NULL,
  param_name VARCHAR(128) NOT NULL,
  data_type VARCHAR(32) NOT NULL DEFAULT 'STRING' COMMENT 'STRING/NUMBER/BOOLEAN',
  required_flag TINYINT NOT NULL DEFAULT 0,
  source_type VARCHAR(32) NOT NULL DEFAULT 'CONST' COMMENT 'CONST/UPSTREAM_OUTPUT/EXPRESSION',
  source_value LONGTEXT DEFAULT NULL COMMENT '来源取值表达式/结构化JSON',
  default_value LONGTEXT DEFAULT NULL COMMENT '默认值（参数空间内）',
  param_space VARCHAR(32) NOT NULL DEFAULT 'NODE' COMMENT 'NODE/TASK/ENV/GLOBAL',
  sort_order INT NOT NULL DEFAULT 1,
  description VARCHAR(500) DEFAULT NULL,
  created_by VARCHAR(64) DEFAULT 'admin',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64) DEFAULT 'admin',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_component_param_code (component_id, io_type, param_code),
  KEY idx_component_id (component_id),
  KEY idx_io_type (io_type),
  KEY idx_param_code (param_code),
  KEY idx_data_type (data_type),
  KEY idx_source_type (source_type),
  KEY idx_param_space (param_space)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS script_version (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  script_id BIGINT NOT NULL,
  version VARCHAR(50) NOT NULL,
  environment VARCHAR(20) NOT NULL COMMENT 'DEV/TEST/PROD',
  script_code_content LONGTEXT NOT NULL,
  timeout INT DEFAULT 30,
  retry_count INT DEFAULT 0,
  dependencies LONGTEXT DEFAULT NULL COMMENT 'JSON: ["requests==2.28.0"]',
  env_vars LONGTEXT DEFAULT NULL COMMENT 'JSON: {"PATH": "/usr/bin"}',
  work_dir VARCHAR(256) DEFAULT '/tmp',
  interpreter_path VARCHAR(256) DEFAULT '/usr/bin/python3',
  max_memory INT DEFAULT 512,
  cpu_limit DECIMAL(3, 1) DEFAULT 1.0,
  change_log VARCHAR(500) DEFAULT NULL,
  is_current TINYINT NOT NULL DEFAULT 0,
  publish_status TINYINT NOT NULL DEFAULT 0 COMMENT '0-待发布 1-已发布',
  created_by VARCHAR(64) DEFAULT 'admin',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64) DEFAULT 'admin',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_script_version_script_id FOREIGN KEY (script_id) REFERENCES script(id),
  KEY idx_script_id (script_id),
  KEY idx_env (environment),
  KEY idx_current (is_current)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS execution_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  execution_id VARCHAR(64) NOT NULL UNIQUE COMMENT '执行唯一ID',
  task_id BIGINT NOT NULL,
  task_name VARCHAR(128) NOT NULL,
  task_version VARCHAR(32) NOT NULL,
  environment VARCHAR(20) NOT NULL COMMENT 'TEST/PROD',
  status VARCHAR(20) NOT NULL COMMENT 'RUNNING/SUCCESS/FAILURE',
  trigger_type VARCHAR(32) DEFAULT 'MANUAL' COMMENT 'MANUAL/AUTO/OPENAPI/CRON/MANUAL_TRIGGER',
  schedule_job_id BIGINT DEFAULT NULL,
  start_time DATETIME(3) NOT NULL,
  end_time DATETIME(3) DEFAULT NULL,
  duration_ms BIGINT DEFAULT 0,
  input_params LONGTEXT DEFAULT NULL,
  output_result LONGTEXT DEFAULT NULL,
  error_message LONGTEXT DEFAULT NULL,
  created_by VARCHAR(64) DEFAULT 'admin',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_task_id (task_id),
  KEY idx_schedule_job_id (schedule_job_id),
  KEY idx_environment (environment),
  KEY idx_status (status),
  KEY idx_start_time (start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS node_instance (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_dsl_id BIGINT NOT NULL COMMENT '所属任务版本 ID',
  node_id VARCHAR(64) NOT NULL COMMENT '画布节点ID',
  node_name VARCHAR(128) NOT NULL,
  component_id BIGINT NOT NULL,
  component_code VARCHAR(64) NOT NULL,
  component_version VARCHAR(32) DEFAULT NULL COMMENT '实例创建时的组件版本',
  sync_status TINYINT NOT NULL DEFAULT 0 COMMENT '0-已同步 1-待同步 2-冲突',
  deprecated_config LONGTEXT DEFAULT NULL COMMENT '被删除字段的备份配置(JSON)',
  position_x DECIMAL(12,2) DEFAULT NULL,
  position_y DECIMAL(12,2) DEFAULT NULL,
  node_type VARCHAR(32) DEFAULT NULL,
  description VARCHAR(500) DEFAULT NULL,
  created_by VARCHAR(64) DEFAULT 'admin',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64) DEFAULT 'admin',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_task_dsl_node (task_dsl_id, node_id),
  KEY idx_task_dsl_id (task_dsl_id),
  KEY idx_component_id (component_id),
  KEY idx_sync_status (sync_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS node_field_value (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  node_instance_id BIGINT NOT NULL,
  field_code VARCHAR(64) NOT NULL,
  field_name VARCHAR(128) DEFAULT NULL,
  value_type VARCHAR(32) NOT NULL DEFAULT 'STRING',
  widget_type VARCHAR(32) DEFAULT 'INPUT' COMMENT '控件类型',
  widget_props LONGTEXT DEFAULT NULL COMMENT '控件属性(JSON)',
  default_value VARCHAR(512) DEFAULT NULL COMMENT '默认值',
  field_value LONGTEXT DEFAULT NULL,
  required_flag TINYINT NOT NULL DEFAULT 0 COMMENT '是否必填',
  description VARCHAR(512) DEFAULT NULL COMMENT '字段描述',
  field_snapshot LONGTEXT DEFAULT NULL COMMENT '字段定义快照(JSON)',
  sort_order INT NOT NULL DEFAULT 1,
  deprecated_flag TINYINT NOT NULL DEFAULT 0,
  created_by VARCHAR(64) DEFAULT 'admin',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64) DEFAULT 'admin',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_node_field (node_instance_id, field_code),
  KEY idx_node_instance_id (node_instance_id),
  KEY idx_deprecated_flag (deprecated_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS node_io_param_value (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  node_instance_id BIGINT NOT NULL,
  io_type VARCHAR(8) NOT NULL DEFAULT 'INPUT' COMMENT 'INPUT/OUTPUT',
  param_code VARCHAR(64) NOT NULL,
  param_name VARCHAR(128) DEFAULT NULL,
  data_type VARCHAR(32) NOT NULL DEFAULT 'STRING',
  source_type VARCHAR(32) DEFAULT NULL,
  source_value LONGTEXT DEFAULT NULL,
  param_value LONGTEXT DEFAULT NULL,
  param_space VARCHAR(32) DEFAULT NULL,
  param_snapshot LONGTEXT DEFAULT NULL COMMENT '参数定义快照(JSON)',
  sort_order INT NOT NULL DEFAULT 1,
  deprecated_flag TINYINT NOT NULL DEFAULT 0,
  created_by VARCHAR(64) DEFAULT 'admin',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64) DEFAULT 'admin',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_node_io_param (node_instance_id, io_type, param_code),
  KEY idx_node_instance_id (node_instance_id),
  KEY idx_io_type (io_type),
  KEY idx_deprecated_flag (deprecated_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS component_field_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  component_id BIGINT NOT NULL,
  version VARCHAR(32) NOT NULL DEFAULT '1.0.0',
  change_type VARCHAR(32) NOT NULL COMMENT 'ADD/UPDATE/DELETE/SYNC',
  field_snapshot LONGTEXT NOT NULL COMMENT '字段快照(JSON)',
  change_log VARCHAR(500) DEFAULT NULL,
  created_by VARCHAR(64) DEFAULT 'admin',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_component_id (component_id),
  KEY idx_version (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS component_io_param_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  component_id BIGINT NOT NULL,
  version VARCHAR(32) NOT NULL DEFAULT '1.0.0',
  change_type VARCHAR(32) NOT NULL COMMENT 'ADD/UPDATE/DELETE/SYNC',
  param_snapshot LONGTEXT NOT NULL COMMENT '参数快照(JSON)',
  change_log VARCHAR(500) DEFAULT NULL,
  created_by VARCHAR(64) DEFAULT 'admin',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_component_id (component_id),
  KEY idx_version (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS node_execution_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  execution_id VARCHAR(64) NOT NULL,
  node_id VARCHAR(64) NOT NULL,
  node_name VARCHAR(128) NOT NULL,
  node_type VARCHAR(32) NOT NULL,
  status VARCHAR(20) NOT NULL,
  start_time DATETIME(3) NOT NULL,
  end_time DATETIME(3) DEFAULT NULL,
  duration_ms BIGINT DEFAULT 0,
  retry_count INT DEFAULT 0,
  input_data LONGTEXT DEFAULT NULL,
  output_data LONGTEXT DEFAULT NULL,
  error_message LONGTEXT DEFAULT NULL,
  field_snapshot LONGTEXT DEFAULT NULL,
  io_schema LONGTEXT DEFAULT NULL,
  edge_from LONGTEXT DEFAULT NULL,
  edge_to LONGTEXT DEFAULT NULL,
  component_code VARCHAR(64) DEFAULT NULL,
  component_id BIGINT DEFAULT NULL,
  component_version VARCHAR(32) DEFAULT NULL,
  KEY idx_execution_id (execution_id),
  KEY idx_node_id (node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS schedule_job (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_code VARCHAR(64) NOT NULL UNIQUE,
  task_id BIGINT NOT NULL,
  task_version_id BIGINT NOT NULL,
  cron_expression VARCHAR(64) NOT NULL,
  environment VARCHAR(20) DEFAULT 'PROD',
  status TINYINT DEFAULT 1,
  last_execution_id VARCHAR(64),
  last_fire_time DATETIME,
  next_fire_time DATETIME,
  created_by VARCHAR(64) DEFAULT 'admin',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64) DEFAULT 'admin',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 系统内置组件初始化数据
-- ============================================================

INSERT IGNORE INTO component (id, component_code, component_name, component_type, category, version, description, status) VALUES
(1, 'COMP_START', '开始节点', '流程控制', '流程控制', '1.0.0', '任务开始节点，作为任务入口定义输入/输出参数', 1),
(2, 'COMP_END', '结束节点', '流程控制', '流程控制', '1.0.0', '任务结束节点，收集所有上游输出合并为最终结果', 1),
(3, 'COMP_DB_QUERY', '数据库查询', '数据接入', '数据接入', '1.0.0', '执行SQL从数据库读取数据', 1),
(4, 'COMP_API_CALL', '接口调用', '数据接入', '数据接入', '1.0.0', '调用HTTP接口获取数据', 1),
(5, 'COMP_SCRIPT', '脚本执行', '数据处理', '数据处理', '1.0.0', '执行Python/Shell/SQL脚本处理数据', 1),
(6, 'COMP_FILTER', '数据过滤', '数据处理', '数据处理', '1.0.0', '对上游数据进行列过滤或条件过滤', 1),
(7, 'COMP_BRANCH', '条件分支', '流程控制', '流程控制', '1.0.0', '根据表达式结果决定执行分支', 1),
(8, 'COMP_SUB_TASK', '子任务调用', '流程控制', '流程控制', '1.0.0', '引用并执行其他已发布的任务', 1);

-- 数据库查询（数据接入）组件字段
INSERT IGNORE INTO component_field (component_id, field_code, field_name, value_type, widget_type, widget_props, required_flag, sort_order, description) VALUES
(3, 'datasource', '数据源', 'STRING', 'MULTI_SELECT', '{"optionsSource":{"sourceType":"DB_QUERY"}}', 1, 1, '选择已管理的数据源'),
(3, 'sql', 'SQL语句', 'STRING', 'TEXTAREA', NULL, 1, 2, '要执行的SQL查询语句'),
(3, 'result_var', '结果变量', 'STRING', 'TEXTAREA', NULL, 0, 3, '结果绑定的变量名');

-- 接口调用（数据接入）组件字段
INSERT IGNORE INTO component_field (component_id, field_code, field_name, value_type, widget_type, widget_props, required_flag, sort_order, description) VALUES
(4, 'apiCode', '选择API', 'STRING', 'MULTI_SELECT', '{"optionsSource":{"sourceType":"API_CALL"}}', 1, 1, '从三方API管理中选择已发布的API（自动填入URL/方法/请求头/请求体）'),
(4, 'url', '接口地址', 'STRING', 'TEXTAREA', NULL, 0, 2, '请求URL（选择API后自动填入，可手动覆盖）'),
(4, 'method', '请求方式', 'STRING', 'MULTI_SELECT', '{"options":[{"label":"GET","value":"GET"},{"label":"POST","value":"POST"},{"label":"PUT","value":"PUT"},{"label":"DELETE","value":"DELETE"}]}', 0, 3, 'HTTP请求方法'),
(4, 'headers', '请求头', 'JSON', 'TEXTAREA', NULL, 0, 4, '自定义请求头JSON（选择API后自动填入）'),
(4, 'body', '请求体', 'JSON', 'TEXTAREA', NULL, 0, 5, '请求体JSON（选择API后自动填入）'),
(4, 'result_var', '结果变量', 'STRING', 'TEXTAREA', NULL, 0, 6, '响应绑定的变量名');

-- 脚本执行（数据处理）组件字段
INSERT IGNORE INTO component_field (component_id, field_code, field_name, value_type, widget_type, widget_props, required_flag, sort_order, description) VALUES
(5, 'scriptCode', '选择脚本', 'STRING', 'MULTI_SELECT', '{"optionsSource":{"sourceType":"SCRIPT"}}', 1, 1, '从脚本管理中选择已发布的脚本'),
(5, 'result_var', '结果变量', 'STRING', 'TEXTAREA', NULL, 0, 2, '执行结果绑定的变量名'),
(5, 'className', '类名', 'STRING', 'INPUT', NULL, 0, 3, 'Python类名，如 Calculator'),
(5, 'methodName', '方法名', 'STRING', 'INPUT', NULL, 0, 4, '类方法名，如 execute');

-- 数据过滤（数据处理）组件字段
INSERT IGNORE INTO component_field (component_id, field_code, field_name, value_type, widget_type, widget_props, required_flag, sort_order, description) VALUES
(6, 'filterMode', '过滤模式', 'STRING', 'MULTI_SELECT', '{"options":[{"label":"列过滤","value":"COLUMN"},{"label":"简单条件","value":"SIMPLE"},{"label":"表达式过滤","value":"EXPRESSION"}]}', 1, 1, '过滤模式'),
(6, 'sourceNodeId', '数据源节点', 'STRING', 'TEXTAREA', NULL, 0, 2, '上游节点ID'),
(6, 'columns', '保留列', 'STRING', 'TEXTAREA', NULL, 0, 3, '需保留的列，逗号分隔'),
(6, 'condition_field', '过滤字段', 'STRING', 'TEXTAREA', NULL, 0, 4, '过滤字段名'),
(6, 'condition_op', '运算符', 'STRING', 'MULTI_SELECT', '{"options":[{"label":"等于","value":"EQ"},{"label":"不等于","value":"NEQ"},{"label":"大于","value":"GT"},{"label":"小于","value":"LT"},{"label":"包含","value":"CONTAINS"},{"label":"为空","value":"IS_NULL"},{"label":"不为空","value":"IS_NOT_NULL"}]}', 0, 5, '过滤运算符'),
(6, 'condition_value', '过滤值', 'STRING', 'TEXTAREA', NULL, 0, 6, '过滤比较值'),
(6, 'expression', '过滤表达式', 'STRING', 'TEXTAREA', NULL, 0, 7, 'Aviator表达式'),
(6, 'result_var', '结果变量', 'STRING', 'TEXTAREA', NULL, 0, 8, '结果绑定的变量名');

-- 条件分支（流程控制）组件字段
INSERT IGNORE INTO component_field (component_id, field_code, field_name, value_type, widget_type, widget_props, required_flag, sort_order, description) VALUES
(7, 'expression', '条件表达式', 'STRING', 'TEXTAREA', NULL, 1, 1, 'Aviator表达式，如 age > 18 && status == ''active''，支持${变量名}引用上游数据'),
(7, 'trueBranch', '为真跳转节点ID', 'STRING', 'TEXTAREA', NULL, 0, 2, '条件为真时跳转的目标节点ID，不填则走默认连线'),
(7, 'falseBranch', '为假跳转节点ID', 'STRING', 'TEXTAREA', NULL, 0, 3, '条件为假时跳转的目标节点ID，不填则走默认连线');

-- 子任务调用（流程控制）组件字段
INSERT IGNORE INTO component_field (component_id, field_code, field_name, value_type, widget_type, widget_props, required_flag, sort_order, description) VALUES
(8, 'subTaskId', '选择子任务', 'STRING', 'MULTI_SELECT', '{"optionsSource":{"sourceType":"TASK"}}', 1, 1, '从任务管理中选择已发布到生产环境的任务'),
(8, 'result_var', '结果变量', 'STRING', 'TEXTAREA', NULL, 0, 2, '子任务结果绑定的变量名');
