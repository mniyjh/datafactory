package com.cqie.datafactory.configuration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cqie.datafactory.configuration.entity.TokenBlacklist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

@Mapper
public interface TokenBlacklistMapper extends BaseMapper<TokenBlacklist> {

    @Select("SELECT COUNT(*) FROM sys_token_blacklist WHERE jti = #{jti}")
    boolean existsByJti(@Param("jti") String jti);

    @Delete("DELETE FROM sys_token_blacklist WHERE expires_at < NOW()")
    int deleteExpired();
}
