-- 员工统计 SQL — 按类型筛选脚本
-- 组件: COMP_DB_QUERY
-- 输入参数: scriptType（从上游 START 传入）
-- 输出: rows + rowCount
SELECT id, script_code, script_name, script_type, created_time
FROM script
WHERE status = 1
  AND script_type = #{scriptType}
ORDER BY created_time DESC;
