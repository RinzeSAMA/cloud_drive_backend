package com.cloudrive.controller;

import com.cloudrive.common.annotation.RateLimit;
import com.cloudrive.common.annotation.RateLimit.Dimension;
import com.cloudrive.common.result.Result;
import com.cloudrive.common.util.UserContext;
import com.cloudrive.model.dto.FileQueryDTO;
import com.cloudrive.model.dto.FileRenameDTO;
import com.cloudrive.model.vo.FileListVO;
import com.cloudrive.model.vo.FileListPageVO;
import com.cloudrive.service.FileService;
import com.cloudrive.service.UploadProgressService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * 文件管理
 */
@RestController
@Validated
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;
    private final UploadProgressService uploadProgressService;

    @Autowired
    public FileController(FileService fileService, UploadProgressService uploadProgressService) {
        this.fileService = fileService;
        this.uploadProgressService = uploadProgressService;
    }

    /**
     * 上传文件
     */
    @PostMapping
    @RateLimit(dimensions = { Dimension.USER, Dimension.IP }, permitsPerSecond = 3.0, timeout = 1000)
    public Result<String> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "parentId", required = false) Long parentId) {
        String filePath = fileService.uploadFile(file, parentId);
        return Result.success(filePath);
    }

    /**
     * 获取文件列表（分页）
     */
    @GetMapping
    @RateLimit(dimensions = { Dimension.USER }, permitsPerSecond = 10.0, timeout = 500)
    public Result<FileListPageVO> listFiles(FileQueryDTO queryDTO) {
        queryDTO.setUserId(UserContext.getCurrentUserId());
        FileListPageVO pageVO = fileService.getFileListWithPagination(queryDTO);
        return Result.success(pageVO);
    }

    /**
     * 搜索文件
     */
    @GetMapping("/search")
    @RateLimit(dimensions = { Dimension.USER }, permitsPerSecond = 5.0, timeout = 1000)
    public Result<List<FileListVO>> searchFiles(@RequestParam String keyword) {
        List<FileListVO> files = fileService.searchFiles(keyword);
        return Result.success(files);
    }

    /**
     * 下载文件
     */
    @GetMapping("/{fileId}/content")
    @RateLimit(dimensions = { Dimension.USER, Dimension.IP }, permitsPerSecond = 2.0, timeout = 1000)
    public ResponseEntity<byte[]> downloadFile(@PathVariable Long fileId) {
        byte[] content = fileService.downloadFile(fileId);
        // 设置响应头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.builder("attachment")
                .filename(fileService.getFilename(fileId), StandardCharsets.UTF_8)
                .build());
        headers.setContentLength(content.length);
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(content);
    }

    /**
     * 重命名文件
     */
    @PatchMapping("/{fileId}/name")
    @RateLimit(dimensions = { Dimension.USER }, permitsPerSecond = 5.0, timeout = 500)
    public Result<Void> renameFile(@PathVariable Long fileId, @Valid @RequestBody FileRenameDTO dto) {
        fileService.renameFile(fileId, dto.getNewFilename());
        return Result.success();
    }

    /**
     * 删除文件
     */
    @PostMapping ("/delFile")
    @RateLimit(dimensions = { Dimension.USER }, permitsPerSecond = 5.0, timeout = 500)
    public Result<Void> deleteFile(@RequestBody List<Long> fileIds ) {
        fileService.deleteFileToRecycleBin(fileIds);
        return Result.success();
    }
    
    /**
     * 上传文件并跟踪进度
     * @param file 文件
     * @param parentId 父文件夹ID
     * @return 上传任务ID
     */
    @PostMapping("/progress")
    public Result<String> uploadWithProgress(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "parentId", required = false) Long parentId) {
        
        try {
            // 生成唯一任务ID
            String taskId = UUID.randomUUID().toString();
            
            // 创建上传任务
            uploadProgressService.createUploadTask(taskId, file.getOriginalFilename(), file.getSize());
            
            // 在主线程中获取当前用户ID
            Long currentUserId = UserContext.getCurrentUserId();
            
            // 检查文件是否为空
            if (file.isEmpty()) {
                uploadProgressService.completeUploadTask(taskId, false, "文件为空");
                return Result.error("文件为空");
            }
            
            // 将文件保存到临时目录，以便在异步线程中使用
            File tempDir = new File(System.getProperty("java.io.tmpdir"));
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }
            
            // 创建临时文件
            File tempFile = new File(tempDir, taskId + "_" + file.getOriginalFilename());
            File parentDir = tempFile.getParentFile();
            if (!parentDir.exists()) {
                parentDir.mkdirs();
            }
            file.transferTo(tempFile);
            
            // 异步执行上传，避免阻塞请求
            Thread uploadThread = new Thread(() -> {
                try {
                    fileService.uploadFileWithProgressFromPath(tempFile.getAbsolutePath(), file.getOriginalFilename(), file.getSize(), parentId, taskId, currentUserId);
                }
                finally {
                    // 上传完成后删除临时文件
                    if (tempFile.exists()) {
                        tempFile.delete();
                    }
                }
            });
            uploadThread.setDaemon(true);
            uploadThread.start();
            
            return Result.success(taskId);
        } catch (Exception e) {
            return Result.error("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 新建文件夹，返回ID
     */
    @PostMapping("/folder")
    public Result<Long> createFolder(
            @RequestParam("folderName") String folderName,
            @RequestParam(value = "parentId", required = false) Long parentId
    ) {
        Long folderId = fileService.createFolderWithId(folderName, parentId, UserContext.getCurrentUserId());
        return Result.success(folderId);
    }


} 