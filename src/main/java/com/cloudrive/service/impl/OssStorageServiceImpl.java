package com.cloudrive.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.event.ProgressEvent;
import com.aliyun.oss.event.ProgressEventType;
import com.aliyun.oss.event.ProgressListener;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.PutObjectRequest;
import com.cloudrive.common.constant.CommonConstants;
import com.cloudrive.common.enums.ErrorCode;
import com.cloudrive.common.util.ExceptionUtil;
import com.cloudrive.config.properties.OssProperties;
import com.cloudrive.service.StorageService;
import com.cloudrive.service.UploadProgressService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.util.UUID;

/**
 * 基于阿里云OSS的存储服务实现
 */
@Service
public class OssStorageServiceImpl implements StorageService {

    private static final Logger logger = LoggerFactory.getLogger(OssStorageServiceImpl.class);

    private final OssProperties ossProperties;
    private final UploadProgressService uploadProgressService;

    @Autowired
    public OssStorageServiceImpl(OssProperties ossProperties, UploadProgressService uploadProgressService) {
        this.ossProperties = ossProperties;
        this.uploadProgressService = uploadProgressService;
    }

    @Override
    public String uploadFile(MultipartFile file, String path) {
        checkOssEnabled();

        String fileName = generateUniqueFileName();
        String objectName = buildObjectName(path, fileName);

        OSS ossClient = null;
        try {
            ossClient = getOssClient();
            ossClient.putObject(ossProperties.getBucketName(), objectName, file.getInputStream());
            return objectName;
        } catch (Exception e) {
            logger.error("Failed to upload file to OSS: bucket={}, objectName={}, error={}", ossProperties.getBucketName(), objectName, e.getMessage());
            ExceptionUtil.throwBizException(ErrorCode.OSS_UPLOAD_FAILED, e.getMessage());
            return null; // 不会执行到这里，为了编译通过
        } finally {
            closeOssClient(ossClient);
        }
    }

    @Override
    public byte[] downloadFile(String path) {
        checkOssEnabled();

        OSS ossClient = null;
        try {
            ossClient = getOssClient();
            OSSObject ossObject = ossClient.getObject(ossProperties.getBucketName(), path);
            if (ossObject == null) {
                logger.error("File not found in OSS: bucket={}, path={}", ossProperties.getBucketName(), path);
                ExceptionUtil.throwBizException(ErrorCode.FILE_NOT_FOUND);
                return null;
            }
            return ossObject.getObjectContent().readAllBytes();
        } catch (Exception e) {
            logger.error("Failed to download file from OSS: bucket={}, path={}, error={}", ossProperties.getBucketName(), path, e.getMessage());
            ExceptionUtil.throwBizException(ErrorCode.OSS_DOWNLOAD_FAILED, e.getMessage());
            return null; // 不会执行到这里，为了编译通过
        } finally {
            closeOssClient(ossClient);
        }
    }

    @Override
    public String uploadFileWithProgressFromPath(File file, Long userId, String taskId, String originalFilename, long fileSize) {
        checkOssEnabled(taskId);

        try {
            if (!file.exists() || !file.isFile()) {
                handleUploadError(taskId, "文件不存在或不是常规文件");
            }

            // 直接使用文件输入流进行上传，而不转换为MultipartFile
            String fileName = generateUniqueFileName();
            String objectName = CommonConstants.File.FILE_PATH_PREFIX + userId + "/" + fileName;

            OSS ossClient = null;
            try (FileInputStream input = new FileInputStream(file)) {
                // 创建带进度监听的请求
                PutObjectRequest putObjectRequest = new PutObjectRequest(ossProperties.getBucketName(), objectName, input);
                
                // 设置进度监听器
                logger.info("Setting up progress listener for file: {}, taskId: {}, size: {}", originalFilename, taskId, fileSize);
                putObjectRequest.withProgressListener(createProgressListener(taskId, fileSize));
                
                // 执行上传
                ossClient = getOssClient();
                ossClient.putObject(putObjectRequest);
                
                return objectName;
            } catch (Exception e) {
                logger.error("Failed to upload file to OSS with progress tracking: bucket={}, objectName={}, error={}", 
                    ossProperties.getBucketName(), objectName, e.getMessage());
                uploadProgressService.completeUploadTask(taskId, false, "OSS上传失败: " + e.getMessage());
                ExceptionUtil.throwBizException(ErrorCode.OSS_UPLOAD_FAILED, "OSS上传失败: " + e.getMessage());
                return null; // 不会执行到这里，为了编译通过
            } finally {
                closeOssClient(ossClient);
            }
        } catch (Exception e) {
            handleUploadError(taskId, "文件上传失败: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 创建进度监听器
     */
    private ProgressListener createProgressListener(String taskId, long fileSize) {
        return new ProgressListener() {
            private long totalBytesTransferred = 0;

            @Override
            public void progressChanged(ProgressEvent progressEvent) {
                long bytes = progressEvent.getBytes();
                ProgressEventType eventType = progressEvent.getEventType();

                logger.info("Progress event: type={}, bytes={}, taskId={}", eventType, bytes, taskId);

                switch (eventType) {
                    case REQUEST_CONTENT_LENGTH_EVENT -> {
                        // 更新总字节数
                        uploadProgressService.updateProgress(taskId, 0, fileSize);
                    }
                    case REQUEST_BYTE_TRANSFER_EVENT -> {
                        // 更新已传输字节数，bytes是本次传输的字节数，需要累加
                        totalBytesTransferred += bytes;
                        uploadProgressService.updateBytesTransferred(taskId, bytes);

                        // 直接设置进度百分比，确保前端能看到变化
                        double percentage = (double) totalBytesTransferred / fileSize * 100;
                        logger.info("Upload percentage: {}%, taskId: {}", percentage, taskId);

                        // 更新任务进度
                        UploadProgressService.UploadTask task = uploadProgressService.getUploadTask(taskId);
                        if (task != null) {
                            task.setProgress(percentage);
                        }
                    }
                    case TRANSFER_COMPLETED_EVENT -> 
                        // 标记任务完成
                        uploadProgressService.completeUploadTask(taskId, true, "上传完成");
                    case TRANSFER_FAILED_EVENT -> 
                        // 标记任务失败
                        uploadProgressService.completeUploadTask(taskId, false, "上传失败");
                    default -> { }
                }
            }
        };
    }

    @Override
    public void deleteFile(String path) {
        checkOssEnabled();

        OSS ossClient = null;
        try {
            ossClient = getOssClient();
            ossClient.deleteObject(ossProperties.getBucketName(), path);
        } catch (Exception e) {
            ExceptionUtil.throwBizException(ErrorCode.OSS_DELETE_FAILED, e.getMessage());
        } finally {
            closeOssClient(ossClient);
        }
    }

    /**
     * 检查OSS是否启用
     */
    private void checkOssEnabled() {
        if (!ossProperties.getEnabled()) {
            logger.error("OSS storage is disabled");
            ExceptionUtil.throwBizException(ErrorCode.OSS_DISABLED);
        }
    }

    /**
     * 检查OSS是否启用，如果未启用则更新任务状态
     */
    private void checkOssEnabled(String taskId) {
        if (!ossProperties.getEnabled()) {
            logger.error("OSS storage is disabled. Please enable OSS in configuration or implement a local storage service.");
            uploadProgressService.completeUploadTask(taskId, false, "OSS存储服务未启用，请在配置中启用OSS或实现本地存储服务");
            ExceptionUtil.throwBizException(ErrorCode.OSS_DISABLED, "OSS存储服务未启用，请在配置中启用OSS或实现本地存储服务");
        }
    }

    /**
     * 处理上传错误
     */
    private void handleUploadError(String taskId, String errorMessage) {
        logger.error(errorMessage);
        uploadProgressService.completeUploadTask(taskId, false, errorMessage);
        ExceptionUtil.throwBizException(ErrorCode.FILE_UPLOAD_FAILED, errorMessage);
    }

    /**
     * 关闭OSS客户端
     */
    private void closeOssClient(OSS ossClient) {
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }

    private OSS getOssClient() {
        return new OSSClientBuilder().build("https://" + ossProperties.getEndpoint(), ossProperties.getAccessKeyId(), ossProperties.getAccessKeySecret());
    }

    private String generateUniqueFileName() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String buildObjectName(String path, String fileName) {
        return path.endsWith(CommonConstants.File.SLASH) ? path + fileName : path + CommonConstants.File.SLASH + fileName;
    }
}
