package com.cqie.datafactory.configuration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cqie.datafactory.common.dto.PageQuery;
import com.cqie.datafactory.common.exception.BusinessException;
import com.cqie.datafactory.common.result.PageResult;
import com.cqie.datafactory.configuration.controller.dto.ScriptCreateDTO;
import com.cqie.datafactory.configuration.controller.vo.ScriptVO;
import com.cqie.datafactory.configuration.entity.Script;
import com.cqie.datafactory.configuration.entity.ScriptVersion;
import com.cqie.datafactory.configuration.mapper.ScriptMapper;
import com.cqie.datafactory.configuration.mapper.ScriptVersionMapper;
import com.cqie.datafactory.configuration.service.ScriptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.stream.Collectors;

@Service
public class ScriptServiceImpl extends ServiceImpl<ScriptMapper, Script> implements ScriptService {

  @Autowired
  private ScriptVersionMapper scriptVersionMapper;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Override
  public PageResult<ScriptVO> pageScript(PageQuery pageQuery, String keyword) {
    Page<Script> page = new Page<>(pageQuery.getCurrent(), pageQuery.getSize());
    LambdaQueryWrapper<Script> wrapper = new LambdaQueryWrapper<>();
    if (StringUtils.hasText(keyword)) {
      wrapper.and(w -> w.like(Script::getScriptCode, keyword).or().like(Script::getScriptName, keyword));
    }
    wrapper.orderByDesc(Script::getCreatedTime);

    Page<Script> resultPage = this.page(page, wrapper);

    PageResult<ScriptVO> pageResult = new PageResult<>();
    pageResult.setTotal(resultPage.getTotal());
    pageResult.setRecords(resultPage.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
    return pageResult;
  }

  @Override
  public void createScript(ScriptCreateDTO dto) {
    String scriptCode = dto.getCode();
    if (!StringUtils.hasText(scriptCode)) {
      throw new BusinessException("脚本编码不能为空");
    }
    long exists = baseMapper.countByScriptCode(scriptCode);
    if (exists > 0) {
      throw new BusinessException("脚本编码已存在（含已删除记录），请更换后重试");
    }

    Script script = new Script();
    script.setScriptCode(scriptCode);
    script.setScriptName(dto.getName());
    script.setScriptType(dto.getType());
    script.setDescription(dto.getDesc());
    script.setStatus("启用".equals(dto.getStatus()) ? 1 : 0);
    this.save(script);
  }

  @Override
  public void updateScript(Long id, ScriptCreateDTO dto) {
    Script script = this.getById(id);
    if (script != null) {
      script.setScriptName(dto.getName());
      script.setScriptType(dto.getType());
      script.setDescription(dto.getDesc());
      script.setStatus("启用".equals(dto.getStatus()) ? 1 : 0);
      this.updateById(script);
    }
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void deleteScript(Long id) {
    Script script = this.getById(id);
    if (script == null) {
      return;
    }

    long referencedByTask = countTaskReferenceByCode(script.getScriptCode());
    if (referencedByTask > 0) {
      throw new BusinessException("该脚本已关联任务，不可删除");
    }

    scriptVersionMapper.delete(new LambdaQueryWrapper<ScriptVersion>().eq(ScriptVersion::getScriptId, id));
    boolean removed = removeById(id);
    if (!removed) {
      throw new BusinessException("删除脚本失败，请稍后重试");
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
    Script script = this.getById(id);
    if (script != null) {
      script.setStatus(script.getStatus() == 1 ? 0 : 1);
      this.updateById(script);
    }
  }

  private ScriptVO toVO(Script entity) {
    ScriptVO vo = new ScriptVO();
    vo.setId(entity.getId());
    vo.setCode(entity.getScriptCode());
    vo.setName(entity.getScriptName());
    vo.setType(entity.getScriptType());
    vo.setDesc(entity.getDescription());
    vo.setStatus(entity.getStatus() == 1 ? "启用" : "禁用");
    vo.setCreatedAt(entity.getCreatedTime());
    return vo;
  }
}
