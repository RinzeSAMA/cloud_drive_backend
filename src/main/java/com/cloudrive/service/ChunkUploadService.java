package com.cloudrive.service;

import com.cloudrive.common.result.Result;
import com.cloudrive.model.dto.ChunkUploadDTO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 分片上传服务接口
 * 新增的分片上传功能，不影响原有上传逻辑
 */
public interface ChunkUploadService {
    
    /**
     * 上传分片
     * @param file 分片文件
     * @param taskId 任务ID
     * @param chunkIndex 分片索引
     * @param totalChunks 总分片数
     * @param filename 文件名
     * @param parentId 父目录ID
     * @return 上传结果
     */
    Result<String> uploadChunk(MultipartFile file, String taskId, Integer chunkIndex, 
                              Integer totalChunks, String filename, Long parentId);
    
    /**
     * 合并分片
     * @param taskId 任务ID
     * @param filename 文件名
     * @param totalChunks 总分片数
     * @param parentId 父目录ID
     * @return 合并结果
     */
    Result<String> mergeChunks(String taskId, String filename, Integer totalChunks, Long parentId);
    
    /**
     * 获取上传状态
     * @param taskId 任务ID
     * @return 上传状态
     */
    Result<Object> getUploadStatus(String taskId);
    
    /**
     * 取消上传
     * @param taskId 任务ID
     * @return 取消结果
     */
    Result<String> cancelUpload(String taskId);
    
    /**
     * 清理过期任务
     * @return 清理结果
     */
    Result<String> cleanExpiredTasks();
} 