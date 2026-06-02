package com.cqie.datafactory.configuration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cqie.datafactory.common.dto.PageQuery;
import com.cqie.datafactory.common.exception.BusinessException;
import com.cqie.datafactory.common.result.PageResult;
import com.cqie.datafactory.configuration.controller.dto.ExternalApiCreateDTO;
import com.cqie.datafactory.configuration.controller.vo.ExternalApiVO;
import com.cqie.datafactory.configuration.entity.ExternalApi;
import com.cqie.datafactory.configuration.entity.ExternalApiVersion;
import com.cqie.datafactory.configuration.mapper.ExternalApiMapper;
import com.cqie.datafactory.configuration.mapper.ExternalApiVersionMapper;
import com.cqie.datafactory.configuration.service.ExternalApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.stream.Collectors;

@Service
public class ExternalApiServiceImpl extends ServiceImpl<ExternalApiMapper, ExternalApi> implements ExternalApiService {

    @Autowired
    private ExternalApiVersionMapper externalApiVersionMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public PageResult<ExternalApiVO> pageApi(PageQuery pageQuery, String keyword) {
        Page<ExternalApi> page = new Page<>(pageQuery.getCurrent(), pageQuery.getSize());
        LambdaQueryWrapper<ExternalApi> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(ExternalApi::getApiCode, keyword).or().like(ExternalApi::getApiName, keyword));
        }
        wrapper.orderByDesc(ExternalApi::getCreatedTime);
        
        Page<ExternalApi> resultPage = this.page(page, wrapper);
        
        PageResult<ExternalApiVO> pageResult = new PageResult<>();
        pageResult.setTotal(resultPage.getTotal());
        pageResult.setRecords(resultPage.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return pageResult;
    }

    @Override
    public void createApi(ExternalApiCreateDTO dto) {
        String apiCode = dto.getCode();
        if (!StringUtils.hasText(apiCode)) {
            throw new BusinessException("API编码不能为空");
        }
        long exists = baseMapper.countByApiCode(apiCode);
        if (exists > 0) {
            throw new BusinessException("API编码已存在（含已删除记录），请更换后重试");
        }

        ExternalApi api = new ExternalApi();
        api.setApiCode(apiCode);
        api.setApiName(dto.getName());
        api.setApiType(dto.getType());
        api.setDescription(dto.getDesc());
        api.setStatus("启用".equals(dto.getStatus()) ? 1 : 0);
        this.save(api);
    }

    @Override
    public void updateApi(Long id, ExternalApiCreateDTO dto) {
        ExternalApi api = this.getById(id);
        if (api != null) {
            api.setApiName(dto.getName());
            api.setApiType(dto.getType());
            api.setDescription(dto.getDesc());
            api.setStatus("启用".equals(dto.getStatus()) ? 1 : 0);
            this.updateById(api);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteApi(Long id) {
        ExternalApi api = this.getById(id);
        if (api == null) {
            return;
        }

        long referencedByTask = countTaskReferenceByCode(api.getApiCode());
        if (referencedByTask > 0) {
            throw new BusinessException("该三方API已关联任务，不可删除");
        }

        externalApiVersionMapper.delete(new LambdaQueryWrapper<ExternalApiVersion>().eq(ExternalApiVersion::getApiId, id));
        boolean removed = removeById(id);
        if (!removed) {
            throw new BusinessException("删除三方API失败，请稍后重试");
        }
    }

    private long countTaskReferenceByCode(String resourceCode) {
        String sql = "SELECT COUNT(1) FROM node_field_value nfv " +
                "JOIN node_instance ni ON ni.id = nfv.node_instance_id " +
                "WHERE nfv.field_value = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, resourceCode);
        return count == null ? 0L : count;
    }

    @Override
    public void toggleStatus(Long id) {
        ExternalApi api = this.getById(id);
        if (api != null) {
            api.setStatus(api.getStatus() == 1 ? 0 : 1);
            this.updateById(api);
        }
    }

    private ExternalApiVO toVO(ExternalApi entity) {
        ExternalApiVO vo = new ExternalApiVO();
        vo.setId(entity.getId());
        vo.setCode(entity.getApiCode());
        vo.setName(entity.getApiName());
        vo.setType(entity.getApiType());
        vo.setDesc(entity.getDescription());
        vo.setStatus(entity.getStatus() == 1 ? "启用" : "禁用");
        vo.setCreatedAt(entity.getCreatedTime());
        return vo;
    }
}
