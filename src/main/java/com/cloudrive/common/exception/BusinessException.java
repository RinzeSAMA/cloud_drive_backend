package com.cloudrive.common.exception;

import com.cloudrive.common.constant.CommonConstants;
import com.cloudrive.common.enums.ErrorCode;

/**
 * 业务异常类
 */
public class BusinessException extends RuntimeException {
    private Integer code;
    private String message;

    public BusinessException(String message) {
        super(message);
        this.code = CommonConstants.StatusCode.SERVER_ERROR;
        this.message = message;
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }
    
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getHttpStatus(); // 使用枚举中的HttpStatus作为错误码
        this.message = errorCode.getMessage();
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
} 