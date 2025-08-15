package com.cloudrive.common.enums;

import lombok.Getter;

@Getter
public enum FileStatus {
    NORMAL(0, "正常"),
    RECYCLED(1, "回收站"),
    DELETE(2, "物理待删除"),
    DELETED(3,"已删除");

    private final Integer code;
    private final String desc;

    FileStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode() { return code; }
    public String getDesc() { return desc; }

    public static FileStatus fromCode(Integer code) {
        for (FileStatus status : values()) {
            if (status.code == code) return status;
        }
        return NORMAL;
    }
}
