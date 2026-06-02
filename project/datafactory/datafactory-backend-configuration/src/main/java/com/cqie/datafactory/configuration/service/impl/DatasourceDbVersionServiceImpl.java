package com.cqie.datafactory.configuration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cqie.datafactory.common.exception.BusinessException;
import com.cqie.datafactory.common.util.AesEncryptUtil;
import com.cqie.datafactory.configuration.controller.dto.DatasourceDbTestDTO;
import com.cqie.datafactory.configuration.controller.dto.DatasourceDbVersionCreateDTO;
import com.cqie.datafactory.configuration.controller.vo.DatasourceDbVersionVO;
import com.cqie.datafactory.configuration.entity.DatasourceDbVersion;
import com.cqie.datafactory.configuration.mapper.DatasourceDbVersionMapper;
import com.cqie.datafactory.configuration.service.DatasourceDbVersionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DatasourceDbVersionServiceImpl extends ServiceImpl<DatasourceDbVersionMapper, DatasourceDbVersion> implements DatasourceDbVersionService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AesEncryptUtil aesEncryptUtil;

    @Override
    public List<DatasourceDbVersionVO> listVersions(Long dbId, String environment) {
        LambdaQueryWrapper<DatasourceDbVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DatasourceDbVersion::getDbId, dbId);
        if (environment != null && !environment.trim().isEmpty()) {
            wrapper.eq(DatasourceDbVersion::getEnvironment, environment.trim().toUpperCase());
        }
        wrapper.orderByDesc(DatasourceDbVersion::getCreatedTime);
        return this.list(wrapper).stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public void createVersion(DatasourceDbVersionCreateDTO dto) {
        if (!StringUtils.hasText(dto.getEnvironment())) {
            throw new BusinessException("环境不能为空");
        }
        if (!StringUtils.hasText(dto.getVersion())) {
            throw new BusinessException("版本号不能为空");
        }
        DatasourceDbVersion version = new DatasourceDbVersion();
        version.setDbId(dto.getDbId());
        version.setEnvironment(dto.getEnvironment().toUpperCase());
        version.setVersion(dto.getVersion());
        version.setDslContent(dto.getDslContent());
        version.setDbType(dto.getDbType());
        version.setDbName(dto.getDbName());
        version.setJdbcUrl(dto.getJdbcUrl());
        version.setUsername(dto.getUsername());
        version.setChangeLog(dto.getChangeLog());
        version.setIsCurrent(0);
        version.setPublishStatus(1);
        version.setPassword(aesEncryptUtil.encrypt(dto.getPassword()));
        this.save(version);
    }

    @Override
    public void testConnection(DatasourceDbTestDTO dto) {
        if (!StringUtils.hasText(dto.getJdbcUrl())) {
            throw new BusinessException("JDBC URL不能为空");
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dto.getJdbcUrl());
        config.setUsername(dto.getUsername());
        config.setPassword(dto.getPassword());
        config.setConnectionTimeout(10000);
        config.setValidationTimeout(5000);
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        try (HikariDataSource ds = new HikariDataSource(config)) {
            ds.getConnection().close();
        } catch (Exception e) {
            throw new BusinessException("连接失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void selectVersion(Long id) {
        DatasourceDbVersion version = this.getById(id);
        if (version == null) {
            throw new BusinessException("版本不存在");
        }
        LambdaQueryWrapper<DatasourceDbVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DatasourceDbVersion::getDbId, version.getDbId());

        DatasourceDbVersion update = new DatasourceDbVersion();
        update.setIsCurrent(0);
        this.update(update, wrapper);

        version.setIsCurrent(1);
        version.setPublishStatus(1);
        this.updateById(version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void promote(Long versionId, String toEnvironment) {
        DatasourceDbVersion source = this.getById(versionId);
        if (source == null) {
            throw new BusinessException("源版本不存在");
        }
        String env = toEnvironment.toUpperCase();
        // 检查目标环境是否已有同版本号
        long exists = this.count(new LambdaQueryWrapper<DatasourceDbVersion>()
                .eq(DatasourceDbVersion::getDbId, source.getDbId())
                .eq(DatasourceDbVersion::getEnvironment, env)
                .eq(DatasourceDbVersion::getVersion, source.getVersion()));
        if (exists > 0) {
            throw new BusinessException("目标环境已存在版本 " + source.getVersion());
        }
        DatasourceDbVersion target = new DatasourceDbVersion();
        target.setDbId(source.getDbId());
        target.setEnvironment(env);
        target.setVersion(source.getVersion());
        target.setDslContent(source.getDslContent());
        target.setDbType(source.getDbType());
        target.setDbName(source.getDbName());
        target.setJdbcUrl(source.getJdbcUrl());
        target.setUsername(source.getUsername());
        target.setPassword(source.getPassword());
        target.setChangeLog("发布自 " + source.getEnvironment() + " 环境");
        target.setIsCurrent(0);
        target.setPublishStatus(1);
        this.save(target);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteVersion(Long id) {
        DatasourceDbVersion version = this.getById(id);
        if (version == null) return;
        // 检查是否有活跃的节点引用此版本
        Long refCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM node_field_value WHERE field_value = ?", Long.class,
                String.valueOf(id));
        if (refCount != null && refCount > 0) {
            throw new BusinessException("该版本正被任务节点引用，无法删除");
        }
        this.removeById(id);
    }

    private DatasourceDbVersionVO toVO(DatasourceDbVersion version) {
        DatasourceDbVersionVO vo = new DatasourceDbVersionVO();
        vo.setId(version.getId());
        vo.setDbId(version.getDbId());
        vo.setVersion(version.getVersion());
        vo.setEnvironment(version.getEnvironment());
        vo.setDslContent(version.getDslContent());
        vo.setRemark(version.getChangeLog());
        vo.setCurrent(version.getIsCurrent() == 1 ? "是" : "否");
        vo.setStatus(version.getPublishStatus() == 1 ? "已发布" : "待发布");
        vo.setCreator(version.getCreatedBy());
        vo.setCreatedAt(version.getCreatedTime());
        return vo;
    }
}