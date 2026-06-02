package com.cqie.datafactory.executor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cqie.datafactory.executor.entity.ExecutorTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ExecutorTaskMapper extends BaseMapper<ExecutorTask> {

    @Select("SELECT COUNT(1) FROM task WHERE task_code = #{taskCode}")
    long countByTaskCode(@Param("taskCode") String taskCode);
}
