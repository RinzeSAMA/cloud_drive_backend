package com.cloudrive.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;

/**
 * 文件存储服务策略，可让多个存储服务实现此接口
 */
public interface StorageStrategy {

    /**
     * 删除文件
     */
    void deleteFile(String path);

}
