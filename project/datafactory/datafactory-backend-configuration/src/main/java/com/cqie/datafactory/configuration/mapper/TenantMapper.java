package com.cqie.datafactory.configuration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cqie.datafactory.configuration.entity.Tenant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface TenantMapper extends BaseMapper<Tenant> {

    @Select("SELECT t.* FROM sys_tenant t " +
            "INNER JOIN sys_user_tenant ut ON t.id = ut.tenant_id " +
            "WHERE ut.user_id = #{userId} AND t.status = 1")
    List<Tenant> selectByUserId(@Param("userId") Long userId);
}
