package com.cqie.datafactory.configuration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cqie.datafactory.configuration.entity.ExternalApi;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ExternalApiMapper extends BaseMapper<ExternalApi> {

    @Select("SELECT COUNT(1) FROM external_api WHERE api_code = #{apiCode}")
    long countByApiCode(@Param("apiCode") String apiCode);
}
