package com.cqie.datafactory.configuration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cqie.datafactory.configuration.entity.ScriptVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ScriptVersionMapper extends BaseMapper<ScriptVersion> {

}
