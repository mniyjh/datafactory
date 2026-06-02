package com.cqie.datafactory.configuration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cqie.datafactory.common.exception.BusinessException;
import com.cqie.datafactory.configuration.controller.dto.ExternalApiVersionCreateDTO;
import com.cqie.datafactory.configuration.controller.dto.ExternalApiVersionPromoteDTO;
import com.cqie.datafactory.configuration.controller.vo.ExternalApiVersionVO;
import com.cqie.datafactory.configuration.entity.ExternalApiVersion;
import com.cqie.datafactory.configuration.mapper.ExternalApiVersionMapper;
import com.cqie.datafactory.configuration.service.ExternalApiVersionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ExternalApiVersionServiceImpl extends ServiceImpl<ExternalApiVersionMapper, ExternalApiVersion>
        implements ExternalApiVersionService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public Map<String, Object> testConnection(ExternalApiVersionCreateDTO dto) {
        if (!StringUtils.hasText(dto.getRequestUrl())) {
            throw new BusinessException("请求URL不能为空");
        }

        try {
            HttpMethod method = HttpMethod.valueOf(dto.getRequestMethod().toUpperCase());
            HttpHeaders headers = new HttpHeaders();

            // 设置 Content-Type
            if (StringUtils.hasText(dto.getContentType())) {
                headers.setContentType(MediaType.parseMediaType(dto.getContentType()));
            }

            // 设置自定义请求头
            if (StringUtils.hasText(dto.getRequestHeaders())) {
                Map<String, String> headerMap = objectMapper.readValue(dto.getRequestHeaders(),
                        new TypeReference<Map<String, String>>() {
                        });
                headerMap.forEach(headers::add);
            }

            // 处理查询参数
            String url = dto.getRequestUrl();
            if (StringUtils.hasText(dto.getQueryParams())) {
                Map<String, String> queryMap = objectMapper.readValue(dto.getQueryParams(),
                        new TypeReference<Map<String, String>>() {
                        });
                if (!queryMap.isEmpty()) {
                    StringBuilder sb = new StringBuilder(url);
                    sb.append(url.contains("?") ? "&" : "?");
                    queryMap.forEach((k, v) -> sb.append(k).append("=").append(v).append("&"));
                    url = sb.substring(0, sb.length() - 1);
                }
            }

            HttpEntity<Object> entity = new HttpEntity<>(dto.getRequestBody(), headers);

            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.exchange(url, method, entity, String.class);
            long duration = System.currentTimeMillis() - startTime;

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("status", response.getStatusCode().value());
            result.put("duration", duration);
            result.put("body", response.getBody());
            result.put("headers", response.getHeaders());
            return result;
        } catch (Exception e) {
            throw new BusinessException("API测试失败: " + e.getMessage());
        }
    }

    @Override
    public List<ExternalApiVersionVO> listVersions(Long apiId, String environment) {
        LambdaQueryWrapper<ExternalApiVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExternalApiVersion::getApiId, apiId);
        if (environment != null && !environment.trim().isEmpty()) {
            wrapper.eq(ExternalApiVersion::getEnvironment, environment.trim().toUpperCase());
        }
        wrapper.orderByDesc(ExternalApiVersion::getCreatedTime);
        return this.list(wrapper).stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void createVersion(ExternalApiVersionCreateDTO dto) {
        ExternalApiVersion version = new ExternalApiVersion();
        version.setApiId(dto.getApiId());
        version.setEnvironment(dto.getEnvironment() == null ? null : dto.getEnvironment().trim().toUpperCase());
        version.setVersion(dto.getVersion());
        version.setDslContent(dto.getDslContent());
        version.setRequestMethod(dto.getRequestMethod());
        version.setRequestUrl(dto.getRequestUrl());
        version.setContentType(dto.getContentType());
        version.setRequestHeaders(dto.getRequestHeaders());
        version.setQueryParams(dto.getQueryParams());
        version.setRequestBody(dto.getRequestBody());
        version.setAuthType(dto.getAuthType());
        version.setAuthConfig(dto.getAuthConfig());
        version.setTimeout(dto.getTimeout());
        version.setRetryCount(dto.getRetryCount());
        version.setChangeLog(dto.getChangeLog());
        version.setIsCurrent(0);
        version.setPublishStatus(1);
        this.save(version);
    }

    @Override
    public void updateVersion(Long id, ExternalApiVersionCreateDTO dto) {
        ExternalApiVersion version = this.getById(id);
        if (version != null && version.getPublishStatus() == 0) {
            version.setDslContent(dto.getDslContent());
            version.setRequestMethod(dto.getRequestMethod());
            version.setRequestUrl(dto.getRequestUrl());
            version.setContentType(dto.getContentType());
            version.setRequestHeaders(dto.getRequestHeaders());
            version.setQueryParams(dto.getQueryParams());
            version.setRequestBody(dto.getRequestBody());
            version.setAuthType(dto.getAuthType());
            version.setAuthConfig(dto.getAuthConfig());
            version.setTimeout(dto.getTimeout());
            version.setRetryCount(dto.getRetryCount());
            version.setChangeLog(dto.getChangeLog());
            version.setVersion(dto.getVersion());
            this.updateById(version);
        }
    }

    @Override
    public void deleteVersion(Long id) {
        ExternalApiVersion version = this.getById(id);
        if (version != null && version.getPublishStatus() == 0) {
            Long refCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM node_field_value WHERE field_value = ?", Long.class,
                    String.valueOf(id));
            if (refCount != null && refCount > 0) {
                throw new BusinessException("该版本正被任务节点引用，无法删除");
            }
            this.removeById(id);
        }
    }

    @Override
    public void publishVersion(Long id) {
        ExternalApiVersion version = this.getById(id);
        if (version != null) {
            version.setPublishStatus(1);
            this.updateById(version);
        }
    }

    @Override
    @Transactional
    public void promoteVersion(ExternalApiVersionPromoteDTO dto) {
        ExternalApiVersion source = this.getById(dto.getSourceVersionId());
        if (source == null) {
            throw new BusinessException("未找到选中的版本");
        }

        String targetEnvironment = dto.getToEnvironment() == null ? null : dto.getToEnvironment().trim().toUpperCase();
        LambdaQueryWrapper<ExternalApiVersion> existsWrapper = new LambdaQueryWrapper<>();
        existsWrapper.eq(ExternalApiVersion::getApiId, source.getApiId())
                .eq(ExternalApiVersion::getEnvironment, targetEnvironment)
                .eq(ExternalApiVersion::getVersion, source.getVersion());
        if (this.count(existsWrapper) > 0) {
            throw new BusinessException("发布失败：目标环境（" + targetEnvironment + "）已存在版本 " + source.getVersion() + "，请勿重复发布");
        }

        ExternalApiVersion target = new ExternalApiVersion();
        target.setApiId(source.getApiId());
        target.setEnvironment(targetEnvironment);
        target.setVersion(source.getVersion());
        target.setDslContent(source.getDslContent());
        target.setRequestMethod(source.getRequestMethod());
        target.setRequestUrl(source.getRequestUrl());
        target.setContentType(source.getContentType());
        target.setRequestHeaders(source.getRequestHeaders());
        target.setQueryParams(source.getQueryParams());
        target.setRequestBody(source.getRequestBody());
        target.setAuthType(source.getAuthType());
        target.setAuthConfig(source.getAuthConfig());
        target.setTimeout(source.getTimeout());
        target.setRetryCount(source.getRetryCount());
        target.setChangeLog(dto.getChangeLog());
        target.setIsCurrent(0);
        target.setPublishStatus(0);
        this.save(target);
    }

    @Override
    @Transactional
    public void selectCurrent(Long id) {
        ExternalApiVersion version = this.getById(id);
        if (version != null) {
            // 同一API下所有环境只能有一个当前版本
            LambdaQueryWrapper<ExternalApiVersion> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ExternalApiVersion::getApiId, version.getApiId());

            ExternalApiVersion update = new ExternalApiVersion();
            update.setIsCurrent(0);
            this.update(update, wrapper);

            // 设置当前版本
            version.setIsCurrent(1);
            this.updateById(version);
        }
    }

    private ExternalApiVersionVO toVO(ExternalApiVersion entity) {
        ExternalApiVersionVO vo = new ExternalApiVersionVO();
        vo.setId(entity.getId());
        vo.setApiId(entity.getApiId());
        vo.setVersion(entity.getVersion());
        vo.setEnvironment(entity.getEnvironment());
        vo.setStatus(entity.getPublishStatus() == 1 ? "已发布" : "未发布");
        vo.setCurrent(entity.getIsCurrent() == 1 ? "是" : "否");
        vo.setRemark(entity.getChangeLog());
        vo.setCreator(entity.getCreatedBy());
        vo.setCreatedAt(entity.getCreatedTime());
        vo.setDsl(entity.getDslContent());
        vo.setDslContent(entity.getDslContent());
        return vo;
    }
}
