package com.cqie.datafactory.configuration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cqie.datafactory.configuration.entity.Component;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ComponentMapper extends BaseMapper<Component> {

    @Select("SELECT COUNT(1) FROM task_dsl WHERE dsl_content LIKE CONCAT('%\"componentId\":', #{componentId}, '%')")
    long countTaskDslReferenceByComponentId(@Param("componentId") Long componentId);
}
