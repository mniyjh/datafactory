package com.cqie.datafactory.configuration.controller.feign;

import com.cqie.datafactory.common.result.Result;
import com.cqie.datafactory.configuration.entity.Script;
import com.cqie.datafactory.configuration.entity.ScriptVersion;
import com.cqie.datafactory.configuration.service.ScriptService;
import com.cqie.datafactory.configuration.service.ScriptVersionService;
import com.cqie.datafactory.executor.feign.vo.ScriptExecutionVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/feign/script")
public class ScriptFeignController {

    private final ScriptService scriptService;
    private final ScriptVersionService scriptVersionService;

    public ScriptFeignController(ScriptService scriptService, ScriptVersionService scriptVersionService) {
        this.scriptService = scriptService;
        this.scriptVersionService = scriptVersionService;
    }

    @GetMapping("/version/for-execution")
    public Result<ScriptExecutionVO> resolveScriptVersion(@RequestParam("scriptId") Long scriptId, @RequestParam("environment") String environment) {
        Script script = scriptService.getOne(new LambdaQueryWrapper<Script>()
                .eq(Script::getId, scriptId));

        if (script == null) {
            return Result.fail("脚本不存在");
        }

        ScriptVersion version = scriptVersionService.getOne(new LambdaQueryWrapper<ScriptVersion>()
                .eq(ScriptVersion::getScriptId, scriptId)
                .eq(ScriptVersion::getEnvironment, environment.toUpperCase())
                .eq(ScriptVersion::getIsCurrent, 1)
                .eq(ScriptVersion::getPublishStatus, 1)
                .orderByDesc(ScriptVersion::getUpdatedTime)
                .last("limit 1"));

        if (version == null) {
            return Result.fail("未找到可用脚本版本");
        }

        ScriptExecutionVO vo = new ScriptExecutionVO();
        vo.setId(version.getId());
        vo.setScriptId(scriptId);
        vo.setScriptCode(script.getScriptCode());
        vo.setScriptName(script.getScriptName());
        vo.setScriptType(script.getScriptType());
        vo.setVersion(version.getVersion());
        vo.setEnvironment(version.getEnvironment());
        vo.setScriptCodeContent(version.getScriptCodeContent());
        vo.setTimeout(version.getTimeout());
        vo.setRetryCount(version.getRetryCount());
        vo.setDependencies(version.getDependencies());
        vo.setEnvVars(version.getEnvVars());
        vo.setWorkDir(version.getWorkDir());
        vo.setInterpreterPath(version.getInterpreterPath());
        vo.setMaxMemory(version.getMaxMemory());
        return Result.success(vo);
    }
}
