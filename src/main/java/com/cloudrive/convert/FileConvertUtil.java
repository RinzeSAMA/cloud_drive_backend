package com.cloudrive.convert;

import com.cloudrive.model.entity.FileInfo;
import com.cloudrive.model.entity.User;
import com.cloudrive.model.vo.FileListVO;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;

@Component
public class FileConvertUtil {
    public static FileListVO toFileListVO(FileInfo fileInfo) {
        if (fileInfo == null) return null;
        FileListVO vo = new FileListVO();
        vo.setId(fileInfo.getId());
        vo.setFilename(fileInfo.getFilename());
        vo.setOriginalFilename(fileInfo.getOriginalFilename());
        vo.setPath(fileInfo.getPath());
        vo.setFileSize(fileInfo.getFileSize());
        vo.setFileType(fileInfo.getFileType());
        vo.setParentId(fileInfo.getParentId());
        vo.setIsFolder(fileInfo.getIsFolder());
        vo.setCreatedAt(fileInfo.getCreatedAt());
        vo.setUpdatedAt(fileInfo.getUpdatedAt());
        vo.setDeleteTime(fileInfo.getDeleteTime());
        return vo;
    }
    public static FileInfo toFileInfo(MultipartFile file, String filePath, User user, Long parentId) {
        if (file == null && filePath == null && user == null && parentId == null) return null;
        FileInfo fileInfo = new FileInfo();
        if (file != null) {
            fileInfo.setFilename(file.getOriginalFilename());
            fileInfo.setOriginalFilename(file.getOriginalFilename());
            fileInfo.setFileSize(file.getSize());
            fileInfo.setFileType(file.getContentType());
        }
        fileInfo.setPath(filePath);
        fileInfo.setUserId(user.getId());
        fileInfo.setParentId(parentId);
        fileInfo.setIsFolder(false);
        fileInfo.setIsDeleted(0);
        fileInfo.setCreatedAt(LocalDateTime.now());
        fileInfo.setUpdatedAt(LocalDateTime.now());
        fileInfo.setSha256Hash(com.cloudrive.common.util.FileHashUtil.calculateSHA256(file));
        return fileInfo;
    }
    public static FileInfo toFileInfoForFastUpload(String filename, FileInfo existingFile, User user, Long parentId, String sha256Hash) {
        if (filename == null && existingFile == null && user == null && parentId == null && sha256Hash == null) return null;
        FileInfo fileInfo = new FileInfo();
        if (filename != null) {
            //赋相对路径值
            fileInfo.setOriginalFilename(filename);
        }
        if (existingFile != null) {
            fileInfo.setFilename(existingFile.getFilename());
            fileInfo.setPath(existingFile.getPath());
            fileInfo.setFileSize(existingFile.getFileSize());
            fileInfo.setFileType(existingFile.getFileType());
            fileInfo.setUserId(existingFile.getUserId());
        }
        fileInfo.setUserId(user.getId());
        fileInfo.setParentId(parentId);
        fileInfo.setSha256Hash(sha256Hash);
        fileInfo.setIsFolder(false);
        fileInfo.setIsDeleted(0);
        fileInfo.setCreatedAt(LocalDateTime.now());
        fileInfo.setUpdatedAt(LocalDateTime.now());
        return fileInfo;
    }
    public static FileInfo toFileInfoFromPath(String filename, String originalFilename, String filePath, long fileSize, User user, Long parentId, String sha256Hash) {
        if (filename == null && filePath == null && user == null && parentId == null && sha256Hash == null) return null;
        FileInfo fileInfo = new FileInfo();
        if (filename != null) {
            fileInfo.setFilename(filename); // 只保存文件名
        }
        if (originalFilename != null) {
            fileInfo.setOriginalFilename(originalFilename); // 保存前端相对路径
        }
        fileInfo.setPath(filePath);
        fileInfo.setFileSize(fileSize);
        fileInfo.setUserId(user.getId());
        fileInfo.setParentId(parentId);
        fileInfo.setSha256Hash(sha256Hash);
        fileInfo.setFileType(com.cloudrive.common.util.FileTypeUtil.getContentTypeFromFileName(filename));
        fileInfo.setIsFolder(false);
        fileInfo.setIsDeleted(0);
        fileInfo.setCreatedAt(LocalDateTime.now());
        fileInfo.setUpdatedAt(LocalDateTime.now());
        return fileInfo;
    }
} 