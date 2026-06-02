-- ============================================================
-- 数据工厂综合演示 - 数据库初始化脚本
-- 使用方式：在本地 MySQL 中执行此脚本
-- ============================================================

CREATE DATABASE IF NOT EXISTS datafactory_demo
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE datafactory_demo;

DROP TABLE IF EXISTS employee;
CREATE TABLE employee (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  emp_name VARCHAR(64) NOT NULL COMMENT '姓名',
  department VARCHAR(64) COMMENT '部门',
  salary DECIMAL(10,2) COMMENT '薪资',
  hire_date DATE COMMENT '入职日期',
  status TINYINT DEFAULT 1 COMMENT '0-离职 1-在职'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO employee (emp_name, department, salary, hire_date, status) VALUES
('张三', '技术部', 15000.00, '2020-03-15', 1),
('李四', '市场部', 12000.00, '2019-07-01', 1),
('王五', '技术部', 18000.00, '2018-01-10', 1),
('赵六', '财务部', 13000.00, '2021-06-20', 1),
('孙七', '市场部', 11000.00, '2022-09-01', 1),
('周八', '技术部', 22000.00, '2017-05-12', 1),
('吴九', '财务部', 14000.00, '2020-11-30', 0),
('郑十', '人事部', 12500.00, '2023-02-15', 1);

DROP TABLE IF EXISTS department_budget;
CREATE TABLE department_budget (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  dept_name VARCHAR(64) NOT NULL,
  budget_year INT NOT NULL,
  budget_amount DECIMAL(12,2) COMMENT '年度预算',
  used_amount DECIMAL(12,2) DEFAULT 0 COMMENT '已使用'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO department_budget (dept_name, budget_year, budget_amount, used_amount) VALUES
('技术部', 2026, 500000.00, 320000.00),
('市场部', 2026, 300000.00, 180000.00),
('财务部', 2026, 200000.00, 95000.00),
('人事部', 2026, 150000.00, 60000.00);
