package com.cqie.datafactory.configuration.controller.feign;

import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.common.util.AesEncryptUtil;
import com.cqie.datafactory.configuration.entity.DatasourceDbVersion;
import com.cqie.datafactory.configuration.service.DatasourceDbVersionService;
import com.cqie.datafactory.executor.feign.vo.DbVersionResolveVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/feign/datasource")
public class DatasourceFeignController {

    private final DatasourceDbVersionService dbVersionService;
    private final AesEncryptUtil aesEncryptUtil;

    public DatasourceFeignController(DatasourceDbVersionService dbVersionService, AesEncryptUtil aesEncryptUtil) {
        this.dbVersionService = dbVersionService;
        this.aesEncryptUtil = aesEncryptUtil;
    }

    @GetMapping("/db-version/resolve")
    public Result<DbVersionResolveVO> resolveDbVersion(@RequestParam("dbId") Long dbId, @RequestParam("environment") String environment) {
        DatasourceDbVersion version = dbVersionService.getOne(new LambdaQueryWrapper<DatasourceDbVersion>()
                .eq(DatasourceDbVersion::getDbId, dbId)
                .eq(DatasourceDbVersion::getIsCurrent, 1)
                .orderByDesc(DatasourceDbVersion::getUpdatedTime)
                .last("limit 1"));

        if (version == null) {
            return Result.fail("未找到可用数据源版本");
        }

        DbVersionResolveVO vo = new DbVersionResolveVO();
        vo.setId(version.getId());
        vo.setDbId(version.getDbId());
        vo.setEnvironment(version.getEnvironment());
        vo.setJdbcUrl(version.getJdbcUrl());
        vo.setUsername(version.getUsername());
        vo.setDbType(version.getDbType());
        vo.setPassword(aesEncryptUtil.decrypt(version.getPassword()));
        return Result.success(vo);
    }
}
