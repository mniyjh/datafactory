package com.cqie.datafactory.executor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cqie.datafactory.executor.entity.ExecutionLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExecutionLogMapper extends BaseMapper<ExecutionLog> {
}
