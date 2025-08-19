package com.cloudrive.convert;

import com.cloudrive.model.entity.FileInfoEntity;
import com.cloudrive.model.entity.UserEntity;
import com.cloudrive.model.vo.FileListVO;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;

@Component
public class FileConvertUtil {
    public static FileListVO toFileListVO(FileInfoEntity fileInfo) {
        if (fileInfo == null) return null;
        FileListVO vo = new FileListVO();
        vo.setId(fileInfo.getId());
        vo.setFilename(fileInfo.getFilename());
        vo.setOriginalFilename(fileInfo.getOriginFileName());
        vo.setPath(fileInfo.getUrl());
        vo.setFileSize(fileInfo.getSize());
        vo.setFileType(fileInfo.getType());
        vo.setParentId(fileInfo.getParentId());
        vo.setIsFolder(fileInfo.getIsFolder());
        vo.setCreatedAt(fileInfo.getCreatedAt());
        vo.setUpdatedAt(fileInfo.getUpdatedAt());
        vo.setDeleteTime(fileInfo.getDeleteTime());
        return vo;
    }
} 