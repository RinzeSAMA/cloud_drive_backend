package com.cloudrive.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cloudrive.common.enums.ErrorCode;
import com.cloudrive.common.util.BeanCopyUtils;
import com.cloudrive.common.util.ExceptionUtil;
import com.cloudrive.common.util.UserContext;
import com.cloudrive.convert.ShareConvertUtil;
import com.cloudrive.mapper.FileInfoMapper;
import com.cloudrive.mapper.ShareRecordMapper;
import com.cloudrive.model.entity.FileInfoEntity;
import com.cloudrive.model.entity.ShareRecordEntity;
import com.cloudrive.model.entity.UserEntity;
import com.cloudrive.model.vo.ShareFileVO;
import com.cloudrive.redis.ShareQueueRedis;
import com.cloudrive.redis.ShareTokenRedis;
import com.cloudrive.service.FileService;
import com.cloudrive.service.ShareService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 文件分享服务实现类
 */
@Service
@RequiredArgsConstructor
public class ShareServiceImpl extends ServiceImpl<ShareRecordMapper, ShareRecordEntity> implements ShareService {

    private static final Logger logger = LoggerFactory.getLogger(ShareServiceImpl.class);

//    private final ShareRecordRepository shareRecordRepository;

    private final ShareTokenRedis shareTokenRedis;

    private final ShareQueueRedis shareQueueRedis;

    private final FileService fileService;
    private final FileInfoMapper fileInfoMapper;

    private final ShareRecordMapper shareRecordMapper;


    @Override
    @Transactional
    public ShareFileVO createShare(Long fileId, LocalDateTime expireTime, String password) {
        // 获取当前登录用户
        UserEntity currentUser = UserContext.getCurrentUser();
        
        // 获取文件信息
        FileInfoEntity file = fileInfoMapper.selectById(fileId);
        ExceptionUtil.throwIfNull(file, ErrorCode.FILE_NOT_FOUND);
        
        // 验证文件权限
        ExceptionUtil.throwIf(
                !file.getUserId().equals(currentUser.getId()),
                ErrorCode.NO_SHARE_PERMISSION);
        
        // 生成分享码
        String shareCode = generateShareCode();
        
        // 创建分享记录
        ShareRecordEntity shareRecord = ShareConvertUtil.toShareRecord(file, currentUser, shareCode, password, expireTime);
        save(shareRecord);
        
        // 添加到延时队列
        addToDelayedQueue(shareRecord.getId(), expireTime);
        
        // 返回分享信息

        // 设置文件相关的
        ShareFileVO shareFileVO = new ShareFileVO();
        BeanUtils.copyProperties(shareRecord, shareFileVO);
        shareFileVO.setFilename(file.getFilename());
        shareFileVO.setFileSize(file.getSize());
        // 返回分享信息
        return shareFileVO;
    }

    @Override
    @Transactional
    public ShareFileVO accessShare(String shareCode, String password) {
        // 获取分享记录
        ShareRecordEntity shareRecord = shareRecordMapper.selectByShareCode(shareCode);
        ExceptionUtil.throwIfNull(shareRecord, ErrorCode.FILE_NOT_FOUND);
        
        // 检查是否过期
        ExceptionUtil.throwIf(
            shareRecord.getIsExpired() || shareRecord.getExpireTime().isBefore(LocalDateTime.now()),
            ErrorCode.SHARE_EXPIRED
        );
        
        // 验证密码
        if (shareRecord.getPassword() != null && !shareRecord.getPassword().isEmpty()) {
            // 如果没有提供密码，抛出缺少密码异常
            ExceptionUtil.throwIf(password == null || password.isEmpty(), ErrorCode.MISSING_PASSWORD);
            // 如果密码不匹配，抛出密码错误异常
            ExceptionUtil.throwIf(!shareRecord.getPassword().equals(password), ErrorCode.INVALID_PASSWORD);
        }
        
        // 检查文件是否存在且未被删除
        FileInfoEntity fileInfo = fileInfoMapper.selectById(shareRecord.getFileId());
        ExceptionUtil.throwIfNull(fileInfo, ErrorCode.FILE_NOT_FOUND);
        ExceptionUtil.throwIf(fileInfo.getIsDeleted() != 0, ErrorCode.FILE_NOT_FOUND);
        
        // 更新访问次数
        shareRecordMapper.incrementVisitCount(shareRecord.getId());

        ShareFileVO shareFileVO = new ShareFileVO();
        BeanUtils.copyProperties(shareRecord, shareFileVO);
        shareFileVO.setFilename(fileInfo.getFilename());
        shareFileVO.setFileSize(fileInfo.getSize());
        // 返回分享信息
        return shareFileVO;
    }

    @Override
    public boolean isShareExpired(String shareCode) {
        ShareRecordEntity shareRecord = shareRecordMapper.selectByShareCode(shareCode);
        ExceptionUtil.throwIfNull(shareRecord, ErrorCode.SHARE_NOT_FOUND);
        
        return shareRecord.getIsExpired() || shareRecord.getExpireTime().isBefore(LocalDateTime.now());
    }

    @Override
    @Transactional
    public void markExpiredShares() {
        // 获取所有过期的分享记录
        List<ShareRecordEntity> expiredShares = shareRecordMapper.selectExpiredButNotMarked(LocalDateTime.now());
        
        // 标记为过期
        for (ShareRecordEntity share : expiredShares) {
            share.setIsExpired(true);
        }
        
        // 批量保存
       saveBatch(expiredShares);
    }

    @Override
    public List<ShareFileVO> getSharedFiles() {
        // 获取当前用户ID
        Long userId = UserContext.getCurrentUserId();

        // 获取用户的所有分享记录
        List<ShareFileVO> shareFileVOS = shareRecordMapper.selectShareFileVOsByUserId(userId);

        // 转换为VO对象
        return shareFileVOS;
    }

    @Override
    @Transactional
    public void cancelShare(String shareCode) {
        // 获取当前用户ID
        Long userId = UserContext.getCurrentUserId();
        
        // 获取分享记录
        ShareRecordEntity shareRecord = shareRecordMapper.selectByShareCode(shareCode);
        ExceptionUtil.throwIfNull(shareRecord, ErrorCode.SHARE_NOT_FOUND);
        
        // 验证权限
        ExceptionUtil.throwIf(
            !shareRecord.getUserId().equals(userId),
            ErrorCode.NO_CANCEL_PERMISSION
        );
        
        // 从延时队列中移除
        shareQueueRedis.removeFromDelayedQueue(shareRecord.getId());
        
        // 删除分享记录
        shareRecordMapper.deleteById(shareRecord);
        
        // 删除token
        shareTokenRedis.deleteToken(shareCode);
    }

    @Override
    public String generateShareToken(String shareCode, String password) {
        ShareRecordEntity shareRecord = shareRecordMapper.selectByShareCode(shareCode);
        ExceptionUtil.throwIfNull(shareRecord, ErrorCode.SHARE_NOT_FOUND);
        
        // 验证密码
        if (shareRecord.getPassword() != null && !shareRecord.getPassword().isEmpty()) {
            ExceptionUtil.throwIf(
                    !shareRecord.getPassword().equals(password),
                ErrorCode.INVALID_PASSWORD
            );
        }
        
        return shareTokenRedis.generateAndStoreToken(shareCode);
    }

    @Override
    public boolean validateShareToken(String shareCode, String token) {
        return shareTokenRedis.validateToken(shareCode, token);
    }

    @Override
    public ShareFileVO accessShareByToken(String shareCode, String token) {
        ExceptionUtil.throwIf(
            !validateShareToken(shareCode, token),
            ErrorCode.INVALID_TOKEN
        );

        ShareRecordEntity shareRecord = shareRecordMapper.selectByShareCode(shareCode);
        ExceptionUtil.throwIfNull(shareRecord, ErrorCode.SHARE_NOT_FOUND);
                
        // 检查文件是否存在且未被删除
        FileInfoEntity fileInfo = fileInfoMapper.selectById(shareRecord.getFileId());
        ExceptionUtil.throwIfNull(fileInfo, ErrorCode.FILE_NOT_FOUND);
        ExceptionUtil.throwIf(fileInfo.getIsDeleted() != 0, ErrorCode.FILE_NOT_FOUND);
        
        shareRecord.setVisitCount(shareRecord.getVisitCount() + 1);
        shareRecordMapper.updateById(shareRecord);

        // 设置文件相关的
        ShareFileVO shareFileVO = new ShareFileVO();
        BeanUtils.copyProperties(shareRecord, shareFileVO);
        shareFileVO.setFilename(fileInfo.getFilename());
        shareFileVO.setFileSize(fileInfo.getSize());
        // 返回分享信息
        return shareFileVO;
    }

    @Override
    public byte[] downloadSharedFile(String shareCode, String token) {
        // 验证token
        ExceptionUtil.throwIf(
            !validateShareToken(shareCode, token),
            ErrorCode.INVALID_TOKEN
        );

        // 获取分享记录
        ShareRecordEntity shareRecord = shareRecordMapper.selectByShareCode(shareCode);
        ExceptionUtil.throwIfNull(shareRecord, ErrorCode.SHARE_NOT_FOUND);

        // 获取文件信息
        FileInfoEntity fileInfo = fileInfoMapper.selectById(shareRecord.getFileId());
        ExceptionUtil.throwIfNull(fileInfo, ErrorCode.FILE_NOT_FOUND);
        
        // 检查文件是否已被删除
        ExceptionUtil.throwIf(
            fileInfo.getIsDeleted()!=0,
            ErrorCode.FILE_NOT_FOUND
        );

        logger.debug("Downloading shared file: id={}, url={}", fileInfo.getId(), fileInfo.getUrl());
        
        // 使用getFileContent获取文件内容
        byte[] content = fileService.getFileContent(fileInfo.getId());
        logger.debug("File content size: {} bytes", content.length);
        
        return content;
    }

    @Override
    public String getFilename(String shareCode) {
        ShareRecordEntity shareRecord = shareRecordMapper.selectByShareCode(shareCode);
        ExceptionUtil.throwIfNull(shareRecord, ErrorCode.SHARE_NOT_FOUND);

        FileInfoEntity fileInfo = fileInfoMapper.selectById(shareRecord.getFileId());
        ExceptionUtil.throwIfNull(fileInfo, ErrorCode.FILE_NOT_FOUND);
        
        // 检查文件是否已被删除
        ExceptionUtil.throwIf(
            fileInfo.getIsDeleted()!=0,
            ErrorCode.FILE_NOT_FOUND
        );

        return fileInfo.getFilename();
    }

    private String generateShareCode() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private void addToDelayedQueue(Long shareId, LocalDateTime expireTime) {
        shareQueueRedis.addToDelayedQueue(shareId, expireTime);
    }
}