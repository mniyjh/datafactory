package com.cqie.datafactory.executor.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cqie.datafactory.executor.schedule.entity.ScheduleLock;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ScheduleLockMapper extends BaseMapper<ScheduleLock> {
}
