package com.cqie.datafactory.executor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cqie.datafactory.executor.entity.TaskTestConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TaskTestConfigMapper extends BaseMapper<TaskTestConfig> {


    @Select("SELECT id, task_id, name, config_mode, config_data, is_default, created_by, created_time, updated_by, updated_time " +
            "FROM task_test_config WHERE task_id=#{taskId} ORDER BY is_default DESC, created_time DESC")
    List<TaskTestConfig> listLegacyByTaskId(Long taskId);

    @Select("SELECT COUNT(1) FROM information_schema.COLUMNS " +
            "WHERE TABLE_SCHEMA = (SELECT DATABASE()) AND TABLE_NAME='task_test_config' AND COLUMN_NAME='version_id'")
    Long countVersionIdColumn();
}
