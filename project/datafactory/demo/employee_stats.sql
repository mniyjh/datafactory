-- 员工统计 SQL — 对脚本管理中的测试表做简单聚合
-- 组件: COMP_DB_QUERY
-- 输入: 无（纯查询）
-- 输出: rows + rowCount
SELECT
  COUNT(*) AS total_scripts,
  MAX(created_time) AS latest_created,
  script_type
FROM script
WHERE status = 1
GROUP BY script_type
ORDER BY script_type;
