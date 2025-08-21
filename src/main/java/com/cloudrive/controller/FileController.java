package com.cloudrive.controller;

import com.cloudrive.common.annotation.RateLimit;
import com.cloudrive.common.annotation.RateLimit.Dimension;
import com.cloudrive.common.result.Result;
import com.cloudrive.common.util.UserContext;
import com.cloudrive.model.common.FileUploadInfo;
import com.cloudrive.model.dto.FileQueryDTO;
import com.cloudrive.model.dto.FileRenameDTO;
import com.cloudrive.model.vo.FileListVO;
import com.cloudrive.model.vo.FileListPageVO;
import com.cloudrive.model.vo.UploadUrlsVO;
import com.cloudrive.service.FileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 文件管理
 */
@RestController
@Validated
@RequestMapping("/api/files")
@Slf4j
public class FileController {

    private final FileService fileService;

    @Autowired
    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    /**
     * 检查文件是否存在（判断是否进行断点续传）
     */
    @GetMapping("/multipart/check/{md5}")
    public Result<FileUploadInfo> checkFileByMd5(@PathVariable String md5) {
        log.info("查询 <{}> 文件是否存在、是否进行断点续传", md5);
        return fileService.checkFileByMd5(md5);
    }

    /**
     * 初始化文件分片地址及相关数据
     */
    @PostMapping("/multipart/init")
    public Result<UploadUrlsVO> initMultiPartUpload(@RequestBody FileUploadInfo fileUploadInfo) {
        log.info("通过 <{}> 初始化上传任务", fileUploadInfo);
        return fileService.initMultipartUpload(fileUploadInfo);
    }

    /**
     * 文件合并（单文件不会合并，仅信息入库）
     */
    @PostMapping("/multipart/merge/{md5}")
    public Result<String> mergeMultipartUpload(@RequestParam String md5,@RequestParam Long parentId) {
        log.info("通过 <{}> 合并上传任务", md5);
        return fileService.mergeMultipartUpload(md5,parentId);
    }

    /**
     * 下载文件（分片）
     */
    @GetMapping("/download/{id}")
    @RateLimit(dimensions = { Dimension.USER, Dimension.IP }, permitsPerSecond = 2.0, timeout = 1000)
    public ResponseEntity<byte[]> downloadMultipartFile(@PathVariable Long id, HttpServletRequest request, HttpServletResponse response) throws Exception {
        log.info("通过 <{}> 开始分片下载", id);
        return fileService.downloadMultipartFile(id, request, response);
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