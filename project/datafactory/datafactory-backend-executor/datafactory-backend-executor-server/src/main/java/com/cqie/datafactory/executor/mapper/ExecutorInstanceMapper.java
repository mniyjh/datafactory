package com.cqie.datafactory.executor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cqie.datafactory.executor.entity.ExecutorInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;

@Mapper
public interface ExecutorInstanceMapper extends BaseMapper<ExecutorInstance> {

    @Select("SELECT * FROM executor_instance WHERE status = 'ONLINE' ORDER BY id")
    List<ExecutorInstance> selectOnlineInstances();

    @Select("SELECT COUNT(*) FROM executor_instance WHERE status = 'ONLINE'")
    int countOnline();

    @Update("UPDATE executor_instance SET last_heartbeat = NOW() WHERE instance_id = #{instanceId}")
    int updateHeartbeat(@Param("instanceId") String instanceId);

    @Update("UPDATE executor_instance SET status = 'OFFLINE' " +
            "WHERE status = 'ONLINE' AND last_heartbeat < DATE_SUB(NOW(), INTERVAL 30 SECOND)")
    int markDeadInstances();

    @Select("SELECT * FROM executor_instance WHERE instance_id = #{instanceId}")
    ExecutorInstance selectByInstanceId(@Param("instanceId") String instanceId);
}
