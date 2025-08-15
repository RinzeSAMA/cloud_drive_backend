package com.cloudrive.service;

import com.cloudrive.redis.UploadProgressRedis;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.io.Serial;
import java.io.Serializable;

/**
 * 上传进度跟踪服务
 * 使用 Redis 缓存存储上传进度，支持分布式部署
 */
@Service
public class UploadProgressService {
    
    private final UploadProgressRedis uploadProgressRedis;
    
    public UploadProgressService(UploadProgressRedis uploadProgressRedis) {
        this.uploadProgressRedis = uploadProgressRedis;
    }

    /**
     * 创建一个新的上传任务
     *
     * @param taskId    任务ID
     * @param filename  文件名
     * @param totalSize 文件总大小
     */
    public void createUploadTask(String taskId, String filename, long totalSize) {
        UploadTask task = new UploadTask(taskId, filename, totalSize);
        uploadProgressRedis.createTask(task);
    }

    /**
     * 更新上传进度
     * @param taskId 任务ID
     * @param bytesTransferred 已传输字节数
     * @param totalBytes 总字节数
     */
    public void updateProgress(String taskId, long bytesTransferred, long totalBytes) {
        UploadTask task = uploadProgressRedis.getTask(taskId);
        if (task != null) {
            task.setBytesTransferred(bytesTransferred);
            task.setTotalSize(totalBytes);
            
            // 计算百分比进度
            if (totalBytes > 0) {
                double percentage = (double) bytesTransferred / totalBytes * 100;
                task.setProgress(Math.min(100, percentage));
            }
            
            uploadProgressRedis.updateTask(task);
        }
    }
    
    /**
     * 累加已传输的字节数
     * @param taskId 任务ID
     * @param additionalBytes 本次传输的字节数
     */
    public void updateBytesTransferred(String taskId, long additionalBytes) {
        UploadTask task = uploadProgressRedis.getTask(taskId);
        if (task != null) {
            // 累加已传输字节数
            long newBytesTransferred = task.getBytesTransferred() + additionalBytes;
            task.setBytesTransferred(newBytesTransferred);
            
            // 计算百分比进度
            if (task.getTotalSize() > 0) {
                double percentage = (double) newBytesTransferred / task.getTotalSize() * 100;
                task.setProgress(Math.min(100, percentage));
            }
            
            uploadProgressRedis.updateTask(task);
        }
    }

    /**
     * 完成上传任务
     * @param taskId 任务ID
     * @param success 是否成功
     * @param message 消息
     */
    public void completeUploadTask(String taskId, boolean success, String message) {
        UploadTask task = uploadProgressRedis.getTask(taskId);
        if (task != null) {
            task.setCompleted(true);
            task.setSuccess(success);
            task.setMessage(message);
            task.setProgress(100);
            
            // 使用Redis的TTL机制自动过期，无需使用线程清理
            uploadProgressRedis.completeTask(task);
        }
    }

    /**
     * 获取上传任务
     * @param taskId 任务ID
     * @return 上传任务
     */
    public UploadTask getUploadTask(String taskId) {
        return uploadProgressRedis.getTask(taskId);
    }

    /**
     * 上传任务
     */
    @Data
    public static class UploadTask implements Serializable {
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
        
        public UploadTask(String id, String filename, long totalSize) {
            this.id = id;
            this.filename = filename;
            this.totalSize = totalSize;
            this.bytesTransferred = 0;
            this.progress = 0;
            this.completed = false;
            this.success = false;
            this.message = "";
            this.createdAt = System.currentTimeMillis();
        }
    }
}
