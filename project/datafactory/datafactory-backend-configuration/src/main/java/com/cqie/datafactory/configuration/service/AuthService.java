package com.cqie.datafactory.configuration.service;

import com.cqie.datafactory.configuration.controller.dto.LoginDTO;
import com.cqie.datafactory.configuration.controller.vo.LoginVO;

public interface AuthService {
    LoginVO login(LoginDTO loginDTO);
    LoginVO refresh(String refreshToken);
    void logout(Long userId);
}
