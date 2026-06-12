package com.cqie.datafactory.configuration.service;

import com.cqie.datafactory.common.result.PageResult;
import com.cqie.datafactory.configuration.controller.dto.ChangePasswordDTO;
import com.cqie.datafactory.configuration.controller.dto.UserCreateDTO;
import com.cqie.datafactory.configuration.controller.dto.UserUpdateDTO;
import com.cqie.datafactory.configuration.controller.vo.UserVO;
import com.cqie.datafactory.common.dto.PageQuery;

public interface UserService {
    PageResult<UserVO> page(PageQuery pageQuery, String keyword);
    UserVO getById(Long id);
    UserVO create(UserCreateDTO dto);
    UserVO update(Long id, UserUpdateDTO dto);
    void delete(Long id);
    UserVO getCurrentUser();
    void changePassword(ChangePasswordDTO dto);
}
