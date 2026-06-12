package com.cqie.datafactory.configuration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cqie.datafactory.configuration.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT DISTINCT p.code FROM sys_permission p " +
            "INNER JOIN sys_role_permission rp ON p.id = rp.permission_id " +
            "INNER JOIN sys_user_role ur ON rp.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId}")
    List<String> selectPermissionsByUserId(@Param("userId") Long userId);

    @Select("SELECT DISTINCT r.code FROM sys_role r " +
            "INNER JOIN sys_user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.status = 1")
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);
}
