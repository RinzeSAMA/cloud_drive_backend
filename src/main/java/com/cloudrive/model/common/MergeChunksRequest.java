package com.cloudrive.model.common;

import lombok.Data;

/**
 * 合并分片请求 DTO
 */
@Data
public class MergeChunksRequest {
    private String taskId;
    private String filename;
    private Integer totalChunks;
    private Long parentId;

}
