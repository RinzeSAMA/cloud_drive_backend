package com.cloudrive.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudrive.common.enums.ErrorCode;
import com.cloudrive.common.util.ExceptionUtil;
import com.cloudrive.common.util.PasswordUtil;
import com.cloudrive.mapper.UserMapper;
import com.cloudrive.model.dto.LoginDTO;
import com.cloudrive.model.dto.RegisterDTO;
import com.cloudrive.model.entity.User;
import com.cloudrive.redis.VerificationCodeRedis;
import com.cloudrive.service.UserService;
import com.cloudrive.common.util.EmailUtil;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

/**
 * 用户服务实现类
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);

    @Resource
    private final UserMapper userMapper;

    private final VerificationCodeRedis verificationCodeRedis;


    @Override
    public void sendVerificationCode(String email) {
        // 检查邮箱是否已被注册
        boolean exists = userMapper.exists(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email)
        );
        ExceptionUtil.throwIf(exists, ErrorCode.EMAIL_ALREADY_EXIST);

        // 生成6位数字验证码
        String code = String.valueOf(100000 + new Random().nextInt(900000));

        try {
            // 使用EmailUtils工具类发送验证码邮件
            boolean success = EmailUtil.sendVerificationCode(email, code);
            if (!success) {
                throw new Exception("邮件发送失败");
            }
            
            // 保存验证码到Redis
            verificationCodeRedis.saveVerificationCode(email, code);
        } catch (Exception e) {
            logger.error("发送验证码失败：{}", e.getMessage());
            ExceptionUtil.throwBizException(ErrorCode.EMAIL_SEND_ERROR);
        }
    }

    @Override
    @Transactional
    public void register(RegisterDTO registerDTO) {
        // 检查用户名是否已存在
        boolean usernameExists = userMapper.exists(
                new LambdaQueryWrapper<User>().eq(User::getUsername, registerDTO.getUsername())
        );
        ExceptionUtil.throwIf(usernameExists, ErrorCode.USERNAME_EXISTS);

        // 检查邮箱是否已存在
        boolean emailExists = userMapper.exists(
                new LambdaQueryWrapper<User>().eq(User::getEmail, registerDTO.getEmail())
        );
        ExceptionUtil.throwIf(emailExists, ErrorCode.EMAIL_EXISTS);

        // 验证验证码
        String savedCode = verificationCodeRedis.getVerificationCode(registerDTO.getEmail());
        ExceptionUtil.throwIf(
            savedCode == null || !savedCode.equals(registerDTO.getCode()),
            ErrorCode.VERIFICATION_CODE_ERROR
        );

        // 创建新用户
        User user = new User();
        user.setUsername(registerDTO.getUsername());
        user.setPassword(PasswordUtil.encode(registerDTO.getPassword()));
        user.setEmail(registerDTO.getEmail());
        user.setStatus(1); // 1表示正常状态
        try{
            userMapper.insert(user);
        } catch(DuplicateKeyException e){
            ExceptionUtil.throwBizException(ErrorCode.EMAIL_ALREADY_EXIST);
        }
        // 删除验证码
        verificationCodeRedis.deleteVerificationCode(registerDTO.getEmail());
    }

    @Override
    public String login(LoginDTO loginDTO) {
        logger.info("开始处理登录请求，用户名：{}", loginDTO.getUsername());

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, loginDTO.getUsername())
        );
        if (user == null) {
            logger.warn("登录失败：用户名不存在，用户名：{}", loginDTO.getUsername());
            ExceptionUtil.throwBizException(ErrorCode.USER_NOT_FOUND);
        }

        if (!PasswordUtil.matches(loginDTO.getPassword(), user.getPassword())) {
            logger.warn("登录失败：密码错误，用户名：{}", loginDTO.getUsername());
            ExceptionUtil.throwBizException(ErrorCode.INVALID_PASSWORD);
        }

        if (user.getStatus() != 1) {
            logger.warn("登录失败：账号已被禁用，用户名：{}", loginDTO.getUsername());
            ExceptionUtil.throwBizException(ErrorCode.ACCOUNT_DISABLED);
        }

        // 登录
        StpUtil.login(user.getId());
        
        // 返回token
        String token = StpUtil.getTokenValue();
        logger.info("登录成功，用户名：{}，token：{}", loginDTO.getUsername(), token);
        return token;
    }

    @Override
    public void logout() {
        ExceptionUtil.throwIf(!StpUtil.isLogin(), ErrorCode.USER_NOT_LOGGED_IN);
        StpUtil.logout();
    }

    @Override
    public void forceLogout(Long userId) {
        ExceptionUtil.throwIf(!StpUtil.isLogin(), ErrorCode.USER_NOT_LOGGED_IN, "操作者未登录");
        // 强制指定用户下线
        StpUtil.logout(userId);
    }
} 