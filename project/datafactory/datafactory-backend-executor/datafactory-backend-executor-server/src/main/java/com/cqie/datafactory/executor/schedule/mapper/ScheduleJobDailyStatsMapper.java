package com.cqie.datafactory.executor.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cqie.datafactory.executor.schedule.entity.ScheduleJobDailyStats;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ScheduleJobDailyStatsMapper extends BaseMapper<ScheduleJobDailyStats> {
}
