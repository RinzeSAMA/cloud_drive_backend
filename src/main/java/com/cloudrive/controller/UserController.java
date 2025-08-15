package com.cloudrive.controller;

import com.cloudrive.common.annotation.RateLimit;
import com.cloudrive.common.annotation.RateLimit.Dimension;
import com.cloudrive.common.result.Result;
import com.cloudrive.model.dto.EmailSendDTO;
import com.cloudrive.model.dto.LoginDTO;
import com.cloudrive.model.dto.RegisterDTO;
import com.cloudrive.service.UserService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理
 */                     
@RestController
@Validated
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 发送验证码
     */
    @PostMapping("/verification-code")
    @RateLimit(dimensions = { Dimension.IP }, permitsPerSecond = 1.0, timeout = 1000)
    public Result<Void> sendVerificationCode(@RequestBody EmailSendDTO emailSendDTO) {
        userService.sendVerificationCode(emailSendDTO.getEmail());
        return Result.success();
    }

    /**
     * 注册
     */
    @PostMapping("/register")
    @RateLimit(dimensions = { Dimension.IP }, permitsPerSecond = 2.0, timeout = 1000)
    public Result<Void> register(@Valid @RequestBody RegisterDTO registerDTO) {
        userService.register(registerDTO);
        return Result.success();
    }

    /**
     * 登录
     */
    @PostMapping("/login")
    @RateLimit(dimensions = { Dimension.IP }, permitsPerSecond = 5.0, timeout = 1000)
    public Result<String> login(@Valid @RequestBody LoginDTO loginDTO) {
        String token = userService.login(loginDTO);
        return Result.success(token);
    }

    /**
     * 登出
     */
    @PostMapping("/logout")
    @RateLimit(dimensions = { Dimension.USER }, permitsPerSecond = 5.0, timeout = 500)
    public Result<Void> logout() {
        userService.logout();
        return Result.success();
    }

    /**
     * 强制登出
     */ 
    @PostMapping("/forceLogout/{userId}")
    @RateLimit(dimensions = { Dimension.USER }, permitsPerSecond = 2.0, timeout = 500)
    public Result<Void> forceLogout(@PathVariable Long userId) {
        userService.forceLogout(userId);
        return Result.success();
    }
} 