package com.cqie.datafactory.configuration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cqie.datafactory.configuration.entity.DatasourceDb;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DatasourceDbMapper extends BaseMapper<DatasourceDb> {
}