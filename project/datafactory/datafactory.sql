-- ============================================================
-- DataFactory 数据库初始化脚本
-- 用法: source datafactory.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS datafactory DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE datafactory;

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

CREATE TABLE IF NOT EXISTS component (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  component_code VARCHAR(64) NOT NULL UNIQUE,
  component_name VARCHAR(128) NOT NULL,
  component_type VARCHAR(32) NOT NULL DEFAULT '数据处理' COMMENT '数据接入/数据处理/流程控制',
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
  widget_type VARCHAR(32) NOT NULL COMMENT 'TEXTAREA/SWITCH/MULTI_SELECT/DATE_PICKER',
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
  widget_type VARCHAR(32) DEFAULT 'TEXTAREA' COMMENT '控件类型',
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

CREATE TABLE schedule_job (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_code VARCHAR(64) NOT NULL UNIQUE,
  cron_expression VARCHAR(64) NOT NULL,
  environment VARCHAR(20) DEFAULT 'PROD',
  status TINYINT DEFAULT 1,
  retry_count INT NOT NULL DEFAULT 0 COMMENT '失败重试次数，0=不重试',
  retry_interval INT NOT NULL DEFAULT 60 COMMENT '重试间隔(秒)',
  current_retry INT NOT NULL DEFAULT 0 COMMENT '当前已重试次数',
  executor_timeout INT NOT NULL DEFAULT 0 COMMENT '执行超时(秒)，0=不限制',
  block_strategy VARCHAR(32) NOT NULL DEFAULT 'SKIP' COMMENT '并发策略: SKIP/QUEUE/COVER',
  max_queue_size INT NOT NULL DEFAULT 5 COMMENT 'QUEUE策略下最大排队数',
  misfire_strategy VARCHAR(32) NOT NULL DEFAULT 'IGNORE' COMMENT '错过触发策略: IGNORE/FIRE_ONCE/FIRE_ALL',
  window_start TIME DEFAULT NULL COMMENT '允许执行开始时间',
  window_end TIME DEFAULT NULL COMMENT '允许执行结束时间',
  parent_job_id BIGINT DEFAULT NULL COMMENT '父调度ID，父任务成功后触发',
  alarm_on_failure TINYINT NOT NULL DEFAULT 1 COMMENT '失败时告警: 0-否 1-是',
  alarm_on_timeout TINYINT NOT NULL DEFAULT 1 COMMENT '超时时告警: 0-否 1-是',
  alarm_email VARCHAR(255) DEFAULT NULL COMMENT '告警邮箱(多个逗号分隔)',
  params_config LONGTEXT DEFAULT NULL COMMENT '定时任务参数配置(JSON)',
  last_execution_id VARCHAR(64),
  last_fire_time DATETIME,
  next_fire_time DATETIME,
  created_by VARCHAR(64) DEFAULT 'admin',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64) DEFAULT 'admin',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_status (status),
  KEY idx_parent_job (parent_job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 多对多关联表: 定时任务 ↔ 任务
CREATE TABLE schedule_job_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  schedule_job_id BIGINT NOT NULL COMMENT '关联 schedule_job.id',
  task_id BIGINT NOT NULL COMMENT '关联 task.id',
  task_version_id BIGINT NOT NULL COMMENT '任务版本ID (关联 task_dsl.id)',
  environment VARCHAR(20) DEFAULT 'PROD' COMMENT '该任务的执行环境(TEST/PROD)，不填则继承job环境',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '执行顺序，数字越小越先执行',
  params_config LONGTEXT DEFAULT NULL COMMENT '每个任务的参数配置覆盖(JSON)',
  created_by VARCHAR(64) DEFAULT 'admin',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by VARCHAR(64) DEFAULT 'admin',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_job_task_version (schedule_job_id, task_id, task_version_id),
  INDEX idx_schedule_job_id (schedule_job_id),
  INDEX idx_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS schedule_lock (
  lock_key VARCHAR(128) PRIMARY KEY COMMENT '锁键(job_{jobId})',
  holder VARCHAR(128) NOT NULL COMMENT '持有者标识(实例ID)',
  acquire_at DATETIME(3) NOT NULL COMMENT '获取时间',
  expire_at DATETIME(3) NOT NULL COMMENT '过期时间',
  INDEX idx_expire (expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS schedule_job_audit_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_id BIGINT NOT NULL COMMENT '关联 schedule_job.id',
  change_type VARCHAR(16) NOT NULL COMMENT 'CREATE/UPDATE/DELETE/TOGGLE',
  field_name VARCHAR(64) DEFAULT NULL COMMENT '变更字段',
  old_value TEXT DEFAULT NULL COMMENT '旧值',
  new_value TEXT DEFAULT NULL COMMENT '新值',
  changed_by VARCHAR(64) DEFAULT 'admin',
  changed_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_job_id (job_id),
  INDEX idx_changed_time (changed_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS schedule_job_daily_stats (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_id BIGINT NOT NULL COMMENT '关联 schedule_job.id',
  stat_date DATE NOT NULL COMMENT '统计日期',
  total_count INT NOT NULL DEFAULT 0 COMMENT '总执行次数',
  success_count INT NOT NULL DEFAULT 0 COMMENT '成功次数',
  failure_count INT NOT NULL DEFAULT 0 COMMENT '失败次数',
  timeout_count INT NOT NULL DEFAULT 0 COMMENT '超时次数',
  skip_count INT NOT NULL DEFAULT 0 COMMENT '跳过次数(并发冲突)',
  avg_duration_ms BIGINT NOT NULL DEFAULT 0 COMMENT '平均执行时长(ms)',
  max_duration_ms BIGINT NOT NULL DEFAULT 0 COMMENT '最长执行时长(ms)',
  min_duration_ms BIGINT NOT NULL DEFAULT 0 COMMENT '最短执行时长(ms)',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_job_date (job_id, stat_date),
  INDEX idx_stat_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 数据血缘追踪表
-- ============================================================
CREATE TABLE IF NOT EXISTS data_lineage (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  execution_id VARCHAR(64) NOT NULL COMMENT '执行ID',
  task_id BIGINT NOT NULL COMMENT '任务ID',
  source_node_id VARCHAR(64) NOT NULL COMMENT '上游节点ID',
  target_node_id VARCHAR(64) NOT NULL COMMENT '下游节点ID',
  source_node_name VARCHAR(128) DEFAULT NULL COMMENT '上游节点名称',
  target_node_name VARCHAR(128) DEFAULT NULL COMMENT '下游节点名称',
  param_code VARCHAR(64) NOT NULL COMMENT '参数字段名',
  source_value VARCHAR(256) DEFAULT NULL COMMENT '来源引用(如 s1.a)',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_execution_id (execution_id),
  INDEX idx_task_id (task_id),
  INDEX idx_source_node (source_node_id),
  INDEX idx_target_node (target_node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 系统内置组件初始化数据
-- ============================================================

INSERT IGNORE INTO component (id, component_code, component_name, component_type, version, description, status) VALUES
(1, 'COMP_START', '开始节点', '流程控制', '1.0.0', '任务开始节点，作为任务入口定义输入/输出参数', 1),
(2, 'COMP_END', '结束节点', '流程控制', '1.0.0', '任务结束节点，收集所有上游输出合并为最终结果', 1),
(3, 'COMP_DB_QUERY', '数据库查询', '数据接入', '1.0.0', '从脚本管理选择SQL脚本连数据源执行查询', 1),
(4, 'COMP_API_CALL', '接口调用', '数据接入', '1.0.0', '调用HTTP接口获取数据', 1),
(5, 'COMP_PYTHON_EXECUTOR', 'PYTHON执行器', '数据处理', '1.0.0', '通过gRPC或本地进程执行Python脚本进行数据处理', 1),
(6, 'COMP_FILTER', '数据过滤', '数据处理', '1.0.0', '对上游数据进行列过滤或条件过滤', 1),
(7, 'COMP_BRANCH', '条件分支', '流程控制', '1.0.0', '根据表达式结果决定执行分支', 1),
(8, 'COMP_SUB_TASK', '子任务调用', '流程控制', '1.0.0', '引用并执行其他已发布的任务', 1),
(9, 'COMP_SHELL_EXECUTOR', 'Shell执行器', '数据处理', '1.0.0', '通过本地进程执行Shell脚本进行数据处理', 1);

-- 数据库查询（数据接入）组件字段
INSERT IGNORE INTO component_field (component_id, field_code, field_name, value_type, widget_type, widget_props, required_flag, sort_order, description) VALUES
(3, 'scriptCode', '选择SQL脚本', 'STRING', 'MULTI_SELECT', '{"optionsSource":{"sourceType":"SCRIPT","scriptType":"SQL"}}', 1, 1, '从脚本管理中选择已发布的SQL脚本'),
(3, 'datasource', '数据源', 'STRING', 'MULTI_SELECT', '{"optionsSource":{"sourceType":"DB_QUERY"}}', 1, 2, '选择已管理的数据源'),
(3, 'result_var', '结果变量', 'STRING', 'TEXTAREA', NULL, 0, 3, '结果绑定的变量名');

-- 接口调用（数据接入）组件字段
INSERT IGNORE INTO component_field (component_id, field_code, field_name, value_type, widget_type, widget_props, required_flag, sort_order, description) VALUES
(4, 'apiCode', '选择API', 'STRING', 'MULTI_SELECT', '{"optionsSource":{"sourceType":"API_CALL"}}', 1, 1, '从三方API管理中选择已发布的API（自动填入URL/方法/请求头/请求体）'),
(4, 'url', '接口地址', 'STRING', 'TEXTAREA', NULL, 0, 2, '请求URL（选择API后自动填入，可手动覆盖）'),
(4, 'method', '请求方式', 'STRING', 'MULTI_SELECT', '{"options":[{"label":"GET","value":"GET"},{"label":"POST","value":"POST"},{"label":"PUT","value":"PUT"},{"label":"DELETE","value":"DELETE"}]}', 0, 3, 'HTTP请求方法'),
(4, 'headers', '请求头', 'JSON', 'TEXTAREA', NULL, 0, 4, '自定义请求头JSON（选择API后自动填入）'),
(4, 'body', '请求体', 'JSON', 'TEXTAREA', NULL, 0, 5, '请求体JSON（选择API后自动填入）'),
(4, 'result_var', '结果变量', 'STRING', 'TEXTAREA', NULL, 0, 6, '响应绑定的变量名');

-- PYTHON执行器（数据处理）组件字段
INSERT IGNORE INTO component_field (component_id, field_code, field_name, value_type, widget_type, widget_props, required_flag, sort_order, description) VALUES
(5, 'scriptCode', '选择Python脚本', 'STRING', 'MULTI_SELECT', '{"optionsSource":{"sourceType":"SCRIPT","scriptType":"PYTHON"}}', 1, 1, '从脚本管理中选择已发布的Python脚本'),
(5, 'result_var', '结果变量', 'STRING', 'TEXTAREA', NULL, 0, 2, '执行结果绑定的变量名'),
(5, 'className', '类名', 'STRING', 'TEXTAREA', NULL, 0, 3, 'Python类名，如 Calculator'),
(5, 'methodName', '方法名', 'STRING', 'TEXTAREA', NULL, 0, 4, '类方法名，如 execute');

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
(7, 'branches', '分支表达式', 'JSON', 'TEXTAREA', '{"itemType":"expression","branchLoad":true}', 1, 1, '每行一个Aviator表达式字符串；从上到下求值，第一个true即命中'),
(7, 'targetNodeIds', '选择下游节点', 'JSON', 'MULTI_SELECT', '{"optionsSource":{"sourceType":"BRANCH_LOAD"}}', 1, 2, '多选下拉框，动态加载已连线到当前BRANCH的下游节点；按选择顺序与上面表达式一一对应');


-- Shell执行器（数据处理）组件字段
INSERT IGNORE INTO component_field (component_id, field_code, field_name, value_type, widget_type, widget_props, required_flag, sort_order, description) VALUES
(9, 'scriptCode', '选择Shell脚本', 'STRING', 'MULTI_SELECT', '{"optionsSource":{"sourceType":"SCRIPT","scriptType":"SHELL"}}', 1, 1, '从脚本管理中选择已发布的Shell脚本'),
(9, 'result_var', '结果变量', 'STRING', 'TEXTAREA', NULL, 0, 2, '执行结果绑定的变量名');

-- 子任务调用（流程控制）组件字段
INSERT IGNORE INTO component_field (component_id, field_code, field_name, value_type, widget_type, widget_props, required_flag, sort_order, description) VALUES
(8, 'subTaskId', '选择子任务', 'STRING', 'MULTI_SELECT', '{"optionsSource":{"sourceType":"TASK"}}', 1, 1, '从任务管理中选择已发布到生产环境的任务'),
(8, 'result_var', '结果变量', 'STRING', 'TEXTAREA', NULL, 0, 2, '子任务结果绑定的变量名');

-- =============================================
-- 安全模块: 用户/角色/权限 (RBAC)
-- =============================================

CREATE TABLE IF NOT EXISTS `sys_user` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `username` VARCHAR(64) NOT NULL UNIQUE COMMENT '用户名',
    `password` VARCHAR(256) NOT NULL COMMENT 'BCrypt加密密码',
    `real_name` VARCHAR(64) DEFAULT NULL COMMENT '真实姓名',
    `email` VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1=正常 0=禁用',
    `created_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_username` (`username`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

CREATE TABLE IF NOT EXISTS `sys_role` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(64) NOT NULL COMMENT '角色名称',
    `code` VARCHAR(64) NOT NULL UNIQUE COMMENT '角色编码',
    `description` VARCHAR(256) DEFAULT NULL COMMENT '角色描述',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1=正常 0=禁用',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色表';

CREATE TABLE IF NOT EXISTS `sys_permission` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(64) NOT NULL COMMENT '权限名称',
    `code` VARCHAR(128) NOT NULL UNIQUE COMMENT '权限编码 (resource:action)',
    `resource` VARCHAR(64) NOT NULL COMMENT '资源标识',
    `action` VARCHAR(32) NOT NULL COMMENT '操作: read/write/execute/delete',
    `description` VARCHAR(256) DEFAULT NULL COMMENT '权限描述',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_code` (`code`),
    INDEX `idx_resource` (`resource`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统权限表';

CREATE TABLE IF NOT EXISTS `sys_user_role` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

CREATE TABLE IF NOT EXISTS `sys_role_permission` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `permission_id` BIGINT NOT NULL COMMENT '权限ID',
    UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
    INDEX `idx_role_id` (`role_id`),
    INDEX `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

CREATE TABLE IF NOT EXISTS `sys_audit_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT DEFAULT NULL COMMENT '操作用户ID',
    `username` VARCHAR(64) DEFAULT NULL COMMENT '操作用户名',
    `operation` VARCHAR(128) NOT NULL COMMENT '操作描述',
    `method` VARCHAR(16) NOT NULL COMMENT 'HTTP方法',
    `url` VARCHAR(512) NOT NULL COMMENT '请求URL',
    `params` TEXT DEFAULT NULL COMMENT '请求参数(截断)',
    `ip` VARCHAR(64) DEFAULT NULL COMMENT '客户端IP',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1=成功 0=失败',
    `error_msg` VARCHAR(1024) DEFAULT NULL COMMENT '错误信息',
    `cost_ms` BIGINT DEFAULT NULL COMMENT '耗时(毫秒)',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_created_time` (`created_time`),
    INDEX `idx_operation` (`operation`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API审计日志表';

-- =============================================
-- 初始化权限数据
-- =============================================
INSERT INTO `sys_permission` (`name`, `code`, `resource`, `action`, `description`) VALUES
('读取任务', 'task:read', 'task', 'read', '查看任务列表和详情'),
('创建编辑任务', 'task:write', 'task', 'write', '创建/编辑/删除任务'),
('执行任务', 'task:execute', 'task', 'execute', '手动执行任务'),
('读取数据源', 'datasource:read', 'datasource', 'read', '查看数据源配置'),
('管理数据源', 'datasource:write', 'datasource', 'write', '创建/编辑/删除数据源'),
('读取脚本', 'script:read', 'script', 'read', '查看脚本内容'),
('管理脚本', 'script:write', 'script', 'write', '创建/编辑/删除脚本'),
('读取调度', 'schedule:read', 'schedule', 'read', '查看调度配置'),
('管理调度', 'schedule:write', 'schedule', 'write', '创建/编辑/启停调度'),
('管理用户', 'user:write', 'user', 'write', '创建/编辑/禁用用户'),
('读取监控', 'monitor:read', 'monitor', 'read', '查看监控面板'),
('读取日志', 'log:read', 'log', 'read', '查看执行日志');

-- =============================================
-- 初始化角色数据
-- =============================================
INSERT INTO `sys_role` (`name`, `code`, `description`, `status`) VALUES
('超级管理员', 'super_admin', '拥有系统所有权限', 1),
('管理员', 'admin', '用户管理 + 配置管理 + 调度管理', 1),
('数据开发', 'developer', '任务/脚本/数据源 CRUD + 执行', 1),
('运维', 'operator', '查看 + 执行 + 监控', 1),
('只读', 'viewer', '仅查看所有资源', 1);

-- =============================================
-- 角色-权限关联 (super_admin 拥有所有权限)
-- =============================================
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT (SELECT id FROM sys_role WHERE code = 'super_admin'), id FROM sys_permission;

-- admin: user管理 + 所有读权限
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT (SELECT id FROM sys_role WHERE code = 'admin'), id FROM sys_permission
WHERE code IN ('task:read', 'task:write', 'task:execute', 'datasource:read', 'datasource:write',
               'script:read', 'script:write', 'schedule:read', 'schedule:write',
               'user:write', 'monitor:read', 'log:read');

-- developer: 任务/脚本/数据源 CRUD + 执行 + 日志查看
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT (SELECT id FROM sys_role WHERE code = 'developer'), id FROM sys_permission
WHERE code IN ('task:read', 'task:write', 'task:execute', 'datasource:read', 'datasource:write',
               'script:read', 'script:write', 'schedule:read', 'log:read');

-- operator: 查看 + 执行 + 监控
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT (SELECT id FROM sys_role WHERE code = 'operator'), id FROM sys_permission
WHERE code IN ('task:read', 'task:execute', 'datasource:read', 'script:read',
               'schedule:read', 'monitor:read', 'log:read');

-- viewer: 仅查看
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT (SELECT id FROM sys_role WHERE code = 'viewer'), id FROM sys_permission
WHERE code IN ('task:read', 'datasource:read', 'script:read', 'schedule:read', 'monitor:read', 'log:read');

-- =============================================
-- 初始化管理员用户 (admin / admin123)
-- 密码: $2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi
-- (BCrypt 加密的 "admin123")
-- =============================================
INSERT INTO `sys_user` (`username`, `password`, `real_name`, `email`, `status`, `created_by`) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '系统管理员', 'admin@datafactory.dev', 1, 'SYSTEM');

-- 为 admin 分配 super_admin 角色
INSERT INTO `sys_user_role` (`user_id`, `role_id`)
SELECT (SELECT id FROM sys_user WHERE username = 'admin'), (SELECT id FROM sys_role WHERE code = 'super_admin');

