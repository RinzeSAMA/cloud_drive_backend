package com.cloudrive.common.enums;

import lombok.Getter;

/**
 * 分享类型枚举
 */
@Getter
public enum ShareRecordType {
    PUBLIC(0,"公开"),
    FRIEND(1,"好友");

    private final Integer code;
    private final String desc;

    ShareRecordType(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode() { return code; }
    public String getDesc() { return desc; }
}
