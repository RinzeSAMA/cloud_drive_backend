package com.cloudrive.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.cloudrive.common.result.Result;
import com.cloudrive.model.entity.FileInfo;
import com.cloudrive.service.ChunkUploadService;
import com.cloudrive.service.StorageService;
import com.cloudrive.service.FileService;
import com.cloudrive.redis.UploadProgressRedis;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;

/**
 * 分片上传服务实现类
 * 基于 Redis 的分片上传功能，参考掘金文章优化实现
 */
@Slf4j
@Service
public class ChunkUploadServiceImpl implements ChunkUploadService {
    
        @Autowired
    private StorageService storageService;

    @Autowired
    private FileService fileService;

    @Autowired
    private UploadProgressRedis uploadProgressRedis;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedissonClient redissonClient;
    
    @Value("${app.chunk-upload.temp-dir:./temp/chunks}")
    private String tempDir;
    
    // 内存缓存，用于存储任务状态
    private final Map<String, UploadTaskInfo> taskInfoMap = new ConcurrentHashMap<>();
    
    @Override
    public Result<String> uploadChunk(MultipartFile file, String taskId, Integer chunkIndex, 
                                    Integer totalChunks, String filename, Long parentId) {
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            log.info("开始处理分片上传: taskId={}, chunkIndex={}/{}, filename={}, userId={}", 
                    taskId, chunkIndex, totalChunks, filename, userId);
            
            // Redis分片索引集合key
            String chunkSetKey = "chunk_uploaded_set:" + taskId;
            RSet<Integer> chunkSet = redissonClient.getSet(chunkSetKey);
            
            // 检查分片是否已上传
            if (chunkSet.contains(chunkIndex)) {
                log.info("分片已存在，跳过: taskId={}, chunkIndex={}", taskId, chunkIndex);
                return Result.success("分片已存在");
            }
            
            // 创建临时目录
            String chunkDir = getChunkDir(taskId);
            Path chunkDirPath = Paths.get(chunkDir);
            Files.createDirectories(chunkDirPath);
            
            log.info("创建分片目录: {}", chunkDirPath.toAbsolutePath());
            
            // 保存分片文件
            String chunkPath = chunkDir + File.separator + chunkIndex;
            File chunkFile = new File(chunkPath);
            file.transferTo(chunkFile);
            
            // 验证文件大小
            if (chunkFile.length() != file.getSize()) {
                log.error("分片文件大小不匹配: expected={}, actual={}", file.getSize(), chunkFile.length());
                chunkFile.delete();
                return Result.error("分片文件损坏");
            }
            
            // 记录分片索引到Redis
            chunkSet.add(chunkIndex);
            chunkSet.expire(java.time.Duration.ofHours(24));
            
            log.info("分片上传成功: taskId={}, chunkIndex={}, 已上传分片数={}", 
                    taskId, chunkIndex, chunkSet.size());
            
            return Result.success("分片上传成功");
            
        } catch (Exception e) {
            log.error("分片上传失败: taskId={}, chunkIndex={}, filename={}", taskId, chunkIndex, filename, e);
            return Result.error("分片上传失败: " + e.getMessage());
        }
    }
    
    @Override
    public Result<String> mergeChunks(String taskId, String filename, Integer totalChunks, Long parentId) {
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            log.info("开始合并分片: taskId={}, filename={}, totalChunks={}, userId={}", 
                    taskId, filename, totalChunks, userId);
            
            // Redis分片索引集合key
            String chunkSetKey = "chunk_uploaded_set:" + taskId;
            RSet<Integer> chunkSet = redissonClient.getSet(chunkSetKey);
            java.util.Set<Integer> uploadedChunks = chunkSet.readAll();
            
            // 检查所有分片是否上传完成
            if (uploadedChunks.size() != totalChunks) {
                log.warn("分片不完整: expected={}, actual={}", totalChunks, uploadedChunks.size());
                return Result.error("分片不完整，无法合并");
            }
            
            // 合并分片
            String chunkDir = getChunkDir(taskId);
            String mergedFilePath = mergeChunkFiles(chunkDir, totalChunks, filename);
            File mergedFile = new File(mergedFilePath);
            
            if (!mergedFile.exists() || mergedFile.length() == 0) {
                log.error("合并文件失败或文件为空: {}", mergedFilePath);
                return Result.error("合并文件失败");
            }
            
            // 调用FileService的方法来上传文件并保存到数据库
            log.info("开始上传合并后的文件: path={}, size={}", mergedFilePath, mergedFile.length());
            fileService.uploadFileWithProgressFromPath(
                mergedFilePath, filename, mergedFile.length(), parentId, taskId, userId
            );
            
            // 清理临时文件和Redis分片索引
            cleanupTempFiles(chunkDir);
            cleanupTempFiles(mergedFilePath);
            chunkSet.delete();
            
            log.info("分片合并成功: taskId={}, filename={}", taskId, filename);
            return Result.success("文件上传完成");
            
        } catch (Exception e) {
            log.error("分片合并失败: taskId={}, filename={}", taskId, filename, e);
            return Result.error("分片合并失败: " + e.getMessage());
        }
    }
    
    @Override
    public Result<Object> getUploadStatus(String taskId) {
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            log.info("获取上传状态: taskId={}, userId={}", taskId, userId);
            
            // Redis分片索引集合key
            String chunkSetKey = "chunk_uploaded_set:" + taskId;
            RSet<Integer> chunkSet = redissonClient.getSet(chunkSetKey);
            java.util.Set<Integer> uploadedChunks = chunkSet.readAll();
            int progress = uploadedChunks.isEmpty() ? 0 : (uploadedChunks.size() * 100) /  (chunkSet.size() > 0 ? chunkSet.size() : 1);
            
            // 创建响应对象
            java.util.Map<String, Object> response = new java.util.HashMap<>();
            response.put("taskId", taskId);
            response.put("uploadedChunks", uploadedChunks.toArray(new Integer[0]));
            response.put("progress", progress);
            response.put("status", "uploading");
            
            log.info("获取上传状态成功: taskId={}, 已上传分片: {}", taskId, uploadedChunks.size());
            
            return Result.success(response);
            
        } catch (Exception e) {
            log.error("获取上传状态失败: taskId={}", taskId, e);
            return Result.error("获取上传状态失败: " + e.getMessage());
        }
    }
    
    @Override
    public Result<String> cancelUpload(String taskId) {
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            log.info("取消上传: taskId={}, userId={}", taskId, userId);
            
            // 获取任务信息
            UploadTaskInfo taskInfo = getTaskInfo(taskId);
            if (taskInfo == null) {
                return Result.error("任务不存在");
            }
            
            // 清理临时文件
            String chunkDir = getChunkDir(taskId);
            cleanupTempFiles(chunkDir);
            
            // 清理内存缓存
            taskInfoMap.remove(taskId);
            
            // 清理 Redis 中的任务信息
            String taskKey = "chunk_task:" + taskId;
            uploadProgressRedis.removeChunkInfo(taskKey);
            
            log.info("取消上传成功: taskId={}", taskId);
            return Result.success("取消上传成功");
            
        } catch (Exception e) {
            log.error("取消上传失败: taskId={}", taskId, e);
            return Result.error("取消上传失败: " + e.getMessage());
        }
    }
    
    @Override
    public Result<String> cleanExpiredTasks() {
        try {
            log.info("开始清理过期任务");
            
            long currentTime = System.currentTimeMillis();
            long expireTime = 24 * 60 * 60 * 1000; // 24小时
            
            // 清理内存中的过期任务
            taskInfoMap.entrySet().removeIf(entry -> {
                UploadTaskInfo taskInfo = entry.getValue();
                boolean expired = (currentTime - taskInfo.getLastUpdateTime()) > expireTime;
                if (expired) {
                    log.info("清理过期任务: taskId={}", taskInfo.getTaskId());
                    // 清理临时文件
                    String chunkDir = getChunkDir(taskInfo.getTaskId());
                    cleanupTempFiles(chunkDir);
                }
                return expired;
            });
            
            log.info("清理过期任务完成");
            return Result.success("清理完成");
            
        } catch (Exception e) {
            log.error("清理过期任务失败", e);
            return Result.error("清理过期任务失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取或创建任务信息
     */
    private UploadTaskInfo getOrCreateTaskInfo(String taskId, String filename, Integer totalChunks, 
                                             Long userId, Long parentId) {
        return taskInfoMap.computeIfAbsent(taskId, k -> {
            UploadTaskInfo taskInfo = new UploadTaskInfo();
            taskInfo.setTaskId(taskId);
            taskInfo.setFilename(filename);
            taskInfo.setTotalChunks(totalChunks);
            taskInfo.setUserId(userId);
            taskInfo.setParentId(parentId);
            taskInfo.setStatus("uploading");
            taskInfo.setProgress(0);
            taskInfo.setCreateTime(System.currentTimeMillis());
            taskInfo.setLastUpdateTime(System.currentTimeMillis());
            taskInfo.setUploadedChunks(new HashSet<>());
            
            // 保存到 Redis
            saveTaskInfoToRedis(taskInfo);
            
            log.info("创建新任务: taskId={}, filename={}, totalChunks={}", taskId, filename, totalChunks);
            return taskInfo;
        });
    }
    
    /**
     * 获取任务信息
     */
    private UploadTaskInfo getTaskInfo(String taskId) {
        // 先从内存获取
        UploadTaskInfo taskInfo = taskInfoMap.get(taskId);
        if (taskInfo != null) {
            return taskInfo;
        }
        
        // 从 Redis 获取
        try {
            String taskKey = "chunk_task:" + taskId;
            String taskInfoJson = uploadProgressRedis.getChunkInfo(taskKey);
            if (taskInfoJson != null && !taskInfoJson.isEmpty()) {
                taskInfo = objectMapper.readValue(taskInfoJson, UploadTaskInfo.class);
                taskInfoMap.put(taskId, taskInfo);
                return taskInfo;
            }
        } catch (Exception e) {
            log.warn("从 Redis 获取任务信息失败: taskId={}", taskId, e);
        }
        
        return null;
    }
    
    /**
     * 保存任务信息到 Redis
     */
    private void saveTaskInfoToRedis(UploadTaskInfo taskInfo) {
        try {
            String taskKey = "chunk_task:" + taskInfo.getTaskId();
            String taskInfoJson = objectMapper.writeValueAsString(taskInfo);
            uploadProgressRedis.setChunkInfo(taskKey, taskInfoJson);
        } catch (Exception e) {
            log.warn("保存任务信息到 Redis 失败: taskId={}", taskInfo.getTaskId(), e);
        }
    }
    
    /**
     * 获取分片目录
     */
    private String getChunkDir(String taskId) {
        return tempDir + File.separator + taskId;
    }
    
    /**
     * 合并分片文件
     */
    private String mergeChunkFiles(String chunkDir, Integer totalChunks, String filename) throws IOException {
        String mergedFilePath = tempDir + File.separator + filename;
        
        log.info("开始合并分片文件: chunkDir={}, totalChunks={}, mergedFilePath={}", 
                chunkDir, totalChunks, mergedFilePath);
        
        try (var outputStream = Files.newOutputStream(Paths.get(mergedFilePath))) {
            for (int i = 0; i < totalChunks; i++) {
                String chunkPath = chunkDir + File.separator + i;
                Path chunkFile = Paths.get(chunkPath);
                
                if (!Files.exists(chunkFile)) {
                    throw new IOException("分片文件不存在: " + chunkPath);
                }
                
                Files.copy(chunkFile, outputStream);
                log.debug("合并分片: {}/{}", i + 1, totalChunks);
            }
        }
        
        log.info("分片合并完成: mergedFilePath={}", mergedFilePath);
        return mergedFilePath;
    }
    
    /**
     * 清理临时文件
     */
    private void cleanupTempFiles(String path) {
        try {
            Path filePath = Paths.get(path);
            if (Files.exists(filePath)) {
                if (Files.isDirectory(filePath)) {
                    Files.walk(filePath)
                        .sorted((a, b) -> b.compareTo(a)) // 先删除文件，再删除目录
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (IOException e) {
                                log.warn("删除临时文件失败: {}", p, e);
                            }
                        });
                } else {
                    Files.delete(filePath);
                }
                log.debug("清理临时文件: {}", path);
            }
        } catch (IOException e) {
            log.warn("清理临时文件失败: {}", path, e);
        }
    }
    
    /**
     * 上传任务信息类
     */
    public static class UploadTaskInfo {
        private String taskId;
        private String filename;
        private Integer totalChunks;
        private Long userId;
        private Long parentId;
        private String status;
        private Integer progress;
        private Set<Integer> uploadedChunks;
        private Long createTime;
        private Long lastUpdateTime;
        private Long completedTime;
        private String filePath;
        
        // Getters and Setters
        public String getTaskId() { return taskId; }
        public void setTaskId(String taskId) { this.taskId = taskId; }
        
        public String getFilename() { return filename; }
        public void setFilename(String filename) { this.filename = filename; }
        
        public Integer getTotalChunks() { return totalChunks; }
        public void setTotalChunks(Integer totalChunks) { this.totalChunks = totalChunks; }
        
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        
        public Long getParentId() { return parentId; }
        public void setParentId(Long parentId) { this.parentId = parentId; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public Integer getProgress() { return progress; }
        public void setProgress(Integer progress) { this.progress = progress; }
        
        public Set<Integer> getUploadedChunks() { return uploadedChunks; }
        public void setUploadedChunks(Set<Integer> uploadedChunks) { this.uploadedChunks = uploadedChunks; }
        
        public Long getCreateTime() { return createTime; }
        public void setCreateTime(Long createTime) { this.createTime = createTime; }
        
        public Long getLastUpdateTime() { return lastUpdateTime; }
        public void setLastUpdateTime(Long lastUpdateTime) { this.lastUpdateTime = lastUpdateTime; }
        
        public Long getCompletedTime() { return completedTime; }
        public void setCompletedTime(Long completedTime) { this.completedTime = completedTime; }
        
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
    }
} 