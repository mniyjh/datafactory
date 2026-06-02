package com.cqie.datafactory.configuration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cqie.datafactory.common.dto.PageQuery;
import com.cqie.datafactory.common.result.PageResult;
import com.cqie.datafactory.common.exception.BusinessException;
import com.cqie.datafactory.configuration.controller.dto.DatasourceDbCreateDTO;
import com.cqie.datafactory.configuration.controller.dto.DatasourceDbTestDTO;
import com.cqie.datafactory.configuration.controller.vo.DatasourceDbVO;
import com.cqie.datafactory.configuration.entity.DatasourceDb;
import com.cqie.datafactory.configuration.entity.DatasourceDbVersion;
import com.cqie.datafactory.configuration.mapper.DatasourceDbMapper;
import com.cqie.datafactory.configuration.mapper.DatasourceDbVersionMapper;
import com.cqie.datafactory.configuration.service.DatasourceDbService;
import com.cqie.datafactory.common.util.CascadeDeleteHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.stream.Collectors;

@Service
public class DatasourceDbServiceImpl extends ServiceImpl<DatasourceDbMapper, DatasourceDb> implements DatasourceDbService {

    @Autowired
    private DatasourceDbVersionMapper datasourceDbVersionMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void testConnection(DatasourceDbTestDTO dto) {
        if (!StringUtils.hasText(dto.getJdbcUrl())) {
            throw new BusinessException("JDBC URL不能为空");
        }
        
        Connection conn = null;
        try {
            // 简单起见，这里直接使用 DriverManager
            // 如果需要支持多种数据库，可能需要根据 dbType 加载对应的驱动类
            conn = DriverManager.getConnection(dto.getJdbcUrl(), dto.getUsername(), dto.getPassword());
            if (conn == null) {
                throw new BusinessException("连接失败：无法获取数据库连接");
            }
        } catch (SQLException e) {
            throw new BusinessException("连接失败：" + e.getMessage());
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    // ignore
                }
            }
        }
    }

    @Override
    public PageResult<DatasourceDbVO> pageDb(PageQuery pageQuery, String keyword) {
        Page<DatasourceDb> page = new Page<>(pageQuery.getCurrent(), pageQuery.getSize());
        LambdaQueryWrapper<DatasourceDb> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(DatasourceDb::getDbCode, keyword).or().like(DatasourceDb::getDbName, keyword));
        }
        wrapper.orderByDesc(DatasourceDb::getCreatedTime);
        
        Page<DatasourceDb> resultPage = this.page(page, wrapper);
        
        PageResult<DatasourceDbVO> pageResult = new PageResult<>();
        pageResult.setTotal(resultPage.getTotal());
        pageResult.setRecords(resultPage.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return pageResult;
    }

    @Override
    public void createDb(DatasourceDbCreateDTO dto) {
        DatasourceDb db = new DatasourceDb();
        db.setDbCode(dto.getCode());
        db.setDbName(dto.getName());
        db.setDbType(dto.getType());
        db.setDescription(dto.getDesc());
        db.setStatus("启用".equals(dto.getStatus()) ? 1 : 0);
        this.save(db);
    }

    @Override
    public void updateDb(Long id, DatasourceDbCreateDTO dto) {
        DatasourceDb db = this.getById(id);
        if (db != null) {
            db.setDbCode(dto.getCode());
            db.setDbName(dto.getName());
            db.setDbType(dto.getType());
            db.setDescription(dto.getDesc());
            db.setStatus("启用".equals(dto.getStatus()) ? 1 : 0);
            this.updateById(db);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDb(Long id) {
        DatasourceDb db = this.getById(id);
        if (db == null) {
            return;
        }

        long referencedByTask = countTaskReferenceByCode(db.getDbCode());
        if (referencedByTask > 0) {
            throw new BusinessException("该数据库已关联任务，不可删除");
        }

        new CascadeDeleteHelper()
                .addChildDelete(() -> datasourceDbVersionMapper.delete(
                        new LambdaQueryWrapper<DatasourceDbVersion>().eq(DatasourceDbVersion::getDbId, id)
                ))
                .setParentDelete(() -> this.removeById(id))
                .execute();
    }

    private long countTaskReferenceByCode(String resourceCode) {
        String sql = "SELECT COUNT(1) FROM node_field_value nfv " +
                "JOIN node_instance ni ON ni.id = nfv.node_instance_id " +
                "WHERE nfv.field_value = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, resourceCode);
        return count == null ? 0L : count;
    }

    private DatasourceDbVO toVO(DatasourceDb db) {
        DatasourceDbVO vo = new DatasourceDbVO();
        vo.setId(db.getId());
        vo.setCode(db.getDbCode());
        vo.setName(db.getDbName());
        vo.setType(db.getDbType());
        vo.setStatus(db.getStatus() == 1 ? "启用" : "禁用");
        vo.setDesc(db.getDescription());
        vo.setCreatedBy(db.getCreatedBy());
        vo.setCreatedAt(db.getCreatedTime());
        return vo;
    }
}