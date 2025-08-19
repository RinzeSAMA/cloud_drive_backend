package com.cloudrive.convert;

import com.cloudrive.model.entity.FileInfoEntity;
import com.cloudrive.model.entity.ShareRecordEntity;
import com.cloudrive.model.entity.UserEntity;
import com.cloudrive.model.vo.ShareFileVO;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ShareConvertUtil {
    public static ShareFileVO toShareFileVO(ShareRecordEntity shareRecord) {
        if (shareRecord == null) return null;
        ShareFileVO vo = new ShareFileVO();
        vo.setShareCode(shareRecord.getShareCode());
        vo.setExpireTime(shareRecord.getExpireTime());
        vo.setHasPassword(shareRecord.getPassword() != null && !shareRecord.getPassword().isEmpty());
        vo.setFileId(shareRecord.getFileId());
        vo.setVisitCount(shareRecord.getVisitCount());
        vo.setCreateTime(shareRecord.getCreateTime());
        vo.setIsExpired(shareRecord.getIsExpired());
        vo.setPassword(shareRecord.getPassword());
        // filename, fileSize等建议用VO专用查询，不再从实体对象取
        return vo;
    }
    public static ShareRecordEntity toShareRecord(FileInfoEntity file, UserEntity user, String shareCode, String password, LocalDateTime expireTime) {
        if (file == null && user == null && shareCode == null && password == null && expireTime == null) return null;
        ShareRecordEntity shareRecord = new ShareRecordEntity();
        if (file != null) {
            shareRecord.setFileId(file.getId());
            shareRecord.setUserId(file.getUserId());
            shareRecord.setCreatedAt(file.getCreatedAt());
            shareRecord.setUpdatedAt(file.getUpdatedAt());
        }
        shareRecord.setUser(user);
        shareRecord.setShareCode(shareCode);
        shareRecord.setPassword(password);
        shareRecord.setExpireTime(expireTime);
        shareRecord.setIsExpired(false);
        shareRecord.setVisitCount(0);
        shareRecord.setCreateTime(LocalDateTime.now());
        return shareRecord;
    }
} 