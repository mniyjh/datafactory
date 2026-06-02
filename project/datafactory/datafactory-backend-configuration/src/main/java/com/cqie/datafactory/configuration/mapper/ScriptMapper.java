package com.cqie.datafactory.configuration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cqie.datafactory.configuration.entity.Script;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ScriptMapper extends BaseMapper<Script> {

    @Select("SELECT COUNT(1) FROM script WHERE script_code = #{scriptCode}")
    long countByScriptCode(@Param("scriptCode") String scriptCode);
}
