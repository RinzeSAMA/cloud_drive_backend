package com.cloudrive.model.dto;

import lombok.Data;

/**
 * 分片上传请求 DTO
 */
@Data
public class ChunkUploadDTO {
    
    /**
     * 任务ID
     */
    private String taskId;
    
    /**
     * 分片索引
     */
    private Integer chunkIndex;
    
    /**
     * 总分片数
     */
    private Integer totalChunks;
    
    /**
     * 文件名
     */
    private String filename;
    
    /**
     * 父目录ID
     */
    private Long parentId;
}

/**
 * 合并分片请求 DTO
 */
@Data
class MergeChunksDTO {
    
    /**
     * 任务ID
     */
    private String taskId;
    
    /**
     * 文件名
     */
    private String filename;
    
    /**
     * 总分片数
     */
    private Integer totalChunks;
    
    /**
     * 父目录ID
     */
    private Long parentId;
}

/**
 * 上传状态响应 DTO
 */
@Data
class UploadStatusDTO {
    
    /**
     * 任务ID
     */
    private String taskId;
    
    /**
     * 已上传的分片索引列表
     */
    private Integer[] uploadedChunks;
    
    /**
     * 任务状态
     */
    private String status;
    
    /**
     * 错误信息
     */
    private String errorMessage;
} 