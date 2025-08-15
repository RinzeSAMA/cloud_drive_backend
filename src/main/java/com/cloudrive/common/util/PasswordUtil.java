package com.cloudrive.common.util;

import org.mindrot.jbcrypt.BCrypt;

public class PasswordUtil {
    
    // 默认的加密强度
    private static final int DEFAULT_LOG_ROUNDS = 12;
    
    /**
     * 加密密码
     */
    public static String encode(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(DEFAULT_LOG_ROUNDS));
    }

    /**
     * 使用指定的强度加密密码
     * @param password 原始密码
     * @param logRounds 加密强度（4-31）
     */
    public static String encode(String password, int logRounds) {
        return BCrypt.hashpw(password, BCrypt.gensalt(logRounds));
    }

    /**
     * 验证密码
     * @param password 原始密码
     * @param hashedPassword 加密后的密码
     * @return 是否匹配
     */
    public static boolean matches(String password, String hashedPassword) {
        return BCrypt.checkpw(password, hashedPassword);
    }
} 