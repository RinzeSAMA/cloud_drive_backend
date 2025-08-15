package com.cloudrive.controller;

import com.cloudrive.common.result.Result;
import com.cloudrive.service.ChunkUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 分片上传控制器
 * 新增的分片上传功能，不影响原有上传逻辑
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
public class ChunkUploadController {
    
    @Autowired
    private ChunkUploadService chunkUploadService;
    
    /**
     * 上传分片
     * POST /api/files/chunk-upload
     */
    @PostMapping("/chunk-upload")
    public Result<String> uploadChunk(
            @RequestParam("file") MultipartFile file,
            @RequestParam("taskId") String taskId,
            @RequestParam("chunkIndex") Integer chunkIndex,
            @RequestParam("totalChunks") Integer totalChunks,
            @RequestParam("filename") String filename,
            @RequestParam(value = "parentId", required = false) Long parentId) {
        
        log.info("接收分片上传请求: taskId={}, chunkIndex={}/{}, filename={}", 
                taskId, chunkIndex, totalChunks, filename);
        
        return chunkUploadService.uploadChunk(file, taskId, chunkIndex, totalChunks, filename, parentId);
    }
    
    /**
     * 合并分片
     * POST /api/files/merge-chunks
     */
    @PostMapping("/merge-chunks")
    public Result<String> mergeChunks(@RequestBody MergeChunksRequest request) {
        log.info("接收合并分片请求: taskId={}, filename={}, totalChunks={}", 
                request.getTaskId(), request.getFilename(), request.getTotalChunks());
        
        return chunkUploadService.mergeChunks(
                request.getTaskId(), 
                request.getFilename(), 
                request.getTotalChunks(), 
                request.getParentId()
        );
    }
    
    /**
     * 获取上传状态
     * GET /api/files/upload-status/{taskId}
     */
    @GetMapping("/upload-status/{taskId}")
    public Result<Object> getUploadStatus(@PathVariable String taskId) {
        log.info("获取上传状态: taskId={}", taskId);
        
        return chunkUploadService.getUploadStatus(taskId);
    }
    
    /**
     * 取消上传
     * DELETE /api/files/cancel-upload/{taskId}
     */
    @DeleteMapping("/cancel-upload/{taskId}")
    public Result<String> cancelUpload(@PathVariable String taskId) {
        log.info("取消上传: taskId={}", taskId);
        
        return chunkUploadService.cancelUpload(taskId);
    }
    
    /**
     * 清理过期任务（管理员接口）
     * POST /api/files/clean-expired-tasks
     */
    @PostMapping("/clean-expired-tasks")
    public Result<String> cleanExpiredTasks() {
        log.info("清理过期任务");
        
        return chunkUploadService.cleanExpiredTasks();
    }
    
    /**
     * 合并分片请求 DTO
     */
    public static class MergeChunksRequest {
        private String taskId;
        private String filename;
        private Integer totalChunks;
        private Long parentId;
        
        // Getters and Setters
        public String getTaskId() { return taskId; }
        public void setTaskId(String taskId) { this.taskId = taskId; }
        
        public String getFilename() { return filename; }
        public void setFilename(String filename) { this.filename = filename; }
        
        public Integer getTotalChunks() { return totalChunks; }
        public void setTotalChunks(Integer totalChunks) { this.totalChunks = totalChunks; }
        
        public Long getParentId() { return parentId; }
        public void setParentId(Long parentId) { this.parentId = parentId; }
    }
} 