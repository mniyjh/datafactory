package com.cqie.datafactory.configuration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cqie.datafactory.configuration.controller.dto.ScriptVersionCreateDTO;
import com.cqie.datafactory.configuration.controller.vo.ScriptVersionVO;
import com.cqie.datafactory.configuration.entity.ScriptVersion;
import com.cqie.datafactory.configuration.mapper.ScriptVersionMapper;
import com.cqie.datafactory.configuration.service.ScriptVersionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ScriptVersionServiceImpl extends ServiceImpl<ScriptVersionMapper, ScriptVersion> implements ScriptVersionService {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public List<ScriptVersionVO> listVersions(Long scriptId, String environment) {
        LambdaQueryWrapper<ScriptVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ScriptVersion::getScriptId, scriptId);
        if (environment != null && !environment.isEmpty()) {
            wrapper.eq(ScriptVersion::getEnvironment, environment.toUpperCase());
        }
        wrapper.orderByDesc(ScriptVersion::getCreatedTime);
        return this.list(wrapper).stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void createVersion(ScriptVersionCreateDTO dto) {
        String environment = dto.getEnvironment().toUpperCase();
        LambdaQueryWrapper<ScriptVersion> existsWrapper = new LambdaQueryWrapper<>();
        existsWrapper.eq(ScriptVersion::getScriptId, dto.getScriptId())
                .eq(ScriptVersion::getEnvironment, environment)
                .eq(ScriptVersion::getVersion, dto.getVersion());
        if (this.count(existsWrapper) > 0) {
            throw new IllegalStateException("发布失败：目标环境（" + environment + "）已存在版本 " + dto.getVersion() + "，请勿重复发布");
        }

        ScriptVersion version = new ScriptVersion();
        version.setScriptId(dto.getScriptId());
        version.setEnvironment(environment);
        version.setVersion(dto.getVersion());
        version.setScriptCodeContent(
            dto.getScriptCodeContent() != null ? dto.getScriptCodeContent() : dto.getScriptCode());
        version.setTimeout(dto.getTimeout());
        version.setRetryCount(dto.getRetryCount());
        version.setDependencies(dto.getDependencies());
        version.setEnvVars(dto.getEnvVars());
        version.setWorkDir(dto.getWorkDir());
        version.setInterpreterPath(dto.getInterpreterPath());
        version.setMaxMemory(dto.getMaxMemory());
        version.setCpuLimit(dto.getCpuLimit());
        version.setChangeLog(dto.getChangeLog());
        version.setIsCurrent(0);
        version.setPublishStatus(0);
        this.save(version);
    }

    @Override
    public void updateVersion(Long id, ScriptVersionCreateDTO dto) {
        ScriptVersion version = this.getById(id);
        if (version != null && version.getPublishStatus() == 0) {
            version.setScriptCodeContent(
                dto.getScriptCodeContent() != null ? dto.getScriptCodeContent() : dto.getScriptCode());
            version.setVersion(dto.getVersion());
            version.setTimeout(dto.getTimeout());
            version.setRetryCount(dto.getRetryCount());
            version.setDependencies(dto.getDependencies());
            version.setEnvVars(dto.getEnvVars());
            version.setWorkDir(dto.getWorkDir());
            version.setInterpreterPath(dto.getInterpreterPath());
            version.setMaxMemory(dto.getMaxMemory());
            version.setCpuLimit(dto.getCpuLimit());
            version.setChangeLog(dto.getChangeLog());
            this.updateById(version);
        }
    }

    @Override
    public void deleteVersion(Long id) {
        ScriptVersion version = this.getById(id);
        if (version != null && version.getPublishStatus() == 0) {
            this.removeById(id);
        }
    }

    @Override
    public void publishVersion(Long id) {
        ScriptVersion version = this.getById(id);
        if (version != null) {
            version.setPublishStatus(1);
            this.updateById(version);
        }
    }

    @Override
    @Transactional
    public void selectCurrent(Long id) {
        ScriptVersion version = this.getById(id);
        if (version != null) {
            LambdaQueryWrapper<ScriptVersion> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ScriptVersion::getScriptId, version.getScriptId());
            wrapper.eq(ScriptVersion::getEnvironment, version.getEnvironment());
            
            ScriptVersion update = new ScriptVersion();
            update.setIsCurrent(0);
            this.update(update, wrapper);
            
            version.setIsCurrent(1);
            this.updateById(version);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void promote(Long versionId, String toEnvironment) {
        ScriptVersion source = this.getById(versionId);
        if (source == null) {
            throw new RuntimeException("源版本不存在");
        }
        String env = toEnvironment.toUpperCase();

        long exists = this.count(new LambdaQueryWrapper<ScriptVersion>()
                .eq(ScriptVersion::getScriptId, source.getScriptId())
                .eq(ScriptVersion::getEnvironment, env)
                .eq(ScriptVersion::getVersion, source.getVersion()));
        if (exists > 0) {
            throw new RuntimeException("目标环境 " + env + " 已存在版本 " + source.getVersion());
        }

        // 标记源版本为已发布
        if (source.getPublishStatus() == null || source.getPublishStatus() == 0) {
            source.setPublishStatus(1);
            this.updateById(source);
        }

        ScriptVersion target = new ScriptVersion();
        target.setScriptId(source.getScriptId());
        target.setEnvironment(env);
        target.setVersion(source.getVersion());
        target.setScriptCodeContent(source.getScriptCodeContent());
        target.setTimeout(source.getTimeout());
        target.setRetryCount(source.getRetryCount());
        target.setDependencies(source.getDependencies());
        target.setEnvVars(source.getEnvVars());
        target.setWorkDir(source.getWorkDir());
        target.setInterpreterPath(source.getInterpreterPath());
        target.setMaxMemory(source.getMaxMemory());
        target.setCpuLimit(source.getCpuLimit());
        target.setChangeLog("由 " + source.getEnvironment() + " 环境晋升");
        target.setIsCurrent(0);
        target.setPublishStatus(1);
        this.save(target);
    }

    @Override
    public Map<String, Object> testScript(Long versionId, String inputJson) {
        ScriptVersion version = this.getById(versionId);
        if (version == null) {
            throw new RuntimeException("版本不存在");
        }
        String code = version.getScriptCodeContent();
        if (code == null || code.isBlank()) {
            throw new RuntimeException("脚本内容为空");
        }
        Map<String, Object> result = new HashMap<>();
        try {
            boolean isWin = System.getProperty("os.name").toLowerCase().contains("win");
            String scriptType = getScriptType(version.getScriptId());
            boolean isShell = "SHELL".equalsIgnoreCase(scriptType);

            String fileExt = isShell ? (isWin ? ".bat" : ".sh") : ".py";
            String content = code;
            if (isShell && isWin && !code.startsWith("@echo off")) {
                content = "@echo off\r\nchcp 65001 >nul 2>&1\r\n" + code;
            }

            Path temp = Files.createTempFile("df-test-", fileExt);
            Files.writeString(temp, content, StandardCharsets.UTF_8);

            String interpreter;
            if (isShell && isWin) {
                interpreter = "cmd";
            } else {
                interpreter = version.getInterpreterPath() != null ? version.getInterpreterPath() : "python";
            }

            ProcessBuilder pb;
            if (isShell && isWin) {
                pb = new ProcessBuilder("cmd", "/q", "/c", temp.toAbsolutePath().toString());
            } else {
                pb = new ProcessBuilder(interpreter, temp.toAbsolutePath().toString());
            }
            pb.redirectErrorStream(false);
            int timeout = version.getTimeout() != null ? version.getTimeout() : 30;
            Process process = pb.start();
            // 写入输入参数到stdin，然后关闭
            if (inputJson != null && !inputJson.isBlank()) {
                process.getOutputStream().write(inputJson.getBytes(StandardCharsets.UTF_8));
                process.getOutputStream().flush();
            }
            process.getOutputStream().close();
            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                result.put("success", false);
                result.put("error", "执行超时(" + timeout + "s)");
                return result;
            }
            String stdout;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                stdout = br.lines().reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
            }
            String stderr;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                stderr = br.lines().reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
            }
            int exitCode = process.exitValue();
            result.put("success", exitCode == 0);
            result.put("exitCode", exitCode);
            result.put("stdout", stdout);
            result.put("stderr", stderr);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    private String getScriptType(Long scriptId) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT script_type FROM script WHERE id = ?", scriptId);
            return rows.isEmpty() ? "PYTHON" : String.valueOf(rows.get(0).get("script_type"));
        } catch (Exception e) {
            return "PYTHON";
        }
    }

    private ScriptVersionVO toVO(ScriptVersion entity) {
        ScriptVersionVO vo = new ScriptVersionVO();
        vo.setId(entity.getId());
        vo.setScriptId(entity.getScriptId());
        vo.setVersion(entity.getVersion());
        vo.setStatus(entity.getPublishStatus() == 1 ? "已发布" : "未发布");
        vo.setCurrent(entity.getIsCurrent() == 1 ? "是" : "否");
        vo.setChangeLog(entity.getChangeLog());
        vo.setCreatedBy(entity.getCreatedBy());
        vo.setCreatedTime(entity.getCreatedTime());

        vo.setScriptCode(entity.getScriptCodeContent());
        vo.setTimeout(entity.getTimeout());
        vo.setRetryCount(entity.getRetryCount());
        vo.setDependencies(entity.getDependencies());
        vo.setEnvVars(entity.getEnvVars());
        vo.setWorkDir(entity.getWorkDir());
        vo.setInterpreterPath(entity.getInterpreterPath());
        vo.setMaxMemory(entity.getMaxMemory());
        vo.setCpuLimit(entity.getCpuLimit());
        return vo;
    }
}
