package com.cqie.datafactory.configuration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cqie.datafactory.common.dto.PageQuery;
import com.cqie.datafactory.common.exception.BusinessException;
import com.cqie.datafactory.common.result.PageResult;
import com.cqie.datafactory.common.security.SecurityUtils;
import com.cqie.datafactory.configuration.controller.dto.ChangePasswordDTO;
import com.cqie.datafactory.configuration.controller.dto.UserCreateDTO;
import com.cqie.datafactory.configuration.controller.dto.UserUpdateDTO;
import com.cqie.datafactory.configuration.controller.vo.UserVO;
import com.cqie.datafactory.configuration.entity.Role;
import com.cqie.datafactory.configuration.entity.User;
import com.cqie.datafactory.configuration.entity.UserRole;
import com.cqie.datafactory.configuration.mapper.RoleMapper;
import com.cqie.datafactory.configuration.mapper.UserMapper;
import com.cqie.datafactory.configuration.mapper.UserRoleMapper;
import com.cqie.datafactory.configuration.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public PageResult<UserVO> page(PageQuery pageQuery, String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                    .like(User::getUsername, keyword)
                    .or()
                    .like(User::getRealName, keyword)
                    .or()
                    .like(User::getEmail, keyword)
            );
        }
        wrapper.orderByDesc(User::getCreatedTime);

        Page<User> page = userMapper.selectPage(
                new Page<>(pageQuery.getCurrent(), pageQuery.getSize()), wrapper);

        List<UserVO> records = page.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        return new PageResult<>(page.getTotal(), page.getSize(), page.getCurrent(), records);
    }

    @Override
    public UserVO getById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return toVO(user);
    }

    @Override
    @Transactional
    public UserVO create(UserCreateDTO dto) {
        // Check duplicate username
        User exist = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
        if (exist != null) {
            throw new BusinessException("用户名已存在");
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRealName(dto.getRealName());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setStatus(1);
        user.setCreatedBy(SecurityUtils.getCurrentUsername());
        userMapper.insert(user);

        // Assign roles
        if (dto.getRoleIds() != null && !dto.getRoleIds().isEmpty()) {
            assignRoles(user.getId(), dto.getRoleIds());
        }

        log.info("用户 {} 被 {} 创建", user.getUsername(), SecurityUtils.getCurrentUsername());
        return toVO(user);
    }

    @Override
    @Transactional
    public UserVO update(Long id, UserUpdateDTO dto) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        if (dto.getRealName() != null) user.setRealName(dto.getRealName());
        if (dto.getEmail() != null) user.setEmail(dto.getEmail());
        if (dto.getPhone() != null) user.setPhone(dto.getPhone());
        if (dto.getStatus() != null) user.setStatus(dto.getStatus());
        if (StringUtils.hasText(dto.getPassword())) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        user.setUpdatedBy(SecurityUtils.getCurrentUsername());
        userMapper.updateById(user);

        // Update roles if provided
        if (dto.getRoleIds() != null) {
            userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, id));
            if (!dto.getRoleIds().isEmpty()) {
                assignRoles(id, dto.getRoleIds());
            }
        }

        log.info("用户 {} 被 {} 更新", user.getUsername(), SecurityUtils.getCurrentUsername());
        return toVO(user);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // Prevent self-deletion
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId != null && currentUserId.equals(id)) {
            throw new BusinessException("不能删除自己");
        }

        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, id));
        userMapper.deleteById(id);
        log.info("用户 {} 被 {} 删除", user.getUsername(), SecurityUtils.getCurrentUsername());
    }

    @Override
    public UserVO getCurrentUser() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException("未登录");
        }
        return getById(userId);
    }

    @Override
    public void changePassword(ChangePasswordDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException("未登录");
        }
        User user = userMapper.selectById(userId);
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException("旧密码错误");
        }
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userMapper.updateById(user);
        log.info("用户 {} 修改了密码", user.getUsername());
    }

    private void assignRoles(Long userId, List<Long> roleIds) {
        for (Long roleId : roleIds) {
            UserRole ur = new UserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            userRoleMapper.insert(ur);
        }
    }

    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setStatus(user.getStatus());
        vo.setCreatedBy(user.getCreatedBy());
        vo.setCreatedTime(user.getCreatedTime());
        vo.setUpdatedTime(user.getUpdatedTime());

        // Load roles
        List<Role> roles = roleMapper.selectByUserId(user.getId());
        if (roles != null) {
            vo.setRoles(roles.stream().map(r -> {
                UserVO.RoleInfo ri = new UserVO.RoleInfo();
                ri.setId(r.getId());
                ri.setName(r.getName());
                ri.setCode(r.getCode());
                return ri;
            }).collect(Collectors.toList()));
        }
        return vo;
    }
}
