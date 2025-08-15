package com.cloudrive.common.util;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cloudrive.common.enums.ErrorCode;
import com.cloudrive.common.exception.BusinessException;
import com.cloudrive.mapper.UserMapper;
import com.cloudrive.model.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 用户上下文工具类，用于管理用户相关的操作（基于 MyBatis-Plus）
 */
@Component
public class UserContext {

    private static UserMapper userMapper;

    @Autowired
    public void setUserMapper(UserMapper userMapper) {
        UserContext.userMapper = userMapper;
    }


    /**
     * 获取当前登录用户的ID
     */
    public static Long getCurrentUserId() {
        return Long.valueOf(StpUtil.getLoginIdAsString());
    }

    /**
     * 获取当前登录用户的完整信息
     */
    public static User getCurrentUser() {
        Long userId = getCurrentUserId();
        User user = userMapper.selectById(userId);
        ExceptionUtil.throwIfNull(user, ErrorCode.USER_NOT_FOUND);
        return user;
    }

    /**
     * 检查用户是否已登录
     */
    public static boolean isLoggedIn() {
        return StpUtil.isLogin();
    }
}