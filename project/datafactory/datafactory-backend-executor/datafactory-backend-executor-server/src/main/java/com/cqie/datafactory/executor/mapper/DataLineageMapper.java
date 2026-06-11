package com.cqie.datafactory.executor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cqie.datafactory.executor.entity.DataLineage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DataLineageMapper extends BaseMapper<DataLineage> {
}
