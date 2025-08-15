package com.cloudrive.service;

import com.cloudrive.model.dto.LoginDTO;
import com.cloudrive.model.dto.RegisterDTO;

public interface UserService {
    /**
     * 发送验证码
     */
    void sendVerificationCode(String email);

    /**
     * 用户注册
     */
    void register(RegisterDTO registerDTO);

    /**
     * 用户登录
     */
    String login(LoginDTO loginDTO);

    /**
     * 用户登出
     */
    void logout();

    /**
     * 强制下线指定用户
     */
    void forceLogout(Long userId);
} 