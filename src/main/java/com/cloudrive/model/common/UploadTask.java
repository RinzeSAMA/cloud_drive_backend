package com.cloudrive.model.common;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 上传任务
 */
@Data
public class UploadTask implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String filename;
    private long totalSize;
    private long bytesTransferred;
    private double progress;
    private boolean completed;
    private boolean success;
    private String message;
    private final long createdAt;
}