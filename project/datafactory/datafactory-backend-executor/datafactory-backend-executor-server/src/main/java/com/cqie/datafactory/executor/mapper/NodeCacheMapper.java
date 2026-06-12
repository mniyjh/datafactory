package com.cqie.datafactory.executor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cqie.datafactory.executor.entity.NodeCache;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

@Mapper
public interface NodeCacheMapper extends BaseMapper<NodeCache> {

    @Select("SELECT * FROM node_cache WHERE node_hash = #{nodeHash}")
    NodeCache selectByHash(@Param("nodeHash") String nodeHash);

    @Delete("DELETE FROM node_cache WHERE created_at < DATE_SUB(NOW(), INTERVAL #{days} DAY)")
    int deleteOlderThan(@Param("days") int days);
}
