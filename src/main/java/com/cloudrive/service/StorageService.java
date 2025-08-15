package com.cloudrive.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;

/**
 * 文件存储服务接口
 */
public interface StorageService {

    /**
     * 上传文件
     */
    String uploadFile(MultipartFile file, String path);

    /**
     * 从文件路径上传文件并跟踪进度
     * @param file 文件对象
     * @param userId 用户ID，用于跟踪进度
     * @param taskId 任务ID，用于跟踪进度
     * @param originalFilename 原始文件名
     * @param fileSize 文件大小
     * @return 文件路径
     */
    String uploadFileWithProgressFromPath(File file, Long userId, String taskId, String originalFilename, long fileSize);

    /**
     * 删除文件
     */
    void deleteFile(String path);

    /**
     * 下载文件
     */
    byte[] downloadFile(String path);
}
