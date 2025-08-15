package com.cloudrive.common.util;

import com.cloudrive.common.enums.ErrorCode;
import com.cloudrive.common.exception.BusinessException;

/**
 * 异常工具类，用于创建和抛出业务异常
 */
public class ExceptionUtil {
    
    /**
     * 抛出业务异常
     * @param errorCode 错误码枚举
     * @throws BusinessException 业务异常
     */
    public static void throwBizException(ErrorCode errorCode) {
        throw new BusinessException(errorCode);
    }
    
    /**
     * 抛出业务异常，带有自定义错误消息
     * @param errorCode 错误码枚举
     * @param customMessage 自定义错误消息
     * @throws BusinessException 业务异常
     */
    public static void throwBizException(ErrorCode errorCode, String customMessage) {
        BusinessException exception = new BusinessException(errorCode);
        exception.setMessage(customMessage);
        throw exception;
    }
    
    /**
     * 根据条件抛出业务异常
     * @param condition 条件，如果为true则抛出异常
     * @param errorCode 错误码枚举
     * @throws BusinessException 业务异常
     */
    public static void throwIf(boolean condition, ErrorCode errorCode) {
        if (condition) {
            throwBizException(errorCode);
        }
    }
    
    /**
     * 根据条件抛出业务异常，带有自定义错误消息
     * @param condition 条件，如果为true则抛出异常
     * @param errorCode 错误码枚举
     * @param customMessage 自定义错误消息
     * @throws BusinessException 业务异常
     */
    public static void throwIf(boolean condition, ErrorCode errorCode, String customMessage) {
        if (condition) {
            throwBizException(errorCode, customMessage);
        }
    }
    
    /**
     * 根据对象是否为空抛出业务异常
     * @param obj 对象
     * @param errorCode 错误码枚举
     * @throws BusinessException 业务异常
     */
    public static void throwIfNull(Object obj, ErrorCode errorCode) {
        if (obj == null) {
            throwBizException(errorCode);
        }
    }
    
    /**
     * 根据对象是否为空抛出业务异常，带有自定义错误消息
     * @param obj 对象
     * @param errorCode 错误码枚举
     * @param customMessage 自定义错误消息
     * @throws BusinessException 业务异常
     */
    public static void throwIfNull(Object obj, ErrorCode errorCode, String customMessage) {
        if (obj == null) {
            throwBizException(errorCode, customMessage);
        }
    }
} 