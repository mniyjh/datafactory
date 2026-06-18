-- ============================================================
-- 迁移脚本: 为执行日志增加 text_output 字段
-- 用途: 存储从 JSON 输出中提取的人类可读格式化文本
--       支持换行符、制表符、emoji 等自然语言美观输出
-- 创建日期: 2026-06-18
-- ============================================================

ALTER TABLE execution_log
    ADD COLUMN text_output LONGTEXT DEFAULT NULL COMMENT '格式化文本输出（从output_result提取的自然语言报告）'
    AFTER output_result;

ALTER TABLE node_execution_log
    ADD COLUMN text_output LONGTEXT DEFAULT NULL COMMENT '格式化文本输出（从output_data提取的自然语言报告）'
    AFTER output_data;
